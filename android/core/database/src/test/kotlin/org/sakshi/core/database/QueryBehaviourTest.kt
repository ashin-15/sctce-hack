package org.sakshi.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryBehaviourTest : DatabaseTestBase() {
    @Test
    fun cutoffQueryReturnsTheRevisionAvailableAtThatTime() = runTest {
        db.caseDao().insert(caseRow())
        val dao = db.eventDao()
        dao.insertEventWithRevision(eventRow(), revisionRow(revision = 1, availableAtEpochMs = 1000), emptyList())
        dao.addRevision(revisionRow(revision = 2, availableAtEpochMs = 2000), emptyList())
        dao.insertEventWithRevision(eventRow("event-2"), revisionRow("event-2", availableAtEpochMs = 3000, tsEarliestEpochMs = T0 + 1), emptyList())

        assertTrue(dao.getLatestRevisions(CASE_ID, 999).isEmpty())
        assertEquals(listOf(EVENT_ID to 1), dao.getLatestRevisions(CASE_ID, 1999).map { it.eventId to it.revision })
        assertEquals(listOf(EVENT_ID to 2), dao.getLatestRevisions(CASE_ID, 2000).map { it.eventId to it.revision })
        assertEquals(listOf(EVENT_ID to 2, "event-2" to 1), dao.getLatestRevisions(CASE_ID, 5000).map { it.eventId to it.revision })
        assertEquals(listOf(EVENT_ID to 2, "event-2" to 1), dao.observeLatestRevisions(CASE_ID).first().map { it.eventId to it.revision })
    }

    @Test
    fun nullTimeBoundsRoundTrip() = runTest {
        db.caseDao().insert(caseRow())
        db.eventDao().insertEventWithRevision(
            eventRow(), revisionRow(tsEarliest = null, tsEarliestEpochMs = null, tsLatest = null, tsLatestEpochMs = null), emptyList(),
        )
        val stored = db.eventDao().getRevisions(EVENT_ID).single()
        assertNull(stored.tsEarliest)
        assertNull(stored.tsEarliestEpochMs)
        assertNull(stored.tsLatest)
        assertNull(stored.tsLatestEpochMs)
        db.eventDao().insertCoverageGap(gapRow().copy(startAt = null, startAtEpochMs = null))
        val gap = db.eventDao().observeCoverageGaps(CASE_ID).first().single()
        assertNull(gap.startAt)
        assertNull(gap.endAtEpochMs)
    }

    @Test
    fun enqueueIsIdempotentAndRejectsADifferentSource() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        val dao = db.jobDao()
        assertTrue(dao.enqueue(jobRow()))
        assertFalse(dao.enqueue(jobRow()))
        assertEquals(1, db.count("processing_job"))
        assertFailsWith<IllegalStateException> { dao.enqueue(jobRow(sha = "99".repeat(32))) }
        assertEquals("ab".repeat(32), dao.get("job-1")?.sourceSha256)
    }

    @Test
    fun claimReturnsEachPendingJobOnceInQueueOrder() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        val dao = db.jobDao()
        dao.enqueue(jobRow("job-c", enqueuedAt = 30))
        dao.enqueue(jobRow("job-b", enqueuedAt = 10))
        dao.enqueue(jobRow("job-a", enqueuedAt = 10))
        val claimed = List(4) { dao.claimNext()?.id }
        assertEquals(listOf("job-a", "job-b", "job-c", null), claimed)
        assertEquals(JobStatus.RUNNING, dao.get("job-a")?.status)
        assertEquals(1, dao.get("job-a")?.attempt)
    }

    @Test
    fun jobStatusTransitionsAndRecovery() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        val dao = db.jobDao()
        dao.enqueue(jobRow("job-1", enqueuedAt = 1))
        dao.enqueue(jobRow("job-2", enqueuedAt = 2))
        dao.enqueue(jobRow("job-3", enqueuedAt = 3))
        dao.claimNext()
        dao.claimNext()
        assertEquals(2, dao.resetRunning())
        assertEquals(listOf("job-1", "job-2", "job-3"), dao.observeByStatus(JobStatus.PENDING).first().map { it.id })
        assertEquals("job-1", dao.claimNext()?.id)
        assertEquals(2, dao.get("job-1")?.attempt)
        assertEquals(1, dao.markComplete("job-1"))
        assertEquals(1, dao.markFailed("job-2", "E_OCR"))
        assertEquals("E_OCR", dao.get("job-2")?.lastErrorCode)
        assertEquals(1, dao.cancel("job-3"))
        assertEquals(0, dao.cancel("job-1"))
        assertEquals(3, dao.observeForEvidence(EVIDENCE_ID).first().size)
    }

    @Test
    fun latestDecisionPerTargetIsTheLastInserted() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        db.eventDao().insertEventWithRevision(eventRow(), revisionRow(), listOf(anchorRow()))
        val dao = db.findingDao()
        dao.insertWithAnchors(findingRow("finding-1"), listOf("anchor-1"))
        dao.insertWithAnchors(findingRow("finding-2"), listOf("anchor-1"))
        dao.insertDecision(decisionRow("d1", "finding-1", ReviewAction.ACCEPT, epochMs = 100))
        dao.insertDecision(decisionRow("d2", "finding-1", ReviewAction.REJECT, epochMs = 200))
        dao.insertDecision(decisionRow("d3", "finding-1", ReviewAction.EDIT, epochMs = 200))
        dao.insertDecision(decisionRow("d4", "finding-2", ReviewAction.MARK_UNKNOWN, epochMs = 50))
        dao.insertDecision(decisionRow("d5", "pattern-9", ReviewAction.ACCEPT, epochMs = 900, targetType = ReviewTargetType.PATTERN))

        assertEquals("d3", dao.getLatestDecision(ReviewTargetType.FINDING, "finding-1")?.id)
        val latest = dao.observeLatestDecisions(CASE_ID, ReviewTargetType.FINDING).first()
        assertEquals(listOf("finding-1" to ReviewAction.EDIT, "finding-2" to ReviewAction.MARK_UNKNOWN), latest.map { it.targetId to it.action })
        assertEquals(listOf("d1", "d2", "d3"), dao.getDecisionHistory(ReviewTargetType.FINDING, "finding-1").map { it.id })
    }

    @Test
    fun markingStaleAffectsOnlyDependentPatterns() = runTest {
        db.caseDao().insert(caseRow())
        db.eventDao().insertEventWithRevision(eventRow(), revisionRow(), emptyList())
        db.eventDao().insertEventWithRevision(eventRow("event-2"), revisionRow("event-2"), emptyList())
        val dao = db.patternDao()
        dao.insertWithSupport(patternRow("p1"), listOf(supportRow("p1", EVENT_ID), supportRow("p1", "event-2")))
        dao.insertWithSupport(patternRow("p2"), listOf(supportRow("p2", "event-2")))
        dao.insertWithSupport(patternRow("p3", type = "other"), listOf(supportRow("p3", EVENT_ID)))

        assertEquals(2, dao.markStaleForEvent(EVENT_ID))
        assertEquals(0, dao.markStaleForEvent(EVENT_ID))
        assertEquals(AssessmentStatus.STALE, dao.get("p1")?.assessmentStatus)
        assertEquals(AssessmentStatus.CANDIDATE, dao.get("p2")?.assessmentStatus)
        assertEquals(AssessmentStatus.STALE, dao.get("p3")?.assessmentStatus)
        assertEquals(1, dao.deleteStale(CASE_ID, "repetition"))
        assertEquals(null, dao.get("p1"))
        assertEquals("p3", dao.get("p3")?.id)
    }
}
