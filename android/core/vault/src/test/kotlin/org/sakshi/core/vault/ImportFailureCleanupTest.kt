package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.core.crypto.KeyWrapper

/** Every way an import can fail after the copy has started must leave no evidence row, no file and no audit entry. */
class ImportFailureCleanupTest : VaultTestBase() {
    private fun newCase(): String = runBlocking { cases.create("Synthetic").id }

    private fun auditCount(): Int = runBlocking { assertIs<AuditVerification.Valid>(audit.verify()).count }

    private fun assertNothingLeft(caseId: String, auditBefore: Int, label: String) {
        assertTrue(runBlocking { evidence.observeForCase(caseId).first() }.isEmpty(), "$label: evidence row left")
        assertEquals(emptyList(), blobDirectory.list()?.toList().orEmpty(), "$label: file left")
        assertEquals(auditBefore, auditCount(), "$label: audit entry written")
    }

    private class ThrowingAfter(private val good: Int, private val failure: () -> Throwable) : InputStream() {
        private var served = 0

        override fun read(): Int {
            if (served >= good) throw failure()
            served++
            return 1
        }
    }

    @Test
    fun aKeyWrapFailureAfterTheFileWasWrittenLeavesNothing() = runBlocking<Unit> {
        val caseId = newCase()
        val before = auditCount()
        val failing = object : KeyWrapper {
            override fun wrap(secret: ByteArray): ByteArray = throw VaultKeyException.Unavailable(null)

            override fun unwrap(wrapped: ByteArray): ByteArray = throw VaultKeyException.Unavailable(null)
        }
        val repository = EvidenceRepository(db, BlobStore(blobDirectory, failing, 4096), audit, clock, ids)
        assertFailsWith<VaultKeyException.Unavailable> { repository.import(request(caseId), ByteArrayInputStream(ByteArray(9000))) }
        assertNothingLeft(caseId, before, "wrap failure")
    }

    @Test
    fun aRuntimeFailureOfTheSourceMidCopyLeavesNothing() = runBlocking<Unit> {
        val caseId = newCase()
        val before = auditCount()
        assertFailsWith<SecurityException> {
            evidence.import(request(caseId), ThrowingAfter(9000) { SecurityException("synthetic revoked grant") })
        }
        assertNothingLeft(caseId, before, "revoked grant")
        assertFailsWith<IllegalStateException> {
            evidence.import(request(caseId), ThrowingAfter(100) { IllegalStateException("synthetic") })
        }
        assertNothingLeft(caseId, before, "illegal state")
    }

    @Test
    fun aBlobDirectoryThatCannotBeUsedFailsWithoutRowsOrAuditEntries() = runBlocking<Unit> {
        val caseId = newCase()
        val before = auditCount()
        val root = File(context.noBackupFilesDir, "vault")
        root.deleteRecursively()
        root.mkdirs()
        File(root, "blobs").writeBytes(byteArrayOf(1))

        assertFailsWith<IllegalStateException> { evidence.import(request(caseId), ByteArrayInputStream(ByteArray(10))) }

        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
        assertEquals(before, auditCount())
        assertEquals(listOf("blobs"), root.list().orEmpty().toList(), "no other file may appear")
    }

    @Test
    fun anOverLimitInputLeavesNothingWhetherTheLimitIsMissedByOneByteOrMore() = runBlocking<Unit> {
        val caseId = newCase()
        val before = auditCount()
        for (size in listOf(101, 102, 4096, 4097, 9000)) {
            assertFailsWith<org.sakshi.core.crypto.BlobTooLargeException> {
                evidence.import(request(caseId, maxBytes = 100), ByteArrayInputStream(ByteArray(size)))
            }
            assertNothingLeft(caseId, before, "size $size")
        }
        evidence.import(request(caseId, maxBytes = 100), ByteArrayInputStream(ByteArray(100)))
        assertEquals(1, evidence.observeForCase(caseId).first().size)
    }

    @Test
    fun aSourceThatReportsNoSizeIsLimitedByTheBytesItDelivers() = runBlocking<Unit> {
        val caseId = newCase()
        val before = auditCount()
        val endless = object : InputStream() {
            override fun read(): Int = 7
        }
        assertFailsWith<org.sakshi.core.crypto.BlobTooLargeException> {
            evidence.import(request(caseId, maxBytes = 50_000), endless)
        }
        assertNothingLeft(caseId, before, "endless source")
    }
}
