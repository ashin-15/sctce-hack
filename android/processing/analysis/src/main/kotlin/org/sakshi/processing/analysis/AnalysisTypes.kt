package org.sakshi.processing.analysis

import java.time.ZoneId
import org.sakshi.processing.text.DateOrder

private const val DEFAULT_MAX_TEXT_BYTES: Long = 5L * 1024 * 1024
private const val DEFAULT_MAX_RECORDS: Int = 10_000
private const val DEFAULT_MAX_IMAGE_BYTES: Long = 30L * 1024 * 1024
private const val DEFAULT_MIN_OCR_LINE_CONFIDENCE: Float = 0.5f
private const val DEFAULT_MIN_SPEECH_SEGMENT_CONFIDENCE: Float = 0.5f

/**
 * Bounds for one analysis. [minOcrLineConfidence] is a demonstration setting, not a calibrated threshold: a line whose
 * engine score is below it, or that has no score, marks the image text as uncertain. It never hides the line.
 * [minSpeechSegmentConfidence] works the same way for transcribed speech: the engine's mean token probability of a
 * segment, below it or missing, marks the transcript text as uncertain. Also a demonstration setting.
 */
public data class AnalysisLimits(
    val maxTextBytes: Long = DEFAULT_MAX_TEXT_BYTES,
    val maxRecords: Int = DEFAULT_MAX_RECORDS,
    val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
    val minOcrLineConfidence: Float = DEFAULT_MIN_OCR_LINE_CONFIDENCE,
    val minSpeechSegmentConfidence: Float = DEFAULT_MIN_SPEECH_SEGMENT_CONFIDENCE,
) {
    init {
        require(maxTextBytes > 0) { "maxTextBytes must be positive" }
        require(maxRecords > 0) { "maxRecords must be positive" }
        require(maxImageBytes > 0) { "maxImageBytes must be positive" }
        require(minOcrLineConfidence in 0f..1f) { "minOcrLineConfidence must be within 0 to 1" }
        require(minSpeechSegmentConfidence in 0f..1f) { "minSpeechSegmentConfidence must be within 0 to 1" }
    }
}

/**
 * What the user told the app about an export. [dateOrder] must be a confirmed order. [ownerSenderClaim] is the
 * sender name the user says is theirs, or null when unknown; it is a claim, not an authenticated identity.
 */
public data class ExportOptions(
    val dateOrder: DateOrder,
    val zone: ZoneId,
    val ownerSenderClaim: String?,
) {
    init {
        require(dateOrder != DateOrder.AMBIGUOUS) { "Date order must be confirmed" }
    }
}

/**
 * How the text was read. [IMAGE_TEXT] is text recognised in an image by software, not the image itself.
 * [AUDIO_TRANSCRIPT] is speech written down by software, not the recording itself.
 */
public enum class InputKind { PLAIN_TEXT, WHATSAPP_EXPORT, IMAGE_TEXT, AUDIO_TRANSCRIPT }

/** Conditions the user should be told about. A set of these never means "nothing was found". */
public enum class AnalysisWarning {
    /** The file's dates fix an order that differs from the one given; the order found in the file was used. */
    DATE_ORDER_OVERRIDDEN,

    /** Lines without a sender were not turned into events. */
    SYSTEM_LINES_SKIPPED,

    /** Some record times could not be resolved, so those events have unknown time. */
    UNRESOLVED_TIMES,

    /** More records than the limit; the rest were not analysed. */
    RECORD_LIMIT_REACHED,

    /** Text before the first record was kept in the derivative but not turned into an event. */
    UNPARSED_PREFIX_SKIPPED,

    /** Some messages are in a script the rules do not support, so no cue check was made for them. */
    UNSUPPORTED_LANGUAGE_PRESENT,

    /** Some cue matches were withheld because their cue list is not reviewed. */
    CUE_LIST_NOT_REVIEWED,

    /**
     * The image was read with a Latin-script engine. Text in other scripts, such as Malayalam or Devanagari, is not
     * read and may be missing without any sign. Given for every image.
     */
    OCR_LATIN_SCRIPT_ONLY,

    /** Some recognised lines have a low or missing engine score, so their text may be wrong. Count is lines. */
    OCR_LOW_CONFIDENCE_LINES,

    /**
     * The words were written down from speech by software on this phone. They can contain mistakes and are not a
     * translation. Given for every transcribed recording.
     */
    AUDIO_TRANSCRIPT_MAY_CONTAIN_ERRORS,

    /** Some transcribed parts have a low or missing engine score, so their words may be wrong. Count is segments. */
    AUDIO_LOW_CONFIDENCE_SEGMENTS,
}

/** Why a piece of evidence was not analysed here. */
public enum class NotAnalysableReason {
    NOT_TEXT,
    TOO_LARGE,
    PRESERVE_ONLY_TYPE,
    MANUAL_NOTE,
    EVIDENCE_MISSING,
    UNREADABLE,
    ALREADY_ANALYSED,

    /** The engine found no text it can read. Text in a script it does not read ends here too. */
    NO_TEXT_RECOGNISED,

    /** The saved file is not an image this phone can decode. */
    IMAGE_NOT_DECODABLE,

    /** Text recognition stopped with an error or ran out of memory. */
    RECOGNITION_FAILED,

    /**
     * No speech was found that this phone could read. This is an abstention: it does not mean nothing was said,
     * and nothing is written for the recording.
     */
    NO_SPEECH,

    /** The recording is longer than the speech lane reads at once. It is kept as received. */
    AUDIO_TOO_LONG,

    /** The saved file is not audio this phone can decode. */
    AUDIO_NOT_DECODABLE,

    /** The speech model is missing, does not match the expected file, or could not be loaded. */
    SPEECH_MODEL_UNAVAILABLE,

    /** Transcription stopped with an error, a hot phone, a lack of memory, or was cancelled. */
    TRANSCRIPTION_FAILED,
}

/** Result of [TextAnalysis.analyse]. */
public sealed interface AnalysisOutcome {
    /** [warningCounts] gives, per warning, how many records it concerns (1 for whole-file warnings). */
    public data class Analysed(
        val derivativeId: String,
        val eventCount: Int,
        val suggestionCount: Int,
        val kind: InputKind,
        val warnings: Set<AnalysisWarning>,
        val warningCounts: Map<AnalysisWarning, Int> = warnings.associateWith { 1 },
    ) : AnalysisOutcome

    /** The export cannot become events until the user answers these. Nothing was written except the derivative. */
    public data class NeedsExportOptions(
        val derivativeId: String,
        val senders: List<String>,
        val detectedDateOrder: DateOrder,
        val recordCount: Int,
        val sampleDates: List<String>,
    ) : AnalysisOutcome

    public data class NotAnalysable(val reason: NotAnalysableReason) : AnalysisOutcome
}
