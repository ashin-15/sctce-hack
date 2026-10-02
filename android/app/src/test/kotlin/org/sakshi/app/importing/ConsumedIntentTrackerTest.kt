package org.sakshi.app.importing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsumedIntentTrackerTest {
    @Test
    fun anIdIsHandledOnceEvenAfterRecreation() {
        val tracker = ConsumedIntentTracker()
        assertTrue(tracker.shouldProcess("synthetic-id", recreated = false))
        assertFalse(tracker.shouldProcess("synthetic-id", recreated = true))
        val restored = ConsumedIntentTracker(tracker.lastId)
        assertFalse(restored.shouldProcess("synthetic-id", recreated = true))
    }

    @Test
    fun aNewIdIsHandled() {
        val tracker = ConsumedIntentTracker("synthetic-old")
        assertTrue(tracker.shouldProcess("synthetic-new", recreated = false))
        assertEquals("synthetic-new", tracker.lastId)
    }

    @Test
    fun anIntentWithoutAnIdIsHandledUnlessTheActivityWasRecreated() {
        val tracker = ConsumedIntentTracker()
        assertTrue(tracker.shouldProcess(null, recreated = false))
        assertFalse(tracker.shouldProcess(null, recreated = true))
    }
}
