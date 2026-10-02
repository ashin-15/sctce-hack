package org.sakshi.processing.analysis

import java.time.ZoneId
import org.sakshi.processing.text.DateOrder

private const val DEFAULT_MAX_TEXT_BYTES: Long = 5L * 1024 * 1024
private const val DEFAULT_MAX_RECORDS: Int = 10_000

/** Size bounds for one analysis. */
public data class AnalysisLimits(
    val maxTextBytes: Long = DEFAULT_MAX_TEXT_BYTES,
    val maxRecords: Int = DEFAULT_MAX_RECORDS,
) {
    init {
        require(maxTextBytes > 0) { "maxTextBytes must be positive" }
        require(maxRecords > 0) { "maxRecords must be positive" }
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

/** How the text was read. */
public enum class InputKind { PLAIN_TEXT, WHATSAPP_EXPORT }

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
