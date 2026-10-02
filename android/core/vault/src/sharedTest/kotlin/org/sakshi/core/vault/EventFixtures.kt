package org.sakshi.core.vault

import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Boundary
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.ConfirmationScope
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Coverage
import org.sakshi.core.model.CoverageContext
import org.sakshi.core.model.Deduplication
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.EventRelationship
import org.sakshi.core.model.EventSchemaAdapter
import org.sakshi.core.model.EventSender
import org.sakshi.core.model.EventSource
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Locator
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.RelationshipBasis
import org.sakshi.core.model.RelationshipReviewStatus
import org.sakshi.core.model.RelationshipType
import org.sakshi.core.model.Representation
import org.sakshi.core.model.Retention
import org.sakshi.core.model.RetentionMode
import org.sakshi.core.model.ReviewPriority
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.SeverityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.UserConfirmation

/** All data here is synthetic. */
object EventFixtures {
    const val CASE: String = "synthetic-case-1"
    const val ACTOR: String = "synthetic-actor-1"
    const val OTHER_ACTOR: String = "synthetic-actor-2"

    /** Copy of `testfixtures/event-valid.json`, so device tests can use it; a unit test keeps the copy in step. */
    val DEMO_EVENT_JSON: String = """
{
  "schema_version": 1,
  "event_id": "demo-schema-1",
  "case_id": "demo-case-a",
  "revision": 1,
  "event_kind": "message_observation",
  "observed_at": "2026-10-01T09:05:00+05:30",
  "available_at": "2026-10-01T09:05:00+05:30",
  "timestamp": {
    "earliest": "2026-10-01T09:05:00+05:30",
    "latest": "2026-10-01T09:05:59.999+05:30",
    "basis": "source_claim",
    "precision": "minute",
    "source_timezone": "+05:30",
    "collector_session_id": null,
    "monotonic_ms": null
  },
  "sender": {
    "actor_id": "demo-actor-a",
    "display_label": "Person A",
    "identity_basis": "user_asserted",
    "association_review": "confirmed"
  },
  "source": {
    "kind": "selected_export",
    "source_app": "user-selected-chat-export",
    "profile_scope_id": null,
    "conversation_scope_id": "demo-chat-a",
    "source_record_id": "demo-row-2",
    "parser_version": "demo-parser-v1"
  },
  "direction": "incoming",
  "categories": [
    {
      "label": "ordinary",
      "basis": "user_tag",
      "confidence": {
        "value": null,
        "semantics": "not_applicable",
        "calibration_version": null
      },
      "producer_version": "manual-review-v1",
      "evidence_reference_ids": [
        "demo-ref-schema-1"
      ],
      "review_status": "accepted"
    }
  ],
  "severity": {
    "review_priority": "review",
    "basis": "user_set",
    "evidence_reference_ids": [
      "demo-ref-schema-1"
    ]
  },
  "evidence_references": [
    {
      "reference_id": "demo-ref-schema-1",
      "artifact_id": "demo-export-a",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "representation": "preserved_import",
      "locator": {
        "kind": "text",
        "start": 0,
        "end": 3,
        "unit": "unicode_code_points"
      }
    }
  ],
  "user_confirmation": {
    "status": "confirmed",
    "reviewed_at": "2026-10-01T09:20:00+05:30",
    "scope": "preservation_and_selected_annotations"
  },
  "deduplication": {
    "status": "distinct_observation",
    "canonical_event_id": null,
    "method_version": "demo-dedup-v1"
  },
  "coverage": {
    "context": "selection_partial",
    "text_status": "available",
    "outgoing_coverage": "unknown",
    "gap_reference_ids": []
  },
  "boundary": {
    "marker": "none",
    "actor_id": null,
    "review_status": "not_applicable",
    "communication_status": "not_applicable",
    "unwanted_contact": "user_marked_unwanted"
  },
  "relationship_to_previous_events": [],
  "retention": {
    "mode": "confirmed_vault",
    "expires_at": null,
    "consent_generation": 1
  }
}
    """.trimIndent()

    fun demoEvent(): Event = EventSchemaAdapter.fromJson(DEMO_EVENT_JSON)

    fun ts(text: String): Timestamp = Timestamp(text)

    fun none(): Confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null)

    fun ref(
        id: String,
        artifact: String = "synthetic-artifact-$id",
        locator: Locator = Locator.WholeArtifact,
        sha256: String? = null,
        representation: Representation = Representation.PRESERVED_IMPORT,
    ): EvidenceReference = EvidenceReference(ReferenceId(id), ArtifactId(artifact), sha256, representation, locator)

    /** The smallest valid event: one whole-artifact reference, no categories, links or boundary. */
    fun plain(id: String, caseId: String = CASE, revision: Int = 1, at: String = "2026-10-01T09:00:00+05:30"): Event = Event(
        eventId = EventId(id),
        caseId = CaseId(caseId),
        revision = revision,
        eventKind = EventKind.MESSAGE_OBSERVATION,
        observedAt = ts(at),
        availableAt = ts(at),
        timestamp = TimeBounds(ts(at), ts(at), TimeBasis.SOURCE_CLAIM, TimePrecision.MINUTE, null, null, null),
        sender = EventSender(null, null, IdentityBasis.UNKNOWN, AssociationReview.UNREVIEWED),
        source = EventSource(SourceKind.SELECTED_TEXT, null, null, null, null, ScopeId("synthetic-parser-1")),
        direction = Direction.INCOMING,
        categories = emptyList(),
        severity = SeverityAssessment(ReviewPriority.ORDINARY, SeverityBasis.UNKNOWN, emptyList()),
        evidenceReferences = listOf(ref("ref-$id")),
        userConfirmation = UserConfirmation(ConfirmationStatus.PENDING, null, ConfirmationScope.NOT_REVIEWED),
        deduplication = Deduplication(DedupStatus.DISTINCT_OBSERVATION, null, ScopeId("synthetic-dedup-1")),
        coverage = Coverage(CoverageContext.UNKNOWN, TextStatus.AVAILABLE, OutgoingCoverage.UNKNOWN, emptyList()),
        boundary = Boundary(
            BoundaryMarker.NONE, null, BoundaryReviewStatus.NOT_APPLICABLE, CommunicationStatus.NOT_APPLICABLE,
            UnwantedContact.UNKNOWN,
        ),
        relationshipToPreviousEvents = emptyList(),
        retention = Retention(RetentionMode.ENCRYPTED_CANDIDATE, ts("2026-11-01T00:00:00Z"), 0),
    )

    /**
     * Exercises every locator subtype, null and present digests, three categories of different bases and confidence semantics
     * with anchors listed out of reference order, two relationships to [targetA] and [targetB], a boundary with
     * an actor, severity and gap references, odd timestamp spellings and non-ASCII text.
     */
    fun maximal(id: String, targetA: String, targetB: String): Event {
        val references = listOf(
            ref("ref-z", "synthetic-artifact-z", Locator.Text(3, 9), "ab".repeat(32), Representation.OCR_DERIVATIVE),
            ref("ref-a", "synthetic-artifact-a", Locator.AudioTime(1_500L, 9_000L), "cd".repeat(32), Representation.TRANSCRIPT_DERIVATIVE),
            ref("ref-m", "synthetic-artifact-m", Locator.WholeArtifact, null, Representation.MANUAL_STATEMENT),
            ref("ref-b", "synthetic-artifact-b", Locator.Text(0, 4), null, Representation.NOTIFICATION_EXCERPT),
            ref("ref-p", "synthetic-artifact-p", Locator.ImageOrPageRegion(2, ScopeId("synthetic-region-1")), "ef".repeat(32), Representation.OCR_DERIVATIVE),
            ref("ref-q", "synthetic-artifact-q", Locator.ImageOrPageRegion(null, ScopeId("synthetic-region-2")), null, Representation.OCR_DERIVATIVE),
        )
        return plain(id).copy(
            eventKind = EventKind.USER_BOUNDARY,
            observedAt = ts("2026-10-01T09:05:00.123456789+05:30"),
            availableAt = ts("2026-10-01T03:35:01.5Z"),
            timestamp = TimeBounds(
                ts("2026-10-01T09:05:00.1+05:30"),
                ts("2026-10-01T09:05:59.999999+05:30"),
                TimeBasis.COLLECTOR_WALL_CLOCK,
                TimePrecision.RANGE,
                "Asia/Kolkata",
                ScopeId("synthetic-session-9"),
                123_456_789_012L,
            ),
            sender = EventSender(ActorId(ACTOR), "Zoë \"quoted\" 日本語 😀", IdentityBasis.APP_SCOPED_HINT, AssociationReview.CONFIRMED),
            source = EventSource(
                SourceKind.SELECTED_EXPORT, "synthetic-app", ScopeId("synthetic-profile"), ScopeId("synthetic-conversation"),
                ScopeId("synthetic-record-7"), ScopeId("synthetic-parser-2"),
            ),
            direction = Direction.OUTGOING,
            categories = listOf(
                CategoryAssessment(
                    CategoryLabel.IMPLIED_THREAT, CategoryBasis.CLASSIFIER_SUGGESTION,
                    Confidence(0.8125, ConfidenceSemantics.CALIBRATED_PROBABILITY, ScopeId("synthetic-calibration-1")),
                    ScopeId("synthetic-classifier-1"), listOf(ReferenceId("ref-b"), ReferenceId("ref-z"), ReferenceId("ref-a")),
                    CategoryReviewStatus.UNREVIEWED,
                ),
                CategoryAssessment(
                    CategoryLabel.CONTACT_REQUEST, CategoryBasis.RULE_SUGGESTION,
                    Confidence(0.1, ConfidenceSemantics.UNCALIBRATED_BOUNDED_SCORE, null),
                    ScopeId("synthetic-rules-1"), listOf(ReferenceId("ref-m"), ReferenceId("ref-a")), CategoryReviewStatus.UNCERTAIN,
                ),
                CategoryAssessment(
                    CategoryLabel.ORDINARY, CategoryBasis.USER_TAG,
                    Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
                    ScopeId("synthetic-user-1"), emptyList(), CategoryReviewStatus.REJECTED,
                ),
            ),
            severity = SeverityAssessment(
                ReviewPriority.URGENT_REVIEW, SeverityBasis.POLICY_SUGGESTION,
                listOf(ReferenceId("ref-m"), ReferenceId("ref-z")),
            ),
            evidenceReferences = references,
            userConfirmation = UserConfirmation(
                ConfirmationStatus.CONFIRMED, ts("2026-10-02T10:00:00.25-07:00"),
                ConfirmationScope.PRESERVATION_AND_SELECTED_ANNOTATIONS,
            ),
            deduplication = Deduplication(DedupStatus.POSSIBLE_DUPLICATE, EventId(targetB), ScopeId("synthetic-dedup-2")),
            coverage = Coverage(
                CoverageContext.NOTIFICATION_PARTIAL, TextStatus.TRUNCATED, OutgoingCoverage.PARTIAL,
                listOf(ReferenceId("gap-2"), ReferenceId("gap-1")),
            ),
            boundary = Boundary(
                BoundaryMarker.DO_NOT_CONTACT, ActorId(OTHER_ACTOR), BoundaryReviewStatus.CONFIRMED,
                CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE, UnwantedContact.USER_MARKED_UNWANTED,
            ),
            relationshipToPreviousEvents = listOf(
                EventRelationship(
                    EventId(targetB), RelationshipType.AFTER_BOUNDARY, RelationshipBasis.RULE,
                    Confidence(0.5, ConfidenceSemantics.UNCALIBRATED_BOUNDED_SCORE, null), RelationshipReviewStatus.CONFIRMED,
                ),
                EventRelationship(
                    EventId(targetA), RelationshipType.USER_LINKED, RelationshipBasis.USER_ASSERTED,
                    Confidence(null, ConfidenceSemantics.UNKNOWN, null), RelationshipReviewStatus.UNKNOWN,
                ),
            ),
            retention = Retention(RetentionMode.ENCRYPTED_CANDIDATE, ts("2027-01-01T00:00:00+00:00"), 7),
        )
    }

    /** Null time bounds, no sender actor, a Malayalam label and a same-representation pointer to [canonical]. */
    fun sparse(id: String, canonical: String): Event = plain(id).copy(
        eventKind = EventKind.USER_NOTE,
        timestamp = TimeBounds(null, null, TimeBasis.UNKNOWN, TimePrecision.UNKNOWN, null, null, null),
        sender = EventSender(null, "അജ്ഞാത അയച്ചയാൾ", IdentityBasis.UNKNOWN, AssociationReview.UNKNOWN),
        deduplication = Deduplication(DedupStatus.SAME_REPRESENTATION, EventId(canonical), ScopeId("synthetic-dedup-1")),
        userConfirmation = UserConfirmation(ConfirmationStatus.PENDING, null, ConfirmationScope.NOT_REVIEWED),
        boundary = Boundary(
            BoundaryMarker.NONE, null, BoundaryReviewStatus.NOT_APPLICABLE, CommunicationStatus.NOT_APPLICABLE,
            UnwantedContact.NOT_APPLICABLE,
        ),
        retention = Retention(RetentionMode.SESSION_ONLY, null, 0),
    )
}
