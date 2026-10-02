package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Deduplication
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.ViolationCode

class ReviewFieldsTest : ReviewTestBase() {
    private fun ids(vararg values: String): List<EventId> = values.map { EventId(it) }

    @Test
    fun setDirectionChangesOnlyDirectionAndSkipsEventsAlreadySet() = runBlocking<Unit> {
        standardCase()
        val one = message("synthetic-d1").also { saveOk(it) }
        val two = message("synthetic-d2") { direction = Direction.OUTGOING }.also { saveOk(it) }

        val result = review.setDirection(ids("synthetic-d1", "synthetic-d2", "synthetic-d1"), Direction.OUTGOING)

        assertApplied(result, "synthetic-d1")
        assertEquals(next(one) { it.copy(direction = Direction.OUTGOING) }, latest("synthetic-d1"))
        assertEquals(two, latest("synthetic-d2"))
        assertEquals(one, store.load(one.eventId, 1))
        assertEquals(ReviewAction.EDIT, review.decisions("synthetic-d1").single().action)
        assertEquals(ReviewResult.NoChange, review.setDirection(ids("synthetic-d1"), Direction.OUTGOING))
        assertEquals(ReviewResult.NoChange, review.setDirection(emptyList(), Direction.OUTGOING))
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.setDirection(ids("synthetic-d2", "synthetic-none"), Direction.INCOMING) })
    }

    @Test
    fun markWantednessChangesOnlyTheUnwantedField() = runBlocking<Unit> {
        standardCase()
        val one = message("synthetic-w1").also { saveOk(it) }
        val two = message("synthetic-w2") { unwanted() }.also { saveOk(it) }

        assertApplied(review.markWantedness(ids("synthetic-w1", "synthetic-w2"), UnwantedContact.USER_MARKED_UNWANTED), "synthetic-w1")
        assertEquals(next(one) { it.copy(boundary = it.boundary.copy(unwantedContact = UnwantedContact.USER_MARKED_UNWANTED)) }, latest("synthetic-w1"))
        assertEquals(two, latest("synthetic-w2"))
        assertApplied(review.markWantedness(ids("synthetic-w2"), UnwantedContact.USER_MARKED_WANTED), "synthetic-w2")
        assertEquals(UnwantedContact.USER_MARKED_WANTED, latest("synthetic-w2").boundary.unwantedContact)
        val invalid = assertWritesNothing { review.markWantedness(ids("synthetic-w1"), UnwantedContact.NOT_APPLICABLE) }
        assertEquals(ReviewProblem.VALUE_NOT_ALLOWED, assertIs<ReviewResult.Invalid>(invalid).problem)
        assertEquals("review.wantedness|case|${EventFixtures.CASE}|{\"case_id\":\"${EventFixtures.CASE}\",\"unwanted_contact\":\"user_marked_wanted\",\"count\":1}", lastAudit("review.wantedness"))
    }

    @Test
    fun setDuplicateLinksAndClears() = runBlocking<Unit> {
        standardCase()
        val canonical = message("synthetic-k1").also { saveOk(it) }
        val repost = message("synthetic-k2").also { saveOk(it) }

        assertApplied(review.setDuplicate(repost.eventId, canonical.eventId, DedupStatus.SAME_REPRESENTATION), "synthetic-k2")
        assertEquals(
            next(repost) { it.copy(deduplication = Deduplication(DedupStatus.SAME_REPRESENTATION, canonical.eventId, ScopeId("manual-review-v1"))) },
            latest("synthetic-k2"),
        )
        assertEquals(ReviewResult.NoChange, review.setDuplicate(repost.eventId, canonical.eventId, DedupStatus.SAME_REPRESENTATION))
        assertApplied(review.setDuplicate(repost.eventId, canonical.eventId, DedupStatus.DISTINCT_OBSERVATION), "synthetic-k2")
        assertEquals(null, latest("synthetic-k2").deduplication.canonicalEventId)
        assertEquals(DedupStatus.DISTINCT_OBSERVATION, latest("synthetic-k2").deduplication.status)
        assertEquals(repost, store.load(repost.eventId, 1))
        assertEquals(2, review.decisions("synthetic-k2").size)
        assertEquals("review.duplicate|event|synthetic-k2|{\"event_id\":\"synthetic-k2\",\"revision\":3,\"status\":\"distinct_observation\"}", lastAudit("review.duplicate"))
    }

    @Test
    fun setDuplicateRefusesSelfUnknownForeignAndCycles() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        val one = message("synthetic-k1").also { saveOk(it) }
        val two = message("synthetic-k2").also { saveOk(it) }
        saveOk(message("synthetic-f1") { caseId = "synthetic-case-2" })

        fun code(result: ReviewResult) = assertIs<ReviewResult.Invalid>(result).violations.single().code

        assertEquals(ViolationCode.CANONICAL_SELF, code(assertWritesNothing { review.setDuplicate(one.eventId, one.eventId, DedupStatus.POSSIBLE_DUPLICATE) }))
        assertEquals(ViolationCode.CANONICAL_UNKNOWN, code(assertWritesNothing { review.setDuplicate(one.eventId, EventId("synthetic-none"), DedupStatus.POSSIBLE_DUPLICATE) }))
        assertEquals(ViolationCode.CANONICAL_CROSS_CASE, code(assertWritesNothing { review.setDuplicate(one.eventId, EventId("synthetic-f1"), DedupStatus.POSSIBLE_DUPLICATE) }))
        assertEquals(ReviewProblem.CANONICAL_REQUIRED, assertIs<ReviewResult.Invalid>(assertWritesNothing { review.setDuplicate(one.eventId, null, DedupStatus.SAME_REPRESENTATION) }).problem)
        assertEquals(ReviewProblem.VALUE_NOT_ALLOWED, assertIs<ReviewResult.Invalid>(assertWritesNothing { review.setDuplicate(one.eventId, two.eventId, DedupStatus.LIFECYCLE_ONLY) }).problem)
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.setDuplicate(EventId("synthetic-none"), one.eventId, DedupStatus.POSSIBLE_DUPLICATE) })

        assertApplied(review.setDuplicate(one.eventId, two.eventId, DedupStatus.POSSIBLE_DUPLICATE), "synthetic-k1")
        assertEquals(ViolationCode.CANONICAL_CYCLE, code(assertWritesNothing { review.setDuplicate(two.eventId, one.eventId, DedupStatus.POSSIBLE_DUPLICATE) }))
    }

    @Test
    fun aBulkOperationWithOneInvalidEventWritesNothing() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        val good = message("synthetic-g1").also { saveOk(it) }
        val foreign = message("synthetic-g2") { caseId = "synthetic-case-2" }.also { saveOk(it) }

        val result = assertWritesNothing { review.setDirection(ids("synthetic-g1", "synthetic-g2"), Direction.OUTGOING) }

        assertEquals(ReviewProblem.MIXED_CASE, assertIs<ReviewResult.Invalid>(result).problem)
        assertEquals(good, latest("synthetic-g1"))
        assertEquals(foreign, latest("synthetic-g2"))
    }

    @Test
    fun aFailureAfterTheEventsAreSavedRollsBackEverything() = runBlocking<Unit> {
        standardCase()
        val event = message("synthetic-r1").also { saveOk(it) }
        val failing = object : AuditLog(db, clock) {
            override suspend fun append(action: String, subjectType: String, subjectId: String, details: kotlinx.serialization.json.JsonObject): Long {
                check(!action.startsWith("review.")) { "synthetic audit failure" }
                return super.append(action, subjectType, subjectId, details)
            }
        }
        val coordinator = ReviewCoordinator(db, store, failing, clock, ids)
        val before = rowCounts() + decisionRows()

        assertFailsWith<IllegalStateException> { coordinator.setDirection(ids("synthetic-r1"), Direction.OUTGOING) }

        assertEquals(before, rowCounts() + decisionRows())
        assertEquals(event, latest("synthetic-r1"))
    }
}
