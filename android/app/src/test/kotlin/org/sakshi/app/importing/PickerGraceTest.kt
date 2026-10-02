package org.sakshi.app.importing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class PickerGraceTest {
    private var expired = 0

    private fun TestScope.grace() = PickerGrace(this, { expired++ }, window = 2.minutes)

    @Test
    fun aStopWithoutAPickerIsNotCovered() = runTest {
        assertFalse(grace().coverStop())
    }

    @Test
    fun aStopDuringAPickerIsCoveredAndExpiresAfterTheWindow() = runTest {
        val grace = grace()
        grace.begin()
        assertTrue(grace.coverStop())
        delay(119.seconds)
        assertEquals(0, expired)
        delay(2.seconds)
        assertEquals(1, expired)
        assertFalse(grace.coverStop())
    }

    @Test
    fun returningCancelsTheTimer() = runTest {
        val grace = grace()
        grace.begin()
        grace.coverStop()
        grace.onStarted()
        delay(10.minutes)
        assertEquals(0, expired)
    }

    @Test
    fun endingThePickerStopsCoveringStops() = runTest {
        val grace = grace()
        grace.begin()
        grace.end()
        assertFalse(grace.coverStop())
    }
}
