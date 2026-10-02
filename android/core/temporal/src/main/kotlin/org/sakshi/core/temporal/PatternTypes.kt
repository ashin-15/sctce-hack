package org.sakshi.core.temporal

import java.time.Instant
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.TimeBasis

/** Why observation coverage is unknown. */
public enum class GapReason {
    LISTENER_DISCONNECTED,
    ACCESS_REVOKED,
    PROCESS_DEATH,
    QUEUE_OVERFLOW,
    KEY_UNAVAILABLE,
    IMPORT_SELECTION,
    UNKNOWN,
}

/**
 * Interval in which observation coverage is unknown. A null [start] or [end] means that bound is unknown
 * and the gap is treated as unbounded on that side. A null [actorId] applies to the whole case.
 */
public data class CoverageGap(
    val id: ReferenceId,
    val caseId: CaseId,
    val actorId: ActorId?,
    val start: Instant?,
    val end: Instant?,
    val reason: GapReason,
)

/** Inclusive bounds on a count of retained observations. */
public data class CountBounds(val lower: Int, val upper: Int) {
    init {
        require(lower in 0..upper) { "need 0 <= lower <= upper, was $lower..$upper" }
    }
}

public enum class PatternType {
    REPEATED_CONTACT,
    RECURRENCE_AFTER_BOUNDARY,
    WORDING_TRANSITION,
    DENSITY_CHANGE,
}

public enum class AssessmentStatus {
    CANDIDATE,
    SUPPORTED_DESCRIPTION,
    INSUFFICIENT_CONTEXT,
    NOT_OBSERVED,
}

public enum class SupportRole {
    CONTACT,
    BOUNDARY,
    RESUMPTION,
    EARLIER_CATEGORY,
    LATER_CATEGORY,
    PREVIOUS_BIN,
    CURRENT_BIN,
    WANTED_CONTEXT,

    /** A non-representative member of a merged or possibly merged contact group. */
    POSSIBLE_DUPLICATE,
}

/** Reference to the event revision a pattern used. */
public data class EventRef(val eventId: EventId, val revision: Int, val role: SupportRole)

public enum class Limitation {
    SENDER_NOT_AUTHENTICATED,
    ACTOR_UNRESOLVED,
    OUTGOING_COVERAGE_UNKNOWN,
    COVERAGE_GAP,
    DUPLICATE_UNCERTAINTY,
    TIME_UNCERTAIN,
    SELECTION_PARTIAL,
    NOTIFICATION_PARTIAL,
    UNREVIEWED_TAGS,
    UNCONFIRMED_EVIDENCE,
    BOUNDARY_NOT_COMMUNICATED,
    BOUNDARY_USER_REPORTED,
    DELIVERY_UNKNOWN,
    DEMO_THRESHOLD,
}

/** One projection of events into a described pattern. Never a legal or safety conclusion. */
public data class PatternRecord(
    /** Deterministic: type, scope key, window or anchor ids, rule version. */
    val patternKey: String,
    val type: PatternType,
    val ruleVersion: String,
    val caseId: CaseId,
    val actorScope: ActorScope,
    val view: EvidenceView,
    val knowledgeCutoff: Instant,
    val windowStart: Instant?,
    val windowEnd: Instant?,
    val clockBases: Set<TimeBasis>,
    val supportingEvents: List<EventRef>,
    val contextEvents: List<EventRef>,
    val measurements: Measurements,
    val limitations: Set<Limitation>,
    val gapIds: List<ReferenceId>,
    val status: AssessmentStatus,
) {
    /** True when this record used any revision of [eventId], so it must be recomputed if that event changes. */
    public fun dependsOn(eventId: EventId): Boolean =
        supportingEvents.any { it.eventId == eventId } || contextEvents.any { it.eventId == eventId }
}

/** One entry of the display timeline. Times are null for untimed events. */
public data class TimelineEntry(
    val eventId: EventId,
    val revision: Int,
    val earliest: Instant?,
    val latest: Instant?,
    val isContact: Boolean,
    val orderCertainAfterPrevious: Boolean,
    /** Representative of the contact group this event was merged into, when it is not the representative. */
    val canonicalEventId: EventId?,
)

public data class TemporalResult(
    val timeline: List<TimelineEntry>,
    val patterns: List<PatternRecord>,
    val gaps: List<CoverageGap>,
)
