package org.sakshi.app.review

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.R
import org.sakshi.app.support.FIXED_INSTANT
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.ui.resolve
import org.sakshi.core.database.RegionEntity
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.Locator
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.RulesEngineFactory
import org.sakshi.processing.analysis.TextAnalysis
import org.sakshi.processing.ocr.MlKitOcrProcessor
import org.sakshi.processing.ocr.OcrBox
import org.sakshi.processing.ocr.OcrEngine
import org.sakshi.processing.ocr.OcrFrame
import org.sakshi.processing.ocr.OcrLine
import org.sakshi.processing.ocr.OcrOutcome
import org.sakshi.processing.ocr.OcrPoint
import org.sakshi.processing.ocr.OcrProcessor
import org.sakshi.processing.ocr.OcrResult

/** Returns a fixed outcome for every image. Every string is synthetic. */
private class FixedOcr(private val outcome: OcrOutcome) : OcrProcessor {
    override val engine: OcrEngine = MlKitOcrProcessor.LATIN_ENGINE

    override suspend fun process(imageBytes: ByteArray): OcrOutcome = outcome
}

/** Hands back a prepared result and counts how often it was asked. */
private class FakeImageLoader(private val result: () -> ImageLoadResult) : EvidenceImageLoader {
    val requested = mutableListOf<String>()

    override suspend fun load(evidenceId: String): ImageLoadResult {
        requested += evidenceId
        return result()
    }
}

class EventReviewPictureTest : PictureTestBase() {
    private val width = 400
    private val height = 200
    private val counter = AtomicInteger()

    private fun line(text: String, top: Int, confidence: Float? = 0.9f) =
        OcrLine(0, text, listOf(OcrPoint(10, top), OcrPoint(300, top), OcrPoint(300, top + 30), OcrPoint(10, top + 30)), OcrBox(10, top, 300, top + 30), confidence, "en")

    private fun recognised(vararg lines: OcrLine) = OcrOutcome.Success(OcrResult(lines.toList(), OcrFrame(width, height, 1, 1, 0)))

    private val screenshot get() = recognised(line("please stop messaging me", 20), line("you are an idiot", 60), line("reply now", 100))

    /** One analysed picture: the case, the evidence id and its single event. */
    private class Picture(val caseId: String, val evidenceId: String, val event: Event)

    private fun analysedPicture(outcome: OcrOutcome = screenshot): Picture {
        val caseId = newCase()
        val evidenceId = importPicture(caseId, SyntheticPictures.png(width, height))
        val analysis = TextAnalysis(vault, RulesEngineFactory.default(), { FIXED_INSTANT }, { "synthetic-picture-${counter.incrementAndGet()}" }, ocr = FixedOcr(outcome))
        runBlocking { assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidenceId)) }
        val event = runBlocking { vault.events.loadLatest(CaseId(caseId), java.time.Instant.ofEpochMilli(Long.MAX_VALUE)) }.single()
        return Picture(caseId, evidenceId, event)
    }

    private fun upright() = LoadedImage(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888), width, height)

    private fun modelFor(picture: Picture, loader: EvidenceImageLoader) =
        EventReviewViewModel(picture.caseId, picture.event.eventId.value, vault, scope, loader)

    @Test
    fun aPictureEventLoadsTheImageAndOutlinesTheLineTheCueCameFrom() {
        val picture = analysedPicture()
        val image = upright()
        val loader = FakeImageLoader { ImageLoadResult.Loaded(image) }
        val state = await(modelFor(picture, loader).state) { it.picture is PictureState.Ready }
        assertEquals(listOf(picture.evidenceId), loader.requested)
        assertTrue(state.fromPicture)
        assertFalse(state.textUncertain)
        val ready = assertIs<PictureState.Ready>(state.picture)
        assertTrue(ready.image === image)
        val outline = ready.outlines.single()
        assertEquals(10f / width, outline.points.first().x, 0.0001f, "left edge of the idiot line")
        assertEquals(60f / height, outline.points.first().y, 0.0001f)
        assertEquals(300f / width, outline.points[1].x, 0.0001f)
        assertEquals(90f / height, outline.points[2].y, 0.0001f)
        assertEquals("idiot", state.marks.single().quote)
        assertTrue(state.body.orEmpty().startsWith("please stop messaging me"), "the text review is loaded alongside")
    }

    @Test
    fun reviewingTheSuggestionDoesNotDecodeThePictureAgain() {
        val picture = analysedPicture()
        val loader = FakeImageLoader { ImageLoadResult.Loaded(upright()) }
        val model = modelFor(picture, loader)
        await(model.state) { it.picture is PictureState.Ready }
        model.agree(0)
        val state = await(model.state) { it.notice != null }
        assertEquals(CategoryReviewStatus.ACCEPTED, state.categories.single().category.reviewStatus)
        assertEquals(1, loader.requested.size)
    }

    @Test
    fun anEventThatIsNotFromAPictureNeverTouchesTheLoader() {
        val caseId = newCase()
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        val event = events(caseId).first { it.categories.isNotEmpty() }
        val loader = FakeImageLoader { error("must not be called") }
        val state = await(EventReviewViewModel(caseId, event.eventId.value, vault, scope, loader).state) { it.loaded }
        assertEquals(SourceKind.SELECTED_EXPORT, event.source.kind)
        assertTrue(loader.requested.isEmpty())
        assertEquals(PictureState.None, state.picture)
        assertFalse(state.fromPicture)
        assertNull(state.uncertainLines)
    }

    @Test
    fun aLoaderFailureLeavesTheTextReviewUsableAndSaysWhy() {
        val picture = analysedPicture()
        val model = modelFor(picture, FakeImageLoader { ImageLoadResult.Unreadable })
        val state = await(model.state) { it.picture is PictureState.Unavailable }
        val message = assertIs<PictureState.Unavailable>(state.picture).message.resolve(context.resources)
        assertEquals(context.getString(R.string.review_picture_unreadable), message)
        assertTrue(message.contains("saved original is unchanged"))
        assertNotNull(state.body)
        model.agree(0)
        assertEquals(CategoryReviewStatus.ACCEPTED, await(model.state) { it.notice != null }.categories.single().category.reviewStatus)
    }

    @Test
    fun aTooLargePictureSaysSoInPlainWords() {
        val picture = analysedPicture()
        val state = await(modelFor(picture, FakeImageLoader { ImageLoadResult.TooLarge }).state) { it.picture is PictureState.Unavailable }
        assertEquals(
            "This picture is too large to show here. The saved original is unchanged.",
            assertIs<PictureState.Unavailable>(state.picture).message.resolve(context.resources),
        )
    }

    @Test
    fun lowScoringLinesMarkTheTextUncertainAndCountThem() {
        val picture = analysedPicture(recognised(line("you are an idiot", 60, 0.2f), line("reply now", 100, null), line("fine", 140)))
        val state = await(modelFor(picture, FakeImageLoader { ImageLoadResult.Unreadable }).state) { it.picture is PictureState.Unavailable }
        assertTrue(state.textUncertain)
        assertEquals(2, state.uncertainLines)
    }

    @Test
    fun closingTheReviewDropsThePictureWithoutRecyclingIt() {
        val picture = analysedPicture()
        val image = upright()
        val store = ViewModelStore()
        val model = ViewModelProvider(
            store,
            viewModelFactory { initializer { EventReviewViewModel(picture.caseId, picture.event.eventId.value, vault, scope, FakeImageLoader { ImageLoadResult.Loaded(image) }) } },
        )[EventReviewViewModel::class.java]
        await(model.state) { it.picture is PictureState.Ready }
        assertFalse(image.bitmap.isRecycled)
        store.clear()
        assertFalse(image.bitmap.isRecycled, "a frame still showing it must not hit a recycled bitmap")
        assertEquals(PictureState.None, model.state.value.picture)
    }

    @Test
    fun onlyRegionsThisEventPointsAtAreOutlinedAndUnknownOnesAreSkipped() {
        val picture = analysedPicture()
        val derivative = runBlocking { vault.derivatives.latest(picture.evidenceId, DerivativeKind.OCR) }!!
        val stored = runBlocking { vault.derivatives.regions(derivative.id) }
        assertEquals(3, stored.size, "the picture has three lines but the event points at one")
        val referenced = picture.event.evidenceReferences.map { it.locator }.filterIsInstance<Locator.ImageOrPageRegion>().single().regionId.value

        val all = RegionOutlines.of(picture.event, derivative.id, stored, width, height)
        assertEquals(1, all.size)
        assertEquals(1, stored.count { it.id == referenced })

        assertEquals(emptyList(), RegionOutlines.of(picture.event, derivative.id, stored.filter { it.id != referenced }, width, height), "an unknown region id is skipped")
        assertEquals(emptyList(), RegionOutlines.of(picture.event, "another-derivative", stored, width, height))
    }

    @Test
    fun aRegionWithAMalformedPolygonOrFrameIsLeftOutNotGuessed() {
        val picture = analysedPicture()
        val derivative = runBlocking { vault.derivatives.latest(picture.evidenceId, DerivativeKind.OCR) }!!
        val stored = runBlocking { vault.derivatives.regions(derivative.id) }
        fun with(change: (RegionEntity) -> RegionEntity) = stored.map(change)
        assertEquals(emptyList(), RegionOutlines.of(picture.event, derivative.id, with { it.copy(polygonJson = "[[1,2]]") }, width, height))
        assertEquals(emptyList(), RegionOutlines.of(picture.event, derivative.id, with { it.copy(transformJson = null) }, width, height))
        assertEquals(emptyList(), RegionOutlines.of(picture.event, derivative.id, stored, width + 1, height), "a picture of another size")
    }
}
