package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Boundary
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.temporal.ActorScope
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.CountBounds
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.Measurements
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

/** Review actions, applied to stored synthetic timelines, give the patterns the finished fixtures give. */
class ReviewTemporalTest : ReviewTestBase() {
    private val plainBoundary = Boundary(BoundaryMarker.NONE, null, BoundaryReviewStatus.NOT_APPLICABLE, CommunicationStatus.NOT_APPLICABLE, UnwantedContact.NOT_APPLICABLE)

    private suspend fun store(input: TemporalInput, vararg actorIds: String, events: List<Event> = input.events) {
        insertCase(input.caseId.value)
        actorIds.forEach { insertActor(input.caseId.value, it) }
        assertEquals(events.size, (store.saveAll(events) as BatchSaveResult.Saved).count)
    }

    private suspend fun reviewed(input: TemporalInput): TemporalInput =
        input.copy(events = store.loadLatest(input.caseId, input.knowledgeCutoff))

    private fun TemporalInput.patterns(type: PatternType): List<PatternRecord> =
        TemporalEngine.analyse(this).patterns.filter { it.type == type }

    @Test
    fun reviewActionsRebuildTimelineAFromUnresolvedSenders() = runBlocking<Unit> {
        val original = SyntheticTimelines.a()
        val expected = original.patterns(PatternType.RECURRENCE_AFTER_BOUNDARY).single()
        val stripped = original.events.map { e ->
            if (e.eventId.value == "synthetic-a0") {
                e.copy(eventKind = EventKind.MESSAGE_OBSERVATION, direction = Direction.UNKNOWN, boundary = plainBoundary)
            } else {
                e.copy(
                    sender = e.sender.copy(actorId = null, identityBasis = IdentityBasis.UNKNOWN, associationReview = AssociationReview.UNREVIEWED),
                    boundary = e.boundary.copy(unwantedContact = UnwantedContact.UNKNOWN),
                )
            }
        }
        store(original, "synthetic-actor-a", events = stripped)
        assertTrue(original.copy(events = stripped).patterns(PatternType.RECURRENCE_AFTER_BOUNDARY).isEmpty())
        val actor = ActorId("synthetic-actor-a")

        val contacts = SenderSelector("Person A", "synthetic-app", ScopeId("synthetic-conversation-1"))
        val me = SenderSelector("You", "synthetic-app", ScopeId("synthetic-conversation-1"))
        assertApplied(review.assignSender(original.caseId, contacts, actor), *(1..6).map { "synthetic-a$it" }.toTypedArray())
        assertApplied(review.markOwnMessages(original.caseId, me), "synthetic-a0")
        assertApplied(review.markEventAsBoundary(EventId("synthetic-a0"), BoundaryMarker.DO_NOT_CONTACT, actor), "synthetic-a0")
        assertApplied(review.markWantedness((1..6).map { EventId("synthetic-a$it") }, UnwantedContact.USER_MARKED_UNWANTED), *(1..6).map { "synthetic-a$it" }.toTypedArray())

        val actual = reviewed(original).patterns(PatternType.RECURRENCE_AFTER_BOUNDARY).single()
        assertEquals(CountBounds(6, 6), (actual.measurements as Measurements.RecurrenceAfterBoundary).afterBoundary)
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, actual.status)
        assertEquals(ActorScope.Confirmed(actor), actual.actorScope)
        assertEquals(expected.measurements, actual.measurements)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.limitations, actual.limitations)
        assertEquals(expected.actorScope, actual.actorScope)
    }

    private fun timelineB(review: CategoryReviewStatus): TemporalInput {
        val original = SyntheticTimelines.b()
        val labels = mapOf("synthetic-b1" to CategoryLabel.VERBAL_ABUSE, "synthetic-b2" to CategoryLabel.INTIMIDATION, "synthetic-b3" to CategoryLabel.EXPLICIT_THREAT)
        return original.copy(
            events = original.events.map { e ->
                e.copy(categories = e.categories.map { it.copy(reviewStatus = review) }, sender = e.sender)
            }.also { assertEquals(labels.keys, it.map { e -> e.eventId.value }.toSet()) },
        )
    }

    @Test
    fun rejectingATagRemovesTheWordingTransition() = runBlocking<Unit> {
        val input = timelineB(CategoryReviewStatus.ACCEPTED)
        store(input, "synthetic-actor-b")
        assertEquals(1, reviewed(input).patterns(PatternType.WORDING_TRANSITION).size)

        assertApplied(review.reviewCategory(EventId("synthetic-b3"), 0, CategoryReviewStatus.REJECTED, ReviewReason.SIGNAL_ABSENT), "synthetic-b3")

        assertTrue(reviewed(input).patterns(PatternType.WORDING_TRANSITION).isEmpty())
    }

    @Test
    fun acceptingUnreviewedTagsMakesTheTransitionAppearInConfirmedOnly() = runBlocking<Unit> {
        val input = timelineB(CategoryReviewStatus.UNREVIEWED)
        store(input, "synthetic-actor-b")
        assertEquals(EvidenceView.CONFIRMED_ONLY, input.view)
        assertTrue(reviewed(input).patterns(PatternType.WORDING_TRANSITION).isEmpty())

        for (id in listOf("synthetic-b1", "synthetic-b2", "synthetic-b3")) {
            assertApplied(review.reviewCategory(EventId(id), 0, CategoryReviewStatus.ACCEPTED), id)
        }

        val record = reviewed(input).patterns(PatternType.WORDING_TRANSITION).single()
        assertFalse(record.status == AssessmentStatus.NOT_OBSERVED)
        assertEquals(EvidenceView.CONFIRMED_ONLY, record.view)
    }
}
