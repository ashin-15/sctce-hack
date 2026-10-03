package org.sakshi.processing.stt

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A segment and the half-open code-point range `[start, end)` its text occupies in [TranscriptDocument.text]. */
public data class PlacedSegment(public val segment: TranscriptSegment, public val start: Int, public val end: Int)

/**
 * The text of a transcription as one string, with each segment's place in it. Segments are joined by a line feed in
 * time order. Segment text is kept exactly as the engine returned it, apart from surrounding whitespace.
 */
public class TranscriptDocument private constructor(
    public val text: String,
    public val segments: List<PlacedSegment>,
    private val success: SttResult.Success,
) {
    /**
     * The source map stored beside the derivative text: for each segment its code-point range in the text and its time
     * range in the audio, in milliseconds. Confidence is the engine's uncalibrated signal.
     */
    public fun sourceMapJson(): String = buildJsonObject {
        put("version", SOURCE_MAP_VERSION)
        put("unit", "code_point")
        put("order", "time")
        put(
            "segments",
            buildJsonArray {
                segments.forEach { placed ->
                    add(
                        buildJsonObject {
                            put("start", placed.start)
                            put("end", placed.end)
                            put("start_ms", placed.segment.startMs)
                            put("end_ms", placed.segment.endMs)
                            put("confidence", placed.segment.uncalibratedConfidence?.let(::JsonPrimitive) ?: JsonNull)
                        },
                    )
                }
            },
        )
    }.toString()

    /** Facts about the extraction, for the derivative's quality record. Nothing here says the words are right. */
    public fun qualityJson(minSegmentConfidence: Float = DEFAULT_MIN_SEGMENT_CONFIDENCE): String = buildJsonObject {
        put("engine", success.engineVersion)
        put("model_id", success.modelId)
        put("model_sha256", success.modelSha256)
        put("language", success.language?.let(::JsonPrimitive) ?: JsonNull)
        put("audio_duration_ms", success.audioDurationMs)
        put("segment_count", segments.size)
        put("low_confidence_segments", segments.count { isLow(it.segment.uncalibratedConfidence, minSegmentConfidence) })
        put("min_segment_confidence", minSegmentConfidence)
        put("confidence_basis", "engine_mean_token_probability_uncalibrated")
        put("translated", false)
    }.toString()

    public companion object {
        private const val SEGMENT_SEPARATOR: String = "\n"
        private const val SOURCE_MAP_VERSION: Int = 1

        /** A demonstration setting, not calibrated, mirroring the OCR line setting (megaplan D-21). */
        public const val DEFAULT_MIN_SEGMENT_CONFIDENCE: Float = 0.5f

        /** True when a segment has no engine confidence or one under [limit]. */
        public fun isLow(confidence: Float?, limit: Float): Boolean = confidence == null || confidence < limit

        public fun of(success: SttResult.Success): TranscriptDocument {
            val text = StringBuilder()
            var codePoints = 0
            val placed = ArrayList<PlacedSegment>(success.segments.size)
            success.segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    text.append(SEGMENT_SEPARATOR)
                    codePoints += SEGMENT_SEPARATOR.length
                }
                val start = codePoints
                text.append(segment.text)
                codePoints += segment.text.codePointCount(0, segment.text.length)
                placed += PlacedSegment(segment, start, codePoints)
            }
            return TranscriptDocument(text.toString(), placed, success)
        }
    }
}
