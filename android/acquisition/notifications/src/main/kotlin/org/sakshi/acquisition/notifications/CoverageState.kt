package org.sakshi.acquisition.notifications

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the lane can honestly say about its own reach. There is deliberately no state that means everything was seen
 * or that nothing happened: silence from the lane is never reassurance (megaplan 13.2 and 17.3).
 */
public enum class CoverageState {
    /** The lane cannot observe: Android below 30, switched off, no access granted, or the session was stopped. */
    UNAVAILABLE,

    /** Access is granted and the lane is on, but the listener has not connected yet. */
    ACCESS_GRANTED_NOT_CONNECTED,

    /** The listener is connected and receiving. Some notifications may still never reach it. */
    CONNECTED,

    /** The person paused observation. Nothing is observed until it is resumed. */
    PAUSED,

    /** A gap is open or not yet reconciled: disconnect, queue overflow, process restart, or a reconnect without a reconciled snapshot. */
    COVERAGE_UNKNOWN,
}

/** Why the state is [CoverageState.UNAVAILABLE]. */
public enum class UnavailableReason { ANDROID_VERSION, DISABLED, ACCESS_NOT_GRANTED, SESSION_STOPPED }

/** Reason strings for coverage gaps. [LISTENER_DISCONNECTED] is the same string the vault already uses for `coverage_gap`. */
public object CoverageGapReason {
    public const val LISTENER_DISCONNECTED: String = "listener_disconnected"
    public const val QUEUE_OVERFLOW: String = "queue_overflow"
    public const val USER_PAUSED: String = "user_paused"
    public const val PROCESS_RESTART: String = "process_restart"
    public const val ACCESS_REVOKED: String = "access_revoked"
    public const val DEVICE_LOCKED_CONTENT_WITHHELD: String = "device_locked_content_withheld"
}

/**
 * A period the lane did not cover. [startAt] is null when the start is unknown (an unclean restart) and [endAt] is
 * null while the gap is still open. This is what a later step turns into a vault `coverage_gap` record.
 */
public data class CoverageInterval(val startAt: Instant?, val endAt: Instant?, val reason: String)

/** The coverage state and the content-free counters and ledger behind it. */
public data class CoverageDetail(
    val state: CoverageState,
    val unavailableReason: UnavailableReason?,
    val gaps: List<CoverageInterval>,
    val gapsDropped: Int,
    val queueOverflowCount: Int,
    val lockedWithheldCount: Int,
    val unreadableCount: Int,
    val candidatesDropped: Int,
)

/** Derives [CoverageState] and keeps the interval ledger. Thread-safe. Never holds notification content. */
internal class CoverageTracker(private val clock: Clock) {
    private val lock = Any()
    private val detail = MutableStateFlow(initial())
    private val stateFlow = MutableStateFlow(CoverageState.UNAVAILABLE)

    private var available = false
    private var enabled = false
    private var sessionActive = true
    private var accessGranted = false
    private var accessEverGranted = false
    private var paused = false
    private var connected = false
    private var reconciled = false
    private var everConnected = false
    private var overflowPending = false
    private var overflowCount = 0
    private var lockedCount = 0
    private var unreadableCount = 0
    private var candidatesDropped = 0
    private var gapsDropped = 0
    private val closed = ArrayList<CoverageInterval>()
    private val open = LinkedHashMap<String, Instant?>()

    val state: StateFlow<CoverageState> = stateFlow.asStateFlow()
    val flow: StateFlow<CoverageDetail> = detail.asStateFlow()

    fun setAvailable(value: Boolean) = update { available = value }

    fun setEnabled(value: Boolean) = update { enabled = value }

    fun setSessionActive(value: Boolean) = update { sessionActive = value }

    fun setAccessGranted(value: Boolean) = update {
        if (accessGranted && !value && accessEverGranted) openGap(CoverageGapReason.ACCESS_REVOKED, now())
        if (value) {
            accessEverGranted = true
            closeGap(CoverageGapReason.ACCESS_REVOKED, now())
        }
        accessGranted = value
    }

    fun setPaused(value: Boolean) = update {
        if (value && !paused) openGap(CoverageGapReason.USER_PAUSED, now())
        if (!value) closeGap(CoverageGapReason.USER_PAUSED, now())
        paused = value
    }

    /** The process started with the lane already on: the start of any gap is unknown. */
    fun onProcessRestart() = update { openGap(CoverageGapReason.PROCESS_RESTART, null) }

    fun onConnected() = update {
        connected = true
        everConnected = true
        reconciled = false
    }

    fun onDisconnected() = update {
        if (connected) openGap(CoverageGapReason.LISTENER_DISCONNECTED, now())
        connected = false
        reconciled = false
    }

    /** The active snapshot of a connection was processed, so the connection is reconciled and earlier gaps end. */
    fun onReconciled() = update {
        reconciled = true
        closeGap(CoverageGapReason.LISTENER_DISCONNECTED, now())
        closeGap(CoverageGapReason.PROCESS_RESTART, now())
    }

    fun onQueueOverflow() = update {
        overflowCount += 1
        overflowPending = true
        openGap(CoverageGapReason.QUEUE_OVERFLOW, now())
    }

    fun onQueueDrained() = update {
        if (overflowPending) {
            overflowPending = false
            closeGap(CoverageGapReason.QUEUE_OVERFLOW, now())
        }
    }

    fun onLockedWithheld() = update {
        lockedCount += 1
        openGap(CoverageGapReason.DEVICE_LOCKED_CONTENT_WITHHELD, now())
    }

    fun onUnlockedObservation() = update { closeGap(CoverageGapReason.DEVICE_LOCKED_CONTENT_WITHHELD, now()) }

    fun onUnreadable() = update { unreadableCount += 1 }

    fun onCandidatesDropped(count: Int) = update { candidatesDropped += count }

    /** Forgets counters, the ledger and connection facts. Settings-derived facts are kept by their owners. */
    fun reset() = update {
        connected = false
        reconciled = false
        everConnected = false
        overflowPending = false
        overflowCount = 0
        lockedCount = 0
        unreadableCount = 0
        candidatesDropped = 0
        gapsDropped = 0
        paused = false
        closed.clear()
        open.clear()
    }

    private fun now(): Instant = clock.instant()

    private fun openGap(reason: String, start: Instant?) {
        if (reason !in open) open[reason] = start
    }

    private fun closeGap(reason: String, end: Instant) {
        if (reason !in open) return
        closed += CoverageInterval(open.remove(reason), end, reason)
        while (closed.size > NotificationBounds.LEDGER_MAX_INTERVALS) {
            closed.removeAt(0)
            gapsDropped += 1
        }
    }

    private fun update(change: () -> Unit) {
        synchronized(lock) {
            change()
            val (derivedState, unavailable) = derive()
            val ledger = closed + open.map { (gapReason, start) -> CoverageInterval(start, null, gapReason) }
            stateFlow.value = derivedState
            detail.value = CoverageDetail(
                derivedState, unavailable, ledger, gapsDropped, overflowCount, lockedCount, unreadableCount, candidatesDropped,
            )
        }
    }

    private fun derive(): Pair<CoverageState, UnavailableReason?> = when {
        !available -> CoverageState.UNAVAILABLE to UnavailableReason.ANDROID_VERSION
        !enabled -> CoverageState.UNAVAILABLE to UnavailableReason.DISABLED
        !sessionActive -> CoverageState.UNAVAILABLE to UnavailableReason.SESSION_STOPPED
        !accessGranted -> CoverageState.UNAVAILABLE to UnavailableReason.ACCESS_NOT_GRANTED
        paused -> CoverageState.PAUSED to null
        !connected && !everConnected && CoverageGapReason.PROCESS_RESTART !in open ->
            CoverageState.ACCESS_GRANTED_NOT_CONNECTED to null
        !connected || !reconciled || overflowPending -> CoverageState.COVERAGE_UNKNOWN to null
        else -> CoverageState.CONNECTED to null
    }

    private fun initial() = CoverageDetail(CoverageState.UNAVAILABLE, UnavailableReason.ANDROID_VERSION, emptyList(), 0, 0, 0, 0, 0)
}
