package org.sakshi.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** Queries the stored-pattern store relies on: view and case scoping, support order, case-wide staleness, deletion. */
class PatternQueryTest : DatabaseTestBase() {
    private suspend fun seed() {
        db.caseDao().insert(caseRow())
        db.caseDao().insert(caseRow("case-2"))
        db.eventDao().insertEventWithRevision(eventRow(), revisionRow(), emptyList())
        db.eventDao().insertEventWithRevision(eventRow("event-2"), revisionRow("event-2"), emptyList())
        db.eventDao().insertEventWithRevision(eventRow("event-3", "case-2"), revisionRow("event-3"), emptyList())
    }

    private fun pattern(id: String, caseId: String = CASE_ID, view: String = EvidenceView.CONFIRMED_ONLY, type: String = "a") =
        patternRow(id, type).copy(caseId = caseId, evidenceView = view)

    @Test
    fun patternsAreScopedToCaseAndViewAndOrdered() = runTest {
        seed()
        val dao = db.patternDao()
        dao.insertPattern(pattern("p-b", type = "b"))
        dao.insertPattern(pattern("p-a2", type = "a").copy(windowStart = "2026-10-02T00:00:00Z"))
        dao.insertPattern(pattern("p-a1", type = "a").copy(windowStart = "2026-10-01T00:00:00Z"))
        dao.insertPattern(pattern("p-preview", view = EvidenceView.CANDIDATE_PREVIEW))
        dao.insertPattern(pattern("p-other", caseId = "case-2"))

        assertEquals(listOf("p-a1", "p-a2", "p-b"), dao.getForCaseView(CASE_ID, EvidenceView.CONFIRMED_ONLY).map { it.id })
        assertEquals(listOf("p-preview"), dao.getForCaseView(CASE_ID, EvidenceView.CANDIDATE_PREVIEW).map { it.id })
        assertEquals(listOf("p-other"), dao.getForCaseView("case-2", EvidenceView.CONFIRMED_ONLY).map { it.id })
    }

    @Test
    fun supportRowsComeBackInInsertionOrderForTheCaseAndViewOnly() = runTest {
        seed()
        val dao = db.patternDao()
        dao.insertWithSupport(
            pattern("p1"),
            listOf(supportRow("p1", "event-2"), supportRow("p1", EVENT_ID), supportRow("p1", EVENT_ID).copy(role = SupportRole.CONTEXT)),
        )
        dao.insertWithSupport(pattern("p2", view = EvidenceView.CANDIDATE_PREVIEW), listOf(supportRow("p2", EVENT_ID)))
        dao.insertWithSupport(pattern("p3", caseId = "case-2"), listOf(supportRow("p3", "event-3")))

        val rows = dao.getSupportForCaseView(CASE_ID, EvidenceView.CONFIRMED_ONLY)
        assertEquals(
            listOf(Triple("p1", "event-2", SupportRole.SUPPORTING), Triple("p1", EVENT_ID, SupportRole.SUPPORTING), Triple("p1", EVENT_ID, SupportRole.CONTEXT)),
            rows.map { Triple(it.patternId, it.eventId, it.role) },
        )
    }

    @Test
    fun markStaleForCaseTouchesEveryViewOfThatCaseOnly() = runTest {
        seed()
        val dao = db.patternDao()
        dao.insertPattern(pattern("p1"))
        dao.insertPattern(pattern("p2", view = EvidenceView.CANDIDATE_PREVIEW))
        dao.insertPattern(pattern("p3").copy(assessmentStatus = AssessmentStatus.STALE))
        dao.insertPattern(pattern("p4", caseId = "case-2"))

        assertEquals(2, dao.markStaleForCase(CASE_ID))
        assertEquals(0, dao.markStaleForCase(CASE_ID))
        assertEquals(listOf(AssessmentStatus.STALE, AssessmentStatus.STALE, AssessmentStatus.STALE), listOf("p1", "p2", "p3").map { dao.get(it)?.assessmentStatus })
        assertEquals(AssessmentStatus.CANDIDATE, dao.get("p4")?.assessmentStatus)
    }

    @Test
    fun deletingByIdRemovesTheirSupportAndLeavesTheRest() = runTest {
        seed()
        val dao = db.patternDao()
        dao.insertWithSupport(pattern("p1"), listOf(supportRow("p1", EVENT_ID)))
        dao.insertWithSupport(pattern("p2"), listOf(supportRow("p2", EVENT_ID)))

        assertEquals(1, dao.deleteByIds(listOf("p1", "missing")))
        assertNull(dao.get("p1"))
        assertEquals(emptyList(), dao.getSupport("p1"))
        assertEquals(listOf("p2"), dao.getSupport("p2").map { it.patternId })
        assertEquals(1, db.count(SakshiSchema.PATTERN_SUPPORT))
    }

    @Test
    fun latestDecisionsForCaseTakeTheNewestPerTarget() = runTest {
        seed()
        val dao = db.findingDao()
        dao.insertDecision(decisionRow("d1", "p1", ReviewAction.ACCEPT, targetType = ReviewTargetType.PATTERN))
        dao.insertDecision(decisionRow("d2", "p1", ReviewAction.REJECT, targetType = ReviewTargetType.PATTERN))
        dao.insertDecision(decisionRow("d3", "p2", ReviewAction.MARK_UNKNOWN, targetType = ReviewTargetType.PATTERN))
        dao.insertDecision(decisionRow("d4", "finding-1", ReviewAction.ACCEPT))

        assertEquals(
            listOf("p1" to ReviewAction.REJECT, "p2" to ReviewAction.MARK_UNKNOWN),
            dao.getLatestDecisionsForCase(CASE_ID, ReviewTargetType.PATTERN).map { it.targetId to it.action },
        )
        assertEquals(emptyList(), dao.getLatestDecisionsForCase("case-2", ReviewTargetType.PATTERN))
    }
}
