package org.sakshi.core.vault

import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * Passes bytes through until [job] is cancelled, then fails every read with [CancellationException], so a
 * blocking copy on a worker thread stops at the next read instead of running to the end of the stream.
 */
internal class CancellableInputStream(private val source: InputStream, private val job: Job) : InputStream() {
    override fun read(): Int {
        checkActive()
        return source.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkActive()
        return source.read(buffer, offset, length)
    }

    private fun checkActive() {
        if (!job.isActive) throw CancellationException("Import cancelled")
    }
}
