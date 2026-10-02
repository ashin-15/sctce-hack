package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class OrphanSweeperTest : VaultTestBase() {
    @Test
    fun deletesOrphansReportsMissingFilesAndKeepsHealthyBlobs() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val healthy = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(50)))
        val lost = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(60)))
        val lostPath = db.evidenceDao().getBlob(lost.id)!!.path
        val healthyPath = db.evidenceDao().getBlob(healthy.id)!!.path
        File(blobDirectory, lostPath).delete()
        val orphan = File(blobDirectory, "${"c".repeat(32)}.skb").apply { writeBytes(ByteArray(10)) }
        File(blobDirectory, "${"d".repeat(32)}.tmp").writeBytes(ByteArray(10))

        val report = sweeper.sweep()

        assertEquals(1, report.orphanFilesDeleted)
        assertEquals(1, report.temporaryFilesDeleted)
        assertEquals(listOf(lost.id), report.missingFileEvidenceIds)
        assertTrue(!orphan.exists())
        assertEquals(listOf(healthyPath), blobFiles())
        assertNotNull(db.evidenceDao().get(lost.id))
    }

    @Test
    fun sweepOnAnEmptyVaultIsANoOp() = runBlocking<Unit> {
        val report = sweeper.sweep()
        assertEquals(0, report.orphanFilesDeleted)
        assertEquals(0, report.temporaryFilesDeleted)
        assertTrue(report.missingFileEvidenceIds.isEmpty())
    }
}
