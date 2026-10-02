package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.core.crypto.BlobTooLargeException
import org.sakshi.core.database.SupportState
import org.sakshi.core.integrity.Sha256

class EvidenceRepositoryTest : VaultTestBase() {
    private val pngHead = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(8000) { 7 }

    private suspend fun newCase(): String = cases.create("Synthetic").id

    @Test
    fun importStoresDigestRowsAndSeparateMimeValues() = runBlocking<Unit> {
        val caseId = newCase()
        val imported = evidence.import(request(caseId, declaredMime = "image/jpeg"), ByteArrayInputStream(pngHead))

        assertEquals(Sha256.hex(Sha256.digest(pngHead)), imported.sha256)
        assertEquals(pngHead.size.toLong(), imported.byteSize)
        assertEquals("image/png", imported.detectedMime)
        val row = db.evidenceDao().get(imported.id)!!
        assertEquals("image/jpeg", row.declaredMime)
        assertEquals("image/png", row.detectedMime)
        assertEquals(imported.sha256, row.sha256)
        assertEquals(SupportState.SAVED, db.evidenceDao().getState(imported.id)!!.supportState)
        val metadata = db.evidenceDao().getMetadata(imported.id)!!
        assertEquals("synthetic.authority", metadata.uriAuthorityClaim)
        assertEquals("synthetic-name.bin", metadata.displayNameClaim)
        assertEquals(SupportState.SAVED, evidence.observeForCase(caseId).first().single().supportState)
        assertEquals(2, assertIs<AuditVerification.Valid>(audit.verify()).count)
    }

    @Test
    fun plainTextHasNoDetectedMime() = runBlocking<Unit> {
        val imported = evidence.import(request(newCase()), ByteArrayInputStream("hello".toByteArray()))
        assertNull(imported.detectedMime)
        assertEquals("text/plain", db.evidenceDao().get(imported.id)!!.declaredMime)
    }

    @Test
    fun openOriginalReturnsIdenticalBytes() = runBlocking<Unit> {
        val plaintext = ByteArray(10_000) { (it % 251).toByte() }
        val imported = evidence.import(request(newCase()), ByteArrayInputStream(plaintext))
        evidence.openOriginal(imported.id).use { assertContentEquals(plaintext, it.inputStream().readBytes()) }
    }

    @Test
    fun importIntoMissingOrArchivedCaseFailsAndLeavesNothing() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> {
            evidence.import(request("synthetic-missing"), ByteArrayInputStream(ByteArray(5)))
        }
        val caseId = newCase()
        cases.archive(caseId)
        assertFailsWith<IllegalStateException> {
            evidence.import(request(caseId), ByteArrayInputStream(ByteArray(5)))
        }
        assertTrue(blobFiles().isEmpty())
        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
    }

    @Test
    fun rejectsUnknownAcquisitionKindAndAccessClass() = runBlocking<Unit> {
        val caseId = newCase()
        val base = request(caseId)
        val badKind = ImportRequest(caseId, "scraped", base.accessClass, "m", null, null, null, null, 10)
        val badAccess = ImportRequest(caseId, AcquisitionKind.PASTED_TEXT, "silent", "m", null, null, null, null, 10)
        assertFailsWith<IllegalArgumentException> { evidence.import(badKind, ByteArrayInputStream(ByteArray(1))) }
        assertFailsWith<IllegalArgumentException> { evidence.import(badAccess, ByteArrayInputStream(ByteArray(1))) }
    }

    @Test
    fun failingStreamLeavesNoFileAndNoRows() = runBlocking<Unit> {
        val caseId = newCase()
        assertFailsWith<IOException> { evidence.import(request(caseId), FailingInputStream(good = 9000)) }
        assertTrue(blobFiles().isEmpty())
        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
    }

    @Test
    fun overLimitInputLeavesNoFileAndNoRows() = runBlocking<Unit> {
        val caseId = newCase()
        assertFailsWith<BlobTooLargeException> {
            evidence.import(request(caseId, maxBytes = 100), ByteArrayInputStream(ByteArray(5000)))
        }
        assertTrue(blobFiles().isEmpty())
        assertTrue(evidence.observeForCase(caseId).first().isEmpty())
    }

    @Test
    fun failedTransactionRemovesTheBlobFile() = runBlocking<Unit> {
        val caseId = newCase()
        val first = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(10)))
        val clashing = EvidenceRepository(db, blobs, audit, clock, { first.id }, Dispatchers.IO)
        assertFailsWith<Exception> { clashing.import(request(caseId), ByteArrayInputStream(ByteArray(20))) }
        assertEquals(1, blobFiles().size)
        assertEquals(1, evidence.observeForCase(caseId).first().size)
    }

    @Test
    fun sameBytesTwiceGivesTwoRowsAndFindSameBytesFindsBoth() = runBlocking<Unit> {
        val caseId = newCase()
        val a = evidence.import(request(caseId), ByteArrayInputStream(MARKER.toByteArray()))
        val b = evidence.import(request(caseId), ByteArrayInputStream(MARKER.toByteArray()))
        assertEquals(a.sha256, b.sha256)
        assertEquals(setOf(a.id, b.id), evidence.findSameBytes(caseId, a.sha256).toSet())
        assertTrue(evidence.findSameBytes(caseId, "0".repeat(64)).isEmpty())
    }

    @Test
    fun verifyDetectsDamageAndNeverDeletes() = runBlocking<Unit> {
        val caseId = newCase()
        val imported = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(6000) { 3 }))
        assertEquals(VerificationResult.Intact, evidence.verify(imported.id))

        val file = File(blobDirectory, blobFiles().single())
        val bytes = file.readBytes()
        bytes[40] = (bytes[40].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertEquals(
            VerificationResult.Unreadable(UnreadableReason.AUTHENTICATION_FAILED),
            evidence.verify(imported.id),
        )
        assertNotNull(db.evidenceDao().get(imported.id))
        assertTrue(file.exists())

        file.delete()
        assertEquals(VerificationResult.Unreadable(UnreadableReason.MISSING_FILE), evidence.verify(imported.id))
        assertNotNull(db.evidenceDao().get(imported.id))
        assertNotNull(db.evidenceDao().getBlob(imported.id))
        assertEquals(1 + 1 + 3, assertIs<AuditVerification.Valid>(audit.verify()).count)
    }

    @Test
    fun unwrapFailureIsKeyUnavailable() = runBlocking<Unit> {
        val imported = evidence.import(request(newCase()), ByteArrayInputStream(ByteArray(100)))
        val locked = BlobStore(blobDirectory, UnwrapFailsWrapper(testWrapper()), chunkSize = 4096)
        val repository = EvidenceRepository(db, locked, audit, clock, ids, Dispatchers.IO)
        assertEquals(
            VerificationResult.Unreadable(UnreadableReason.KEY_UNAVAILABLE),
            repository.verify(imported.id),
        )
        assertNotNull(db.evidenceDao().get(imported.id))
        assertEquals(1, blobFiles().size)
    }

    @Test
    fun deleteRemovesRowsFileAndAuditsIt() = runBlocking<Unit> {
        val caseId = newCase()
        val imported = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(100)))
        val before = assertIs<AuditVerification.Valid>(audit.verify()).count
        evidence.delete(imported.id)
        assertNull(db.evidenceDao().get(imported.id))
        assertNull(db.evidenceDao().getBlob(imported.id))
        assertTrue(blobFiles().isEmpty())
        assertEquals(before + 1, assertIs<AuditVerification.Valid>(audit.verify()).count)
        assertFailsWith<IllegalArgumentException> { evidence.delete(imported.id) }
    }
}
