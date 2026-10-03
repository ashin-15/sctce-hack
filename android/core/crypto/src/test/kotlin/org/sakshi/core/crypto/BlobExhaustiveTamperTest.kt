package org.sakshi.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exhaustive tamper checks over one small blob (two full chunks and a short final chunk): every byte position,
 * every truncation length, a range of extensions and every chunk substitution must be rejected, and a failed
 * read must never hand out bytes of the damaged chunk.
 */
class BlobExhaustiveTamperTest {
    private val key = BlobKey.generate()
    private val blobId = VaultSecrets.newBlobId()
    private val plaintext = plaintextOf(2 * CHUNK + 300)
    private val original = seal(plaintext, key, blobId).bytes

    private fun chunkStart(index: Int): Int = HEADER + index * STRIDE

    /** True when opening and authenticating every chunk of [bytes] fails with the integrity exception. */
    private fun isRejected(bytes: ByteArray): Boolean = try {
        openBytes(bytes, key, blobId).use { it.verifyAll() }
        false
    } catch (_: BlobIntegrityException) {
        true
    }

    @Test
    fun everyByteOfTheFileIsAuthenticated() {
        val accepted = ArrayList<String>()
        for (offset in original.indices) {
            for (mask in listOf(0x01, 0x80, 0xFF)) {
                val damaged = original.copyOf().also { it[offset] = (it[offset].toInt() xor mask).toByte() }
                if (!isRejected(damaged)) accepted += "offset $offset mask $mask"
            }
        }
        assertEquals(emptyList(), accepted.take(5))
    }

    @Test
    fun everyTruncationLengthIsRejected() {
        val accepted = (0 until original.size).filter { !isRejected(original.copyOf(it)) }
        assertEquals(emptyList(), accepted.take(5))
    }

    @Test
    fun everyExtensionUpToTwoChunksIsRejected() {
        val accepted = ArrayList<String>()
        for (extra in 1..(2 * STRIDE)) {
            if (!isRejected(original + ByteArray(extra))) accepted += "zeros $extra"
            if (!isRejected(original + original.copyOfRange(HEADER, minOf(original.size, HEADER + extra)))) accepted += "copy $extra"
        }
        assertEquals(emptyList(), accepted.take(5))
    }

    @Test
    fun everyFullChunkSubstitutionIsRejected() {
        val full = listOf(0, 1)
        for (target in full) {
            for (source in full + 2) {
                if (target == source) continue
                val sourceEnd = if (source == 2) original.size else chunkStart(source + 1)
                val piece = original.copyOfRange(chunkStart(source), sourceEnd)
                val damaged = original.copyOfRange(0, chunkStart(target)) + piece + original.copyOfRange(chunkStart(target + 1), original.size)
                assertTrue(isRejected(damaged), "chunk $source written over chunk $target was accepted")
            }
        }
    }

    @Test
    fun aFailedReadNeverCopiesBytesOfTheDamagedChunk() {
        val damaged = original.copyOf().also { it[chunkStart(1) + 7] = (it[chunkStart(1) + 7].toInt() xor 1).toByte() }
        val sentinel = 0x55.toByte()
        openBytes(damaged, key, blobId).use { reader ->
            val destination = ByteArray(2 * CHUNK) { sentinel }
            assertFailsWith<BlobIntegrityException> { reader.read(0, destination, 0, destination.size) }
            // Bytes of the earlier, authenticated chunk may be present; nothing of the damaged chunk may be.
            assertTrue(destination.copyOfRange(CHUNK, 2 * CHUNK).all { it == sentinel }, "damaged chunk bytes were copied out")
            assertContentEquals(plaintext.copyOfRange(0, CHUNK), destination.copyOfRange(0, CHUNK))

            val again = ByteArray(CHUNK) { sentinel }
            assertFailsWith<BlobIntegrityException> { reader.read(CHUNK.toLong(), again, 0, again.size) }
            assertTrue(again.all { it == sentinel }, "a repeated read of the damaged chunk returned data")
            assertFailsWith<BlobIntegrityException> { reader.inputStream().readBytes() }
        }
    }

    @Test
    fun aSingleByteReadInsideTheDamagedChunkFails() {
        val damaged = original.copyOf().also { it[chunkStart(0) + 1] = (it[chunkStart(0) + 1].toInt() xor 1).toByte() }
        openBytes(damaged, key, blobId).use { reader ->
            assertFailsWith<BlobIntegrityException> { reader.inputStream().read() }
        }
    }
}
