package org.sakshi.core.vault

import java.time.Instant
import org.sakshi.core.database.JobStatus
import org.sakshi.core.database.ProcessingJobEntity
import org.sakshi.core.database.SakshiDatabase

/** Thin wrapper over the processing queue. Job ids are `"<evidenceId>:<stage>"`, so enqueueing is idempotent. */
public class JobQueue(private val database: SakshiDatabase, private val clock: () -> Instant) {
    private val dao get() = database.jobDao()

    /** Returns true if a job was added and false if one already existed for the same source bytes. */
    public suspend fun enqueue(
        evidenceId: String,
        stage: String,
        sourceSha256: String,
        consentGeneration: Int,
    ): Boolean = dao.enqueue(
        ProcessingJobEntity(
            id = jobId(evidenceId, stage),
            evidenceId = evidenceId,
            stage = stage,
            status = JobStatus.PENDING,
            sourceSha256 = sourceSha256,
            attempt = 0,
            consentGeneration = consentGeneration,
            lastErrorCode = null,
            enqueuedAtEpochMs = clock().toEpochMilli(),
        ),
    )

    /** Moves the oldest pending job to running and returns it, or null if none is pending. */
    public suspend fun claimNext(): ProcessingJobEntity? = dao.claimNext()

    public suspend fun complete(jobId: String) {
        dao.markComplete(jobId)
    }

    public suspend fun fail(jobId: String, errorCode: String) {
        dao.markFailed(jobId, errorCode)
    }

    /** Returns running jobs to pending after a restart and reports how many were reset. */
    public suspend fun recover(): Int = dao.resetRunning()

    public companion object {
        public fun jobId(evidenceId: String, stage: String): String = "$evidenceId:$stage"
    }
}
