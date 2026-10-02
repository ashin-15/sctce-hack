package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.JobStatus

class JobQueueTest : VaultTestBase() {
    private suspend fun importOne() =
        evidence.import(request(cases.create("Synthetic").id), ByteArrayInputStream(ByteArray(20)))

    @Test
    fun idIsDeterministicAndEnqueueIsIdempotent() = runBlocking<Unit> {
        val imported = importOne()
        assertTrue(jobs.enqueue(imported.id, "ocr", imported.sha256, 1))
        assertFalse(jobs.enqueue(imported.id, "ocr", imported.sha256, 1))
        assertEquals("${imported.id}:ocr", JobQueue.jobId(imported.id, "ocr"))
        assertNotNull(db.jobDao().get("${imported.id}:ocr"))
        assertFailsWith<IllegalStateException> { jobs.enqueue(imported.id, "ocr", "0".repeat(64), 1) }
    }

    @Test
    fun claimCompleteAndFail() = runBlocking<Unit> {
        val imported = importOne()
        jobs.enqueue(imported.id, "ocr", imported.sha256, 1)
        jobs.enqueue(imported.id, "stt", imported.sha256, 1)
        val first = assertNotNull(jobs.claimNext())
        val second = assertNotNull(jobs.claimNext())
        assertNull(jobs.claimNext())
        jobs.complete(first.id)
        jobs.fail(second.id, "synthetic_error")
        assertEquals(JobStatus.COMPLETE, db.jobDao().get(first.id)!!.status)
        val failed = db.jobDao().get(second.id)!!
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals("synthetic_error", failed.lastErrorCode)
    }

    @Test
    fun recoverReturnsRunningJobsToPending() = runBlocking<Unit> {
        val imported = importOne()
        jobs.enqueue(imported.id, "ocr", imported.sha256, 1)
        val claimed = assertNotNull(jobs.claimNext())
        assertEquals(1, jobs.recover())
        assertEquals(JobStatus.PENDING, db.jobDao().get(claimed.id)!!.status)
        assertEquals(0, jobs.recover())
        assertEquals(2, assertNotNull(jobs.claimNext()).attempt)
    }
}
