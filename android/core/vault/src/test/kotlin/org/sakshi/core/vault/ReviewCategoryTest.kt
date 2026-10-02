package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewTargetType
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId

class ReviewCategoryTest : ReviewTestBase() {
    private val id = "synthetic-m1"
    private val eventId = EventId(id)

    private suspend fun savedWithTwoCategories() = message(id) {
        category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED)
        category(CategoryLabel.INTIMIDATION, CategoryReviewStatus.UNREVIEWED)
    }.also {
        standardCase()
        saveOk(it)
    }

    @Test
    fun rejectionChangesOnlyThatCategoryStatusAndKeepsRevisionOne() = runBlocking<Unit> {
        val original = savedWithTwoCategories()

        val result = review.reviewCategory(eventId, 1, CategoryReviewStatus.REJECTED, ReviewReason.SIGNAL_ABSENT)

        assertApplied(result, id)
        val expected = next(original) { e ->
            e.copy(categories = e.categories.mapIndexed { i, c -> if (i == 1) c.copy(reviewStatus = CategoryReviewStatus.REJECTED) else c })
        }
        assertEquals(expected, latest(id))
        assertEquals(2, expected.categories.size)
        assertEquals(original, store.load(eventId, 1))
        val decision = review.decisions(DecisionTargets.category(eventId, 2, 1)).single()
        assertEquals(ReviewTargetType.FINDING, decision.targetType)
        assertEquals(ReviewAction.REJECT, decision.action)
        assertEquals(ReviewResult.NoChange, review.reviewCategory(eventId, 1, CategoryReviewStatus.REJECTED))
        assertEquals("review.category|event|$id|{\"event_id\":\"$id\",\"revision\":2,\"category_index\":1," +
            "\"decision\":\"rejected\",\"reason_code\":\"signal_absent\"}", lastAudit("review.category"))
        assertEquals(decision, review.latestDecision(ReviewTargetType.FINDING, decision.targetId))
    }

    @Test
    fun acceptedAndUncertainMapToDecisionActions() = runBlocking<Unit> {
        savedWithTwoCategories()
        assertApplied(review.reviewCategory(eventId, 0, CategoryReviewStatus.ACCEPTED), id)
        assertApplied(review.reviewCategory(eventId, 0, CategoryReviewStatus.UNCERTAIN), id)

        assertEquals(ReviewAction.ACCEPT, review.decisions(DecisionTargets.category(eventId, 2, 0)).single().action)
        assertEquals(ReviewAction.MARK_UNKNOWN, review.decisions(DecisionTargets.category(eventId, 3, 0)).single().action)
        assertEquals("c000000", DecisionTargets.category(eventId, 3, 0).substringAfterLast('/'))
    }

    @Test
    fun badInputsWriteNothing() = runBlocking<Unit> {
        savedWithTwoCategories()

        assertFailsWith<IllegalArgumentException> { review.reviewCategory(eventId, 0, CategoryReviewStatus.REJECTED, "free text") }
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.reviewCategory(EventId("synthetic-none"), 0, CategoryReviewStatus.ACCEPTED) })
        val outOfRange = assertWritesNothing { review.reviewCategory(eventId, 2, CategoryReviewStatus.ACCEPTED) }
        assertEquals(ReviewProblem.CATEGORY_INDEX, assertIs<ReviewResult.Invalid>(outOfRange).problem)
        assertWritesNothing { review.reviewCategory(eventId, -1, CategoryReviewStatus.ACCEPTED) }
    }

    @Test
    fun userTagAppendsAnAcceptedManualCategory() = runBlocking<Unit> {
        val original = savedWithTwoCategories()
        val references = listOf(ReferenceId("ref-1"))

        assertApplied(review.addUserTag(eventId, CategoryLabel.EXPLICIT_THREAT, references), id)

        val expected = next(original) {
            it.copy(
                categories = it.categories + org.sakshi.core.model.CategoryAssessment(
                    CategoryLabel.EXPLICIT_THREAT, CategoryBasis.USER_TAG,
                    Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null), ScopeId("manual-review-v1"),
                    references, CategoryReviewStatus.ACCEPTED,
                ),
            )
        }
        assertEquals(expected, latest(id))
        assertEquals(original, store.load(eventId, 1))
        assertEquals(ReviewAction.ACCEPT, review.decisions(DecisionTargets.category(eventId, 2, 2)).single().action)
        val audit = lastAudit("review.user_tag")
        assertEquals("review.user_tag|event|$id|{\"event_id\":\"$id\",\"revision\":2,\"label\":\"explicit_threat\",\"reference_count\":1}", audit)
        assertEquals(ReviewResult.NoChange, review.addUserTag(eventId, CategoryLabel.EXPLICIT_THREAT, references))
    }

    @Test
    fun userTagReferencesMustExistAndBeDistinct() = runBlocking<Unit> {
        savedWithTwoCategories()
        fun problem(result: ReviewResult) = assertIs<ReviewResult.Invalid>(result).problem

        assertEquals(ReviewProblem.REFERENCE_UNKNOWN, problem(assertWritesNothing { review.addUserTag(eventId, CategoryLabel.ORDINARY, listOf(ReferenceId("ref-x"))) }))
        assertEquals(ReviewProblem.REFERENCES_REQUIRED, problem(assertWritesNothing { review.addUserTag(eventId, CategoryLabel.ORDINARY, emptyList()) }))
        assertEquals(ReviewProblem.REFERENCE_DUPLICATE, problem(assertWritesNothing { review.addUserTag(eventId, CategoryLabel.ORDINARY, List(2) { ReferenceId("ref-1") }) }))
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.addUserTag(EventId("synthetic-none"), CategoryLabel.ORDINARY, listOf(ReferenceId("ref-1"))) })
        assertFalse(recording.calls.any { it.startsWith("review.") })
    }
}
