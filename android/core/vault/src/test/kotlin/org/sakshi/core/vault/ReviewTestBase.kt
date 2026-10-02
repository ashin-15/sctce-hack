package org.sakshi.core.vault

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Before
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.fixtures.EventBuilder
import org.sakshi.core.temporal.fixtures.syntheticEvent

/** Review tests over the in-memory database. All data is synthetic. */
abstract class ReviewTestBase : EventStoreTestBase() {
    protected lateinit var review: ReviewCoordinator
    protected val caseId: CaseId = CaseId(EventFixtures.CASE)
    protected val now: Timestamp = Timestamp(FIXED_INSTANT.toString())

    @Before
    fun openReview() {
        review = ReviewCoordinator(db, store, recording, clock, ids, Dispatchers.IO)
    }

    /** An incoming message of an unresolved sender, one confirmed category-free reference "ref-1". */
    protected fun message(id: String, configure: EventBuilder.() -> Unit = {}): Event = syntheticEvent(id) {
        caseId = EventFixtures.CASE
        actor = null
        associationReview = AssociationReview.UNREVIEWED
        identityBasis = IdentityBasis.UNKNOWN
        displayLabel = "synthetic-name-$id"
        at("2026-10-01T09:00:00+05:30")
        configure()
    }

    /** What the next revision must look like: the change, a bumped revision and `availableAt` = now, nothing else. */
    protected fun next(original: Event, change: (Event) -> Event): Event =
        change(original).copy(revision = original.revision + 1, availableAt = now)

    protected suspend fun latest(id: String): Event {
        val revision = assertNotNull(db.eventDao().getLatestRevisionNumber(id))
        return assertNotNull(store.load(EventId(id), revision))
    }

    protected suspend fun decisionRows(): Int = withContext(Dispatchers.IO) {
        db.query("SELECT COUNT(*) FROM review_decision", null).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
    }

    protected fun assertApplied(result: ReviewResult, vararg eventIds: String) {
        assertEquals(eventIds.map { EventId(it) }, assertIs<ReviewResult.Applied>(result).changedEventIds)
    }

    /** Runs [block] and asserts no event, finding, decision or audit row was added. */
    protected suspend fun assertWritesNothing(block: suspend () -> ReviewResult): ReviewResult {
        val before = rowCounts() + decisionRows()
        val result = block()
        assertTrue(result !is ReviewResult.Applied, "expected a refusal, got $result")
        assertEquals(before, rowCounts() + decisionRows())
        return result
    }

    protected fun lastAudit(action: String): String = recording.calls.last { it.startsWith("$action|") }
}
