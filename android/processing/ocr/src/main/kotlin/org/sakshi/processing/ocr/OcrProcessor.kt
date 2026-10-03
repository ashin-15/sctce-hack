package org.sakshi.processing.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Reads text from image bytes on the device. The bytes are only read; nothing is written anywhere. */
public interface OcrProcessor {
    public val engine: OcrEngine

    public suspend fun process(imageBytes: ByteArray): OcrOutcome
}

/** Images above this many pixels are decoded at a lower resolution, so a crafted file cannot exhaust memory. */
private const val MAX_PIXEL_COUNT: Long = 16_000_000L

/**
 * ML Kit Text Recognition v2 with the bundled Latin model: the model ships inside the app, so nothing is downloaded.
 * It reads Latin script only (English, Manglish and Hinglish in Latin letters). Malayalam and Devanagari text is not
 * read by it and comes back as missing text, never as a translation.
 *
 * The engine is created on first use and released by [close].
 */
public class MlKitOcrProcessor internal constructor(
    private val recognizerFactory: () -> TextRecognizer,
    private val dispatcher: CoroutineDispatcher,
) : OcrProcessor, AutoCloseable {
    public constructor(dispatcher: CoroutineDispatcher = Dispatchers.Default) :
        this({ TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }, dispatcher)

    override val engine: OcrEngine = LATIN_ENGINE

    private val lock = Any()
    private var recognizer: TextRecognizer? = null

    override suspend fun process(imageBytes: ByteArray): OcrOutcome = withContext(dispatcher) {
        if (imageBytes.isEmpty()) return@withContext OcrOutcome.Failed(OcrFailure.UNDECODABLE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext OcrOutcome.Failed(OcrFailure.UNDECODABLE)
        val orientation = exifOrientation(imageBytes)
        val frame = OcrFrame(
            originalWidth = bounds.outWidth,
            originalHeight = bounds.outHeight,
            sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight),
            exifOrientation = orientation,
            rotationDegrees = rotationOf(orientation),
        )
        val options = BitmapFactory.Options().apply { inSampleSize = frame.sampleSize }
        val bitmap = try {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        } catch (_: OutOfMemoryError) {
            return@withContext OcrOutcome.Failed(OcrFailure.OUT_OF_MEMORY)
        } ?: return@withContext OcrOutcome.Failed(OcrFailure.UNDECODABLE)
        try {
            recognise(bitmap, frame)
        } finally {
            bitmap.recycle()
        }
    }

    /** Recognises an already decoded [bitmap] whose coordinates are described by [frame]. */
    internal suspend fun recognise(bitmap: Bitmap, frame: OcrFrame): OcrOutcome {
        val text = try {
            client().process(InputImage.fromBitmap(bitmap, frame.rotationDegrees)).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return OcrOutcome.Failed(OcrFailure.ENGINE_FAILED)
        }
        val lines = text.textBlocks.flatMapIndexed { blockIndex, block ->
            block.lines.filter { it.text.isNotBlank() }.map { it.toOcrLine(blockIndex) }
        }
        return if (lines.isEmpty()) OcrOutcome.NoText(frame) else OcrOutcome.Success(OcrResult(lines, frame))
    }

    override fun close() {
        synchronized(lock) {
            recognizer?.close()
            recognizer = null
        }
    }

    private fun client(): TextRecognizer = synchronized(lock) { recognizer ?: recognizerFactory().also { recognizer = it } }

    public companion object {
        public val LATIN_ENGINE: OcrEngine = OcrEngine(
            id = "mlkit-text-recognition-latin-bundled",
            version = BuildConfig.MLKIT_TEXT_RECOGNITION_VERSION,
            scripts = setOf("Latn"),
        )
    }
}

private fun Text.Line.toOcrLine(blockIndex: Int): OcrLine {
    val corners = cornerPoints?.map { OcrPoint(it.x, it.y) }.orEmpty()
    val rect = boundingBox ?: corners.toRect()
    val polygon = corners.ifEmpty {
        listOf(OcrPoint(rect.left, rect.top), OcrPoint(rect.right, rect.top), OcrPoint(rect.right, rect.bottom), OcrPoint(rect.left, rect.bottom))
    }
    return OcrLine(
        blockIndex = blockIndex,
        text = text,
        polygon = polygon,
        box = OcrBox(rect.left, rect.top, rect.right, rect.bottom),
        confidence = confidence.takeIf { it.isFinite() && it in 0f..1f },
        language = recognizedLanguage.takeIf { it.isNotBlank() && it != UNDETERMINED_LANGUAGE },
    )
}

private const val UNDETERMINED_LANGUAGE = "und"

private fun List<OcrPoint>.toRect(): Rect =
    if (isEmpty()) Rect(0, 0, 0, 0) else Rect(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })

private val directExecutor = Executor { it.run() }

/** Waits for a Play services task. ML Kit tasks cannot be stopped, so a cancelled caller just stops waiting. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener(directExecutor) { continuation.resume(it) }
    addOnFailureListener(directExecutor) { continuation.resumeWithException(it) }
    addOnCanceledListener(directExecutor) { continuation.cancel() }
}

/** The EXIF orientation tag, or [ExifInterface.ORIENTATION_UNDEFINED] when the image has none or it cannot be read. */
internal fun exifOrientation(bytes: ByteArray): Int = try {
    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
} catch (_: IOException) {
    ExifInterface.ORIENTATION_UNDEFINED
}

/** Clockwise rotation that makes the image upright. Mirrored orientations keep their rotation part only. */
internal fun rotationOf(exifOrientation: Int): Int = when (exifOrientation) {
    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
    ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
    else -> 0
}

/** The smallest power-of-two reduction that brings the image to at most [maxPixels]. */
internal fun calculateInSampleSize(width: Int, height: Int, maxPixels: Long = MAX_PIXEL_COUNT): Int {
    if (width <= 0 || height <= 0) return 1
    var inSampleSize = 1
    while ((width / inSampleSize).toLong() * (height / inSampleSize).toLong() > maxPixels) inSampleSize *= 2
    return inSampleSize
}
