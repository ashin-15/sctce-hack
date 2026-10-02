package org.sakshi.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlobRoundTripTest {
    private val key = BlobKey.generate()
    private val blobId = VaultSecrets.newBlobId()

    @Test
    fun roundTripsAcrossChunkBoundaries() {
        for (length in listOf(0, 1, CHUNK - 1, CHUNK, CHUNK + 1, 2 * CHUNK, 3 * CHUNK + 5)) {
            val plaintext = plaintextOf(length)
            val sealed = seal(plaintext, key, blobId)
            val expectedChunks = maxOf(1, (length + CHUNK - 1) / CHUNK)
            val expectedLength = HEADER + length + 16 * expectedChunks
            val sha = MessageDigest.getInstance("SHA-256").digest(plaintext)

            assertEquals(length.toLong(), sealed.result.plaintextLength, "length $length")
            assertEquals(expectedChunks.toLong(), sealed.result.chunkCount, "chunks $length")
            assertEquals(expectedLength.toLong(), sealed.result.ciphertextLength, "ciphertext $length")
            assertEquals(expectedLength, sealed.bytes.size, "actual size $length")
            assertContentEquals(sha, sealed.result.plaintextSha256, "sha $length")

            openBytes(sealed.bytes, key, blobId).use { reader ->
                assertEquals(length.toLong(), reader.size)
                assertEquals(CHUNK, reader.chunkSize)
                assertContentEquals(plaintext, readAll(reader), "content $length")
                assertContentEquals(sha, reader.verifyAll(), "verifyAll $length")
            }
        }
    }

    @Test
    fun randomAccessMatchesPlaintext() {
        val plaintext = plaintextOf(3 * CHUNK + 5)
        val sealed = seal(plaintext, key, blobId)
        openBytes(sealed.bytes, key, blobId).use { reader ->
            val cases = listOf(
                0L to 1, 0L to CHUNK, 1L to CHUNK, CHUNK - 3L to 10, CHUNK.toLong() to CHUNK,
                CHUNK - 1L to 2 * CHUNK + 2, 2L * CHUNK + 7 to 50, 0L to plaintext.size,
                plaintext.size - 1L to 1, plaintext.size - 5L to 100, 3L * CHUNK to 5,
            )
            for ((position, length) in cases) {
                val buffer = ByteArray(length + 3)
                val expectedCount = minOf(length.toLong(), plaintext.size - position).toInt()
                val count = reader.read(position, buffer, 2, length)
                assertEquals(expectedCount, count, "count at $position length $length")
                assertContentEquals(
                    plaintext.copyOfRange(position.toInt(), position.toInt() + expectedCount),
                    buffer.copyOfRange(2, 2 + expectedCount),
                    "bytes at $position length $length",
                )
            }
            val buffer = ByteArray(8)
            assertEquals(-1, reader.read(plaintext.size.toLong(), buffer, 0, 8))
            assertEquals(-1, reader.read(plaintext.size + 100L, buffer, 0, 8))
            assertEquals(0, reader.read(10, buffer, 0, 0))
        }
    }

    @Test
    fun readRejectsBadArguments() {
        val sealed = seal(plaintextOf(10), key, blobId)
        openBytes(sealed.bytes, key, blobId).use { reader ->
            val buffer = ByteArray(8)
            assertFailsWith<IllegalArgumentException> { reader.read(-1, buffer, 0, 1) }
            assertFailsWith<IndexOutOfBoundsException> { reader.read(0, buffer, 4, 5) }
            assertFailsWith<IndexOutOfBoundsException> { reader.read(0, buffer, -1, 1) }
        }
    }

    @Test
    fun readerRefusesUseAfterClose() {
        val sealed = seal(plaintextOf(10), key, blobId)
        val reader = openBytes(sealed.bytes, key, blobId)
        reader.close()
        assertFailsWith<IllegalStateException> { reader.read(0, ByteArray(1), 0, 1) }
    }

    @Test
    fun shortReadsProduceIdenticalOutput() {
        val plaintext = plaintextOf(3 * CHUNK + 17)
        val normal = ByteArrayOutputStream()
        BlobWriter.encrypt(ByteArrayInputStream(plaintext), normal, key, blobId, CHUNK, random = seededRandom())
        val trickle = ByteArrayOutputStream()
        val result = BlobWriter.encrypt(TrickleInputStream(plaintext), trickle, key, blobId, CHUNK, random = seededRandom())

        assertContentEquals(normal.toByteArray(), trickle.toByteArray())
        assertEquals(trickle.size().toLong(), result.ciphertextLength)
        openBytes(trickle.toByteArray(), key, blobId).use { assertContentEquals(plaintext, readAll(it)) }
    }

    @Test
    fun limitIsEnforcedExactly() {
        for (length in listOf(CHUNK, CHUNK + 10)) {
            val plaintext = plaintextOf(length)
            val ok = BlobWriter.encrypt(
                ByteArrayInputStream(plaintext), ByteArrayOutputStream(), key, blobId, CHUNK, length.toLong(),
            )
            assertEquals(length.toLong(), ok.plaintextLength)
            assertFailsWith<BlobTooLargeException> {
                BlobWriter.encrypt(
                    ByteArrayInputStream(plaintext), ByteArrayOutputStream(), key, blobId, CHUNK, length - 1L,
                )
            }
        }
    }

    @Test
    fun writerRejectsBadArguments() {
        val empty = ByteArrayInputStream(ByteArray(0))
        val out = ByteArrayOutputStream()
        assertFailsWith<IllegalArgumentException> { BlobWriter.encrypt(empty, out, key, ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { BlobWriter.encrypt(empty, out, key, blobId, CHUNK - 1) }
        assertFailsWith<IllegalArgumentException> { BlobWriter.encrypt(empty, out, key, blobId, 1_048_577) }
        assertFailsWith<IllegalArgumentException> { BlobWriter.encrypt(empty, out, key, blobId, CHUNK, -1) }
        val closed = BlobKey.generate().also { it.close() }
        assertFailsWith<IllegalStateException> { BlobWriter.encrypt(empty, out, closed, blobId) }
    }

    @Test
    fun largestChunkSizeRoundTrips() {
        val plaintext = plaintextOf(1_048_576 + 1)
        val output = ByteArrayOutputStream()
        BlobWriter.encrypt(ByteArrayInputStream(plaintext), output, key, blobId, 1_048_576)
        openBytes(output.toByteArray(), key, blobId).use { assertContentEquals(plaintext, readAll(it)) }
    }

    /** Returns 1 to 7 bytes per read. */
    private class TrickleInputStream(private val data: ByteArray) : InputStream() {
        private var position = 0
        private var step = 0

        override fun read(): Int = if (position < data.size) data[position++].toInt() and 0xFF else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return -1
            step = step % 7 + 1
            val count = minOf(step, length, data.size - position)
            System.arraycopy(data, position, buffer, offset, count)
            position += count
            return count
        }
    }
}
