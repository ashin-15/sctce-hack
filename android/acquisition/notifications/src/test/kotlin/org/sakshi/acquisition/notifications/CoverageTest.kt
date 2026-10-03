package org.sakshi.acquisition.notifications

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverageTest {
    private val clock = MutableClock()
    private val tracker = CoverageTracker(clock)

    private fun connectedAndReconciled() {
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        tracker.onConnected()
        tracker.onReconciled()
    }

    private val detail get() = tracker.flow.value

    @Test
    fun `no state means all clear`() {
        val names = CoverageState.entries.map { it.name }
        assertEquals(
            setOf("UNAVAILABLE", "ACCESS_GRANTED_NOT_CONNECTED", "CONNECTED", "PAUSED", "COVERAGE_UNKNOWN"),
            names.toSet(),
        )
        val reasons = CoverageGapReason::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(reasons.none { "clear" in it || "nothing" in it || "safe" in it })
    }

    @Test
    fun `starts unavailable until everything is in place`() {
        assertEquals(CoverageState.UNAVAILABLE, tracker.state.value)
        assertEquals(UnavailableReason.ANDROID_VERSION, detail.unavailableReason)
        tracker.setAvailable(true)
        assertEquals(UnavailableReason.DISABLED, detail.unavailableReason)
        tracker.setEnabled(true)
        assertEquals(UnavailableReason.ACCESS_NOT_GRANTED, detail.unavailableReason)
        tracker.setAccessGranted(true)
        assertEquals(CoverageState.ACCESS_GRANTED_NOT_CONNECTED, tracker.state.value)
        assertNull(detail.unavailableReason)
    }

    @Test
    fun `access granted and connected are separate and a connection is unknown until reconciled`() {
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        tracker.onConnected()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        tracker.onReconciled()
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
    }

    @Test
    fun `disconnect opens a gap with the vault reason and reconnect closes it only after reconciliation`() {
        connectedAndReconciled()
        val start = clock.now
        tracker.onDisconnected()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        assertEquals(CoverageInterval(start, null, "listener_disconnected"), detail.gaps.single())
        clock.now = start.plus(Duration.ofMinutes(5))
        tracker.onConnected()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        tracker.onReconciled()
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
        assertEquals(CoverageInterval(start, start.plus(Duration.ofMinutes(5)), "listener_disconnected"), detail.gaps.single())
    }

    @Test
    fun `pause is reflected in coverage and recorded as a gap`() {
        connectedAndReconciled()
        tracker.setPaused(true)
        assertEquals(CoverageState.PAUSED, tracker.state.value)
        assertEquals("user_paused", detail.gaps.single().reason)
        tracker.setPaused(false)
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
        assertTrue(detail.gaps.single().endAt != null)
    }

    @Test
    fun `overflow makes coverage unknown and drained ends the gap but keeps the count`() {
        connectedAndReconciled()
        tracker.onQueueOverflow()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        assertEquals(1, detail.queueOverflowCount)
        tracker.onQueueDrained()
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
        assertEquals(1, detail.queueOverflowCount)
        assertEquals("queue_overflow", detail.gaps.single().reason)
    }

    @Test
    fun `a restart starts a gap with an unknown start`() {
        tracker.setAvailable(true)
        tracker.setEnabled(true)
        tracker.setAccessGranted(true)
        tracker.onProcessRestart()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, tracker.state.value)
        assertNull(detail.gaps.single().startAt)
        assertEquals("process_restart", detail.gaps.single().reason)
        tracker.onConnected()
        tracker.onReconciled()
        assertEquals(CoverageState.CONNECTED, tracker.state.value)
        val closed: CoverageInterval = detail.gaps.single()
        assertNull(closed.startAt)
        assertTrue(closed.endAt is Instant)
    }

    @Test
    fun `revoking access opens a gap and the state is unavailable`() {
        connectedAndReconciled()
        tracker.setAccessGranted(false)
        assertEquals(CoverageState.UNAVAILABLE, tracker.state.value)
        assertEquals(UnavailableReason.ACCESS_NOT_GRANTED, detail.unavailableReason)
        assertEquals("access_revoked", detail.gaps.single().reason)
    }

    @Test
    fun `locked content withheld is counted without content`() {
        connectedAndReconciled()
        tracker.onLockedWithheld()
        tracker.onLockedWithheld()
        assertEquals(2, detail.lockedWithheldCount)
        assertEquals("device_locked_content_withheld", detail.gaps.single().reason)
        tracker.onUnlockedObservation()
        assertTrue(detail.gaps.single().endAt != null)
    }

    @Test
    fun `the ledger is bounded and counts what it drops`() {
        connectedAndReconciled()
        repeat(NotificationBounds.LEDGER_MAX_INTERVALS + 3) {
            tracker.onQueueOverflow()
            tracker.onQueueDrained()
        }
        assertEquals(NotificationBounds.LEDGER_MAX_INTERVALS, detail.gaps.size)
        assertEquals(3, detail.gapsDropped)
    }

    @Test
    fun `an Android version below 30 is unavailable whatever else is true`() {
        connectedAndReconciled()
        tracker.setAvailable(false)
        assertEquals(CoverageState.UNAVAILABLE, tracker.state.value)
        assertEquals(UnavailableReason.ANDROID_VERSION, detail.unavailableReason)
    }

    @Test
    fun `a stopped session is unavailable and reset clears the ledger`() {
        connectedAndReconciled()
        tracker.onQueueOverflow()
        tracker.reset()
        tracker.setSessionActive(false)
        assertEquals(CoverageState.UNAVAILABLE, tracker.state.value)
        assertEquals(UnavailableReason.SESSION_STOPPED, detail.unavailableReason)
        assertTrue(detail.gaps.isEmpty())
        assertEquals(0, detail.queueOverflowCount)
    }

    @Test
    fun `dropped candidates feed the detail`() {
        tracker.onCandidatesDropped(4)
        assertEquals(4, detail.candidatesDropped)
    }
}
