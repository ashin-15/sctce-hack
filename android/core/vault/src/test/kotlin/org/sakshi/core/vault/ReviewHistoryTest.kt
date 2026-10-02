package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewDecisionEntity
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.UnwantedContact

class ReviewHistoryTest : ReviewTestBase() {
    private val actor = ActorId(EventFixtures.ACTOR)
    private val id = EventId("synthetic-m1")

    private suspend fun savedWithCategories() {
        standardCase()
        saveOk(message("synthetic-m1") {
            category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED)
            category(CategoryLabel.INTIMIDATION, CategoryReviewStatus.UNREVIEWED)
        })
        saveOk(message("synthetic-m2"))
    }

    @Test
    fun historyHoldsEveryTypedDecisionAcrossRevisionsOldestFirst() = runBlocking<Unit> {
        savedWithCategories()
        review.reviewCategory(id, 1, CategoryReviewStatus.REJECTED, ReviewReason.SIGNAL_ABSENT)
        review.reviewCategory(id, 1, CategoryReviewStatus.UNCERTAIN)
        review.addUserTag(id, CategoryLabel.EXPLICIT_THREAT, listOf(ReferenceId("ref-1")))
        review.assignSender(caseId, SenderSelector("synthetic-name-synthetic-m1", "synthetic-app", ScopeId("synthetic-conversation-1")), actor)
        review.setDirection(listOf(id), Direction.OUTGOING)
        review.markWantedness(listOf(id), UnwantedContact.USER_MARKED_UNWANTED)
        review.setDuplicate(id, EventId("synthetic-m2"), DedupStatus.POSSIBLE_DUPLICATE)
        review.markEventAsBoundary(id, BoundaryMarker.DO_NOT_CONTACT, actor)
        review.unassignSender(listOf(id))
        review.reviewCategory(EventId("synthetic-m2"), 0, CategoryReviewStatus.ACCEPTED).also { assertIs<ReviewResult.Invalid>(it) }

        val history = review.decisionsForEvent(id)

        assertEquals(history.sortedBy { it.seq }, history)
        assertEquals(
            listOf(
                DecisionTargetKind.Category(1, CategoryLabel.INTIMIDATION) to DecisionChange.CategoryStatus(CategoryReviewStatus.UNREVIEWED, CategoryReviewStatus.REJECTED),
                DecisionTargetKind.Category(1, CategoryLabel.INTIMIDATION) to DecisionChange.CategoryStatus(CategoryReviewStatus.REJECTED, CategoryReviewStatus.UNCERTAIN),
                DecisionTargetKind.Category(2, CategoryLabel.EXPLICIT_THREAT) to DecisionChange.CategoryStatus(null, CategoryReviewStatus.ACCEPTED),
                DecisionTargetKind.Association to DecisionChange.Sender(actor),
                DecisionTargetKind.Direction to DecisionChange.Direction(Direction.OUTGOING),
                DecisionTargetKind.Wantedness to DecisionChange.Wantedness(UnwantedContact.USER_MARKED_UNWANTED),
                DecisionTargetKind.Duplicate to DecisionChange.Duplicate(DedupStatus.POSSIBLE_DUPLICATE, EventId("synthetic-m2")),
                DecisionTargetKind.Boundary to DecisionChange.Boundary(BoundaryMarker.DO_NOT_CONTACT),
                DecisionTargetKind.Association to DecisionChange.Sender(null),
            ),
            history.map { it.target to it.change },
        )
        assertEquals(ReviewReason.SIGNAL_ABSENT, history.first().reasonCode)
        assertEquals(ReviewAction.REJECT, history.first().action)
        assertEquals("2026-10-02T10:00:00Z", history.first().decidedAt)
        assertEquals(emptyList(), review.decisionsForEvent(EventId("synthetic-m2")))
        assertEquals(emptyList(), review.decisionsForEvent(EventId("synthetic-none")))
    }

    @Test
    fun unreadableStoredValuesBecomeUnknownAndNeverThrow() = runBlocking<Unit> {
        savedWithCategories()
        review.setDirection(listOf(id), Direction.OUTGOING)
        fun row(n: Int, type: String, action: String, json: String?) = ReviewDecisionEntity(
            "synthetic-bad-$n", EventFixtures.CASE, type, id.value, 2, action, null, json, null, "2026-10-02T10:00:00Z", 0,
        )
        val bad = listOf(
            row(1, ReviewTarget.DIRECTION, ReviewAction.EDIT, "not json"),
            row(2, ReviewTarget.DIRECTION, ReviewAction.EDIT, "{\"direction\":\"sideways\"}"),
            row(3, ReviewTarget.WANTEDNESS, ReviewAction.EDIT, "[1,2]"),
            row(4, ReviewTarget.BOUNDARY, ReviewAction.ACCEPT, null),
            row(5, ReviewTarget.DUPLICATE, ReviewAction.EDIT, "{\"status\":\"bogus\"}"),
            row(6, "future_kind", ReviewAction.EDIT, "{}"),
            row(7, ReviewTarget.DIRECTION, ReviewAction.EDIT, "{\"direction\":{\"a\":1}}"),
        )
        bad.forEach { db.findingDao().insertDecision(it) }

        val history = review.decisionsForEvent(id)

        assertEquals(DecisionChange.Direction(Direction.OUTGOING), history.first().change)
        assertEquals(List(bad.size) { DecisionChange.Unknown }, history.drop(1).map { it.change })
        assertEquals(DecisionTargetKind.Other, history.last { it.targetType == "future_kind" }.target)
    }

    @Test
    fun decisionsAndLatestDecisionCarryTypedChangesToo() = runBlocking<Unit> {
        savedWithCategories()
        review.setDirection(listOf(id), Direction.SYSTEM)
        assertEquals(DecisionChange.Direction(Direction.SYSTEM), review.decisions(id.value).single().change)
        assertEquals(DecisionTargetKind.Direction, review.latestDecision(ReviewTarget.DIRECTION, id.value)?.target)
    }

    @Test
    fun clearBoundaryRemovesTheMarkerInANewRevision() = runBlocking<Unit> {
        savedWithCategories()
        review.markWantedness(listOf(id), UnwantedContact.USER_MARKED_UNWANTED)
        review.markEventAsBoundary(id, BoundaryMarker.DO_NOT_CONTACT, actor)
        val marked = latest("synthetic-m1")

        assertApplied(review.clearBoundary(id), "synthetic-m1")

        val cleared = latest("synthetic-m1")
        assertEquals(marked.revision + 1, cleared.revision)
        assertEquals(BoundaryMarker.NONE, cleared.boundary.marker)
        assertEquals(null, cleared.boundary.actorId)
        assertEquals(org.sakshi.core.model.BoundaryReviewStatus.NOT_APPLICABLE, cleared.boundary.reviewStatus)
        assertEquals(org.sakshi.core.model.CommunicationStatus.NOT_APPLICABLE, cleared.boundary.communicationStatus)
        assertEquals(UnwantedContact.USER_MARKED_UNWANTED, cleared.boundary.unwantedContact)
        assertEquals(marked, store.load(id, marked.revision))
        val last = review.decisionsForEvent(id).last()
        assertEquals(DecisionTargetKind.Boundary, last.target)
        assertEquals(DecisionChange.Boundary(BoundaryMarker.NONE), last.change)
        assertEquals(
            "review.boundary|event|synthetic-m1|{\"case_id\":\"${EventFixtures.CASE}\",\"event_id\":\"synthetic-m1\"," +
                "\"revision\":${cleared.revision},\"marker\":\"none\",\"cleared_marker\":\"do_not_contact\"}",
            lastAudit("review.boundary"),
        )
        assertEquals(ReviewResult.NoChange, review.clearBoundary(id))
        assertEquals(ReviewResult.NotFound, review.clearBoundary(EventId("synthetic-none")))
    }

    @Test
    fun clearBoundaryNeverTouchesANoteBoundaryEvent() = runBlocking<Unit> {
        standardCase()
        val note = message("synthetic-note") {
            boundary(BoundaryMarker.LIMITED_CONTACT, EventFixtures.ACTOR, org.sakshi.core.model.CommunicationStatus.USER_REPORTED)
        }.also { saveOk(it) }

        val result = assertWritesNothing { review.clearBoundary(note.eventId) }

        val invalid = assertIs<ReviewResult.Invalid>(result)
        assertEquals(ReviewProblem.VALUE_NOT_ALLOWED, invalid.problem)
        assertEquals(org.sakshi.core.model.ViolationCode.BOUNDARY_MARKER_NOT_APPLICABLE, invalid.violations.single().code)
        assertEquals("/boundary/marker", invalid.violations.single().path)
    }
}
