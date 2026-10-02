package org.sakshi.app.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.support.TEST_ZONE
import org.sakshi.core.model.CaseId
import org.sakshi.core.temporal.GapReason

class TimelineViewModelTest : AnalysisTestBase() {
    private fun modelFor(caseId: String) = TimelineViewModel(caseId, vault, TEST_ZONE, scope)

    @Test
    fun anEmptyCaseLoadsWithNoRows() {
        val state = await(modelFor(newCase()).uiState) { it.loaded }
        assertEquals(0, state.view.total)
        assertTrue(state.view.items.isEmpty())
    }

    @Test
    fun analysedMessagesAppearWithTheirBodiesAndCounts() {
        val caseId = newCase()
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        val state = await(modelFor(caseId).uiState) { it.loaded }
        assertEquals(8, state.view.total)
        assertEquals(2, state.view.needsReview)
        val bodies = state.view.items.filterIsInstance<TimelineItem.EventItem>().map { (it.row.body as? BodyView.Message)?.full }
        assertTrue("hello there" in bodies)
        assertTrue(bodies.contains("you are an idiot\nand this line continues\non a third line"))
    }

    @Test
    fun theFilterChangesTheRowsButNotTheCounts() {
        val caseId = newCase()
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        val model = modelFor(caseId)
        await(model.uiState) { it.loaded }
        model.setFilter(TimelineFilter.NEEDS_REVIEW)
        val state = await(model.uiState) { it.filter == TimelineFilter.NEEDS_REVIEW }
        assertEquals(2, state.view.items.count { it is TimelineItem.EventItem })
        assertEquals(8, state.view.total)
    }

    @Test
    fun aSavedGapAppearsInTheList() {
        val caseId = newCase()
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        val model = modelFor(caseId)
        await(model.uiState) { it.loaded }
        model.addGap("2026-09-25 14:00", "2026-09-25 22:00", GapReason.IMPORT_SELECTION)
        assertEquals(GapOutcome.Saved, await(model.gapOutcome) { it == GapOutcome.Saved })
        await(model.uiState) { state -> state.view.items.any { it is TimelineItem.GapItem } }
        val stored = runBlocking { vault.events.coverageGaps(CaseId(caseId)) }
        assertEquals(listOf("import_selection"), stored.map { it.reason })
    }

    @Test
    fun anInvalidGapIsNotSavedAndSaysWhy() {
        val caseId = newCase()
        val model = modelFor(caseId)
        model.addGap("tomorrow", "", GapReason.UNKNOWN)
        assertEquals(GapOutcome.Problem(GapProblem.START_FORMAT), model.gapOutcome.value)
        model.gapOutcomeShown()
        assertEquals(GapOutcome.None, model.gapOutcome.value)
        assertTrue(runBlocking { vault.events.coverageGaps(CaseId(caseId)) }.isEmpty())
    }

    @Test
    fun reviewChangesReachTheTimeline() {
        val caseId = newCase()
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        val model = modelFor(caseId)
        await(model.uiState) { it.loaded }
        val insult = events(caseId).first { it.categories.isNotEmpty() }
        runBlocking { vault.review.reviewCategory(insult.eventId, 0, org.sakshi.core.model.CategoryReviewStatus.ACCEPTED) }
        val state = await(model.uiState) { it.view.needsReview == 1 }
        val row = state.view.items.filterIsInstance<TimelineItem.EventItem>().first { it.row.eventId == insult.eventId.value }.row
        assertEquals(TagKind.ACCEPTED, assertIs<TagLine>(row.tags.first()).kind)
    }
}
