package org.sakshi.processing.stt

import android.media.MediaDataSource
import java.io.IOException
import org.sakshi.core.crypto.BlobReader

/**
 * Read-only random access to the plaintext of one evidence item. The vault's [BlobReader] provides it by decrypting and
 * authenticating one chunk at a time, so a decoder can seek without any decrypted byte reaching a file.
 */
public interface RandomAccessSource {
    /** Plaintext length in bytes. */
    public val size: Long

    /**
     * Reads up to [length] bytes at [position] into [destination] at [offset]. Returns the count read, or -1 at or
     * beyond the end.
     */
    public fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int
}

/** A [RandomAccessSource] over the vault's authenticated random-access reader. The caller keeps ownership of [reader] and closes it. */
public fun BlobReader.asRandomAccessSource(): RandomAccessSource = object : RandomAccessSource {
    override val size: Long
        get() = this@asRandomAccessSource.size

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int =
        this@asRandomAccessSource.read(position, destination, offset, length)
}

/** Plaintext already held in memory, for tests and for callers that decrypted into a bounded buffer. */
public class ByteArrayRandomAccessSource(private val bytes: ByteArray) : RandomAccessSource {
    override val size: Long
        get() = bytes.size.toLong()

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= bytes.size) return -1
        val count = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), destination, offset, count)
        return count
    }
}

/** Feeds [MediaDataSource] consumers from a [RandomAccessSource]. Closing it does not close the source. */
internal class RandomAccessMediaDataSource(private val source: RandomAccessSource) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        try {
            return source.read(position, buffer, offset, size)
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw IOException("Source could not be read", e)
        }
    }

    override fun getSize(): Long = source.size

    override fun close(): Unit = Unit
}
