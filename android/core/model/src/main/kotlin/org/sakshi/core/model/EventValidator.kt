package org.sakshi.core.model

private const val MAX_CANONICAL_HOPS: Int = 1024

/** Case and artifact lookups needed for checks that span records. */
public interface CaseContext {
    /** Case owning [eventId], or null if the event is unknown. */
    public fun caseOfEvent(eventId: EventId): CaseId?

    /** Case owning [actorId], or null if the actor is unknown. */
    public fun caseOfActor(actorId: ActorId): CaseId?

    /** Canonical pointer recorded by another event's deduplication, or null if none. */
    public fun canonicalOf(eventId: EventId): EventId?

    /** Code point length of the text a locator points into, or null if unknown or not text. */
    public fun codePointLength(artifactId: ArtifactId): Int?
}

public data class Violation(val code: ViolationCode, val path: String, val message: String)

public enum class ViolationCode {
    TIME_BOUNDS_INVERTED,
    REVIEW_BEFORE_OBSERVATION,
    DUPLICATE_REFERENCE_ID,
    UNRESOLVED_REFERENCE_ID,
    TEXT_LOCATOR_EMPTY,
    TEXT_LOCATOR_OUT_OF_RANGE,
    AUDIO_LOCATOR_EMPTY,
    ACTOR_UNKNOWN,
    ACTOR_CROSS_CASE,
    RELATIONSHIP_SELF_LINK,
    RELATIONSHIP_TARGET_UNKNOWN,
    RELATIONSHIP_CROSS_CASE,
    CANONICAL_SELF,
    CANONICAL_UNKNOWN,
    CANONICAL_CROSS_CASE,
    CANONICAL_CYCLE,
    DISTINCT_WITH_CANONICAL,
    BOUNDARY_NONE_WITH_STATUS,
    BOUNDARY_MARKER_NOT_APPLICABLE,
    COMMUNICATION_WITHOUT_EVIDENCE,
    SESSION_ONLY_WITH_EXPIRY,
}

/** Invariants that JSON Schema cannot express. Returns every violation found. */
public object EventValidator {
    public fun validate(event: Event, context: CaseContext): List<Violation> {
        val found = mutableListOf<Violation>()
        fun add(code: ViolationCode, path: String, message: String) {
            found += Violation(code, path, message)
        }
        checkTime(event, ::add)
        checkReferences(event, ::add)
        checkLocators(event, context, ::add)
        checkActors(event, context, ::add)
        checkRelationships(event, context, ::add)
        checkDeduplication(event, context, ::add)
        checkBoundary(event, ::add)
        checkRetention(event, ::add)
        return found
    }

    private fun checkTime(event: Event, add: (ViolationCode, String, String) -> Unit) {
        val earliest = event.timestamp.earliest
        val latest = event.timestamp.latest
        if (earliest != null && latest != null && earliest.instant > latest.instant) {
            add(ViolationCode.TIME_BOUNDS_INVERTED, "/timestamp", "earliest is after latest")
        }
        val reviewedAt = event.userConfirmation.reviewedAt
        if (reviewedAt != null && reviewedAt.instant < event.observedAt.instant) {
            add(
                ViolationCode.REVIEW_BEFORE_OBSERVATION,
                "/user_confirmation/reviewed_at",
                "review precedes observed_at",
            )
        }
    }

    private fun checkReferences(event: Event, add: (ViolationCode, String, String) -> Unit) {
        val known = mutableSetOf<ReferenceId>()
        event.evidenceReferences.forEachIndexed { i, ref ->
            if (!known.add(ref.referenceId)) {
                add(
                    ViolationCode.DUPLICATE_REFERENCE_ID,
                    "/evidence_references/$i/reference_id",
                    "duplicate reference_id ${ref.referenceId.value}",
                )
            }
        }
        fun checkIds(ids: List<ReferenceId>, base: String) {
            ids.forEachIndexed { j, id ->
                if (id !in known) {
                    add(
                        ViolationCode.UNRESOLVED_REFERENCE_ID,
                        "$base/$j",
                        "reference ${id.value} not in evidence_references",
                    )
                }
            }
        }
        event.categories.forEachIndexed { i, c -> checkIds(c.evidenceReferenceIds, "/categories/$i/evidence_reference_ids") }
        checkIds(event.severity.evidenceReferenceIds, "/severity/evidence_reference_ids")
    }

    private fun checkLocators(event: Event, context: CaseContext, add: (ViolationCode, String, String) -> Unit) {
        event.evidenceReferences.forEachIndexed { i, ref ->
            val path = "/evidence_references/$i/locator"
            when (val locator = ref.locator) {
                is Locator.Text -> {
                    if (locator.start >= locator.end) {
                        add(ViolationCode.TEXT_LOCATOR_EMPTY, path, "text span must be non-empty")
                    }
                    val length = context.codePointLength(ref.artifactId)
                    if (length != null && locator.end > length) {
                        add(
                            ViolationCode.TEXT_LOCATOR_OUT_OF_RANGE,
                            path,
                            "end ${locator.end} exceeds $length code points",
                        )
                    }
                }
                is Locator.AudioTime ->
                    if (locator.startMs >= locator.endMs) {
                        add(ViolationCode.AUDIO_LOCATOR_EMPTY, path, "audio range must be non-empty")
                    }
                is Locator.WholeArtifact, is Locator.ImageOrPageRegion -> Unit
            }
        }
    }

    private fun checkActors(event: Event, context: CaseContext, add: (ViolationCode, String, String) -> Unit) {
        fun check(actorId: ActorId?, path: String) {
            if (actorId == null) return
            val owner = context.caseOfActor(actorId)
            when {
                owner == null -> add(ViolationCode.ACTOR_UNKNOWN, path, "unknown actor ${actorId.value}")
                owner != event.caseId ->
                    add(ViolationCode.ACTOR_CROSS_CASE, path, "actor ${actorId.value} belongs to another case")
            }
        }
        check(event.sender.actorId, "/sender/actor_id")
        check(event.boundary.actorId, "/boundary/actor_id")
    }

    private fun checkRelationships(event: Event, context: CaseContext, add: (ViolationCode, String, String) -> Unit) {
        event.relationshipToPreviousEvents.forEachIndexed { i, rel ->
            val path = "/relationship_to_previous_events/$i/target_event_id"
            if (rel.targetEventId == event.eventId) {
                add(ViolationCode.RELATIONSHIP_SELF_LINK, path, "event links to itself")
                return@forEachIndexed
            }
            val owner = context.caseOfEvent(rel.targetEventId)
            when {
                owner == null ->
                    add(ViolationCode.RELATIONSHIP_TARGET_UNKNOWN, path, "unknown target ${rel.targetEventId.value}")
                owner != event.caseId ->
                    add(ViolationCode.RELATIONSHIP_CROSS_CASE, path, "target belongs to another case")
            }
        }
    }

    private fun checkDeduplication(event: Event, context: CaseContext, add: (ViolationCode, String, String) -> Unit) {
        val canonical = event.deduplication.canonicalEventId
        val path = "/deduplication/canonical_event_id"
        if (canonical == null) return
        if (event.deduplication.status == DedupStatus.DISTINCT_OBSERVATION) {
            add(ViolationCode.DISTINCT_WITH_CANONICAL, path, "distinct_observation cannot name a canonical event")
        }
        if (canonical == event.eventId) {
            add(ViolationCode.CANONICAL_SELF, path, "event is its own canonical event")
            return
        }
        val owner = context.caseOfEvent(canonical)
        when {
            owner == null -> {
                add(ViolationCode.CANONICAL_UNKNOWN, path, "unknown canonical event ${canonical.value}")
                return
            }
            owner != event.caseId ->
                add(ViolationCode.CANONICAL_CROSS_CASE, path, "canonical event belongs to another case")
        }
        val visited = mutableSetOf(canonical)
        var current = context.canonicalOf(canonical)
        var hops = 0
        while (current != null && hops < MAX_CANONICAL_HOPS) {
            if (current == event.eventId) {
                add(ViolationCode.CANONICAL_CYCLE, path, "canonical chain returns to this event")
                return
            }
            if (!visited.add(current)) return
            current = context.canonicalOf(current)
            hops++
        }
    }

    private fun checkBoundary(event: Event, add: (ViolationCode, String, String) -> Unit) {
        val boundary = event.boundary
        if (boundary.marker == BoundaryMarker.NONE) {
            if (boundary.reviewStatus != BoundaryReviewStatus.NOT_APPLICABLE ||
                boundary.communicationStatus != CommunicationStatus.NOT_APPLICABLE
            ) {
                add(
                    ViolationCode.BOUNDARY_NONE_WITH_STATUS,
                    "/boundary",
                    "marker none requires review_status and communication_status not_applicable",
                )
            }
        } else if (boundary.reviewStatus == BoundaryReviewStatus.NOT_APPLICABLE) {
            add(
                ViolationCode.BOUNDARY_MARKER_NOT_APPLICABLE,
                "/boundary/review_status",
                "a boundary marker needs a review status",
            )
        }
        if (boundary.communicationStatus == CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE &&
            event.evidenceReferences.none { it.representation != Representation.MANUAL_STATEMENT }
        ) {
            add(
                ViolationCode.COMMUNICATION_WITHOUT_EVIDENCE,
                "/boundary/communication_status",
                "a manual statement alone cannot support communicated",
            )
        }
    }

    private fun checkRetention(event: Event, add: (ViolationCode, String, String) -> Unit) {
        if (event.retention.mode == RetentionMode.SESSION_ONLY && event.retention.expiresAt != null) {
            add(ViolationCode.SESSION_ONLY_WITH_EXPIRY, "/retention/expires_at", "session_only must not set expires_at")
        }
    }
}
