package org.sakshi.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher

/** Streams plaintext into the version 1 blob envelope using O(chunk size) memory. */
public object BlobWriter {
    public const val DEFAULT_CHUNK_SIZE: Int = 65536

    /**
     * Streams [input] to [output] in the envelope format. Reads at most [maxPlaintextBytes] and throws
     * [BlobTooLargeException] if the input is longer; nothing useful may be assumed about the partially
     * written output in that case (the caller deletes it).
     * Also computes SHA-256 of the exact plaintext bytes read. Does not close either stream.
     */
    public fun encrypt(
        input: InputStream,
        output: OutputStream,
        key: BlobKey,
        blobId: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        maxPlaintextBytes: Long = Long.MAX_VALUE,
        random: SecureRandom = SecureRandom(),
    ): BlobWriteResult {
        require(blobId.size == BlobFormat.BLOB_ID_SIZE) { "Blob id must be ${BlobFormat.BLOB_ID_SIZE} bytes" }
        require(chunkSize in BlobFormat.MIN_CHUNK_SIZE..BlobFormat.MAX_CHUNK_SIZE) {
            "Chunk size must be in ${BlobFormat.MIN_CHUNK_SIZE}..${BlobFormat.MAX_CHUNK_SIZE}, was $chunkSize"
        }
        require(maxPlaintextBytes >= 0) { "maxPlaintextBytes must not be negative" }

        val secretKey = key.toSecretKeySpec()
        val noncePrefix = ByteArray(BlobFormat.NONCE_PREFIX_SIZE).also(random::nextBytes)
        val header = BlobFormat.buildHeader(chunkSize, noncePrefix, blobId)
        output.write(header)

        val digest = MessageDigest.getInstance("SHA-256")
        // One spare byte lets us look ahead to learn whether the current chunk is the final one.
        val buffer = ByteArray(chunkSize + 1)
        var carried = 0
        var index = 0L
        var plaintextLength = 0L
        var ciphertextLength = BlobFormat.HEADER_SIZE.toLong()
        try {
            while (true) {
                val filled = readUpTo(input, buffer, carried)
                val isFinal = filled <= chunkSize
                val length = if (isFinal) filled else chunkSize
                if (length > maxPlaintextBytes - plaintextLength) {
                    throw BlobTooLargeException("Plaintext exceeds the limit of $maxPlaintextBytes bytes")
                }
                if (index >= BlobFormat.MAX_CHUNK_COUNT) {
                    throw BlobTooLargeException("Plaintext needs more than ${BlobFormat.MAX_CHUNK_COUNT} chunks")
                }
                val cipher = BlobFormat.chunkCipher(Cipher.ENCRYPT_MODE, secretKey, header, noncePrefix, index, isFinal)
                val sealed = cipher.doFinal(buffer, 0, length)
                output.write(sealed)
                digest.update(buffer, 0, length)
                plaintextLength += length
                ciphertextLength += sealed.size
                index++
                if (isFinal) {
                    return BlobWriteResult(plaintextLength, digest.digest(), index, ciphertextLength)
                }
                buffer[0] = buffer[chunkSize]
                carried = 1
            }
        } finally {
            buffer.fill(0)
        }
    }

    /** Reads until [buffer] is full or the stream ends, starting at [start]; returns the number of valid bytes. */
    private fun readUpTo(input: InputStream, buffer: ByteArray, start: Int): Int {
        var filled = start
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }
}
