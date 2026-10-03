package org.sakshi.processing.analysis

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.PatternReviewAction
import org.sakshi.core.vault.PatternReviewResult
import org.sakshi.core.vault.ReviewReason
import org.sakshi.processing.text.DateOrder

/** [CasePatterns.refresh] and [CasePatterns.stored]: the same view as compute, kept in the vault. */
class CasePatternsRefreshTest : AnalysisTestBase() {
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val patterns: CasePatterns get() = CasePatterns(vault, clock)
    private val view = EvidenceView.CONFIRMED_ONLY

    private fun import() = runBlocking {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER))
    }

    @Test
    fun refreshGivesTheSameViewAsComputeAndStoresTheRows() = runBlocking<Unit> {
        import()
        val computed = patterns.compute(CaseId(caseId), view, kolkata, withSupportingEvents = true)

        val refreshed = patterns.refresh(CaseId(caseId), view, kolkata, withSupportingEvents = true)

        assertTrue(computed.result.patterns.isNotEmpty())
        assertEquals(computed.result, refreshed.result)
        assertEquals(computed.explanations, refreshed.explanations)
        assertEquals(computed.scopeLabels, refreshed.scopeLabels)
        assertEquals(computed.supportingEvents, refreshed.supportingEvents)
        assertEquals(computed.result.patterns.map { it.patternKey }.toSet(), refreshed.storedIds.keys)
        assertTrue(refreshed.reviews.values.all { it == PatternReview.NOT_REVIEWED })
        val stored = vault.patterns.list(CaseId(caseId), view)
        assertEquals(refreshed.storedIds.values.toSet(), stored.map { it.id }.toSet())
        assertEquals(computed.result.patterns.toSet(), stored.map { it.record }.toSet())
        assertTrue(stored.all { !it.stale })
        // The stored sentence is the one the view shows for the same record (absent for descriptions that have none).
        for (pattern in stored) assertEquals(refreshed.explanations.getValue(pattern.record.patternKey).interpretation, pattern.interpretation)
    }

    @Test
    fun refreshOfAnEmptyCaseStoresNothing() = runBlocking<Unit> {
        val refreshed = patterns.refresh(CaseId(caseId), view, kolkata)
        assertTrue(refreshed.result.patterns.isEmpty())
        assertTrue(refreshed.storedIds.isEmpty())
        assertEquals(emptyList(), vault.patterns.list(CaseId(caseId), view))
    }

    @Test
    fun aReviewAppearsInTheNextRefreshOfTheSameDescription() = runBlocking<Unit> {
        import()
        val first = patterns.refresh(CaseId(caseId), view, kolkata)
        val (key, id) = first.storedIds.entries.first()
        assertEquals(PatternReviewResult.Recorded(PatternReview.ACCEPTED), vault.patterns.review(id, PatternReviewAction.ACCEPT))

        val second = patterns.refresh(CaseId(caseId), view, kolkata)

        assertEquals(id, second.storedIds.getValue(key))
        assertEquals(PatternReview.ACCEPTED, second.reviews.getValue(key))
    }

    @Test
    fun aRejectionReasonIsExposedByRefreshAndStored() = runBlocking<Unit> {
        import()
        val first = patterns.refresh(CaseId(caseId), view, kolkata)
        assertTrue(first.reviewReasons.isEmpty())
        val (key, id) = first.storedIds.entries.first()
        vault.patterns.review(id, PatternReviewAction.REJECT, ReviewReason.INSUFFICIENT_CONTEXT)

        val second = patterns.refresh(CaseId(caseId), view, kolkata)

        assertEquals(mapOf(key to ReviewReason.INSUFFICIENT_CONTEXT), second.reviewReasons)
        assertEquals(PatternReview.REJECTED, second.reviews.getValue(key))
        val stored = patterns.stored(CaseId(caseId), view, kolkata).patterns
        assertEquals(ReviewReason.INSUFFICIENT_CONTEXT, stored.single { it.id == id }.reviewReason)

        vault.patterns.review(id, PatternReviewAction.REJECT)
        assertTrue(patterns.refresh(CaseId(caseId), view, kolkata).reviewReasons.isEmpty())
        assertNull(patterns.stored(CaseId(caseId), view, kolkata).patterns.single { it.id == id }.reviewReason)

        vault.patterns.review(id, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        vault.patterns.review(id, PatternReviewAction.ACCEPT)
        assertTrue(patterns.refresh(CaseId(caseId), view, kolkata).reviewReasons.isEmpty())
    }

    @Test
    fun storedShowsOutOfDateDescriptionsWithoutRecomputing() = runBlocking<Unit> {
        import()
        assertEquals(emptyList(), patterns.stored(CaseId(caseId), view, kolkata).patterns)
        val refreshed = patterns.refresh(CaseId(caseId), view, kolkata)

        val current = patterns.stored(CaseId(caseId), view, kolkata)
        assertFalse(current.anyStale)
        assertEquals(refreshed.storedIds.values.toSet(), current.patterns.map { it.id }.toSet())
        assertEquals(current.patterns.map { it.id }.toSet(), current.explanations.keys)
        assertEquals(refreshed.explanations.values.map { it.observed }.toSet(), current.explanations.values.map { it.observed }.toSet())

        vault.events.addCoverageGap(CaseId(caseId), Timestamp("2026-09-01T00:00:00Z"), Timestamp("2026-09-02T00:00:00Z"), "import_selection")
        val stale = patterns.stored(CaseId(caseId), view, kolkata)
        assertTrue(stale.anyStale)
        assertEquals(current.patterns.map { it.id }, stale.patterns.map { it.id })

        patterns.refresh(CaseId(caseId), view, kolkata)
        assertFalse(patterns.stored(CaseId(caseId), view, kolkata).anyStale)
    }

    @Test
    fun twoCasesNeverSeeEachOthersStoredPatterns() = runBlocking<Unit> {
        import()
        val other = vault.cases.create("synthetic-other-case").id
        val mine = patterns.refresh(CaseId(caseId), view, kolkata)

        assertEquals(emptyList(), patterns.stored(CaseId(other), view, kolkata).patterns)
        val theirs = patterns.refresh(CaseId(other), view, kolkata)
        assertTrue(theirs.storedIds.isEmpty())
        assertEquals(mine.storedIds.values.toSet(), vault.patterns.list(CaseId(caseId), view).map { it.id }.toSet())
    }
}
