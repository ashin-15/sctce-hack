package org.sakshi.processing.analysis

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SupportState
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Locator
import org.sakshi.core.model.Representation
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.processing.ocr.MlKitOcrProcessor
import org.sakshi.processing.ocr.OcrBox
import org.sakshi.processing.ocr.OcrEngine
import org.sakshi.processing.ocr.OcrFailure
import org.sakshi.processing.ocr.OcrFrame
import org.sakshi.processing.ocr.OcrLine
import org.sakshi.processing.ocr.OcrOutcome
import org.sakshi.processing.ocr.OcrPoint
import org.sakshi.processing.ocr.OcrProcessor
import org.sakshi.processing.ocr.OcrResult

/** A recognizer that returns a fixed outcome and records what it was given. Every string is synthetic. */
private class FakeOcr(private val outcome: OcrOutcome) : OcrProcessor {
    override val engine: OcrEngine = MlKitOcrProcessor.LATIN_ENGINE
    val received = mutableListOf<String>()

    override suspend fun process(imageBytes: ByteArray): OcrOutcome {
        received += Sha256.hex(Sha256.digest(imageBytes))
        return outcome
    }
}

class ImageAnalysisTest : AnalysisTestBase() {
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { 7 }
    private val gifBytes = "GIF89a".toByteArray() + ByteArray(32) { 1 }
    private val frame = OcrFrame(1080, 2400, 1, 1, 0)

    private fun line(text: String, block: Int, top: Int, confidence: Float? = 0.92f) =
        OcrLine(block, text, listOf(OcrPoint(10, top), OcrPoint(500, top), OcrPoint(500, top + 40), OcrPoint(10, top + 40)), OcrBox(10, top, 500, top + 40), confidence, "en")

    private val screenshot = OcrOutcome.Success(
        OcrResult(
            listOf(
                line("please stop messaging me", 0, 100),
                line("you are an idiot", 1, 300),
                line("reply now", 1, 350),
            ),
            frame,
        ),
    )

    private fun analyser(
        ocr: OcrProcessor?,
        limits: AnalysisLimits = AnalysisLimits(),
        threatClassifier: ThreatLanguageClassifier? = null,
    ) = TextAnalysis(vault, RulesEngineFactory.default(), clock, ids, limits, ocr = ocr, threatClassifier = threatClassifier)

    private fun importImage(bytes: ByteArray = pngBytes, mime: String = "image/png"): String =
        importBytes(bytes, declaredMime = mime, kind = AcquisitionKind.SELECTED_VISUAL_MEDIA)

    private fun refusal(ocr: OcrProcessor?, evidenceId: String, limits: AnalysisLimits = AnalysisLimits()) = runBlocking {
        assertIs<AnalysisOutcome.NotAnalysable>(analyser(ocr, limits).analyse(evidenceId)).reason
    }

    private fun assertNothingWritten(evidenceId: String) = runBlocking<Unit> {
        assertTrue(events().isEmpty())
        assertTrue(vault.derivatives.listForEvidence(evidenceId).isEmpty())
    }

    @Test
    fun screenshotBecomesOneEventWithSuggestionsTracedToTextAndImageRegion() = runBlocking<Unit> {
        val evidenceId = importImage()
        val ocr = FakeOcr(screenshot)
        val outcome = assertIs<AnalysisOutcome.Analysed>(analyser(ocr).analyse(evidenceId))
        assertEquals(InputKind.IMAGE_TEXT, outcome.kind)
        assertEquals(1, outcome.eventCount)
        assertTrue(AnalysisWarning.OCR_LATIN_SCRIPT_ONLY in outcome.warnings, "every image says only Latin script is read")
        assertEquals(listOf(Sha256.hex(Sha256.digest(pngBytes))), ocr.received, "the engine saw the exact original")

        val derivative = vault.derivatives.latest(evidenceId, DerivativeKind.OCR)!!
        assertEquals("please stop messaging me\n\nyou are an idiot\nreply now", derivative.text)
        assertEquals(MlKitOcrProcessor.LATIN_ENGINE.id, derivative.toolId)
        assertEquals("16.0.1", derivative.toolVersion)
        val regions = vault.derivatives.regions(derivative.id)
        assertEquals(3, regions.size)

        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(SourceKind.SELECTED_IMAGE, event.source.kind)
        assertEquals("mlkit-text-recognition-latin-bundled-16.0.1", event.source.parserVersion.value)
        assertEquals(TextStatus.AVAILABLE, event.coverage.textStatus)
        assertEquals(Direction.UNKNOWN, event.direction, "an image does not say who sent it")
        assertEquals(TimeBasis.UNKNOWN, event.timestamp.basis, "an image does not say when")
        assertNull(event.sender.displayLabel)

        val body = event.evidenceReferences.first()
        assertEquals(Representation.OCR_DERIVATIVE, body.representation)
        assertEquals(derivative.id, body.artifactId.value)
        assertEquals(derivative.text, EventText(vault).bodyOf(event))
        assertTrue(event.evidenceReferences.all { it.representation == Representation.OCR_DERIVATIVE })

        val category = event.categories.single()
        assertEquals(CategoryLabel.VERBAL_ABUSE, category.label)
        val referenced = event.evidenceReferences.filter { it.referenceId in category.evidenceReferenceIds }
        val cue = referenced.single { it.locator is Locator.Text }
        assertEquals("idiot", EventText(vault).quote(event, cue)?.lowercase(Locale.ROOT))
        val region = referenced.single { it.locator is Locator.ImageOrPageRegion }.locator as Locator.ImageOrPageRegion
        val stored = regions.single { it.id == region.regionId.value }
        assertEquals("[[10,300],[500,300],[500,340],[10,340]]", stored.polygonJson, "the cue points at the line it was read from")
        assertEquals(0, region.pageIndex)
        assertEquals(SupportState.ANALYZED, vault.evidence.details(evidenceId)?.supportState)
    }

    @Test
    fun ocrTextIsNotSentToThreatClassifier() = runBlocking<Unit> {
        val evidenceId = importImage()
        var classifierCalls = 0
        val classifier = ThreatLanguageClassifier { _, inputs ->
            classifierCalls += 1
            inputs.map { ThreatLanguageResult(ThreatLanguageResultStatus.POSSIBLE_THREAT_LANGUAGE, "idiot") }
        }

        val outcome = assertIs<AnalysisOutcome.Analysed>(
            analyser(FakeOcr(screenshot), threatClassifier = classifier).analyse(evidenceId),
        )

        assertEquals(InputKind.IMAGE_TEXT, outcome.kind)
        assertEquals(0, classifierCalls)
        assertTrue(vault.threatAnalysisRuns.forEvent(events().single().eventId.value).isEmpty())
    }

    @Test
    fun imageWithoutReadableLatinTextAbstainsAndWritesNothing() {
        val id = importImage()
        assertEquals(NotAnalysableReason.NO_TEXT_RECOGNISED, refusal(FakeOcr(OcrOutcome.NoText(frame)), id))
        assertNothingWritten(id)
        assertEquals(SupportState.SAVED, runBlocking { vault.evidence.details(id)?.supportState })
    }

    @Test
    fun undecodableImageAndEngineErrorsAreDistinctRefusals() {
        val id = importImage()
        assertEquals(NotAnalysableReason.IMAGE_NOT_DECODABLE, refusal(FakeOcr(OcrOutcome.Failed(OcrFailure.UNDECODABLE)), id))
        assertEquals(NotAnalysableReason.RECOGNITION_FAILED, refusal(FakeOcr(OcrOutcome.Failed(OcrFailure.ENGINE_FAILED)), id))
        assertEquals(NotAnalysableReason.RECOGNITION_FAILED, refusal(FakeOcr(OcrOutcome.Failed(OcrFailure.OUT_OF_MEMORY)), id))
        assertNothingWritten(id)
    }

    @Test
    fun lowOrMissingLineScoresMarkTheTextUncertainButKeepEveryLine() = runBlocking<Unit> {
        val id = importImage()
        val shaky = OcrOutcome.Success(OcrResult(listOf(line("you are an idiot", 0, 10, 0.31f), line("synthetic", 0, 60, null), line("fine", 1, 120)), frame))
        val outcome = assertIs<AnalysisOutcome.Analysed>(analyser(FakeOcr(shaky)).analyse(id))
        assertEquals(2, outcome.warningCounts[AnalysisWarning.OCR_LOW_CONFIDENCE_LINES])
        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, event.coverage.textStatus)
        assertEquals("you are an idiot\nsynthetic\n\nfine", EventText(vault).bodyOf(event))
        assertEquals(listOf(CategoryLabel.VERBAL_ABUSE), event.categories.map { it.label }, "uncertain text still gets suggestions to review")
        assertEquals(SupportState.PARTIAL, vault.evidence.details(id)?.supportState)
    }

    @Test
    fun secondRunIsRefusedAndDoesNotRecogniseAgain() = runBlocking<Unit> {
        val id = importImage()
        val ocr = FakeOcr(screenshot)
        assertIs<AnalysisOutcome.Analysed>(analyser(ocr).analyse(id))
        assertEquals(NotAnalysableReason.ALREADY_ANALYSED, refusal(ocr, id))
        assertEquals(1, ocr.received.size)
        assertEquals(1, events().size)
        assertEquals(1, vault.derivatives.listForEvidence(id).size)
    }

    @Test
    fun aStoredDerivativeWithoutEventsIsReusedWithoutRecognisingAgain() = runBlocking<Unit> {
        val id = importImage()
        val first = FakeOcr(screenshot)
        val draft = OcrRecord.draft(screenshot.result, first.engine, ids, AnalysisLimits().minOcrLineConfidence)
        VaultTextDerivatives(vault.derivatives).saveOcr(id, draft)
        val ocr = FakeOcr(OcrOutcome.Failed(OcrFailure.ENGINE_FAILED))
        assertIs<AnalysisOutcome.Analysed>(analyser(ocr).analyse(id))
        assertTrue(ocr.received.isEmpty())
        assertEquals(1, vault.derivatives.listForEvidence(id).size)
    }

    @Test
    fun withoutAnEngineImagesStayPreserveOnly() {
        val id = importImage()
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(null, id))
        assertNothingWritten(id)
    }

    @Test
    fun otherImageTypesStayPreserveOnlyEvenWithAnEngine() {
        val ocr = FakeOcr(screenshot)
        val id = importImage(gifBytes, "image/gif")
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(ocr, id))
        assertTrue(ocr.received.isEmpty())
    }

    @Test
    fun oversizeImageIsRefusedBeforeReading() {
        val ocr = FakeOcr(screenshot)
        val id = importImage()
        assertEquals(NotAnalysableReason.TOO_LARGE, refusal(ocr, id, AnalysisLimits(maxImageBytes = 10)))
        assertTrue(ocr.received.isEmpty())
        assertNothingWritten(id)
    }

    @Test
    fun aStoredSourceMapThatDoesNotFitTheTextIsNotTrusted() {
        assertNull(OcrRecord.read(OcrDerivative("synthetic-d", "abc", """{"version":1,"regions":[{"region_id":"r","start":0,"end":9}]}""")))
        assertNull(OcrRecord.read(OcrDerivative("synthetic-d", "abc", """{"version":2,"regions":[]}""")))
        assertNull(OcrRecord.read(OcrDerivative("synthetic-d", "abc", "not json")))
        assertNull(OcrRecord.read(OcrDerivative("synthetic-d", "abc", null)))
        val ok = OcrRecord.read(OcrDerivative("synthetic-d", "abc", """{"version":1,"regions":[{"region_id":"r","start":0,"end":3,"confidence":null}]}"""))
        assertEquals(listOf("r"), ok?.regions?.map { it.id })
        assertNull(ok?.regions?.single()?.confidence)
    }
}
