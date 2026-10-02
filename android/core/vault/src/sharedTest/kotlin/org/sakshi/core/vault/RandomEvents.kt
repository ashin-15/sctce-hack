package org.sakshi.core.vault

import kotlin.random.Random
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

/**
 * Seeded generator of valid synthetic events that vary every enum, nullability, list size (0..4) and string.
 * Each event may point at events generated before it, so saving them in order always satisfies the validator.
 */
class RandomEvents(seed: Long, private val caseId: String, private val actorIds: List<String>) {
    private val random = Random(seed)
    private val earlier = mutableListOf<String>()
    private var scopeCounter = 0

    private val texts = listOf(
        "plain", "", "Zoë", "日本語", "മലയാളം അക്ഷരം", "हिन्दी पाठ", "emoji 😀 end", "say \"hi\" \\ back",
        "tab\tand\nnewline", "<tag> & 'quote'", "  padded  ", "Ünïcödé Ⅻ",
    )
    private val offsets = listOf("Z", "+00:00", "+05:30", "-07:00", "+14:00", "-03:30")
    private val fractions = listOf("", ".5", ".123", ".000001", ".123456789")

    private fun <T> pick(values: List<T>): T = values[random.nextInt(values.size)]

    private fun <T> pickNullable(values: List<T>): T? = if (random.nextBoolean()) null else pick(values)

    private fun scope(): ScopeId = ScopeId("synthetic-scope-${scopeCounter++}")

    private fun stamp(day: Int, hour: Int, minute: Int, second: Int, fraction: String, offset: String): Timestamp =
        Timestamp("2026-10-%02dT%02d:%02d:%02d%s%s".format(day, hour, minute, second, fraction, offset))

    private fun confidence(allowCalibrated: Boolean): Confidence {
        val semantics = if (allowCalibrated) pick(ConfidenceSemantics.entries) else ConfidenceSemantics.NOT_APPLICABLE
        return when (semantics) {
            ConfidenceSemantics.CALIBRATED_PROBABILITY -> Confidence(random.nextDouble(), semantics, scope())
            ConfidenceSemantics.UNCALIBRATED_BOUNDED_SCORE -> Confidence(random.nextDouble(), semantics, null)
            else -> Confidence(null, semantics, null)
        }
    }

    private fun locator(): Locator = when (random.nextInt(4)) {
        0 -> Locator.WholeArtifact
        3 -> Locator.ImageOrPageRegion(if (random.nextBoolean()) null else random.nextInt(20), scope())
        1 -> Locator.Text(random.nextInt(50), 50 + random.nextInt(50))
        else -> Locator.AudioTime(random.nextLong(1000), 1000 + random.nextLong(100_000))
    }

    private fun subset(ids: List<ReferenceId>): List<ReferenceId> = ids.shuffled(random).take(random.nextInt(ids.size + 1))

    fun next(index: Int): Event {
        val id = "synthetic-random-$index"
        val day = 1 + random.nextInt(27)
        val hour = random.nextInt(3)
        val offset = pick(offsets)
        val fraction = pick(fractions)
        fun at(extraHours: Int) = stamp(day, hour + extraHours, random.nextInt(60), random.nextInt(60), fraction, offset)
        val observed = at(0)
        val kind = pick(EventKind.entries)

        val timeBasis = pick(TimeBasis.entries)
        val minute = random.nextInt(60)
        val timestamp = if (timeBasis == TimeBasis.UNKNOWN) {
            TimeBounds(null, null, timeBasis, TimePrecision.UNKNOWN, pickNullable(texts), pickNullable(listOf(scope())), null)
        } else {
            TimeBounds(
                stamp(day, hour, minute, 0, fraction, offset),
                stamp(day, hour + random.nextInt(3), minute, 59, fraction, offset),
                timeBasis,
                pick(TimePrecision.entries),
                pickNullable(texts),
                pickNullable(listOf(scope())),
                if (random.nextBoolean()) null else random.nextLong(1_000_000_000L),
            )
        }

        val references = List(random.nextInt(5)) { i ->
            EvidenceReference(
                ReferenceId("ref-$i-${random.nextInt(1000)}"),
                ArtifactId("synthetic-artifact-$index-$i"),
                if (random.nextBoolean()) null else List(64) { "0123456789abcdef"[random.nextInt(16)] }.joinToString(""),
                pick(Representation.entries),
                locator(),
            )
        }
        val referenceIds = references.map { it.referenceId }
        val categories = List(random.nextInt(5)) {
            val basis = pick(CategoryBasis.entries)
            CategoryAssessment(
                pick(CategoryLabel.entries), basis, confidence(basis != CategoryBasis.USER_TAG), scope(),
                subset(referenceIds), pick(CategoryReviewStatus.entries),
            )
        }

        val retentionMode = pick(RetentionMode.entries)
        val reviewed = at(1)
        val confirmation = if (retentionMode == RetentionMode.CONFIRMED_VAULT) {
            UserConfirmation(
                ConfirmationStatus.CONFIRMED, reviewed,
                pick(listOf(ConfirmationScope.PRESERVATION_ONLY, ConfirmationScope.PRESERVATION_AND_SELECTED_ANNOTATIONS)),
            )
        } else {
            val status = pick(ConfirmationStatus.entries)
            if (status == ConfirmationStatus.PENDING) {
                UserConfirmation(status, null, ConfirmationScope.NOT_REVIEWED)
            } else {
                UserConfirmation(status, reviewed, pick(ConfirmationScope.entries))
            }
        }
        val retention = Retention(
            retentionMode,
            when (retentionMode) {
                RetentionMode.SESSION_ONLY -> null
                RetentionMode.ENCRYPTED_CANDIDATE -> at(20)
                RetentionMode.CONFIRMED_VAULT -> if (random.nextBoolean()) null else at(20)
            },
            random.nextInt(6),
        )

        var dedupStatus = pick(DedupStatus.entries)
        if (dedupStatus == DedupStatus.SAME_REPRESENTATION && earlier.isEmpty()) dedupStatus = DedupStatus.DISTINCT_OBSERVATION
        val canonical = when {
            earlier.isEmpty() || dedupStatus == DedupStatus.DISTINCT_OBSERVATION -> null
            dedupStatus == DedupStatus.SAME_REPRESENTATION -> pick(earlier)
            else -> pickNullable(earlier)
        }

        val hasEvidenceStatement = references.any { it.representation != Representation.MANUAL_STATEMENT }
        val marker = if (kind == EventKind.USER_BOUNDARY) {
            pick(BoundaryMarker.entries.filter { it != BoundaryMarker.NONE })
        } else {
            pick(BoundaryMarker.entries)
        }
        val boundaryActor = pickNullable(actorIds)?.let(::ActorId)
        val boundary = if (marker == BoundaryMarker.NONE) {
            Boundary(marker, boundaryActor, BoundaryReviewStatus.NOT_APPLICABLE, CommunicationStatus.NOT_APPLICABLE, pick(UnwantedContact.entries))
        } else {
            val communication = pick(CommunicationStatus.entries.filter { hasEvidenceStatement || it != CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE })
            Boundary(
                marker, boundaryActor, pick(BoundaryReviewStatus.entries.filter { it != BoundaryReviewStatus.NOT_APPLICABLE }),
                communication, pick(UnwantedContact.entries),
            )
        }

        val relationships = if (earlier.isEmpty()) {
            emptyList()
        } else {
            List(random.nextInt(5)) {
                EventRelationship(
                    EventId(pick(earlier)), pick(RelationshipType.entries), pick(RelationshipBasis.entries),
                    confidence(true), pick(RelationshipReviewStatus.entries),
                )
            }
        }

        val event = Event(
            eventId = EventId(id),
            caseId = CaseId(caseId),
            revision = 1,
            eventKind = kind,
            observedAt = observed,
            availableAt = at(2),
            timestamp = timestamp,
            sender = EventSender(
                pickNullable(actorIds)?.let(::ActorId), pickNullable(texts), pick(IdentityBasis.entries),
                pick(AssociationReview.entries),
            ),
            source = EventSource(
                pick(SourceKind.entries), pickNullable(texts), pickNullable(listOf(scope())), pickNullable(listOf(scope())),
                pickNullable(listOf(scope())), scope(),
            ),
            direction = pick(Direction.entries),
            categories = categories,
            severity = SeverityAssessment(pick(ReviewPriority.entries), pick(SeverityBasis.entries), subset(referenceIds)),
            evidenceReferences = references,
            userConfirmation = confirmation,
            deduplication = Deduplication(dedupStatus, canonical?.let(::EventId), scope()),
            coverage = Coverage(
                pick(CoverageContext.entries), pick(TextStatus.entries), pick(OutgoingCoverage.entries),
                List(random.nextInt(4)) { ReferenceId("gap-$index-$it") }.shuffled(random),
            ),
            boundary = boundary,
            relationshipToPreviousEvents = relationships,
            retention = retention,
        )
        earlier += id
        return event
    }
}
