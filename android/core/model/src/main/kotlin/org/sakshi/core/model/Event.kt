package org.sakshi.core.model

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SCHEMA_VERSION: Int = 1
private const val MAX_TEXT_LENGTH: Int = 256

@Serializable
public data class EventSender(
    @SerialName("actor_id") val actorId: ActorId?,
    @SerialName("display_label") val displayLabel: String?,
    @SerialName("identity_basis") val identityBasis: IdentityBasis,
    @SerialName("association_review") val associationReview: AssociationReview,
) {
    init {
        require(displayLabel == null || displayLabel.length <= MAX_TEXT_LENGTH) { "display_label too long" }
    }
}

@Serializable
public data class EventSource(
    val kind: SourceKind,
    @SerialName("source_app") val sourceApp: String?,
    @SerialName("profile_scope_id") val profileScopeId: ScopeId?,
    @SerialName("conversation_scope_id") val conversationScopeId: ScopeId?,
    @SerialName("source_record_id") val sourceRecordId: ScopeId?,
    @SerialName("parser_version") val parserVersion: ScopeId,
) {
    init {
        require(sourceApp == null || sourceApp.length <= MAX_TEXT_LENGTH) { "source_app too long" }
    }
}

@Serializable
public data class CategoryAssessment(
    val label: CategoryLabel,
    val basis: CategoryBasis,
    val confidence: Confidence,
    @SerialName("producer_version") val producerVersion: ScopeId,
    @SerialName("evidence_reference_ids") val evidenceReferenceIds: List<ReferenceId>,
    @SerialName("review_status") val reviewStatus: CategoryReviewStatus,
)

@Serializable
public data class SeverityAssessment(
    @SerialName("review_priority") val reviewPriority: ReviewPriority,
    val basis: SeverityBasis,
    @SerialName("evidence_reference_ids") val evidenceReferenceIds: List<ReferenceId>,
)

private val SHA256_HEX: Regex = Regex("[a-f0-9]{64}")

@Serializable
public data class EvidenceReference(
    @SerialName("reference_id") val referenceId: ReferenceId,
    @SerialName("artifact_id") val artifactId: ArtifactId,
    val sha256: String?,
    val representation: Representation,
    val locator: Locator,
) {
    init {
        require(sha256 == null || SHA256_HEX.matches(sha256)) { "sha256 must be 64 lowercase hex characters" }
    }
}

@Serializable
public data class UserConfirmation(
    val status: ConfirmationStatus,
    @SerialName("reviewed_at") val reviewedAt: Timestamp?,
    val scope: ConfirmationScope,
)

@Serializable
public data class Deduplication(
    val status: DedupStatus,
    @SerialName("canonical_event_id") val canonicalEventId: EventId?,
    @SerialName("method_version") val methodVersion: ScopeId,
)

@Serializable
public data class Coverage(
    val context: CoverageContext,
    @SerialName("text_status") val textStatus: TextStatus,
    @SerialName("outgoing_coverage") val outgoingCoverage: OutgoingCoverage,
    @SerialName("gap_reference_ids") val gapReferenceIds: List<ReferenceId>,
)

@Serializable
public data class Boundary(
    val marker: BoundaryMarker,
    @SerialName("actor_id") val actorId: ActorId?,
    @SerialName("review_status") val reviewStatus: BoundaryReviewStatus,
    @SerialName("communication_status") val communicationStatus: CommunicationStatus,
    @SerialName("unwanted_contact") val unwantedContact: UnwantedContact,
)

@Serializable
public data class EventRelationship(
    @SerialName("target_event_id") val targetEventId: EventId,
    val type: RelationshipType,
    val basis: RelationshipBasis,
    val confidence: Confidence,
    @SerialName("review_status") val reviewStatus: RelationshipReviewStatus,
)

@Serializable
public data class Retention(
    val mode: RetentionMode,
    @SerialName("expires_at") val expiresAt: Timestamp?,
    @SerialName("consent_generation") val consentGeneration: Int,
) {
    init {
        require(consentGeneration >= 0) { "consent_generation must be non-negative" }
    }
}

/** Typed form of `data/sakshi-event-schema.json`, version 1. */
@Serializable
public data class Event(
    @Required @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("event_id") val eventId: EventId,
    @SerialName("case_id") val caseId: CaseId,
    val revision: Int,
    @SerialName("event_kind") val eventKind: EventKind,
    @SerialName("observed_at") val observedAt: Timestamp,
    @SerialName("available_at") val availableAt: Timestamp,
    val timestamp: TimeBounds,
    val sender: EventSender,
    val source: EventSource,
    val direction: Direction,
    val categories: List<CategoryAssessment>,
    val severity: SeverityAssessment,
    @SerialName("evidence_references") val evidenceReferences: List<EvidenceReference>,
    @SerialName("user_confirmation") val userConfirmation: UserConfirmation,
    val deduplication: Deduplication,
    val coverage: Coverage,
    val boundary: Boundary,
    @SerialName("relationship_to_previous_events") val relationshipToPreviousEvents: List<EventRelationship>,
    val retention: Retention,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported schema_version: $schemaVersion" }
        require(revision >= 1) { "revision must be >= 1" }
    }
}
