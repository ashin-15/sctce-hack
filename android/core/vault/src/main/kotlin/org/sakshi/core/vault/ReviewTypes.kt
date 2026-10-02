package org.sakshi.core.vault

import org.sakshi.core.database.ReviewDecisionEntity
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.Violation

/** Reason codes a person can give when rejecting a suggestion (megaplan 18.2). */
public object ReviewReason {
    public const val WRONG_SENDER_OR_QUOTE: String = "wrong_sender_or_quote"
    public const val EXTRACTION_ERROR: String = "extraction_error"
    public const val DUPLICATE: String = "duplicate"
    public const val INSUFFICIENT_CONTEXT: String = "insufficient_context"
    public const val SIGNAL_ABSENT: String = "signal_absent"

    internal val all: Set<String> =
        setOf(WRONG_SENDER_OR_QUOTE, EXTRACTION_ERROR, DUPLICATE, INSUFFICIENT_CONTEXT, SIGNAL_ABSENT)
}

/**
 * Values of `review_decision.target_type` beyond the database ones. For decisions about an event field the
 * target id is the event id and the target revision is the revision the decision created.
 */
public object ReviewTarget {
    public const val DIRECTION: String = "direction"
    public const val WANTEDNESS: String = "wantedness"
    public const val BOUNDARY: String = "boundary"
    public const val DUPLICATE: String = "duplicate"
}

/** Target ids of review decisions. */
public object DecisionTargets {
    /**
     * Target id of a category decision: the id of the `finding` row that category has in the revision the
     * decision created, `"<eventId>/<revision>/c<index, 6 digits>"`. Decisions of removed evidence go with it.
     */
    public fun category(eventId: EventId, revision: Int, index: Int): String =
        RowKeys.finding(eventId.value, revision, index)
}

/**
 * Picks the events of a case that show the same sender label in the same app and conversation. All three parts
 * are compared exactly, null only equals null; at least one must be set.
 */
public data class SenderSelector(
    val displayLabel: String?,
    val sourceApp: String?,
    val conversationScopeId: ScopeId?,
)

/** Why a review request was refused when no model [Violation] explains it. */
public enum class ReviewProblem {
    EMPTY_SELECTOR,
    CATEGORY_INDEX,
    REFERENCES_REQUIRED,
    REFERENCE_UNKNOWN,
    REFERENCE_DUPLICATE,
    VALUE_NOT_ALLOWED,
    CANONICAL_REQUIRED,
    WRONG_EVIDENCE_KIND,
    MIXED_CASE,
    ACTOR_UNKNOWN,
    NOT_STORABLE,
}

/** Outcome of a review operation. Anything but [Applied] means nothing was written. */
public sealed interface ReviewResult {
    /** New revisions were saved for these events, in order. */
    public data class Applied(val changedEventIds: List<EventId>) : ReviewResult

    public data object NotFound : ReviewResult

    public data class Invalid(val violations: List<Violation>, val problem: ReviewProblem? = null) : ReviewResult

    /** The request would not change anything. */
    public data object NoChange : ReviewResult
}

/**
 * A stored response of a person to a suggestion or an event field. [target] and [change] are read from the stored
 * columns, so callers never parse [editedValueJson]; [ReviewCoordinator.decisionsForEvent] also fills in the category
 * label and the earlier status.
 */
public data class StoredDecision(
    val seq: Long,
    val id: String,
    val caseId: String,
    val targetType: String,
    val targetId: String,
    val targetRevision: Int?,
    val action: String,
    val reasonCode: String?,
    val editedValueJson: String?,
    val decidedAt: String,
    val target: DecisionTargetKind = DecisionParser.target(targetType, targetId),
    val change: DecisionChange = DecisionParser.change(targetType, action, editedValueJson),
)

internal fun ReviewDecisionEntity.toStored(): StoredDecision = StoredDecision(
    seq, id, caseId, targetType, targetId, targetRevision, action, reasonCode, editedValueJson, decidedAt,
)

/** One decision row to insert in the transaction that saves the revision it belongs to. */
internal data class DecisionDraft(
    val targetType: String,
    val targetId: String,
    val action: String,
    val reasonCode: String? = null,
    val editedValueJson: String? = null,
)

