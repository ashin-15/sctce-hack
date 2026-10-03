package org.sakshi.acquisition.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {
    private val whatsapp = "com.whatsapp"

    @Test fun defaultOffAndUnconnectedSessionCannotStart() {
        val session = CaptureSession()
        assertFalse(session.status.value.active)
        assertFalse(session.start(setOf(whatsapp), 0))
        session.observe(whatsapp, listOf("private"), 1, 1)
        assertTrue(session.candidates.value.isEmpty())
    }

    @Test fun rejectsEmptyUnknownAndSystemPackages() {
        val session = connected()
        assertFalse(session.start(emptySet(), 0))
        assertFalse(session.start(setOf("com.android.settings"), 0))
        assertFalse(session.start(setOf(whatsapp, "unknown.app"), 0))
    }

    @Test fun boundedSessionOnlyObservesSelectedAppAndDeduplicatesSnapshots() {
        val session = active()
        session.observe("org.telegram.messenger", listOf("secret"), 1, 1)
        session.observe(whatsapp, listOf("visible", "text"), 2, 2)
        session.observe(whatsapp, listOf("visible", "text"), 3, 3)
        val candidate = session.candidates.value.single()
        assertEquals("visible\ntext", candidate.text)
        assertEquals("UNKNOWN", candidate.direction)
        assertEquals("UNKNOWN", candidate.messageBoundaries)
        assertEquals(null, candidate.sender)
        assertEquals(2L, candidate.collectorElapsedRealtimeMs)
        session.observe(whatsapp, listOf("expired"), 4, CaptureSession.MAX_SESSION_MS)
        assertFalse(session.status.value.active)
        assertEquals(1, session.candidates.value.size)
    }

    @Test fun stopRetainsReviewWhileClearAndDisconnectDiscard() {
        val session = active()
        session.observe(whatsapp, listOf("review me"), 1, 1)
        session.stop()
        assertEquals(1, session.candidates.value.size)
        session.observe(whatsapp, listOf("not captured"), 2, 2)
        assertEquals(1, session.candidates.value.size)
        session.clear()
        assertTrue(session.candidates.value.isEmpty())
        session.start(setOf(whatsapp), 3)
        session.observe(whatsapp, listOf("new session"), 4, 4)
        session.connection(false)
        assertTrue(session.candidates.value.isEmpty())
        assertFalse(session.status.value.active)
    }

    @Test fun ephemeralMarkerStopsAndClearsAllHeldText() {
        val session = active()
        session.observe(whatsapp, listOf("earlier"), 1, 1)
        session.observe(whatsapp, listOf("View once", "do not collect"), 2, 2)
        assertFalse(session.status.value.active)
        assertTrue(session.candidates.value.isEmpty())
        assertTrue(CaptureSession.isRestricted("Disappearing messages enabled"))
        assertTrue(CaptureSession.isRestricted("SECRET CHAT"))
    }

    @Test fun candidateMemoryLimitStopsCaptureWithoutGrowingQueue() {
        val session = active()
        repeat(CaptureSession.MAX_CANDIDATES + 5) { session.observe(whatsapp, listOf("snapshot $it"), 1, 1) }
        assertEquals(CaptureSession.MAX_CANDIDATES, session.candidates.value.size)
        assertFalse(session.status.value.active)
    }

    @Test fun newSessionClearsPreviousTextAndDeduplication() {
        val session = active()
        session.observe(whatsapp, listOf("text"), 1, 1)
        val oldId = session.candidates.value.single().collectorSessionId
        session.start(setOf(whatsapp), 2)
        assertTrue(session.candidates.value.isEmpty())
        session.observe(whatsapp, listOf("text"), 3, 3)
        assertFalse(oldId == session.candidates.value.single().collectorSessionId)
    }

    private fun connected(): CaptureSession = CaptureSession().also { it.connection(true) }
    private fun active(): CaptureSession = connected().also { assertTrue(it.start(setOf(whatsapp), 0)) }
}
