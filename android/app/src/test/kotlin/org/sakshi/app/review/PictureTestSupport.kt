package org.sakshi.app.review

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest

/** Synthetic pictures: flat colour, no content. Nothing here is real-world evidence. */
object SyntheticPictures {
    fun png(width: Int, height: Int): ByteArray = encode(width, height, Bitmap.CompressFormat.PNG)

    /** A JPEG whose EXIF orientation tag is [orientation]; the pixels are [width] by [height] as stored. */
    fun jpeg(width: Int, height: Int, orientation: Int): ByteArray {
        val file = File.createTempFile("synthetic", ".jpg")
        try {
            file.writeBytes(encode(width, height, Bitmap.CompressFormat.JPEG))
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun encode(width: Int, height: Int, format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}

/** Puts synthetic picture bytes into a case as saved originals. */
abstract class PictureTestBase : AnalysisTestBase() {
    protected fun importPicture(caseId: String, bytes: ByteArray, mime: String = "image/png"): String = runBlocking {
        val request = ImportRequest(caseId, AcquisitionKind.SELECTED_VISUAL_MEDIA, AccessClass.USER_MEDIATED, "synthetic-test", mime, null, null, null, 50_000_000L)
        vault.evidence.import(request, ByteArrayInputStream(bytes)).id
    }
}
