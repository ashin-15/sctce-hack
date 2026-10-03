package org.sakshi.processing.stt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.sakshi.core.model.Locator
import org.sakshi.core.model.TextStatus

class TranscriptDocumentTest {
    private fun success(vararg segments: TranscriptSegment, duration: Long = 9_000): SttResult.Success = SttResult.Success(
        segments = segments.toList(),
        language = "ml",
        audioDurationMs = duration,
        modelId = "test-model",
        modelSha256 = "0".repeat(64),
        engineVersion = "fake-engine-1",
        timings = SttTimings(1, 2, 3, 6),
    )

    private val malayalam = "നീ എവിടെ പോയി"
    private val hindi = "तुम कहाँ हो"
    private val emoji = "call me 😡😡 now"

    private fun segments() = arrayOf(
        TranscriptSegment(malayalam, 0, 2_000, 0.9f),
        TranscriptSegment(hindi, 2_000, 4_500, 0.8f),
        TranscriptSegment(emoji, 4_500, 7_000, 0.7f),
    )

    @Test
    fun spansAreCodePointsNotUtf16Units() {
        val document = TranscriptDocument.of(success(*segments()))
        val placed = document.segments
        val offsets = document.text.offsetByCodePoints(0, 0)
        assertEquals(0, offsets)
        assertEquals(0, placed[0].start)
        assertEquals(malayalam.codePointCount(0, malayalam.length), placed[0].end)
        assertEquals(placed[0].end + 1, placed[1].start)
        assertEquals(placed[1].start + hindi.codePointCount(0, hindi.length), placed[1].end)
        assertEquals(placed[1].end + 1, placed[2].start)
        // Two emoji are four UTF-16 units but two code points.
        assertEquals(emoji.length - 2, emoji.codePointCount(0, emoji.length))
        assertEquals(placed[2].start + emoji.codePointCount(0, emoji.length), placed[2].end)
        placed.forEach { p ->
            val from = document.text.offsetByCodePoints(0, p.start)
            val to = document.text.offsetByCodePoints(0, p.end)
            assertEquals(p.segment.text, document.text.substring(from, to))
        }
    }

    @Test
    fun sourceMapRecordsTextSpansAndTimeRanges() {
        val document = TranscriptDocument.of(success(*segments()))
        val map = Json.parseToJsonElement(document.sourceMapJson()).jsonObject
        assertEquals("code_point", map.getValue("unit").jsonPrimitive.content)
        val entries = map.getValue("segments").jsonArray
        assertEquals(3, entries.size)
        val second = entries[1].jsonObject
        assertEquals(document.segments[1].start, second.getValue("start").jsonPrimitive.int)
        assertEquals(2_000L, second.getValue("start_ms").jsonPrimitive.long)
        assertEquals(4_500L, second.getValue("end_ms").jsonPrimitive.long)
    }

    @Test
    fun missingConfidenceIsStoredAsNullAndCountsAsLow() {
        val document = TranscriptDocument.of(success(TranscriptSegment("hello", 0, 1_000, null)))
        val entry = Json.parseToJsonElement(document.sourceMapJson()).jsonObject.getValue("segments").jsonArray[0].jsonObject
        assertEquals(JsonNull, entry.getValue("confidence"))
        val quality = Json.parseToJsonElement(document.qualityJson()).jsonObject
        assertEquals(1, quality.getValue("low_confidence_segments").jsonPrimitive.int)
        assertEquals("engine_mean_token_probability_uncalibrated", quality.getValue("confidence_basis").jsonPrimitive.content)
        assertEquals("false", quality.getValue("translated").jsonPrimitive.content)
    }

    @Test
    fun oneEventPerClipWithAnchorsToTextAndAudio() {
        val plan = AudioEventPlan.of(success(*segments()))
        assertEquals(TextStatus.AVAILABLE, plan.textStatus)
        assertEquals(Locator.AudioTime(0, 9_000), plan.clipRange)
        val second = plan.document.segments[1]
        val within = assertNotNull(plan.anchorFor(second.start + 1, second.end - 1))
        assertEquals(Locator.Text(second.start + 1, second.end - 1), within.text)
        assertEquals(Locator.AudioTime(2_000, 4_500), within.audio)
    }

    @Test
    fun anchorAcrossSegmentsSpansTheirTimeRangesAndLineFeedAloneHasNone() {
        val plan = AudioEventPlan.of(success(*segments()))
        val first = plan.document.segments[0]
        val second = plan.document.segments[1]
        val across = assertNotNull(plan.anchorFor(first.end - 2, second.start + 2))
        assertEquals(Locator.AudioTime(0, 4_500), across.audio)
        assertNull(plan.anchorFor(first.end, first.end + 1))
        assertNull(plan.anchorFor(5, 5))
        assertNull(plan.anchorFor(plan.document.text.length + 10, plan.document.text.length + 12))
    }

    @Test
    fun zeroLengthSegmentStillGetsANonEmptyAudioRange() {
        val plan = AudioEventPlan.of(success(TranscriptSegment("ok", 1_000, 1_000, 0.9f)))
        val anchor = assertNotNull(plan.anchorFor(0, 2))
        assertEquals(Locator.AudioTime(1_000, 1_001), anchor.audio)
    }

    @Test
    fun lowOrMissingConfidenceMakesTheEventExtractionUncertain() {
        val low = AudioEventPlan.of(success(TranscriptSegment("a b", 0, 1_000, 0.2f), TranscriptSegment("c", 1_000, 2_000, 0.9f)))
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, low.textStatus)
        assertEquals(1, low.lowConfidenceSegments)
        val unknown = AudioEventPlan.of(success(TranscriptSegment("a", 0, 1_000, null)))
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, unknown.textStatus)
    }
}
