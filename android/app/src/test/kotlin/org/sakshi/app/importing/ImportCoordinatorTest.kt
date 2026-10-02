package org.sakshi.app.importing

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.acquisition.importer.PendingItem

class ImportCoordinatorTest {
    private var now = Instant.parse("2026-10-02T10:00:00Z")
    private val coordinator = ImportCoordinator({ now })

    private fun batch(text: String) =
        PendingBatch(ImportMechanism.SHARE_SEND, listOf(PendingItem.Text(0, text)), referrerClaim = null)

    @Test
    fun offeredBatchIsTakenOnceAndThenCleared() {
        val offered = batch("synthetic one")
        coordinator.offer(offered)
        assertTrue(coordinator.hasPending.value)
        assertSame(offered, coordinator.take())
        assertFalse(coordinator.hasPending.value)
        assertNull(coordinator.take())
    }

    @Test
    fun secondShareReplacesTheFirst() {
        coordinator.offer(batch("synthetic one"))
        val second = batch("synthetic two")
        coordinator.offer(second)
        assertSame(second, coordinator.take())
        assertNull(coordinator.take())
    }

    @Test
    fun pendingBatchSurvivesALockWithinFiveMinutes() {
        val offered = batch("synthetic one")
        coordinator.offer(offered)
        now = now.plus(Duration.ofMinutes(4))
        coordinator.sessionLocked()
        now = now.plus(Duration.ofMinutes(5))
        assertSame(offered, coordinator.take())
    }

    @Test
    fun pendingBatchIsDroppedAfterFiveMinutesSinceTheLock() {
        coordinator.offer(batch("synthetic one"))
        coordinator.sessionLocked()
        now = now.plus(Duration.ofMinutes(5)).plusSeconds(1)
        coordinator.dropIfExpired()
        assertFalse(coordinator.hasPending.value)
        assertNull(coordinator.take())
    }

    @Test
    fun takeAlsoRefusesAnExpiredBatch() {
        coordinator.offer(batch("synthetic one"))
        now = now.plus(Duration.ofMinutes(6))
        assertNull(coordinator.take())
    }

    @Test
    fun lockingWithNothingPendingDoesNothing() {
        coordinator.sessionLocked()
        assertEquals(false, coordinator.hasPending.value)
        assertNull(coordinator.take())
    }
}
