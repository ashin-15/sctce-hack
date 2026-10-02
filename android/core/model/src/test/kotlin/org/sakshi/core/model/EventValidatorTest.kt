package org.sakshi.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeContext(
    val actors: Map<String, String> = mapOf("demo-actor-a" to "demo-case-a"),
    val events: Map<String, String> = mapOf("other-1" to "demo-case-a", "foreign-1" to "demo-case-b"),
    val canonical: Map<String, String> = emptyMap(),
    val lengths: Map<String, Int> = mapOf("demo-export-a" to 10),
) : CaseContext {
    override fun caseOfEvent(eventId: EventId): CaseId? = events[eventId.value]?.let(::CaseId)

    override fun caseOfActor(actorId: ActorId): CaseId? = actors[actorId.value]?.let(::CaseId)

    override fun canonicalOf(eventId: EventId): EventId? = canonical[eventId.value]?.let(::EventId)

    override fun codePointLength(artifactId: ArtifactId): Int? = lengths[artifactId.value]
}

class EventValidatorTest {
    private val valid = TestSupport.validEvent()

    private fun codes(event: Event, context: CaseContext = FakeContext()): List<ViolationCode> =
        EventValidator.validate(event, context).map { it.code }

    private fun link(target: String): EventRelationship = EventRelationship(
        EventId(target),
        RelationshipType.REPLY_TO,
        RelationshipBasis.SOURCE_EXPLICIT,
        Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
        RelationshipReviewStatus.UNREVIEWED,
    )

    private fun withLocator(locator: Locator): Event =
        valid.copy(evidenceReferences = listOf(valid.evidenceReferences[0].copy(locator = locator)))

    @Test
    fun validExampleHasNoViolations() {
        assertEquals(emptyList(), EventValidator.validate(valid, FakeContext()))
    }

    @Test
    fun invertedTimeBounds() {
        val bounds = valid.timestamp.copy(earliest = Timestamp("2026-10-01T10:00:00Z"), latest = Timestamp("2026-10-01T09:00:00Z"))
        val violations = EventValidator.validate(valid.copy(timestamp = bounds), FakeContext())
        assertEquals(listOf(ViolationCode.TIME_BOUNDS_INVERTED), violations.map { it.code })
        assertEquals("/timestamp", violations.single().path)
    }

    @Test
    fun reviewBeforeObservation() {
        val review = valid.userConfirmation.copy(reviewedAt = Timestamp("2026-10-01T09:00:00+05:30"))
        assertEquals(listOf(ViolationCode.REVIEW_BEFORE_OBSERVATION), codes(valid.copy(userConfirmation = review)))
    }

    @Test
    fun availableAtMayPrecedeObservedAt() {
        val event = valid.copy(availableAt = Timestamp("2026-10-01T08:00:00+05:30"))
        assertEquals(emptyList(), codes(event))
    }

    @Test
    fun duplicateReferenceId() {
        val ref = valid.evidenceReferences[0]
        assertEquals(listOf(ViolationCode.DUPLICATE_REFERENCE_ID), codes(valid.copy(evidenceReferences = listOf(ref, ref))))
    }

    @Test
    fun unresolvedCategoryAndSeverityReferences() {
        val category = valid.categories[0].copy(
            evidenceReferenceIds = listOf(ReferenceId("demo-ref-schema-1"), ReferenceId("missing")),
        )
        val severity = valid.severity.copy(evidenceReferenceIds = listOf(ReferenceId("missing")))
        val violations = EventValidator.validate(valid.copy(categories = listOf(category), severity = severity), FakeContext())
        assertEquals(listOf(ViolationCode.UNRESOLVED_REFERENCE_ID, ViolationCode.UNRESOLVED_REFERENCE_ID), violations.map { it.code })
        assertEquals("/categories/0/evidence_reference_ids/1", violations[0].path)
        assertEquals("/severity/evidence_reference_ids/0", violations[1].path)
    }

    @Test
    fun emptyTextLocator() {
        assertEquals(listOf(ViolationCode.TEXT_LOCATOR_EMPTY), codes(withLocator(Locator.Text(4, 4))))
    }

    @Test
    fun textLocatorBeyondKnownLength() {
        assertEquals(listOf(ViolationCode.TEXT_LOCATOR_OUT_OF_RANGE), codes(withLocator(Locator.Text(0, 11))))
        assertEquals(emptyList(), codes(withLocator(Locator.Text(0, 11)), FakeContext(lengths = emptyMap())))
    }

    @Test
    fun emptyAudioLocator() {
        assertEquals(listOf(ViolationCode.AUDIO_LOCATOR_EMPTY), codes(withLocator(Locator.AudioTime(500, 500))))
    }

    @Test
    fun unknownSenderActor() {
        assertEquals(listOf(ViolationCode.ACTOR_UNKNOWN), codes(valid, FakeContext(actors = emptyMap())))
    }

    @Test
    fun crossCaseBoundaryActor() {
        val boundary = valid.boundary.copy(actorId = ActorId("foreign-actor"))
        val context = FakeContext(actors = mapOf("demo-actor-a" to "demo-case-a", "foreign-actor" to "demo-case-b"))
        assertEquals(listOf(ViolationCode.ACTOR_CROSS_CASE), codes(valid.copy(boundary = boundary), context))
    }

    @Test
    fun relationshipSelfUnknownAndCrossCase() {
        assertEquals(listOf(ViolationCode.RELATIONSHIP_SELF_LINK), codes(valid.copy(relationshipToPreviousEvents = listOf(link("demo-schema-1")))))
        assertEquals(listOf(ViolationCode.RELATIONSHIP_TARGET_UNKNOWN), codes(valid.copy(relationshipToPreviousEvents = listOf(link("nope")))))
        assertEquals(listOf(ViolationCode.RELATIONSHIP_CROSS_CASE), codes(valid.copy(relationshipToPreviousEvents = listOf(link("foreign-1")))))
        assertEquals(emptyList(), codes(valid.copy(relationshipToPreviousEvents = listOf(link("other-1")))))
    }

    private fun withCanonical(status: DedupStatus, target: String?): Event =
        valid.copy(deduplication = valid.deduplication.copy(status = status, canonicalEventId = target?.let(::EventId)))

    @Test
    fun canonicalSelfUnknownAndCrossCase() {
        assertEquals(listOf(ViolationCode.CANONICAL_SELF), codes(withCanonical(DedupStatus.SAME_REPRESENTATION, "demo-schema-1")))
        assertEquals(listOf(ViolationCode.CANONICAL_UNKNOWN), codes(withCanonical(DedupStatus.SAME_REPRESENTATION, "nope")))
        assertEquals(listOf(ViolationCode.CANONICAL_CROSS_CASE), codes(withCanonical(DedupStatus.SAME_REPRESENTATION, "foreign-1")))
        assertEquals(emptyList(), codes(withCanonical(DedupStatus.SAME_REPRESENTATION, "other-1")))
    }

    @Test
    fun canonicalCycleIsDetected() {
        val cyclic = FakeContext(canonical = mapOf("other-1" to "demo-schema-1"))
        assertEquals(listOf(ViolationCode.CANONICAL_CYCLE), codes(withCanonical(DedupStatus.SAME_REPRESENTATION, "other-1"), cyclic))
    }

    @Test
    fun foreignCycleNotInvolvingEventTerminates() {
        val loop = FakeContext(
            events = mapOf("other-1" to "demo-case-a", "other-2" to "demo-case-a"),
            canonical = mapOf("other-1" to "other-2", "other-2" to "other-1"),
        )
        assertEquals(emptyList(), codes(withCanonical(DedupStatus.POSSIBLE_DUPLICATE, "other-1"), loop))
    }

    @Test
    fun distinctObservationCannotHaveCanonical() {
        assertEquals(listOf(ViolationCode.DISTINCT_WITH_CANONICAL), codes(withCanonical(DedupStatus.DISTINCT_OBSERVATION, "other-1")))
    }

    @Test
    fun markerNoneWithReviewStatus() {
        val boundary = valid.boundary.copy(reviewStatus = BoundaryReviewStatus.UNREVIEWED)
        assertEquals(listOf(ViolationCode.BOUNDARY_NONE_WITH_STATUS), codes(valid.copy(boundary = boundary)))
    }

    @Test
    fun markerWithoutReviewStatus() {
        val boundary = valid.boundary.copy(marker = BoundaryMarker.DO_NOT_CONTACT)
        assertEquals(listOf(ViolationCode.BOUNDARY_MARKER_NOT_APPLICABLE), codes(valid.copy(boundary = boundary)))
    }

    @Test
    fun communicationNeedsNonManualEvidence() {
        val boundary = valid.boundary.copy(
            marker = BoundaryMarker.DO_NOT_CONTACT,
            reviewStatus = BoundaryReviewStatus.CONFIRMED,
            communicationStatus = CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE,
        )
        val manual = valid.evidenceReferences[0].copy(representation = Representation.MANUAL_STATEMENT)
        assertEquals(
            listOf(ViolationCode.COMMUNICATION_WITHOUT_EVIDENCE),
            codes(valid.copy(boundary = boundary, evidenceReferences = listOf(manual))),
        )
        assertEquals(emptyList(), codes(valid.copy(boundary = boundary)))
    }

    @Test
    fun sessionOnlyCannotExpire() {
        val retention = Retention(RetentionMode.SESSION_ONLY, Timestamp("2026-10-08T09:05:00Z"), 1)
        assertEquals(listOf(ViolationCode.SESSION_ONLY_WITH_EXPIRY), codes(valid.copy(retention = retention)))
        assertEquals(emptyList(), codes(valid.copy(retention = retention.copy(expiresAt = null))))
    }

    @Test
    fun confirmedVaultMayHaveAnyExpiry() {
        val retention = Retention(RetentionMode.CONFIRMED_VAULT, Timestamp("2020-01-01T00:00:00Z"), 1)
        assertTrue(codes(valid.copy(retention = retention)).isEmpty())
    }
}
