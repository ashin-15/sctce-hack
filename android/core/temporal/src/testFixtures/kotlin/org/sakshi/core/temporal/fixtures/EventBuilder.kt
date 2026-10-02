package org.sakshi.core.temporal.fixtures

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.SeverityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.ReviewPriority
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.UserConfirmation

/*
 * All fixtures in this package are synthetic. They are designed test data and are not evidence.
 */

private const val DEFAULT_EXPIRY: String = "2027-01-01T00:00:00Z"

/** Builds a valid synthetic [Event] with confirmed, incoming message defaults. */
public class EventBuilder internal constructor(private val id: String) {
    public var caseId: String = "synthetic-case"
    public var revision: Int = 1
    public var kind: EventKind = EventKind.MESSAGE_OBSERVATION
    public var direction: Direction = Direction.INCOMING

    /** Earliest and latest time as RFC 3339 text; null makes the event untimed. */
    public var earliest: String? = null
    public var latest: String? = null
    public var observedAt: String? = null
    public var availableAt: String? = null

    public var actor: String? = "synthetic-actor-a"
    public var associationReview: AssociationReview = AssociationReview.CONFIRMED
    public var identityBasis: IdentityBasis = IdentityBasis.USER_ASSERTED
    public var displayLabel: String? = "Person A"
    public var sourceApp: String? = "synthetic-app"
    public var conversation: String? = "synthetic-conversation-1"

    public var confirmation: ConfirmationStatus = ConfirmationStatus.CONFIRMED

    /** Review time; null means the availability time for reviewed events and absent for pending ones. */
    public var reviewedAt: String? = null

    /** When true the review time is omitted even for a confirmed event. */
    public var omitReviewTime: Boolean = false

    public var retentionMode: RetentionMode = RetentionMode.CONFIRMED_VAULT
    public var expiresAt: String? = null

    public var dedupStatus: DedupStatus = DedupStatus.DISTINCT_OBSERVATION
    public var canonical: String? = null
    public var coverageContext: CoverageContext = CoverageContext.COMPLETE_FOR_SELECTED_RANGE
    public var textStatus: TextStatus = TextStatus.AVAILABLE
    public var outgoingCoverage: OutgoingCoverage = OutgoingCoverage.INCLUDED_FOR_SELECTED_RANGE

    public var marker: BoundaryMarker = BoundaryMarker.NONE
    public var boundaryActor: String? = null
    public var boundaryReview: BoundaryReviewStatus = BoundaryReviewStatus.NOT_APPLICABLE
    public var communication: CommunicationStatus = CommunicationStatus.NOT_APPLICABLE
    public var unwantedContact: UnwantedContact = UnwantedContact.NOT_APPLICABLE

    public var representation: Representation = Representation.PRESERVED_IMPORT
    public var sha256: String? = null
    public var sourceRecord: String? = null

    private val categories = mutableListOf<Pair<CategoryLabel, CategoryReviewStatus>>()
    private val relationships = mutableListOf<EventRelationship>()

    /** Sets a point in time (earliest equals latest). */
    public fun at(iso: String) {
        earliest = iso
        latest = iso
    }

    public fun between(from: String, to: String) {
        earliest = from
        latest = to
    }

    public fun untimed() {
        earliest = null
        latest = null
    }

    public fun category(label: CategoryLabel, review: CategoryReviewStatus = CategoryReviewStatus.ACCEPTED) {
        categories += label to review
    }

    public fun unwanted() {
        unwantedContact = UnwantedContact.USER_MARKED_UNWANTED
    }

    public fun wanted() {
        unwantedContact = UnwantedContact.USER_MARKED_WANTED
    }

    /** A confirmed boundary note by the user about [about]. */
    public fun boundary(
        marker: BoundaryMarker,
        about: String,
        communication: CommunicationStatus,
        review: BoundaryReviewStatus = BoundaryReviewStatus.CONFIRMED,
    ) {
        kind = EventKind.USER_BOUNDARY
        direction = if (communication == CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE) {
            Direction.OUTGOING
        } else {
            Direction.UNKNOWN
        }
        this.marker = marker
        boundaryActor = about
        boundaryReview = review
        this.communication = communication
        actor = null
        associationReview = AssociationReview.UNKNOWN
        identityBasis = IdentityBasis.UNKNOWN
        displayLabel = "You"
        if (communication != CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE) {
            representation = Representation.MANUAL_STATEMENT
        }
    }

    /** Marks this event as a pending candidate with an encrypted candidate retention. */
    public fun pending() {
        confirmation = ConfirmationStatus.PENDING
        retentionMode = RetentionMode.ENCRYPTED_CANDIDATE
        expiresAt = expiresAt ?: DEFAULT_EXPIRY
    }

    public fun dedup(status: DedupStatus, canonicalId: String?) {
        dedupStatus = status
        canonical = canonicalId
    }

    public fun relate(
        target: String,
        type: RelationshipType = RelationshipType.POSSIBLE_SAME_OCCURRENCE,
        review: RelationshipReviewStatus = RelationshipReviewStatus.UNREVIEWED,
    ) {
        relationships +=
            EventRelationship(
                targetEventId = EventId(target),
                type = type,
                basis = RelationshipBasis.USER_ASSERTED,
                confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
                reviewStatus = review,
            )
    }

    internal fun build(): Event {
        val timeText = earliest ?: latest
        val observed = observedAt ?: timeText ?: DEFAULT_OBSERVED
        val available = availableAt ?: observed
        val reviewed =
            when {
                omitReviewTime || confirmation == ConfirmationStatus.PENDING -> reviewedAt
                else -> reviewedAt ?: available
            }
        val referenceId = ReferenceId("ref-1")
        val basis =
            if (representation == Representation.MANUAL_STATEMENT) TimeBasis.USER_REPORTED else TimeBasis.SOURCE_CLAIM
        return Event(
            eventId = EventId(id),
            caseId = CaseId(caseId),
            revision = revision,
            eventKind = kind,
            observedAt = Timestamp(observed),
            availableAt = Timestamp(available),
            timestamp =
                TimeBounds(
                    earliest = earliest?.let(::Timestamp),
                    latest = latest?.let(::Timestamp),
                    basis = basis,
                    precision = TimePrecision.MINUTE,
                    sourceTimezone = null,
                    collectorSessionId = null,
                    monotonicMs = null,
                ),
            sender =
                EventSender(
                    actorId = actor?.let(::ActorId),
                    displayLabel = displayLabel,
                    identityBasis = identityBasis,
                    associationReview = associationReview,
                ),
            source =
                EventSource(
                    kind = SourceKind.SELECTED_EXPORT,
                    sourceApp = sourceApp,
                    profileScopeId = null,
                    conversationScopeId = conversation?.let(::ScopeId),
                    sourceRecordId = ScopeId(sourceRecord ?: "record-$id"),
                    parserVersion = ScopeId("synthetic-parser-1"),
                ),
            direction = direction,
            categories =
                categories.map { (label, review) ->
                    CategoryAssessment(
                        label = label,
                        basis = CategoryBasis.USER_TAG,
                        confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
                        producerVersion = ScopeId("synthetic-producer-1"),
                        evidenceReferenceIds = listOf(referenceId),
                        reviewStatus = review,
                    )
                },
            severity = SeverityAssessment(ReviewPriority.ORDINARY, SeverityBasis.UNKNOWN, emptyList()),
            evidenceReferences =
                listOf(
                    EvidenceReference(
                        referenceId = referenceId,
                        artifactId = ArtifactId("synthetic-artifact-$id"),
                        sha256 = sha256,
                        representation = representation,
                        locator = Locator.WholeArtifact,
                    ),
                ),
            userConfirmation =
                UserConfirmation(
                    status = confirmation,
                    reviewedAt = reviewed?.let(::Timestamp),
                    scope =
                        if (confirmation == ConfirmationStatus.PENDING) {
                            ConfirmationScope.NOT_REVIEWED
                        } else {
                            ConfirmationScope.PRESERVATION_AND_SELECTED_ANNOTATIONS
                        },
                ),
            deduplication = Deduplication(dedupStatus, canonical?.let(::EventId), ScopeId("synthetic-dedup-1")),
            coverage = Coverage(coverageContext, textStatus, outgoingCoverage, emptyList()),
            boundary =
                Boundary(
                    marker = marker,
                    actorId = boundaryActor?.let(::ActorId),
                    reviewStatus = boundaryReview,
                    communicationStatus = communication,
                    unwantedContact = unwantedContact,
                ),
            relationshipToPreviousEvents = relationships.toList(),
            retention =
                Retention(
                    mode = retentionMode,
                    expiresAt = expiresAt?.let(::Timestamp),
                    consentGeneration = 0,
                ),
        )
    }

    private companion object {
        const val DEFAULT_OBSERVED: String = "2026-10-01T00:00:00Z"
    }
}

/** Builds one synthetic event; [id] should start with "synthetic-" for fixtures. */
public fun syntheticEvent(id: String, configure: EventBuilder.() -> Unit = {}): Event =
    EventBuilder(id).apply(configure).build()

private val RFC3339: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

/** Re-expresses an instant as RFC 3339 text at [offset], preserving the instant. */
public fun Instant.atOffsetText(offset: ZoneOffset): Timestamp = Timestamp(RFC3339.format(atOffset(offset)))

/** Applies [transform] to every timestamp of the event. */
public fun Event.mapTimestamps(transform: (Timestamp) -> Timestamp): Event =
    copy(
        observedAt = transform(observedAt),
        availableAt = transform(availableAt),
        timestamp =
            timestamp.copy(
                earliest = timestamp.earliest?.let(transform),
                latest = timestamp.latest?.let(transform),
            ),
        userConfirmation = userConfirmation.copy(reviewedAt = userConfirmation.reviewedAt?.let(transform)),
        retention = retention.copy(expiresAt = retention.expiresAt?.let(transform)),
    )
