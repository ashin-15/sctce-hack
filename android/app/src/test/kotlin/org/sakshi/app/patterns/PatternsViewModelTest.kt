package org.sakshi.app.patterns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.analysis.AnalysisUiState
import org.sakshi.app.analysis.AnalysisViewModel
import org.sakshi.app.analysis.OwnerChoice
import org.sakshi.app.people.WhoIsWhoViewModel
import org.sakshi.app.review.EventReviewViewModel
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.support.FIXED_INSTANT
import org.sakshi.app.support.TEST_ZONE
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.Event
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternType
import org.sakshi.processing.analysis.CasePatterns

class PatternsViewModelTest : AnalysisTestBase() {
    private lateinit var caseId: String

    private fun modelFor() = PatternsViewModel(caseId, vault, CasePatterns(vault, { FIXED_INSTANT }), TEST_ZONE, scope)

    private fun settled(model: PatternsViewModel, view: EvidenceView = EvidenceView.CONFIRMED_ONLY): PatternsUiState {
        model.recompute()
        return await(model.state) { !it.loading && it.view == view }
    }

    /** The whole path a person takes, through the view models: analyse, name the sender, mark the boundary and the contacts. */
    private fun reviewTheFixture() {
        caseId = newCase()
        val evidenceId = importText(caseId, SyntheticChats.STOP_THEN_SIX)
        val analyser = AnalysisViewModel(evidenceId, analysis::analyse, { TEST_ZONE }, scope)
        analyser.analyse()
        await(analyser.state) { it is AnalysisUiState.Questions }
        analyser.chooseOwner(OwnerChoice.Sender(SyntheticChats.OWNER))
        analyser.continueWithAnswers()
        await(analyser.state) { it is AnalysisUiState.Done }

        val people = WhoIsWhoViewModel(caseId, null, vault, scope)
        val claim = await(people.state) { it.claims.any { c -> c.selector.displayLabel == SyntheticChats.OTHER } }
            .claims.first { it.selector.displayLabel == SyntheticChats.OTHER }
        people.assignToNewPerson(claim.selector, "synthetic-person")
        val actor = await(people.state) { it.assignments.isNotEmpty() }.people.single().id

        val all = events(caseId)
        val stop = all.first { it.sender.displayLabel == SyntheticChats.OWNER }
        val stopModel = EventReviewViewModel(caseId, stop.eventId.value, vault, scope)
        await(stopModel.state) { it.loaded && it.people.isNotEmpty() }
        stopModel.markBoundary(BoundaryMarker.DO_NOT_CONTACT, actor)
        await(stopModel.state) { it.event?.boundary?.marker == BoundaryMarker.DO_NOT_CONTACT }

        all.filter { it.sender.displayLabel == SyntheticChats.OTHER }.forEach { contact: Event ->
            val model = EventReviewViewModel(caseId, contact.eventId.value, vault, scope)
            await(model.state) { it.loaded }
            model.markWantedness(UnwantedContact.USER_MARKED_UNWANTED)
            await(model.state) { it.event?.boundary?.unwantedContact == UnwantedContact.USER_MARKED_UNWANTED }
        }
    }

    @Test
    fun aReviewedStopMessageFollowedBySixContactsGivesAContactAfterABoundaryCard() {
        reviewTheFixture()
        val state = settled(modelFor())
        val card = state.cards.single { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }
        assertEquals("Contact after a boundary", card.title.resolve(context.resources))
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, card.status)
        assertEquals("Description supported by the selected records", card.statusText.resolve(context.resources))
        assertTrue(card.observed.contains("6"), card.observed)
        assertTrue(card.observed.contains("synthetic-person"), card.observed)
        assertTrue(card.limitations.isNotEmpty())
        assertEquals(7, card.support.size)
        assertTrue(card.support.all { it.snippet != null })
        assertTrue(card.support.any { it.snippet == "please stop messaging me" })
    }

    @Test
    fun theInterpretationIsOnlyWhatTheEngineSays() {
        reviewTheFixture()
        val card = settled(modelFor()).cards.single { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }
        assertEquals("This may indicate repeated unwanted contact after that boundary.", card.interpretation)
        assertEquals(
            "One way to read this: This may indicate repeated unwanted contact after that boundary.",
            interpretationText(card.interpretation.orEmpty()).resolve(context.resources),
        )
    }

    @Test
    fun theViewToggleChangesTheEngineView() {
        reviewTheFixture()
        val model = modelFor()
        assertEquals(EvidenceView.CONFIRMED_ONLY, settled(model).view)
        model.setView(EvidenceView.CANDIDATE_PREVIEW)
        val preview = await(model.state) { !it.loading && it.view == EvidenceView.CANDIDATE_PREVIEW }
        assertTrue(preview.cards.isNotEmpty())
        model.setView(EvidenceView.CONFIRMED_ONLY)
        assertEquals(EvidenceView.CONFIRMED_ONLY, await(model.state) { !it.loading && it.view == EvidenceView.CONFIRMED_ONLY }.view)
    }

    @Test
    fun withoutEventsTheEmptyStateShows() {
        caseId = newCase()
        val state = settled(modelFor())
        assertTrue(state.cards.isEmpty())
        assertTrue(!state.failed)
    }

    @Test
    fun anUnreviewedExportGivesNoBoundaryCard() {
        caseId = newCase()
        analyseExport(importText(caseId, SyntheticChats.STOP_THEN_SIX))
        val state = settled(modelFor())
        assertTrue(state.cards.none { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY })
    }

    @Test
    fun recomputingPicksUpAReviewChange() {
        reviewTheFixture()
        val model = modelFor()
        assertEquals(1, settled(model).cards.count { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY })
        val contact = events(caseId).first { it.sender.displayLabel == SyntheticChats.OTHER }
        runBlocking { vault.review.setDirection(listOf(contact.eventId), org.sakshi.core.model.Direction.OUTGOING) }
        val card = settled(model).cards.single { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }
        assertTrue(card.observed.contains("5"), card.observed)
    }
}
