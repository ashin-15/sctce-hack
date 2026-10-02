package org.sakshi.app.importing

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sakshi.acquisition.importer.PendingBatch

/**
 * Holds one shared batch in memory until the user has unlocked the app. Nothing here is written to disk or to
 * saved instance state, so a batch is lost if the process dies. The batch is dropped when more than
 * [keepAfterLock] has passed since it was offered or since the session was last locked.
 *
 * Call every method from the main thread.
 */
class ImportCoordinator(
    private val clock: () -> Instant,
    private val keepAfterLock: Duration = Duration.ofMinutes(KEEP_MINUTES),
) {
    private var pending: PendingBatch? = null
    private var since: Instant = Instant.EPOCH
    private val mutableHasPending = MutableStateFlow(false)

    /** True while a batch is waiting. Does not notice expiry until [dropIfExpired] or [take] runs. */
    val hasPending: StateFlow<Boolean> = mutableHasPending.asStateFlow()

    /** Replaces any batch already waiting. */
    fun offer(batch: PendingBatch) {
        pending = batch
        since = clock()
        mutableHasPending.value = true
    }

    /** Restarts the expiry period for a batch that is still waiting. */
    fun sessionLocked() {
        if (pending != null) since = clock()
    }

    /** Releases the batch if it is too old. */
    fun dropIfExpired() {
        if (pending != null && clock().isAfter(since.plus(keepAfterLock))) clear()
    }

    /** Returns the waiting batch, if any and not expired, and forgets it. */
    fun take(): PendingBatch? {
        dropIfExpired()
        val batch = pending
        clear()
        return batch
    }

    private fun clear() {
        pending = null
        mutableHasPending.value = false
    }

    private companion object {
        const val KEEP_MINUTES = 5L
    }
}
