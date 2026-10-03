package org.sakshi.app.review

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.sakshi.core.vault.EvidenceRepository

/**
 * A picture decoded in memory for showing. [bitmap] is upright (turned as its EXIF orientation says) and reduced to a
 * bounded size. [uprightWidth] and [uprightHeight] are the pixel size of the saved original once upright, before the
 * reduction, so a region recorded against the original can be checked against it. The bitmap is never recycled by
 * the app: a frame that still shows it may be drawn after the review closes, so whoever holds the picture just drops
 * its reference and the garbage collector frees the pixels.
 */
class LoadedImage(val bitmap: Bitmap, val uprightWidth: Int, val uprightHeight: Int)

sealed interface ImageLoadResult {
    class Loaded(val image: LoadedImage) : ImageLoadResult

    /** The declared size is beyond what is shown safely on a phone. Nothing was decoded. */
    data object TooLarge : ImageLoadResult

    /** The picture is damaged, is not an image this phone can decode, or failed its integrity check. */
    data object Unreadable : ImageLoadResult
}

/** Brings a saved original image into memory for showing. Never writes the picture anywhere. */
interface EvidenceImageLoader {
    suspend fun load(evidenceId: String): ImageLoadResult
}

/**
 * Decodes the original from the vault's decrypted stream, in memory only: no file, cache or temporary directory is
 * written and nothing is passed to another app. The size is read first and the picture is decoded reduced to at most
 * about [maxLongSide] pixels on its longer side. A picture declaring more than [maxDeclaredSide] pixels on a side, or
 * [maxDeclaredPixels] in all, is refused without decoding. Runs on [dispatcher] and stops at the next read when cancelled.
 */
class VaultImageLoader(
    private val evidence: EvidenceRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxLongSide: Int = MAX_LONG_SIDE,
    private val maxDeclaredSide: Int = MAX_DECLARED_SIDE,
    private val maxDeclaredPixels: Long = MAX_DECLARED_PIXELS,
) : EvidenceImageLoader {
    override suspend fun load(evidenceId: String): ImageLoadResult = try {
        withContext(dispatcher) { decode(evidenceId) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        ImageLoadResult.Unreadable
    } catch (_: GeneralSecurityException) {
        ImageLoadResult.Unreadable
    } catch (_: IllegalArgumentException) {
        ImageLoadResult.Unreadable
    } catch (_: OutOfMemoryError) {
        ImageLoadResult.TooLarge
    }

    private suspend fun decode(evidenceId: String): ImageLoadResult {
        val job = currentCoroutineContext().job
        return evidence.openOriginal(evidenceId).use { reader ->
            // The decoder swallows a read error and may return a half-decoded picture, so every chunk is checked first.
            reader.verifyAll()
            fun stream(): InputStream = StoppableStream(reader.inputStream(), job)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream().use { BitmapFactory.decodeStream(it, null, bounds) }
            job.ensureActive()
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@use ImageLoadResult.Unreadable
            if (width > maxDeclaredSide || height > maxDeclaredSide || width.toLong() * height > maxDeclaredPixels) {
                return@use ImageLoadResult.TooLarge
            }
            val orientation = stream().use { orientationOf(it) }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(width, height) }
            val decoded = stream().use { BitmapFactory.decodeStream(it, null, options) }
            job.ensureActive()
            if (decoded == null) return@use ImageLoadResult.Unreadable
            val upright = turned(decoded, orientation)
            val swap = orientation in SWAPPING_ORIENTATIONS
            ImageLoadResult.Loaded(LoadedImage(upright, if (swap) height else width, if (swap) width else height))
        }
    }

    /** The smallest power of two that brings the longer side to at most [maxLongSide]. */
    internal fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxLongSide) sample *= 2
        return sample
    }

    private fun orientationOf(stream: InputStream): Int = try {
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** The picture as its camera meant it to be seen. Recycles [source] when a new bitmap replaces it. */
    private fun turned(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return source
        }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }

    companion object {
        /** Longest side of the decoded picture, in pixels. */
        const val MAX_LONG_SIDE: Int = 2048

        /** Pictures declaring a side or a pixel count beyond these are refused without decoding. */
        const val MAX_DECLARED_SIDE: Int = 30_000
        const val MAX_DECLARED_PIXELS: Long = 200_000_000L

        private val SWAPPING_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}

/** Passes bytes through until [job] is cancelled, then fails the next read, so a decode stops promptly. */
private class StoppableStream(private val source: InputStream, private val job: Job) : InputStream() {
    override fun read(): Int {
        check()
        return source.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check()
        return source.read(buffer, offset, length)
    }

    override fun available(): Int = source.available()

    private fun check() {
        if (!job.isActive) throw CancellationException("Picture loading cancelled")
    }
}
