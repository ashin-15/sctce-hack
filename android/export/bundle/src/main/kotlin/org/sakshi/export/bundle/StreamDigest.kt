package org.sakshi.export.bundle

import java.io.InputStream
import java.security.MessageDigest
import org.sakshi.core.integrity.Sha256

internal class StreamDigest(val sha256: String, val length: Long, val exceededLimit: Boolean)

private const val BUFFER_SIZE: Int = 64 * 1024

/** Hashes [input] in fixed-size chunks, reading at most [limit] + 1 bytes so oversize input is detected. */
internal fun digestStream(input: InputStream, limit: Long = Long.MAX_VALUE - 1): StreamDigest {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    while (true) {
        val room = minOf(buffer.size.toLong(), limit + 1 - total).toInt()
        if (room <= 0) break
        val read = input.read(buffer, 0, room)
        if (read < 0) break
        digest.update(buffer, 0, read)
        total += read
        if (total > limit) break
    }
    return StreamDigest(Sha256.hex(digest.digest()), total, total > limit)
}
