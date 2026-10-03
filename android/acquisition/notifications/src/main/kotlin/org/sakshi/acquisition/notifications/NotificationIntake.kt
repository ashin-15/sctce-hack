package org.sakshi.acquisition.notifications

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** What the platform side of the intake must answer. Kept separate so the intake is pure Kotlin and unit tested. */
internal interface IntakeEnvironment {
    /** True when the lane may run on this Android version. */
    fun isAvailable(): Boolean

    /** True when the device is locked. An unknown state must be reported as locked (fail closed). */
    fun isDeviceLocked(): Boolean

    fun wallMs(): Long

    fun elapsedRealtimeMs(): Long
}

/** An item in the one bounded channel. */
internal sealed interface IntakeItem {
    val generation: Long

    class Snapshot(override val generation: Long, val value: NotificationSnapshot) : IntakeItem

    /** Marks the end of an active snapshot batch of one connection. */
    class Reconciled(override val generation: Long) : IntakeItem
}

/**
 * The callback side and the consumer side of the lane. The callback methods are cheap: a gate on settings and the
 * package name, a bounded snapshot, and a non-blocking `trySend` to ONE channel of [NotificationBounds.QUEUE_CAPACITY].
 * They never touch disk, crypto, a tokenizer or a model. A full channel drops the newest item and records an overflow,
 * which turns coverage to unknown; it never blocks the caller.
 *
 * One consumer coroutine in [scope] drains the channel in order into [differ] and [inbox]. Stop, lock and revoke bump
 * the generation under [processLock], so work that was already queued is discarded and nothing is added after a clear.
 */
internal class NotificationIntake(
    private val settings: () -> NotificationSettingsState,
    private val environment: IntakeEnvironment,
    private val tracker: CoverageTracker,
    private val differ: ObservationDiffer,
    private val inbox: CandidateInbox,
    scope: CoroutineScope,
) {
    private val channel = Channel<IntakeItem>(NotificationBounds.QUEUE_CAPACITY)
    private val processLock = Any()
    private val generation = AtomicLong(0)
    private val pending = AtomicInteger(0)

    @Volatile
    private var sessionId: String = UUID.randomUUID().toString()

    @Volatile
    private var sessionActive = true

    private val consumer = scope.launch {
        for (item in channel) process(item)
    }

    /** True when an allowlisted notification may be observed right now. Reads settings and the package name only. */
    private fun admits(packageName: String): Boolean {
        val current = settings()
        return sessionActive &&
            environment.isAvailable() &&
            current.enabled &&
            !current.paused &&
            packageName in current.allowlist
    }

    fun onPosted(source: NotificationSource) = observe(source, SnapshotOrigin.LIVE)

    /** The active notifications found when the listener connected, followed by the end-of-batch marker. */
    fun onActiveSnapshot(sources: List<NotificationSource>) {
        sources.forEach { observe(it, SnapshotOrigin.ACTIVE_SNAPSHOT) }
        if (sessionActive && environment.isAvailable()) enqueue(IntakeItem.Reconciled(generation.get()))
    }

    private fun observe(source: NotificationSource, origin: SnapshotOrigin) {
        if (!admits(source.packageName)) return
        val locked = environment.isDeviceLocked()
        if (locked && !settings().lockScreenPreviewsOptIn) {
            // Only the fact that an allowlisted notification occurred is kept. No text is read.
            tracker.onLockedWithheld()
            return
        }
        val snapshot = source.read(
            SnapshotRequest(origin, locked, sessionId, environment.wallMs(), environment.elapsedRealtimeMs()),
        )
        enqueue(IntakeItem.Snapshot(generation.get(), snapshot))
    }

    /** Removal is lifecycle metadata. Nothing from the notification extras is read. */
    fun onRemoved(source: NotificationSource, reasonCode: Int) {
        if (!admits(source.packageName)) return
        val snapshot = NotificationSnapshot(
            origin = SnapshotOrigin.REMOVAL,
            packageName = source.packageName,
            notificationKey = source.notificationKey,
            groupKey = source.groupKey,
            isGroupSummary = false,
            category = null,
            shortcutId = null,
            postTimeMs = source.postTimeMs,
            whenMs = null,
            isGroupConversation = null,
            conversationTitle = null,
            title = null,
            text = null,
            bigText = null,
            subText = null,
            messages = emptyList(),
            historicMessages = emptyList(),
            truncatedFields = emptySet(),
            visibility = NotificationVisibility.UNKNOWN,
            removalReasonCode = reasonCode,
            deviceLocked = environment.isDeviceLocked(),
            collectorSessionId = sessionId,
            observedWallMs = environment.wallMs(),
            elapsedRealtimeMs = environment.elapsedRealtimeMs(),
        )
        enqueue(IntakeItem.Snapshot(generation.get(), snapshot))
    }

    fun onConnected() {
        if (environment.isAvailable()) tracker.onConnected()
    }

    fun onDisconnected() {
        if (environment.isAvailable()) tracker.onDisconnected()
    }

    private fun enqueue(item: IntakeItem) {
        if (channel.trySend(item).isSuccess) pending.incrementAndGet() else tracker.onQueueOverflow()
    }

    private fun process(item: IntakeItem) {
        pending.decrementAndGet()
        synchronized(processLock) {
            if (item.generation != generation.get() || !sessionActive) return
            when (item) {
                is IntakeItem.Reconciled -> tracker.onReconciled()
                is IntakeItem.Snapshot -> processSnapshot(item.value)
            }
        }
        if (pending.get() == 0) tracker.onQueueDrained()
    }

    private fun processSnapshot(snapshot: NotificationSnapshot) {
        val current = settings()
        if (!current.enabled || current.paused || snapshot.packageName !in current.allowlist) return
        val result = differ.process(snapshot)
        inbox.add(result.newCandidates)
        result.lifecycle?.let(inbox::applyLifecycle)
        if (snapshot.origin != SnapshotOrigin.REMOVAL && !snapshot.deviceLocked) tracker.onUnlockedObservation()
    }

    /** Discards queued work and everything held, and ends the session. Safe from any thread. */
    fun stopAndClear() {
        synchronized(processLock) {
            sessionActive = false
            generation.incrementAndGet()
            discardChannel()
            differ.reset()
            inbox.clear()
            tracker.reset()
            tracker.setSessionActive(false)
        }
    }

    /** Starts a new session with a fresh collector session id. Nothing from an earlier session is restored. */
    fun beginSession() {
        synchronized(processLock) {
            generation.incrementAndGet()
            sessionId = UUID.randomUUID().toString()
            sessionActive = true
            tracker.setSessionActive(true)
        }
    }

    /** Discards what is queued and held when the person pauses. Observation resumes with new notifications only. */
    fun discardQueued() {
        synchronized(processLock) {
            generation.incrementAndGet()
            discardChannel()
        }
    }

    /** Drops everything queued. Callers hold [processLock]. */
    private fun discardChannel() {
        while (channel.tryReceive().isSuccess) pending.decrementAndGet()
    }

    fun close() {
        consumer.cancel()
        channel.close()
    }
}
