package org.sakshi.processing.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Locator
import org.sakshi.core.model.Representation
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.processing.ocr.MlKitOcrProcessor

/**
 * A synthetic screenshot drawn here goes through the real encrypted vault and the real bundled ML Kit engine:
 * saved, read, regions stored, one event with a suggestion anchored to text and image region.
 */
class ImageAnalysisDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrapper = KeystoreKeyWrapper(alias = "synthetic-image-${UUID.randomUUID()}", requireUserAuthentication = false)
    private val ocr = MlKitOcrProcessor()
    private var vault: Vault? = null

    @After
    fun cleanUp() {
        ocr.close()
        runCatching { vault?.close() }
        runCatching { wrapper.delete() }
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    private fun screenshot(lines: List<String>): ByteArray {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 48f
        }
        val bitmap = Bitmap.createBitmap(1080, 120 * (lines.size + 1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            lines.forEachIndexed { index, line -> drawText(line, 60f, 120f * (index + 1), paint) }
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun import(opened: Vault, caseId: String, bytes: ByteArray): String = runBlocking {
        val request = ImportRequest(
            caseId, AcquisitionKind.SELECTED_VISUAL_MEDIA, AccessClass.USER_MEDIATED, "synthetic-device-test",
            "image/png", null, "synthetic.png", null, 20_000_000L,
        )
        opened.evidence.import(request, ByteArrayInputStream(bytes)).id
    }

    @Test
    fun screenshotBecomesAnEventWithACueAnchoredToItsImageRegion() = runBlocking<Unit> {
        val opened = Vault.open(context, wrapper).also { vault = it }
        val caseId = opened.cases.create("synthetic-image-case").id
        val evidenceId = import(opened, caseId, screenshot(listOf("Please stop messaging me", "You are an idiot")))
        val analysis = TextAnalysis(opened, RulesEngineFactory.default(), Instant::now, { UUID.randomUUID().toString() }, ocr = ocr)

        val started = System.nanoTime()
        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidenceId))
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(InputKind.IMAGE_TEXT, outcome.kind)

        val event = opened.events.loadLatest(CaseId(caseId), Instant.ofEpochMilli(Long.MAX_VALUE)).single()
        assertEquals(SourceKind.SELECTED_IMAGE, event.source.kind)
        assertTrue(event.evidenceReferences.all { it.representation == Representation.OCR_DERIVATIVE })
        val derivative = checkNotNull(opened.derivatives.latest(evidenceId, DerivativeKind.OCR))
        val regionIds = opened.derivatives.regions(derivative.id).map { it.id }.toSet()
        val category = event.categories.single()
        val referenced = event.evidenceReferences.filter { it.referenceId in category.evidenceReferenceIds }
        val cue = referenced.single { it.locator is Locator.Text }
        assertEquals("idiot", EventText(opened).quote(event, cue)?.lowercase(Locale.ROOT))
        val region = referenced.single { it.locator is Locator.ImageOrPageRegion }.locator as Locator.ImageOrPageRegion
        assertTrue(region.regionId.value in regionIds, "the region anchor names a stored region")
        assertEquals(NotAnalysableReason.ALREADY_ANALYSED, assertIs<AnalysisOutcome.NotAnalysable>(analysis.analyse(evidenceId)).reason)
        record("image_analysis_end_to_end", "total_ms" to millis, "regions" to regionIds.size, "build" to "debug", *deviceState(context))
    }

    @Test
    fun malayalamScreenshotIsNotAnalysedAndNothingIsWritten() = runBlocking<Unit> {
        val opened = Vault.open(context, wrapper).also { vault = it }
        val caseId = opened.cases.create("synthetic-image-case").id
        val evidenceId = import(opened, caseId, screenshot(listOf("എനിക്ക് മെസ്സേജ് അയക്കരുത്", "ഇത് ഒരു പരീക്ഷണം ആണ്")))
        val analysis = TextAnalysis(opened, RulesEngineFactory.default(), Instant::now, { UUID.randomUUID().toString() }, ocr = ocr)
        val outcome = analysis.analyse(evidenceId)
        record("image_analysis_malayalam", "outcome" to outcome.toString().take(120), *deviceState(context))
        assertEquals(AnalysisOutcome.NotAnalysable(NotAnalysableReason.NO_TEXT_RECOGNISED), outcome)
        assertTrue(opened.derivatives.listForEvidence(evidenceId).isEmpty())
        assertTrue(opened.events.loadLatest(CaseId(caseId), Instant.ofEpochMilli(Long.MAX_VALUE)).isEmpty())
    }
}
