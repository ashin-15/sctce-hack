package org.sakshi.core.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewTargetType
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.model.UnwantedContact

/** What a stored decision was about. */
public sealed interface DecisionTargetKind {
    /** A category of the event; [label] is null when the category row is no longer known. */
    public data class Category(val categoryIndex: Int, val label: CategoryLabel?) : DecisionTargetKind

    public data object Association : DecisionTargetKind

    public data object Direction : DecisionTargetKind

    public data object Wantedness : DecisionTargetKind

    public data object Boundary : DecisionTargetKind

    public data object Duplicate : DecisionTargetKind

    /** A person's response to a stored pattern description; the target id is the pattern's content id. */
    public data object Pattern : DecisionTargetKind

    /** A target type this version does not know, or an explanation. */
    public data object Other : DecisionTargetKind
}

/** The change a decision made, read from its stored value. Never throws; anything unreadable is [Unknown]. */
public sealed interface DecisionChange {
    /** [from] is null when the category had no earlier revision. */
    public data class CategoryStatus(val from: CategoryReviewStatus?, val to: CategoryReviewStatus) : DecisionChange

    /** The confirmed person, or null when the link was withdrawn. */
    public data class Sender(val actorId: ActorId?) : DecisionChange

    public data class Direction(val to: org.sakshi.core.model.Direction) : DecisionChange

    public data class Wantedness(val to: UnwantedContact) : DecisionChange

    /** [marker] is `NONE` when the boundary was cleared. */
    public data class Boundary(val marker: BoundaryMarker) : DecisionChange

    public data class Duplicate(val status: DedupStatus, val canonicalEventId: EventId?) : DecisionChange

    /** What a pattern decision says now: [PatternReview.NOT_REVIEWED] when the person withdrew an earlier response. */
    public data class PatternReviewed(val to: PatternReview) : DecisionChange

    public data object Unknown : DecisionChange
}

internal object DecisionParser {
    private const val CATEGORY_MARKER = "/c"

    fun target(targetType: String, targetId: String): DecisionTargetKind = when (targetType) {
        ReviewTargetType.FINDING -> categoryIndex(targetId)?.let { DecisionTargetKind.Category(it, null) } ?: DecisionTargetKind.Other
        ReviewTargetType.ASSOCIATION -> DecisionTargetKind.Association
        ReviewTargetType.PATTERN -> DecisionTargetKind.Pattern
        ReviewTarget.DIRECTION -> DecisionTargetKind.Direction
        ReviewTarget.WANTEDNESS -> DecisionTargetKind.Wantedness
        ReviewTarget.BOUNDARY -> DecisionTargetKind.Boundary
        ReviewTarget.DUPLICATE -> DecisionTargetKind.Duplicate
        else -> DecisionTargetKind.Other
    }

    fun change(targetType: String, action: String, editedValueJson: String?): DecisionChange {
        val value = fields(editedValueJson)
        return try {
            when (targetType) {
                ReviewTargetType.FINDING -> categoryStatus(action)
                ReviewTargetType.PATTERN -> DecisionChange.PatternReviewed(patternReview(action))
                ReviewTargetType.ASSOCIATION -> when (action) {
                    ReviewAction.ACCEPT -> value["actor_id"]?.let { DecisionChange.Sender(ActorId(it)) }
                    ReviewAction.REJECT -> DecisionChange.Sender(null)
                    else -> null
                }
                ReviewTarget.DIRECTION -> value["direction"]?.let { DecisionChange.Direction(Codecs.direction.parse(it)) }
                ReviewTarget.WANTEDNESS ->
                    value["unwanted_contact"]?.let { DecisionChange.Wantedness(Codecs.unwantedContact.parse(it)) }
                ReviewTarget.BOUNDARY -> value["marker"]?.let { DecisionChange.Boundary(Codecs.boundaryMarker.parse(it)) }
                ReviewTarget.DUPLICATE -> value["status"]?.let {
                    DecisionChange.Duplicate(Codecs.dedupStatus.parse(it), value["canonical_event_id"]?.let(::EventId))
                }
                else -> null
            } ?: DecisionChange.Unknown
        } catch (_: IllegalStateException) {
            DecisionChange.Unknown
        } catch (_: IllegalArgumentException) {
            DecisionChange.Unknown
        }
    }

    /** The review state a pattern decision leaves: only accept, reject and mark unknown set one; anything else clears it. */
    fun patternReview(action: String): PatternReview = when (action) {
        ReviewAction.ACCEPT -> PatternReview.ACCEPTED
        ReviewAction.REJECT -> PatternReview.REJECTED
        ReviewAction.MARK_UNKNOWN -> PatternReview.MARKED_UNKNOWN
        else -> PatternReview.NOT_REVIEWED
    }

    private fun categoryStatus(action: String): DecisionChange? = when (action) {
        ReviewAction.ACCEPT -> DecisionChange.CategoryStatus(null, CategoryReviewStatus.ACCEPTED)
        ReviewAction.REJECT -> DecisionChange.CategoryStatus(null, CategoryReviewStatus.REJECTED)
        ReviewAction.MARK_UNKNOWN -> DecisionChange.CategoryStatus(null, CategoryReviewStatus.UNCERTAIN)
        else -> null
    }

    /** Index of the category a finding id names: `"<eventId>/<revision>/c<index>"`. */
    fun categoryIndex(findingId: String): Int? {
        val at = findingId.lastIndexOf(CATEGORY_MARKER)
        return if (at < 0) null else findingId.substring(at + CATEGORY_MARKER.length).toIntOrNull()
    }

    private fun fields(json: String?): Map<String, String> {
        if (json == null) return emptyMap()
        return try {
            val root: JsonObject = Json.parseToJsonElement(json).jsonObject
            root.mapNotNull { (key, element) -> element.jsonPrimitive.contentOrNull?.let { key to it } }.toMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }
}
