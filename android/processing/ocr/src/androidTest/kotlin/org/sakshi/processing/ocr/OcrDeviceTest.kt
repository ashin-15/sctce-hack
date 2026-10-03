package org.sakshi.processing.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Runs the real bundled ML Kit Latin engine on this phone over images drawn here from synthetic strings. Nothing on
 * the phone outside the test package is read. Timings are from a debug build and are observations, not V-10 numbers.
 */
class OcrDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ocr = MlKitOcrProcessor()

    @After
    fun close() = ocr.close()

    private fun render(lines: List<String>, width: Int = 1080, textSize: Float = 48f): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        val lineHeight = (textSize * 1.6f).toInt()
        val bitmap = Bitmap.createBitmap(width, lineHeight * (lines.size + 2), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            lines.forEachIndexed { index, line -> drawText(line, 60f, (lineHeight * (index + 1.5f)), paint) }
        }
        return bitmap
    }

    private fun Bitmap.encode(format: Bitmap.CompressFormat): ByteArray =
        ByteArrayOutputStream().also { compress(format, 95, it) }.toByteArray()

    private fun timed(bytes: ByteArray): Pair<OcrOutcome, Long> = runBlocking {
        val started = System.nanoTime()
        val outcome = ocr.process(bytes)
        outcome to (System.nanoTime() - started) / 1_000_000
    }

    private fun recognisedText(outcome: OcrOutcome): String =
        OcrDocument.of(assertIs<OcrOutcome.Success>(outcome).result).text.lowercase(Locale.ROOT)

    @Test
    fun thisTestPackageHasNoNetworkPermission() {
        val packages = context.packageManager
        for (permission in listOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)) {
            assertEquals(
                PackageManager.PERMISSION_DENIED,
                packages.checkPermission(permission, context.packageName),
                "$permission must not be granted to the process that runs ML Kit",
            )
        }
    }

    @Test
    fun readsSyntheticLatinScreenshotWithoutNetworkPermission() {
        val png = render(listOf("Please stop messaging me", "You are an idiot", "reply now")).encode(Bitmap.CompressFormat.PNG)
        val (cold, coldMs) = timed(png)
        val text = recognisedText(cold)
        assertTrue("stop messaging me" in text, text)
        assertTrue("idiot" in text, text)
        val (warm, warmMs) = timed(png)
        assertEquals(text, recognisedText(warm), "the same image gives the same text")
        val lines = assertIs<OcrOutcome.Success>(warm).result.lines
        record(
            "ocr_latin_synthetic",
            "cold_ms" to coldMs,
            "warm_ms" to warmMs,
            "lines" to lines.size,
            "min_confidence" to (lines.mapNotNull { it.confidence }.minOrNull() ?: -1f),
            "languages" to lines.map { it.language ?: "null" }.distinct().joinToString("|"),
        )
    }

    @Test
    fun exifRotatedJpegIsReadUpright() {
        val upright = render(listOf("Please stop messaging me"))
        val turned = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(270f) }, true)
        val file = File.createTempFile("synthetic-ocr", ".jpg", context.cacheDir)
        try {
            file.writeBytes(turned.encode(Bitmap.CompressFormat.JPEG))
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val (outcome, _) = timed(file.readBytes())
            assertEquals(90, assertIs<OcrOutcome.Success>(outcome).result.frame.rotationDegrees)
            assertTrue("stop messaging me" in recognisedText(outcome), recognisedText(outcome))
        } finally {
            file.delete()
        }
    }

    /**
     * The Latin engine does not read Malayalam or Devanagari. This records what it returns for them so the abstention
     * path is based on what the engine does on a phone, not on an assumption.
     */
    @Test
    fun malayalamAndDevanagariAreNotReadAsTheirScripts() {
        val samples = mapOf(
            "malayalam" to listOf("എനിക്ക് മെസ്സേജ് അയക്കരുത്", "ഇത് ഒരു പരീക്ഷണം ആണ്"),
            "devanagari" to listOf("मुझे संदेश मत भेजो", "यह एक परीक्षण है"),
        )
        for ((script, lines) in samples) {
            val (outcome, ms) = timed(render(lines).encode(Bitmap.CompressFormat.PNG))
            val recognised = (outcome as? OcrOutcome.Success)?.result?.lines.orEmpty()
            val text = recognised.joinToString("\n") { it.text }
            assertTrue(text.none { Character.UnicodeScript.of(it.code) in setOf(Character.UnicodeScript.MALAYALAM, Character.UnicodeScript.DEVANAGARI) })
            record(
                "ocr_unsupported_$script",
                "outcome" to outcome::class.java.simpleName,
                "ms" to ms,
                "lines" to recognised.size,
                "chars" to text.length,
                "max_confidence" to (recognised.mapNotNull { it.confidence }.maxOrNull() ?: -1f),
                "min_confidence" to (recognised.mapNotNull { it.confidence }.minOrNull() ?: -1f),
            )
        }
    }

    @Test
    fun corruptBytesAreUndecodableOnTheDevice() {
        assertEquals(OcrOutcome.Failed(OcrFailure.UNDECODABLE), timed(ByteArray(512) { (it * 31).toByte() }).first)
    }

    private fun record(test: String, vararg entries: Pair<String, Any>) {
        val all = entries.toList() + listOf("model" to Build.MODEL, "sdk" to Build.VERSION.SDK_INT, "build" to "debug")
        val bundle = Bundle().apply {
            putString("test", test)
            all.forEach { (key, value) -> putString(key, value.toString()) }
        }
        Log.i(TAG, "$test " + all.joinToString(" ") { "${it.first}=${it.second}" })
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }

    private companion object {
        const val TAG = "SakshiBench"
    }
}
