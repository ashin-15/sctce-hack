package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SakshiDatabase

/** Cancelling a caller must never leave a blob file without rows, or rows without a blob file. */
class CancellationTest : VaultTestBase() {
    /** Serves [total] bytes in small reads and runs [onTrigger] once, at [triggerAt] bytes or at end of stream. */
    private class TriggeringStream(
        private val total: Int,
        private val triggerAt: Int,
        private val onTrigger: () -> Unit,
    ) : InputStream() {
        private var served = 0
        private var fired = false

        private fun fire() {
            if (!fired) {
                fired = true
                onTrigger()
            }
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (served >= total) {
                if (triggerAt == AT_END) fire()
                return -1
            }
            val count = minOf(length, SLICE, total - served)
            buffer.fill(MARKER_BYTE, offset, offset + count)
            served += count
            if (triggerAt != AT_END && served >= triggerAt) fire()
            return count
        }

        companion object {
            const val AT_END = -1
            const val SLICE = 1000
            const val MARKER_BYTE: Byte = 7
        }
    }

    private class CancellingAuditLog(
        database: SakshiDatabase,
        clock: () -> Instant,
        private val cancel: () -> Unit,
    ) : AuditLog(database, clock) {
        override suspend fun append(
            action: String,
            subjectType: String,
            subjectId: String,
            details: kotlinx.serialization.json.JsonObject,
        ): Long {
            if (action == AuditActions.EVIDENCE_IMPORTED) cancel()
            return super.append(action, subjectType, subjectId, details)
        }
    }

    /** Cancels the job the first time work is dispatched to it, i.e. at the first file operation. */
    private class CancellingDispatcher(private val cancel: () -> Unit) : CoroutineDispatcher() {
        private var fired = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!fired) {
                fired = true
                cancel()
            }
            Dispatchers.IO.dispatch(context, block)
        }
    }

    @Test
    fun cancelDuringTheStreamReadLeavesNothing() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        lateinit var job: Job
        val stream = TriggeringStream(total = 200_000, triggerAt = 10_000) { job.cancel() }
        job = launch(start = CoroutineStart.LAZY) { evidence.import(request(caseId), stream) }
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(blobFiles().isEmpty())
        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
        assertEquals(1, audit.count())
    }

    @Test
    fun cancelExactlyAfterTheLastReadLeavesNothing() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        lateinit var job: Job
        val stream = TriggeringStream(total = 20_000, triggerAt = TriggeringStream.AT_END) { job.cancel() }
        job = launch(start = CoroutineStart.LAZY) { evidence.import(request(caseId), stream) }
        job.start()
        job.join()

        // The blob was fully written and renamed into place before the cancellation was noticed.
        assertTrue(job.isCancelled)
        assertTrue(blobFiles().isEmpty())
        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
        assertEquals(1, audit.count())
    }

    @Test
    fun cancelDuringRecordCompletesTheRecordThenPropagates() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        lateinit var job: Job
        val cancelling = CancellingAuditLog(db, clock) { job.cancel() }
        val repository = EvidenceRepository(db, blobs, cancelling, clock, ids, Dispatchers.IO)
        job = launch(start = CoroutineStart.LAZY) { repository.import(request(caseId), ByteArrayInputStream(ByteArray(5000))) }
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        val listed = evidence.observeForCase(caseId).first()
        assertEquals(1, listed.size)
        assertEquals(1, blobFiles().size)
        assertEquals(VerificationResult.Intact, evidence.verify(listed.single().id))
        assertIs<AuditVerification.Valid>(audit.verify())
        assertEquals(listOf("case.created", "evidence.imported", "evidence.verified"), audit.records().map { it.action })
    }

    @Test
    fun cancelledBeforeStartingWritesNothing() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val job = launch(start = CoroutineStart.LAZY) { evidence.import(request(caseId), ByteArrayInputStream(ByteArray(10))) }
        job.cancel()
        job.join()
        assertTrue(blobFiles().isEmpty())
        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
    }

    @Test
    fun cancelAfterEvidenceRowsAreGoneStillErasesTheFile() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val imported = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(100)))
        lateinit var job: Job
        val repository = EvidenceRepository(db, blobs, audit, clock, ids, CancellingDispatcher { job.cancel() })
        job = launch(start = CoroutineStart.LAZY) { repository.delete(imported.id) }
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(db.evidenceDao().get(imported.id))
        assertTrue(blobFiles().isEmpty())
    }

    @Test
    fun cancelAfterCaseRowsAreGoneStillErasesTheFiles() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        evidence.import(request(caseId), ByteArrayInputStream(ByteArray(100)))
        evidence.import(request(caseId), ByteArrayInputStream(ByteArray(200)))
        lateinit var job: Job
        val repository = CaseRepository(db, blobs, audit, clock, ids, CancellingDispatcher { job.cancel() })
        job = launch(start = CoroutineStart.LAZY) { repository.delete(caseId) }
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(db.caseDao().get(caseId))
        assertTrue(blobFiles().isEmpty())
    }
}
