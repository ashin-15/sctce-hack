package org.sakshi.acquisition.importer

import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** Stops a blocking copy promptly: every read first checks that [job] is still active. */
internal class CancellableInputStream(delegate: InputStream, private val job: Job?) : FilterInputStream(delegate) {
    override fun read(): Int {
        job?.ensureActive()
        return super.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        job?.ensureActive()
        return super.read(buffer, offset, length)
    }
}
