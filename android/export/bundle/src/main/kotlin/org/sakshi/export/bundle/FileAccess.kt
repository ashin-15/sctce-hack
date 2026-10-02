package org.sakshi.export.bundle

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Raised when a file in the bundle cannot be read within the limits. */
internal class BundleReadException(message: String) : Exception(message)

internal object FileAccess {
    /** Reads attributes without following links; returns null if the entry is missing or not a regular file. */
    fun regularFileSize(path: Path): Long? = try {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attrs.isRegularFile) attrs.size() else null
    } catch (e: IOException) {
        null
    }

    fun readBounded(path: Path, max: Long): ByteArray {
        val size = regularFileSize(path) ?: throw BundleReadException("not a readable regular file")
        if (size > max) throw BundleReadException("larger than the limit of $max bytes")
        try {
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                val data = input.readNBytes(max.toInt() + 1)
                if (data.size > max) throw BundleReadException("larger than the limit of $max bytes")
                return data
            }
        } catch (e: IOException) {
            throw BundleReadException("could not be read")
        }
    }

    fun hash(path: Path, limit: Long): StreamDigest? = try {
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { digestStream(it, limit) }
    } catch (e: IOException) {
        null
    }
}
