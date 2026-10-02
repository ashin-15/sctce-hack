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
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Deduplication
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.EventSender
import org.sakshi.core.model.EventSource
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Locator
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.Representation
import org.sakshi.core.model.Retention
import org.sakshi.core.model.RetentionMode
import org.sakshi.core.model.ReviewPriority
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.SeverityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.UserConfirmation

/** Pure field changes of review operations. Each returns a copy that differs only in the named fields. */
internal object ReviewEdits {
    const val PRODUCER: String = "manual-review-v1"

    fun categoryStatus(event: Event, index: Int, status: CategoryReviewStatus): Event = event.copy(
        categories = event.categories.mapIndexed { i, c -> if (i == index) c.copy(reviewStatus = status) else c },
    )

    fun userTag(event: Event, label: CategoryLabel, references: List<ReferenceId>): Event = event.copy(
        categories = event.categories + CategoryAssessment(
            label = label,
            basis = CategoryBasis.USER_TAG,
            confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
            producerVersion = ScopeId(PRODUCER),
            evidenceReferenceIds = references,
            reviewStatus = CategoryReviewStatus.ACCEPTED,
        ),
    )

    fun hasUserTag(event: Event, label: CategoryLabel, references: List<ReferenceId>): Boolean =
        event.categories.any {
            it.basis == CategoryBasis.USER_TAG && it.label == label && it.evidenceReferenceIds == references
        }

    fun assigned(event: Event, actorId: ActorId): Event = event.copy(
        sender = event.sender.copy(
            actorId = actorId,
            identityBasis = IdentityBasis.USER_ASSERTED,
            associationReview = AssociationReview.CONFIRMED,
        ),
    )

    fun unassigned(event: Event): Event =
        event.copy(sender = event.sender.copy(actorId = null, associationReview = AssociationReview.REJECTED))

    fun hasConfirmedActor(sender: EventSender): Boolean =
        sender.actorId != null && sender.associationReview == AssociationReview.CONFIRMED

    fun matches(event: Event, selector: SenderSelector): Boolean =
        event.sender.displayLabel == selector.displayLabel &&
            event.source.sourceApp == selector.sourceApp &&
            event.source.conversationScopeId == selector.conversationScopeId

    fun direction(event: Event, direction: Direction): Event = event.copy(direction = direction)

    fun unwanted(event: Event, value: UnwantedContact): Event =
        event.copy(boundary = event.boundary.copy(unwantedContact = value))

    /** Supported by selected evidence only for an outgoing event that rests on something other than a statement. */
    fun boundary(event: Event, marker: BoundaryMarker, actorId: ActorId): Event {
        val supported = event.direction == Direction.OUTGOING &&
            event.evidenceReferences.any { it.representation != Representation.MANUAL_STATEMENT }
        return event.copy(
            boundary = Boundary(
                marker = marker,
                actorId = actorId,
                reviewStatus = BoundaryReviewStatus.CONFIRMED,
                communicationStatus = if (supported) {
                    CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE
                } else {
                    CommunicationStatus.USER_REPORTED
                },
                unwantedContact = event.boundary.unwantedContact,
            ),
        )
    }

    fun boundaryCleared(event: Event): Event = event.copy(
        boundary = Boundary(
            marker = BoundaryMarker.NONE,
            actorId = null,
            reviewStatus = BoundaryReviewStatus.NOT_APPLICABLE,
            communicationStatus = CommunicationStatus.NOT_APPLICABLE,
            unwantedContact = event.boundary.unwantedContact,
        ),
    )

    fun duplicate(event: Event, status: DedupStatus, canonical: EventId?): Event = event.copy(
        deduplication = Deduplication(status, canonical, ScopeId(PRODUCER)),
    )

    /** A first-revision boundary event resting on one manual note. A statement is never "supported by evidence". */
    fun boundaryNote(
        eventId: EventId,
        caseId: CaseId,
        now: Timestamp,
        noteEvidenceId: String,
        noteSha256: String,
        referenceId: ReferenceId,
        time: TimeBounds,
        marker: BoundaryMarker,
        actorId: ActorId,
        communicated: Boolean,
    ): Event = Event(
        eventId = eventId,
        caseId = caseId,
        revision = 1,
        eventKind = EventKind.USER_BOUNDARY,
        observedAt = now,
        availableAt = now,
        timestamp = time,
        sender = EventSender(null, null, IdentityBasis.UNKNOWN, AssociationReview.UNKNOWN),
        source = EventSource(SourceKind.MANUAL_ENTRY, null, null, null, null, ScopeId(PRODUCER)),
        direction = Direction.UNKNOWN,
        categories = emptyList(),
        severity = SeverityAssessment(ReviewPriority.ORDINARY, SeverityBasis.UNKNOWN, emptyList()),
        evidenceReferences = listOf(
            EvidenceReference(
                referenceId = referenceId,
                artifactId = ArtifactId(noteEvidenceId),
                sha256 = noteSha256,
                representation = Representation.MANUAL_STATEMENT,
                locator = Locator.WholeArtifact,
            ),
        ),
        userConfirmation = UserConfirmation(
            ConfirmationStatus.CONFIRMED, now, ConfirmationScope.PRESERVATION_AND_SELECTED_ANNOTATIONS,
        ),
        deduplication = Deduplication(DedupStatus.DISTINCT_OBSERVATION, null, ScopeId(PRODUCER)),
        coverage = Coverage(CoverageContext.UNKNOWN, TextStatus.ABSENT, OutgoingCoverage.UNKNOWN, emptyList()),
        boundary = Boundary(
            marker = marker,
            actorId = actorId,
            reviewStatus = BoundaryReviewStatus.CONFIRMED,
            communicationStatus = if (communicated) CommunicationStatus.USER_REPORTED else CommunicationStatus.NOT_COMMUNICATED,
            unwantedContact = UnwantedContact.NOT_APPLICABLE,
        ),
        relationshipToPreviousEvents = emptyList(),
        retention = Retention(RetentionMode.CONFIRMED_VAULT, null, 0),
    )
}
