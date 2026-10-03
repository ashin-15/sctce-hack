package org.sakshi.acquisition.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** A source that counts reads and can fail on any touch of its extras. */
class FakeSource(
    override val packageName: String = APP,
    override val notificationKey: String = "key-1",
    override val groupKey: String? = null,
    override val postTimeMs: Long = T0,
    private val messages: List<SnapshotMessage> = listOf(msg("synthetic text")),
    private val extrasThrow: Boolean = false,
    private val shortcut: String? = "shortcut-$notificationKey",
) : NotificationSource {
    var reads = 0
        private set

    override fun read(request: SnapshotRequest): NotificationSnapshot {
        check(!extrasThrow) { "extras must not be touched" }
        reads += 1
        return snap(
            key = notificationKey,
            packageName = packageName,
            origin = request.origin,
            messages = messages,
            shortcut = shortcut,
            locked = request.deviceLocked,
            wall = request.observedWallMs,
            elapsed = request.elapsedRealtimeMs,
        ).copy(collectorSessionId = request.collectorSessionId)
    }
}

class FakeEnvironment(var available: Boolean = true, var locked: Boolean = false) : IntakeEnvironment {
    var wall = T0
    var elapsed = 1_000L

    override fun isAvailable(): Boolean = available

    override fun isDeviceLocked(): Boolean = locked

    override fun wallMs(): Long = wall

    override fun elapsedRealtimeMs(): Long = elapsed
}

@OptIn(ExperimentalCoroutinesApi::class)
class IntakeTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val environment = FakeEnvironment()
    private val tracker = CoverageTracker(MutableClock())
    private val inbox = CandidateInbox(onDropped = tracker::onCandidatesDropped)
    private val differ = differ()
    private var settings = NotificationSettingsState(enabled = true, allowlist = PackageAllowlist.of(listOf(APP)))
    private val intake = NotificationIntake({ settings }, environment, tracker, differ, inbox, scope)

    private val detail get() = tracker.flow.value

    private fun ready() {
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        intake.onConnected()
        intake.onActiveSnapshot(emptyList())
        scope.testScheduler.runCurrent()
    }

    private fun drain() = scope.testScheduler.runCurrent()

    @Test
    fun `an allowlisted notification becomes a candidate`() {
        ready()
        intake.onPosted(FakeSource())
        drain()
        assertEquals(listOf("synthetic text"), inbox.candidates.value.map { it.message.text })
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
    }

    @Test
    fun `a package that is not allowlisted is dropped before its extras are read`() {
        ready()
        intake.onPosted(FakeSource(packageName = "synthetic.other.app", extrasThrow = true))
        intake.onRemoved(FakeSource(packageName = "synthetic.other.app", extrasThrow = true), reasonCode = 8)
        intake.onActiveSnapshot(listOf(FakeSource(packageName = "synthetic.other.app", extrasThrow = true)))
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `the default empty allowlist observes nothing`() {
        settings = NotificationSettingsState(enabled = true)
        assertTrue(settings.allowlist.isEmpty)
        ready()
        val source = FakeSource(extrasThrow = true)
        intake.onPosted(source)
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `disabled observes nothing`() {
        ready()
        settings = settings.copy(enabled = false)
        val source = FakeSource()
        intake.onPosted(source)
        drain()
        assertEquals(0, source.reads)
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `paused observes nothing and coverage says paused`() {
        ready()
        settings = settings.copy(paused = true)
        tracker.setPaused(true)
        val source = FakeSource()
        intake.onPosted(source)
        drain()
        assertEquals(0, source.reads)
        assertEquals(CoverageState.PAUSED, tracker.state.value)
    }

    @Test
    fun `work queued before a pause is not processed after it`() {
        ready()
        intake.onPosted(FakeSource())
        intake.discardQueued()
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `an Android version below 30 observes nothing`() {
        environment.available = false
        val source = FakeSource()
        intake.onPosted(source)
        intake.onConnected()
        drain()
        assertEquals(0, source.reads)
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `overflow sets coverage unknown never blocks and drops only the newest`() {
        ready()
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
        // The consumer is parked, so nothing drains. The channel holds its capacity (plus at most one item that was
        // handed straight to the waiting consumer) and then the next callback is dropped instead of blocking.
        var posted = 0
        while (detail.queueOverflowCount == 0 && posted < 2 * NotificationBounds.QUEUE_CAPACITY) {
            intake.onPosted(FakeSource(notificationKey = "k$posted"))
            posted += 1
        }
        val accepted = posted - 1
        assertTrue(accepted in NotificationBounds.QUEUE_CAPACITY..NotificationBounds.QUEUE_CAPACITY + 1, "accepted $accepted")
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        assertEquals(1, detail.queueOverflowCount)
        drain()
        assertEquals(accepted, inbox.candidates.value.size)
        assertFalse(inbox.candidates.value.any { it.message.conversation.notificationKey == "k$accepted" })
        assertEquals(1, detail.queueOverflowCount)
        assertTrue(detail.gaps.any { it.reason == CoverageGapReason.QUEUE_OVERFLOW && it.endAt != null })
    }

    @Test
    fun `a locked device keeps no text by default`() {
        ready()
        environment.locked = true
        val source = FakeSource(extrasThrow = true)
        intake.onPosted(source)
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
        assertEquals(1, detail.lockedWithheldCount)
    }

    @Test
    fun `a locked device keeps available previews only with the separate opt in`() {
        ready()
        environment.locked = true
        settings = settings.copy(lockScreenPreviewsOptIn = true)
        val source = FakeSource()
        intake.onPosted(source)
        drain()
        assertEquals(1, source.reads)
        assertEquals(1, inbox.candidates.value.size)
        assertEquals(0, detail.lockedWithheldCount)
    }

    @Test
    fun `removal reads no extras and only adds lifecycle to a held candidate`() {
        ready()
        intake.onPosted(FakeSource())
        drain()
        intake.onRemoved(FakeSource(extrasThrow = true), reasonCode = 10)
        drain()
        val kept = inbox.candidates.value.single()
        assertEquals("synthetic text", kept.message.text)
        assertEquals(10, kept.removal?.platformReasonCode)
    }

    @Test
    fun `an active snapshot batch reconciles coverage and by default only seeds state`() {
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        intake.onConnected()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        val source = FakeSource()
        intake.onActiveSnapshot(listOf(source))
        drain()
        assertEquals(1, source.reads)
        assertTrue(inbox.candidates.value.isEmpty())
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
        // The seeded content is not reported again by a live update of the same notification.
        intake.onPosted(FakeSource())
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `an active platform batch interrupted by lock cannot reconcile the next session`() {
        ready()
        val source = FakeSource()
        intake.onActiveSnapshot {
            intake.stopAndClear()
            intake.beginSession()
            intake.onConnected()
            listOf(source)
        }
        drain()
        assertEquals(0, source.reads)
        assertTrue(inbox.candidates.value.isEmpty())
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        intake.onActiveSnapshot(emptyList())
        drain()
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
    }

    @Test
    fun `an extras read interrupted by lock cannot leak into the next collector session`() {
        ready()
        val underlying = FakeSource()
        val interrupted = object : NotificationSource by underlying {
            override fun read(request: SnapshotRequest): NotificationSnapshot {
                val snapshot = underlying.read(request)
                intake.stopAndClear()
                intake.beginSession()
                intake.onConnected()
                return snapshot
            }
        }
        intake.onPosted(interrupted)
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
        intake.onPosted(FakeSource(notificationKey = "new-session-key"))
        drain()
        assertEquals(1, inbox.candidates.value.size)
    }

    @Test
    fun `an active snapshot is reported and tagged when the person opted in`() {
        val reporting = NotificationIntake(
            { settings }, environment, tracker, differ(ActiveSnapshotPolicy.REPORT_AS_ACTIVE_SNAPSHOT), inbox, scope,
        )
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        reporting.onConnected()
        reporting.onActiveSnapshot(listOf(FakeSource()))
        drain()
        assertEquals(SnapshotOrigin.ACTIVE_SNAPSHOT, inbox.candidates.value.single().message.origin)
        reporting.close()
    }

    @Test
    fun `a disconnect makes coverage unknown`() {
        ready()
        intake.onDisconnected()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
    }

    @Test
    fun `stop and clear leaves nothing reachable`() {
        ready()
        intake.onPosted(FakeSource(notificationKey = "k1"))
        drain()
        intake.onPosted(FakeSource(notificationKey = "k2"))
        intake.stopAndClear()
        drain()
        assertTrue(inbox.candidates.value.isEmpty())
        assertEquals(0, inbox.approximateBytes)
        assertEquals(0, differ.trackedScopeCount)
        assertTrue(detail.gaps.isEmpty())
        assertEquals(CoverageState.UNAVAILABLE, tracker.state.value)
        assertEquals(UnavailableReason.SESSION_STOPPED, detail.unavailableReason)
        // Nothing is accepted after the stop.
        val after = FakeSource(notificationKey = "k3")
        intake.onPosted(after)
        drain()
        assertEquals(0, after.reads)
        assertTrue(inbox.candidates.value.isEmpty())
    }

    @Test
    fun `a new session after a stop starts empty with a new session id`() {
        ready()
        intake.onPosted(FakeSource(notificationKey = "k1"))
        drain()
        val firstSession = inbox.candidates.value.single().message.collector.sessionId
        intake.stopAndClear()
        intake.beginSession()
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        assertTrue(inbox.candidates.value.isEmpty())
        intake.onPosted(FakeSource(notificationKey = "k1"))
        drain()
        assertNotEquals(firstSession, inbox.candidates.value.single().message.collector.sessionId)
    }

    @Test
    fun `candidates are processed in arrival order by one consumer`() {
        ready()
        listOf("first", "second", "third").forEachIndexed { index, text ->
            intake.onPosted(FakeSource(notificationKey = "k$index", messages = listOf(msg(text))))
        }
        drain()
        assertEquals(listOf("first", "second", "third"), inbox.candidates.value.map { it.message.text })
    }

    @Test
    fun `the inbox bound feeds the coverage detail`() {
        val small = CandidateInbox(maxRecords = 2, onDropped = tracker::onCandidatesDropped)
        val bounded = NotificationIntake({ settings }, environment, tracker, differ(), small, scope)
        ready()
        repeat(3) { bounded.onPosted(FakeSource(notificationKey = "k$it", messages = listOf(msg("text $it")))) }
        drain()
        assertEquals(1, detail.candidatesDropped)
        assertEquals(1, small.droppedCount.value)
        bounded.close()
    }
}
