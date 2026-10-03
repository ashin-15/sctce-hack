package org.sakshi.processing.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBitmapFactory

/** Every image and string here is synthetic. The ML Kit engine itself only runs in the device tests. */
@RunWith(RobolectricTestRunner::class)
class MlKitOcrProcessorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        MlKitContext.initializeIfNeeded(context)
    }

    /** A recognizer that returns [result] and records the rotation of each image it was given. */
    private class FakeRecognizer(private val result: () -> Task<Text>) {
        val rotations = mutableListOf<Int>()
        var closed = false
        var created = 0

        fun factory(): () -> TextRecognizer = {
            created++
            Proxy.newProxyInstance(TextRecognizer::class.java.classLoader, arrayOf(TextRecognizer::class.java)) { proxy, method, args ->
                when (method.name) {
                    "process" -> {
                        (args?.firstOrNull() as? InputImage)?.let { rotations += it.rotationDegrees }
                        result()
                    }
                    "close" -> {
                        closed = true
                        null
                    }
                    "hashCode" -> 1
                    "equals" -> args?.getOrNull(0) === proxy
                    "toString" -> "FakeRecognizer"
                    else -> null
                }
            } as TextRecognizer
        }
    }

    private fun TestScope.processor(fake: FakeRecognizer) =
        MlKitOcrProcessor(fake.factory(), StandardTestDispatcher(testScheduler))

    private fun line(text: String, rect: Rect, corners: List<Point>, language: String = "en", confidence: Float = 0.9f) =
        Text.Line(text, rect, corners, language, null, emptyList<Text.Element>(), confidence, 0f)

    private fun corners(rect: Rect) =
        listOf(Point(rect.left, rect.top), Point(rect.right, rect.top), Point(rect.right, rect.bottom), Point(rect.left, rect.bottom))

    private fun block(vararg lines: Text.Line) =
        Text.TextBlock(lines.joinToString("\n") { it.text }, Rect(0, 0, 1, 1), emptyList<Point>(), "en", null, lines.toList())

    private fun text(vararg blocks: Text.TextBlock) = Text(blocks.joinToString("\n") { it.text }, blocks.toList())

    private fun png(width: Int = 40, height: Int = 20): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun jpegWithOrientation(orientation: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val file = File.createTempFile("synthetic", ".jpg", context.cacheDir)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyAndCorruptBytesAreUndecodableAndNeverReachTheEngine() = runTest {
        val fake = FakeRecognizer { Tasks.forResult(text()) }
        val ocr = processor(fake)
        assertEquals(OcrOutcome.Failed(OcrFailure.UNDECODABLE), ocr.process(ByteArray(0)))
        assertEquals(OcrOutcome.Failed(OcrFailure.UNDECODABLE), ocr.process(byteArrayOf(1, 2, 3, 4, 5)))
        assertEquals(0, fake.created)
    }

    @Test
    fun linesKeepBlockOrderConfidenceLanguageAndPolygon() = runTest {
        val first = Rect(2, 3, 30, 9)
        val result = text(
            block(line("Please stop", first, corners(first)), line("messaging me", Rect(2, 10, 30, 16), emptyList(), language = "und")),
            block(line("synthetic", Rect(1, 1, 5, 5), emptyList(), confidence = 0.4f)),
        )
        val outcome = processor(FakeRecognizer { Tasks.forResult(result) }).process(png())
        val lines = assertIs<OcrOutcome.Success>(outcome).result.lines
        assertEquals(listOf("Please stop", "messaging me", "synthetic"), lines.map { it.text })
        assertEquals(listOf(0, 0, 1), lines.map { it.blockIndex })
        assertEquals(listOf(0.9f, 0.9f, 0.4f), lines.map { it.confidence })
        assertEquals("en", lines[0].language)
        assertNull(lines[1].language, "undetermined language is not a language")
        assertEquals(OcrBox(2, 3, 30, 9), lines[0].box)
        assertEquals(listOf(OcrPoint(2, 3), OcrPoint(30, 3), OcrPoint(30, 9), OcrPoint(2, 9)), lines[0].polygon)
        assertEquals(listOf(OcrPoint(2, 10), OcrPoint(30, 10), OcrPoint(30, 16), OcrPoint(2, 16)), lines[1].polygon, "box corners stand in")
        assertEquals(OcrFrame(40, 20, 1, ExifInterface.ORIENTATION_UNDEFINED, 0), assertIs<OcrOutcome.Success>(outcome).result.frame)
    }

    @Test
    fun confidenceOutsideZeroToOneIsTreatedAsMissing() = runTest {
        val rect = Rect(0, 0, 4, 4)
        val result = text(block(line("a", rect, emptyList(), confidence = Float.NaN), line("b", rect, emptyList(), confidence = 1.5f)))
        val lines = assertIs<OcrOutcome.Success>(processor(FakeRecognizer { Tasks.forResult(result) }).process(png())).result.lines
        assertEquals(listOf(null, null), lines.map { it.confidence })
    }

    @Test
    fun blankLinesAndEmptyResultsAreNoTextNotSuccess() = runTest {
        val rect = Rect(0, 0, 4, 4)
        val blank = processor(FakeRecognizer { Tasks.forResult(text(block(line("   ", rect, emptyList())))) }).process(png())
        assertIs<OcrOutcome.NoText>(blank)
        val empty = processor(FakeRecognizer { Tasks.forResult(text()) }).process(png())
        assertEquals(40, assertIs<OcrOutcome.NoText>(empty).frame.originalWidth)
    }

    @Test
    fun engineErrorIsAFailureNotEmptyText() = runTest {
        val outcome = processor(FakeRecognizer { Tasks.forException(IllegalStateException("synthetic engine fault")) }).process(png())
        assertEquals(OcrOutcome.Failed(OcrFailure.ENGINE_FAILED), outcome)
    }

    @Test
    fun exifOrientationIsReadAndPassedToTheEngineAsRotation() = runTest {
        val fake = FakeRecognizer { Tasks.forResult(text()) }
        val outcome = processor(fake).process(jpegWithOrientation(ExifInterface.ORIENTATION_ROTATE_90))
        val frame = assertIs<OcrOutcome.NoText>(outcome).frame
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, frame.exifOrientation)
        assertEquals(90, frame.rotationDegrees)
        assertEquals(listOf(90), fake.rotations)
    }

    @Test
    fun rotationCoversEveryExifOrientation() {
        assertEquals(0, rotationOf(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(0, rotationOf(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, rotationOf(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(180, rotationOf(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(180, rotationOf(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(90, rotationOf(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(90, rotationOf(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(270, rotationOf(ExifInterface.ORIENTATION_TRANSVERSE))
        assertEquals(270, rotationOf(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun engineIsCreatedOnceOnFirstUseAndReleasedOnClose() = runTest {
        val fake = FakeRecognizer { Tasks.forResult(text()) }
        val ocr = processor(fake)
        assertEquals(0, fake.created)
        ocr.process(png())
        ocr.process(png())
        assertEquals(1, fake.created)
        ocr.close()
        assertTrue(fake.closed)
        ocr.process(png())
        assertEquals(2, fake.created, "a closed processor makes a new engine when used again")
    }

    @Test
    fun largeImagesAreDownsampledToAtMostSixteenMegapixels() {
        assertEquals(1, calculateInSampleSize(100, 100))
        assertEquals(1, calculateInSampleSize(4000, 4000))
        assertEquals(2, calculateInSampleSize(4001, 4000))
        assertEquals(2, calculateInSampleSize(8000, 8000))
        assertEquals(4, calculateInSampleSize(10_000, 10_000))
        val extreme = calculateInSampleSize(100_000, 100_000)
        assertTrue((100_000L / extreme) * (100_000L / extreme) <= 16_000_000L)
        assertEquals(1, calculateInSampleSize(0, 0))
        assertEquals(1, calculateInSampleSize(-10, 100))
    }

    @Test
    fun engineIdentityNamesTheLinkedVersion() {
        assertEquals("16.0.1", MlKitOcrProcessor.LATIN_ENGINE.version)
        assertEquals(setOf("Latn"), MlKitOcrProcessor.LATIN_ENGINE.scripts)
    }

    @Test
    fun polygonJsonHasNoSpaces() {
        assertEquals("[]", polygonToJson(emptyList()))
        assertEquals("[[0,0],[100,0],[100,50],[0,50]]", polygonToJson(listOf(OcrPoint(0, 0), OcrPoint(100, 0), OcrPoint(100, 50), OcrPoint(0, 50))))
    }
}
