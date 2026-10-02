package org.sakshi.core.crypto

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Constants and helpers for blob envelope format version 1. */
internal object BlobFormat {
    val MAGIC: ByteArray = "SKB1".toByteArray(Charsets.US_ASCII)
    const val VERSION: Byte = 1
    const val HEADER_SIZE: Int = 33
    const val NONCE_PREFIX_SIZE: Int = 8
    const val BLOB_ID_SIZE: Int = 16
    const val TAG_SIZE: Int = 16
    const val MIN_CHUNK_SIZE: Int = 4096
    const val MAX_CHUNK_SIZE: Int = 1_048_576
    const val MAX_CHUNK_COUNT: Long = 0xFFFF_FFFFL

    private const val TAG_BITS = 128
    private const val NONCE_SIZE = 12
    private const val CHUNK_OFFSET_IN_HEADER = 5
    private const val PREFIX_OFFSET_IN_HEADER = 9
    private const val BLOB_ID_OFFSET_IN_HEADER = 17

    fun buildHeader(chunkSize: Int, noncePrefix: ByteArray, blobId: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .put(VERSION)
            .putInt(chunkSize)
            .put(noncePrefix)
            .put(blobId)
            .array()

    fun chunkSizeOf(header: ByteArray): Long =
        ByteBuffer.wrap(header).getInt(CHUNK_OFFSET_IN_HEADER).toLong() and 0xFFFF_FFFFL

    fun noncePrefixOf(header: ByteArray): ByteArray =
        header.copyOfRange(PREFIX_OFFSET_IN_HEADER, PREFIX_OFFSET_IN_HEADER + NONCE_PREFIX_SIZE)

    fun blobIdOf(header: ByteArray): ByteArray =
        header.copyOfRange(BLOB_ID_OFFSET_IN_HEADER, BLOB_ID_OFFSET_IN_HEADER + BLOB_ID_SIZE)

    /** Creates a cipher for one chunk. [index] must be below [MAX_CHUNK_COUNT]. */
    fun chunkCipher(
        mode: Int,
        key: SecretKeySpec,
        header: ByteArray,
        noncePrefix: ByteArray,
        index: Long,
        isFinal: Boolean,
    ): Cipher {
        val nonce = ByteBuffer.allocate(NONCE_SIZE).put(noncePrefix).putInt(index.toInt()).array()
        val aad = ByteBuffer.allocate(HEADER_SIZE + Long.SIZE_BYTES + 1)
            .put(header)
            .putLong(index)
            .put(if (isFinal) 1 else 0)
            .array()
        return newGcmCipher(mode, key, nonce, aad)
    }

    fun newGcmCipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, aad: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher
    }

    /** Fills the first [length] bytes of [destination] from [position]; a short channel means a truncated blob. */
    fun readFully(channel: SeekableByteChannel, position: Long, destination: ByteArray, length: Int) {
        channel.position(position)
        val buffer = ByteBuffer.wrap(destination, 0, length)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw BlobIntegrityException("Blob is truncated")
        }
    }
}
