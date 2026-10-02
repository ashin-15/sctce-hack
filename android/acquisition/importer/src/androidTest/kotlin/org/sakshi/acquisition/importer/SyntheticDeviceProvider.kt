package org.sakshi.acquisition.importer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException

/** Serves synthetic bytes only. Not exported, so only this test package can reach it. */
class SyntheticDeviceProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = describe(uri)?.mime

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? {
        val item = describe(uri) ?: return null
        val columns = projection ?: arrayOf("_display_name", "_size")
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { if (it == "_display_name") item.name else if (it == "_size") item.size else null })
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val path = uri.lastPathSegment
        if (path == DENIED) throw SecurityException("synthetic")
        val bytes = when (path) {
            IMAGE -> imageBytes()
            TEXT -> textBytes()
            MIDWAY -> imageBytes()
            else -> throw java.io.FileNotFoundException("synthetic")
        }
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val writeSide = pipe[1]
        Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                    if (path == MIDWAY) {
                        out.write(bytes, 0, bytes.size / 2)
                        out.flush()
                        writeSide.closeWithError("synthetic")
                    } else {
                        out.write(bytes)
                    }
                }
            } catch (_: IOException) {
                // The reader went away or the failure above was injected on purpose.
            }
        }.start()
        return pipe[0]
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

    private class Described(val mime: String, val name: String, val size: Long)

    private fun describe(uri: Uri): Described? = when (uri.lastPathSegment) {
        IMAGE -> Described("image/jpeg", "synthetic-image.jpg", IMAGE_SIZE.toLong())
        TEXT -> Described("text/plain", "synthetic-notes.txt", textBytes().size.toLong())
        MIDWAY -> Described("image/jpeg", "synthetic-midway.jpg", IMAGE_SIZE.toLong())
        DENIED -> Described("image/jpeg", "synthetic-denied.jpg", 1)
        else -> null
    }

    companion object {
        const val IMAGE: String = "image"
        const val TEXT: String = "text"
        const val MIDWAY: String = "midway"
        const val DENIED: String = "denied"
        const val IMAGE_SIZE: Int = 1024 * 1024

        fun imageBytes(): ByteArray {
            var state = 0x2545F4914F6CDD1DL
            return ByteArray(IMAGE_SIZE) {
                state = state xor (state shl 13)
                state = state xor (state ushr 7)
                state = state xor (state shl 17)
                state.toByte()
            }
        }

        fun textBytes(): ByteArray =
            "synthetic notes ഇത് यह 😀\nline two\n".toByteArray(Charsets.UTF_8)
    }
}
