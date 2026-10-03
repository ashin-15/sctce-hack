package org.sakshi.processing.analysis

import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Boundary
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.CommunicationStatus
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
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.UserConfirmation

/** Facts shared by every event of one analysis. */
internal class EventContext(
    val caseId: CaseId,
    val derivative: ArtifactId,
    val evidenceSha256: String,
    val receivedAt: Timestamp,
    val availableAt: Timestamp,
)

/** The parts of one event that differ between messages. */
internal class EventDraft(
    val eventId: EventId,
    val timestamp: TimeBounds,
    val sender: EventSender,
    val direction: Direction,
    val source: EventSource,
    val bodySpan: CodePointSpan,
    val textStatus: TextStatus,
    val outgoingCoverage: OutgoingCoverage,
    val assessment: CueAssessment?,
    val representation: Representation = Representation.PRESERVED_IMPORT,
    val coverageContext: CoverageContext = CoverageContext.SELECTION_PARTIAL,
)

/**
 * Builds events the way megaplan 18.1 requires: the user saving evidence confirms preservation only, so the
 * confirmation scope is `preservation_only` and every tag stays unreviewed.
 */
internal object EventFactory {
    val BODY_REFERENCE: ReferenceId = ReferenceId("body")
    private val DEDUP_METHOD: ScopeId = ScopeId("analysis-dedup-none-v1")

    fun build(context: EventContext, draft: EventDraft): Event {
        val body = EvidenceReference(
            referenceId = BODY_REFERENCE,
            artifactId = context.derivative,
            sha256 = context.evidenceSha256,
            representation = draft.representation,
            locator = Locator.Text(draft.bodySpan.start, draft.bodySpan.end),
        )
        val assessment = draft.assessment
        return Event(
            eventId = draft.eventId,
            caseId = context.caseId,
            revision = 1,
            eventKind = EventKind.MESSAGE_OBSERVATION,
            observedAt = context.receivedAt,
            availableAt = context.availableAt,
            timestamp = draft.timestamp,
            sender = draft.sender,
            source = draft.source,
            direction = draft.direction,
            categories = assessment?.categories.orEmpty(),
            severity = assessment?.severity ?: CueReferences.severity(emptyList()),
            evidenceReferences = listOf(body) + assessment?.cueReferences.orEmpty(),
            userConfirmation = UserConfirmation(ConfirmationStatus.CONFIRMED, context.receivedAt, ConfirmationScope.PRESERVATION_ONLY),
            deduplication = Deduplication(DedupStatus.DISTINCT_OBSERVATION, null, DEDUP_METHOD),
            coverage = Coverage(draft.coverageContext, draft.textStatus, draft.outgoingCoverage, emptyList()),
            boundary = Boundary(
                BoundaryMarker.NONE,
                null,
                BoundaryReviewStatus.NOT_APPLICABLE,
                CommunicationStatus.NOT_APPLICABLE,
                UnwantedContact.UNKNOWN,
            ),
            relationshipToPreviousEvents = emptyList(),
            retention = Retention(RetentionMode.CONFIRMED_VAULT, null, 0),
        )
    }

    /** Sender of an event whose author is only a claim in the text. */
    fun claimedSender(label: String?): EventSender =
        EventSender(null, label, IdentityBasis.UNKNOWN, AssociationReview.UNREVIEWED)

    /** The "no time known" bounds the schema requires for an unknown basis. */
    fun unknownTime(): TimeBounds =
        TimeBounds(null, null, TimeBasis.UNKNOWN, TimePrecision.UNKNOWN, null, null, null)
}
