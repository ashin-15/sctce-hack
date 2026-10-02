package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Access to the processing queue. */
@Dao
public abstract class JobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract suspend fun insertIfAbsent(job: ProcessingJobEntity): Long

    @Query("SELECT * FROM processing_job WHERE id = :id")
    public abstract suspend fun get(id: String): ProcessingJobEntity?

    /**
     * Enqueues a job once. Returns true when a row was added and false when the id already
     * existed for the same source bytes.
     *
     * @throws IllegalStateException when the id exists for a different `source_sha256`.
     */
    @Transaction
    public open suspend fun enqueue(job: ProcessingJobEntity): Boolean {
        val existing = get(job.id)
        if (existing == null) return insertIfAbsent(job) != -1L
        check(existing.sourceSha256 == job.sourceSha256) { "Job id already exists for different source bytes" }
        return false
    }

    /** Moves the oldest pending job to running and returns it, or null when none is pending. */
    @Transaction
    public open suspend fun claimNext(): ProcessingJobEntity? {
        val next = firstPending() ?: return null
        check(markRunning(next.id) == 1) { "Pending job could not be claimed" }
        return next.copy(status = JobStatus.RUNNING, attempt = next.attempt + 1)
    }

    @Query("SELECT * FROM processing_job WHERE status = 'pending' ORDER BY enqueued_at_epoch_ms, id LIMIT 1")
    protected abstract suspend fun firstPending(): ProcessingJobEntity?

    @Query("UPDATE processing_job SET status = 'running', attempt = attempt + 1 WHERE id = :id AND status = 'pending'")
    protected abstract suspend fun markRunning(id: String): Int

    @Query("UPDATE processing_job SET status = 'complete', last_error_code = NULL WHERE id = :id")
    public abstract suspend fun markComplete(id: String): Int

    @Query("UPDATE processing_job SET status = 'failed', last_error_code = :errorCode WHERE id = :id")
    public abstract suspend fun markFailed(id: String, errorCode: String): Int

    @Query("UPDATE processing_job SET status = 'cancelled' WHERE id = :id AND status IN ('pending', 'running')")
    public abstract suspend fun cancel(id: String): Int

    /** Returns running jobs to pending after a crash or restart. Returns how many were reset. */
    @Query("UPDATE processing_job SET status = 'pending' WHERE status = 'running'")
    public abstract suspend fun resetRunning(): Int

    @Query("SELECT * FROM processing_job WHERE status = :status ORDER BY enqueued_at_epoch_ms, id")
    public abstract fun observeByStatus(status: String): Flow<List<ProcessingJobEntity>>

    @Query("SELECT * FROM processing_job WHERE evidence_id = :evidenceId ORDER BY enqueued_at_epoch_ms, id")
    public abstract fun observeForEvidence(evidenceId: String): Flow<List<ProcessingJobEntity>>
}
