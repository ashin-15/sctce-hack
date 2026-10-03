package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.crypto.BlobIntegrityException

/**
 * Blob files edited on disk, below the repository: every edit must be reported as an authentication failure, a
 * full read must never succeed, and the evidence row and the file must stay as they are (nothing is silently
 * dropped or replaced). Chunk size is 4096 in this test base; a blob has a 33 byte header and 4112 byte chunks.
 */
class BlobTamperAtRestTest : VaultTestBase() {
    private val plaintext = ByteArray(3 * 4096 + 100) { (it * 13 + 5).toByte() }

    private fun chunkStart(index: Int): Int = 33 + index * 4112

    private fun importOne(caseId: String, bytes: ByteArray = plaintext): String = runBlocking {
        evidence.import(request(caseId), ByteArrayInputStream(bytes)).id
    }

    private fun fileOf(evidenceId: String): File = runBlocking {
        File(blobDirectory, assertNotNull(db.evidenceDao().getBlob(evidenceId)).path)
    }

    private fun newCase(): String = runBlocking { cases.create("Synthetic").id }

    private fun assertFailsClosed(evidenceId: String, label: String) {
        assertEquals(
            VerificationResult.Unreadable(UnreadableReason.AUTHENTICATION_FAILED),
            runBlocking { evidence.verify(evidenceId) },
            label,
        )
        val outcome = runCatching { runBlocking { evidence.openOriginal(evidenceId).use { it.inputStream().readBytes() } } }
        assertTrue(outcome.exceptionOrNull() is BlobIntegrityException, "$label: full read returned ${outcome.getOrNull()?.size} bytes")
        assertNotNull(runBlocking { db.evidenceDao().get(evidenceId) }, "$label: evidence row was removed")
        assertTrue(fileOf(evidenceId).exists(), "$label: blob file was removed")
    }

    private fun mutate(label: String, change: (ByteArray) -> ByteArray) {
        val evidenceId = importOne(newCase())
        val file = fileOf(evidenceId)
        file.writeBytes(change(file.readBytes()))
        assertFailsClosed(evidenceId, label)
    }

    private fun flip(bytes: ByteArray, offset: Int): ByteArray =
        bytes.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }

    @Test
    fun anUntouchedBlobIsIntact() = runBlocking<Unit> {
        val id = importOne(newCase())
        assertEquals(VerificationResult.Intact, evidence.verify(id))
        evidence.openOriginal(id).use { assertContentEquals(plaintext, it.inputStream().readBytes()) }
    }

    @Test
    fun flippedHeaderMiddleChunkLastChunkAndTagFailClosed() {
        mutate("header magic") { flip(it, 0) }
        mutate("header nonce prefix") { flip(it, 10) }
        mutate("header blob id") { flip(it, 20) }
        mutate("middle chunk") { flip(it, chunkStart(1) + 100) }
        mutate("last chunk") { flip(it, chunkStart(3) + 10) }
        mutate("tag of a middle chunk") { flip(it, chunkStart(2) - 1) }
        mutate("final tag") { flip(it, it.size - 1) }
    }

    @Test
    fun swappedChunksFailClosed() {
        mutate("swap chunks 1 and 2") { bytes ->
            val damaged = bytes.copyOf()
            bytes.copyInto(damaged, chunkStart(1), chunkStart(2), chunkStart(3))
            bytes.copyInto(damaged, chunkStart(2), chunkStart(1), chunkStart(2))
            damaged
        }
    }

    @Test
    fun truncatedAtABoundaryAndInsideAChunkFailClosed() {
        mutate("truncated at a chunk boundary") { it.copyOf(chunkStart(2)) }
        mutate("truncated inside a chunk") { it.copyOf(chunkStart(2) + 700) }
        mutate("truncated to the header") { it.copyOf(33) }
        mutate("emptied") { ByteArray(0) }
    }

    @Test
    fun extendedBlobsFailClosed() {
        mutate("one byte appended") { it + byteArrayOf(0) }
        mutate("a chunk appended") { it + it.copyOfRange(chunkStart(1), chunkStart(2)) }
    }

    @Test
    fun aBlobFileOfAnotherEvidenceItemFailsClosedAndLeavesTheOtherIntact() {
        val caseId = newCase()
        val first = importOne(caseId)
        val second = importOne(caseId, ByteArray(plaintext.size) { (it * 7).toByte() })
        val firstFile = fileOf(first)
        val secondFile = fileOf(second)
        val secondBytes = secondFile.readBytes()
        firstFile.writeBytes(secondBytes)

        assertFailsClosed(first, "another item's blob file")
        assertEquals(VerificationResult.Intact, runBlocking { evidence.verify(second) })
        assertContentEquals(secondBytes, secondFile.readBytes())
    }

    @Test
    fun aBlobFileWithIdenticalPlaintextButAnotherBlobIdFailsClosed() {
        val caseId = newCase()
        val first = importOne(caseId)
        val second = importOne(caseId)
        assertEquals(runBlocking { evidence.details(first)?.sha256 }, runBlocking { evidence.details(second)?.sha256 })
        fileOf(first).writeBytes(fileOf(second).readBytes())
        assertFailsClosed(first, "same plaintext, other blob")
    }

    @Test
    fun aWrappedKeyOfAnotherItemCannotOpenTheBlob() = runBlocking<Unit> {
        val caseId = newCase()
        val first = importOne(caseId)
        val second = importOne(caseId)
        val firstBlob = assertNotNull(db.evidenceDao().getBlob(first))
        val secondBlob = assertNotNull(db.evidenceDao().getBlob(second))
        assertFailsWith<BlobIntegrityException> {
            blobs.open(firstBlob.path, BlobStore.blobIdOf(firstBlob.path), secondBlob.wrappedKey)
        }
        blobs.open(firstBlob.path, BlobStore.blobIdOf(firstBlob.path), firstBlob.wrappedKey).use {
            assertContentEquals(plaintext, it.inputStream().readBytes())
        }
    }

    @Test
    fun aBlobFileRenamedToAnotherNameIsNotReadable() = runBlocking<Unit> {
        val caseId = newCase()
        val first = importOne(caseId)
        val second = importOne(caseId)
        val firstFile = fileOf(first)
        val secondFile = fileOf(second)
        val firstBytes = firstFile.readBytes()
        secondFile.writeBytes(firstBytes)
        firstFile.delete()

        assertEquals(VerificationResult.Unreadable(UnreadableReason.MISSING_FILE), evidence.verify(first))
        assertEquals(VerificationResult.Unreadable(UnreadableReason.AUTHENTICATION_FAILED), evidence.verify(second))
    }
}
