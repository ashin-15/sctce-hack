package org.sakshi.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.security.SecureRandom
import kotlin.random.Random

const val CHUNK: Int = 4096
const val STRIDE: Int = CHUNK + 16
const val HEADER: Int = 33

/** Read-only in-memory channel. */
class ByteArrayChannel(private val data: ByteArray) : SeekableByteChannel {
    private var position = 0L
    private var open = true

    override fun read(dst: ByteBuffer): Int {
        if (position >= data.size) return -1
        val count = minOf(dst.remaining().toLong(), data.size - position).toInt()
        dst.put(data, position.toInt(), count)
        position += count
        return count
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    override fun position(): Long = position

    override fun position(newPosition: Long): SeekableByteChannel = apply { position = newPosition }

    override fun size(): Long = data.size.toLong()

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = open

    override fun close() {
        open = false
    }
}

fun plaintextOf(length: Int, seed: Int = length): ByteArray = Random(seed).nextBytes(length)

fun seededRandom(seed: Long = 42L): SecureRandom =
    SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) }

class Sealed(val bytes: ByteArray, val result: BlobWriteResult)

fun seal(
    plaintext: ByteArray,
    key: BlobKey,
    blobId: ByteArray,
    chunkSize: Int = CHUNK,
): Sealed {
    val output = ByteArrayOutputStream()
    val result = BlobWriter.encrypt(ByteArrayInputStream(plaintext), output, key, blobId, chunkSize)
    return Sealed(output.toByteArray(), result)
}

fun openBytes(bytes: ByteArray, key: BlobKey, blobId: ByteArray): BlobReader =
    BlobReader.open(ByteArrayChannel(bytes), key, blobId)

fun readAll(reader: BlobReader): ByteArray = reader.inputStream().readBytes()

fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
    for (start in 0..haystack.size - needle.size) {
        if (needle.indices.all { haystack[start + it] == needle[it] }) return start
    }
    return -1
}
