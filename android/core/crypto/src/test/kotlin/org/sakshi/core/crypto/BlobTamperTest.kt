package org.sakshi.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class BlobTamperTest {
    private val key = BlobKey.generate()
    private val blobId = VaultSecrets.newBlobId()
    private val plaintext = plaintextOf(4 * CHUNK + 100)
    private val original = seal(plaintext, key, blobId).bytes

    private fun chunkStart(index: Int): Int = HEADER + index * STRIDE

    private fun flipped(offset: Int): ByteArray = original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }

    /**
     * Asserts that opening or reading [bytes] fails and that every byte returned before the failure is genuine
     * plaintext from chunks before [firstDamagedChunk].
     */
    private fun assertRejected(bytes: ByteArray, firstDamagedChunk: Int = 0, label: String) {
        val returned = java.io.ByteArrayOutputStream()
        try {
            openBytes(bytes, key, blobId).use { reader ->
                val buffer = ByteArray(CHUNK)
                var position = 0L
                while (true) {
                    val count = reader.read(position, buffer, 0, buffer.size)
                    if (count < 0) break
                    returned.write(buffer, 0, count)
                    position += count
                }
            }
            fail("$label: tampering was not detected")
        } catch (_: BlobIntegrityException) {
            val got = returned.toByteArray()
            assertTrue(got.size <= firstDamagedChunk * CHUNK, "$label: returned ${got.size} bytes past damage")
            assertContentEquals(plaintext.copyOf(got.size), got, "$label: returned bytes are not genuine")
        }
    }

    @Test
    fun untouchedBlobIsAccepted() {
        openBytes(original, key, blobId).use { assertContentEquals(plaintext, readAll(it)) }
    }

    @Test
    fun headerFieldFlipsAreRejected() {
        val fields = mapOf("magic" to 0, "version" to 4, "chunkSize high" to 7, "chunkSize low" to 8,
            "noncePrefix" to 9, "blobId" to 17)
        for ((name, offset) in fields) {
            assertRejected(flipped(offset), label = name)
        }
    }

    @Test
    fun ciphertextAndTagFlipsAreRejectedAtTheDamagedChunk() {
        assertRejected(flipped(chunkStart(2) + 10), firstDamagedChunk = 2, label = "ciphertext")
        assertRejected(flipped(chunkStart(2) + CHUNK + 3), firstDamagedChunk = 2, label = "tag")
        assertRejected(flipped(chunkStart(0)), firstDamagedChunk = 0, label = "first chunk")
        assertRejected(flipped(original.size - 1), label = "final tag")
    }

    @Test
    fun swappedChunksAreRejected() {
        val bytes = original.copyOf()
        original.copyInto(bytes, chunkStart(1), chunkStart(2), chunkStart(3))
        original.copyInto(bytes, chunkStart(2), chunkStart(1), chunkStart(2))
        assertRejected(bytes, firstDamagedChunk = 1, label = "swap")
    }

    @Test
    fun truncationIsRejected() {
        assertRejected(original.copyOf(chunkStart(3)), label = "chunk boundary")
        assertRejected(original.copyOf(chunkStart(3) + 500), label = "mid chunk")
        assertRejected(original.copyOf(HEADER), label = "header only")
        assertRejected(original.copyOf(HEADER - 1), label = "short header")
        assertRejected(original.copyOf(0), label = "empty file")
        assertRejected(original.copyOf(original.size - 1), label = "one byte short")
    }

    @Test
    fun truncatingAtBoundaryOfExactMultipleIsRejected() {
        val exact = plaintextOf(3 * CHUNK)
        val bytes = seal(exact, key, blobId).bytes
        val cut = bytes.copyOf(bytes.size - STRIDE)
        assertFailsWith<BlobIntegrityException> { openBytes(cut, key, blobId) }
    }

    @Test
    fun extensionIsRejected() {
        assertRejected(original + byteArrayOf(0), label = "one byte")
        assertRejected(original + original.copyOfRange(chunkStart(4), original.size), label = "copy of last chunk")
        assertRejected(original + original.copyOfRange(chunkStart(2), chunkStart(3)), label = "copy of middle chunk")
        assertRejected(original + ByteArray(16), label = "sixteen zero bytes")
    }

    @Test
    fun replacingFinalChunkWithAnotherPositionIsRejected() {
        val bytes = original.copyOf(chunkStart(4)) + original.copyOfRange(chunkStart(1), chunkStart(2))
        assertRejected(bytes, label = "full chunk as final")
        val short = original.copyOf(chunkStart(3)) + original.copyOfRange(chunkStart(4), original.size)
        assertRejected(short, label = "final chunk moved earlier")
    }

    @Test
    fun wrongKeyIsRejected() {
        assertFailsWith<BlobIntegrityException> { openBytes(original, BlobKey.generate(), blobId) }
    }

    @Test
    fun wrongBlobIdIsRejected() {
        assertFailsWith<BlobIntegrityException> { openBytes(original, key, VaultSecrets.newBlobId()) }
    }

    @Test
    fun failedOpenClosesTheChannel() {
        val channel = ByteArrayChannel(flipped(0))
        assertFailsWith<BlobIntegrityException> { BlobReader.open(channel, key, blobId) }
        assertTrue(!channel.isOpen)
    }

    @Test
    fun sameKeyAndPlaintextProduceDifferentBlobsThatDoNotMix() {
        val other = seal(plaintext, key, blobId).bytes
        assertEquals(original.size, other.size)
        assertTrue(!original.contentEquals(other))

        val spliced = original.copyOf()
        other.copyInto(spliced, chunkStart(1), chunkStart(1), chunkStart(2))
        assertRejected(spliced, firstDamagedChunk = 1, label = "chunk from another blob")
    }

    @Test
    fun ciphertextHidesPlaintextPatterns() {
        val pattern = ByteArray(32) { (0xA0 + it).toByte() }
        val repeated = ByteArray(3 * CHUNK) { pattern[it % pattern.size] }
        assertEquals(0, indexOf(repeated, pattern))
        val sealed = seal(repeated, key, blobId).bytes
        assertEquals(-1, indexOf(sealed, pattern))
        assertEquals(-1, indexOf(sealed, pattern.copyOf(8)))
    }

    @Test
    fun verifyAllDetectsDamageInUnreadChunks() {
        openBytes(flipped(chunkStart(1) + 5), key, blobId).use { reader ->
            assertFailsWith<BlobIntegrityException> { reader.verifyAll() }
        }
    }
}
