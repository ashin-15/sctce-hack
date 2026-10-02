package org.sakshi.core.crypto

import java.io.Closeable
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.Objects
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated random-access reader. Never returns bytes from a chunk that failed authentication.
 *
 * Only the most recently read chunk is cached in memory, and it is zeroed when replaced and on [close].
 * A chunk is authenticated when it is read, so damage to chunks that are never read is only found by [verifyAll].
 */
public class BlobReader private constructor(
    private val channel: SeekableByteChannel,
    private val key: SecretKeySpec,
    private val header: ByteArray,
    public val chunkSize: Int,
    /** Plaintext length in bytes. */
    public val size: Long,
    private val chunkCount: Long,
    private val lastChunkSealedLength: Int,
) : Closeable {
    private val noncePrefix: ByteArray = BlobFormat.noncePrefixOf(header)
    private val sealedBuffer = ByteArray(chunkSize + BlobFormat.TAG_SIZE)
    private var cachedIndex = -1L
    private var cachedPlaintext = ByteArray(0)
    private var closed = false

    /**
     * Reads up to [length] plaintext bytes at [position] into [destination] at [offset]. Returns the number of
     * bytes read, 0 if [length] is 0, or -1 at or beyond the end. Authenticates every chunk it touches.
     * If a chunk fails authentication the call throws; bytes already copied from earlier, authenticated
     * chunks may remain in [destination] and must be ignored.
     */
    @Synchronized
    public fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        require(position >= 0) { "position must not be negative" }
        Objects.checkFromIndexSize(offset, length, destination.size)
        if (length == 0) return 0
        if (position >= size) return -1
        val total = minOf(length.toLong(), size - position).toInt()
        var copied = 0
        while (copied < total) {
            val at = position + copied
            val chunk = loadChunk(at / chunkSize)
            val inChunk = (at % chunkSize).toInt()
            val count = minOf(total - copied, chunk.size - inChunk)
            System.arraycopy(chunk, inChunk, destination, offset + copied, count)
            copied += count
        }
        return copied
    }

    /** Sequential stream over the whole plaintext; each chunk is authenticated before any of its bytes are returned. */
    public fun inputStream(): InputStream = object : InputStream() {
        private var position = 0L

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = this@BlobReader.read(position, buffer, offset, length)
            if (count > 0) position += count
            return count
        }

        override fun available(): Int = (size - position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Decrypts and authenticates every chunk and returns the SHA-256 of the plaintext. */
    @Synchronized
    public fun verifyAll(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        for (index in 0L until chunkCount) {
            digest.update(loadChunk(index))
        }
        return digest.digest()
    }

    /** Closes the channel and wipes the cached plaintext. */
    @Synchronized
    override fun close() {
        closed = true
        cachedPlaintext.fill(0)
        cachedIndex = -1L
        channel.close()
    }

    private fun loadChunk(index: Long): ByteArray {
        check(!closed) { "Reader is closed" }
        if (index == cachedIndex) return cachedPlaintext
        val isFinal = index == chunkCount - 1
        val sealedLength = if (isFinal) lastChunkSealedLength else chunkSize + BlobFormat.TAG_SIZE
        val offset = BlobFormat.HEADER_SIZE + index * (chunkSize + BlobFormat.TAG_SIZE)
        BlobFormat.readFully(channel, offset, sealedBuffer, sealedLength)
        val cipher = BlobFormat.chunkCipher(Cipher.DECRYPT_MODE, key, header, noncePrefix, index, isFinal)
        val plaintext = try {
            cipher.doFinal(sealedBuffer, 0, sealedLength)
        } catch (e: AEADBadTagException) {
            throw BlobIntegrityException("Chunk $index failed authentication", e)
        }
        cachedPlaintext.fill(0)
        cachedPlaintext = plaintext
        cachedIndex = index
        return plaintext
    }

    public companion object {
        /**
         * Validates the header, the layout, [expectedBlobId], and authenticates the final chunk before returning.
         * Takes ownership of [channel]: it is closed if this call fails.
         * @throws BlobIntegrityException if the blob is malformed, modified, or does not match [key] and [expectedBlobId].
         */
        public fun open(channel: SeekableByteChannel, key: BlobKey, expectedBlobId: ByteArray): BlobReader {
            var opened = false
            try {
                require(expectedBlobId.size == BlobFormat.BLOB_ID_SIZE) {
                    "Blob id must be ${BlobFormat.BLOB_ID_SIZE} bytes"
                }
                val reader = create(channel, key.toSecretKeySpec(), expectedBlobId)
                reader.loadChunk(reader.chunkCount - 1)
                opened = true
                return reader
            } finally {
                if (!opened) channel.close()
            }
        }

        private fun create(channel: SeekableByteChannel, key: SecretKeySpec, expectedBlobId: ByteArray): BlobReader {
            val fileLength = channel.size()
            if (fileLength < BlobFormat.HEADER_SIZE + BlobFormat.TAG_SIZE) {
                throw BlobIntegrityException("Blob is too short")
            }
            val header = ByteArray(BlobFormat.HEADER_SIZE)
            BlobFormat.readFully(channel, 0, header, header.size)
            if (!header.copyOfRange(0, BlobFormat.MAGIC.size).contentEquals(BlobFormat.MAGIC)) {
                throw BlobIntegrityException("Not a blob: bad magic")
            }
            if (header[BlobFormat.MAGIC.size] != BlobFormat.VERSION) {
                throw BlobIntegrityException("Unsupported blob version ${header[BlobFormat.MAGIC.size]}")
            }
            val chunkSize = BlobFormat.chunkSizeOf(header)
            if (chunkSize !in BlobFormat.MIN_CHUNK_SIZE..BlobFormat.MAX_CHUNK_SIZE) {
                throw BlobIntegrityException("Chunk size $chunkSize is out of range")
            }
            if (!MessageDigest.isEqual(BlobFormat.blobIdOf(header), expectedBlobId)) {
                throw BlobIntegrityException("Blob id does not match")
            }

            val stride = chunkSize + BlobFormat.TAG_SIZE
            val body = fileLength - BlobFormat.HEADER_SIZE
            val chunkCount = (body + stride - 1) / stride
            val lastSealed = body - (chunkCount - 1) * stride
            if (chunkCount > BlobFormat.MAX_CHUNK_COUNT || lastSealed < BlobFormat.TAG_SIZE) {
                throw BlobIntegrityException("Blob length is not valid for its layout")
            }
            return BlobReader(
                channel = channel,
                key = key,
                header = header,
                chunkSize = chunkSize.toInt(),
                size = body - BlobFormat.TAG_SIZE * chunkCount,
                chunkCount = chunkCount,
                lastChunkSealedLength = lastSealed.toInt(),
            )
        }
    }
}
