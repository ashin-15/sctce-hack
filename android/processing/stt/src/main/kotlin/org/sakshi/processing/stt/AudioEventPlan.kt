package org.sakshi.processing.stt

import org.sakshi.core.model.Locator
import org.sakshi.core.model.Representation
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus

/** A cue's place in the transcript text and in the audio it was heard in. */
public data class CueAnchor(public val text: Locator.Text, public val audio: Locator.AudioTime)

/**
 * What the analysis layer creates for one transcribed clip, mirroring the screenshot rule (megaplan D-20): exactly one
 * event per clip, whose text is the whole transcript. Who spoke, in which direction and when stay unknown until the user
 * says: speech-to-text does not decide who said what, and the clip has no trustworthy clock. Each cue is anchored to its
 * text span and to the audio time range of the segments that span touches.
 *
 * The event is marked [TextStatus.EXTRACTION_UNCERTAIN] when any segment has no engine confidence or a low one; the text is
 * still analysed and nothing is hidden. The confidence is the engine's own and uncalibrated.
 */
public class AudioEventPlan private constructor(
    public val document: TranscriptDocument,
    public val textStatus: TextStatus,
    public val lowConfidenceSegments: Int,
    public val clipRange: Locator.AudioTime,
) {
    /** One event per clip: the text the rules and review work on is the whole transcript. */
    public val text: String
        get() = document.text

    public val sourceKind: SourceKind = SourceKind.SELECTED_AUDIO

    /** The derivative the event's text comes from. */
    public val representation: Representation = Representation.TRANSCRIPT_DERIVATIVE

    /**
     * The anchors for a cue at code points `[start, end)` of [text], or null when the range touches no segment (for
     * example it lies in a line feed between segments or beyond the text). The audio range runs from the earliest start
     * to the latest end of the segments the range touches, and is never empty.
     */
    public fun anchorFor(start: Int, end: Int): CueAnchor? {
        if (start < 0 || end <= start) return null
        val touched = document.segments.filter { it.start < end && start < it.end }
        if (touched.isEmpty()) return null
        val from = touched.minOf { it.segment.startMs }
        val to = maxOf(touched.maxOf { it.segment.endMs }, from + 1)
        return CueAnchor(Locator.Text(start, end), Locator.AudioTime(from, to))
    }

    public companion object {
        public fun of(
            success: SttResult.Success,
            minSegmentConfidence: Float = TranscriptDocument.DEFAULT_MIN_SEGMENT_CONFIDENCE,
        ): AudioEventPlan {
            val document = TranscriptDocument.of(success)
            val low = document.segments.count { TranscriptDocument.isLow(it.segment.uncalibratedConfidence, minSegmentConfidence) }
            return AudioEventPlan(
                document = document,
                textStatus = if (low > 0) TextStatus.EXTRACTION_UNCERTAIN else TextStatus.AVAILABLE,
                lowConfidenceSegments = low,
                clipRange = Locator.AudioTime(0, maxOf(success.audioDurationMs, 1)),
            )
        }
    }
}
