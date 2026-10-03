package org.sakshi.app.patterns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.PatternReviewAction
import org.sakshi.core.vault.ReviewReason
import org.sakshi.processing.analysis.CasePatterns

class PatternsViewModelTest : AnalysisTestBase() {
    private lateinit var caseId: String

    private val realRefresh: PatternRefresh = { id, view, zone ->
        CasePatterns(vault, { FIXED_INSTANT }).refresh(id, view, zone, withSupportingEvents = true)
    }

    private fun modelFor(refresh: PatternRefresh = realRefresh) = PatternsViewModel(caseId, vault, refresh, TEST_ZONE, scope)

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

    private fun boundaryCard(state: PatternsUiState): PatternCardView = state.cards.single { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }

    private fun storedBoundary(view: EvidenceView = EvidenceView.CONFIRMED_ONLY) =
        runBlocking { vault.patterns.list(CaseId(caseId), view) }.single { it.record.type == PatternType.RECURRENCE_AFTER_BOUNDARY }

    private fun answered(model: PatternsViewModel, review: PatternReview): PatternCardView =
        boundaryCard(await(model.state) { s -> s.cards.any { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY && it.review == review } })

    /** A correction the person could make elsewhere: one contact is really outgoing, so the description changes. */
    private fun correctOneContact() {
        val contact = events(caseId).first { it.sender.displayLabel == SyntheticChats.OTHER }
        runBlocking { vault.review.setDirection(listOf(contact.eventId), Direction.OUTGOING) }
    }

    @Test
    fun enteringStoresTheDescriptionsInTheVault() {
        reviewTheFixture()
        assertEquals(emptyList(), runBlocking { vault.patterns.list(CaseId(caseId), EvidenceView.CONFIRMED_ONLY) })
        val card = boundaryCard(settled(modelFor()))
        val stored = storedBoundary()
        assertEquals(stored.id, card.storedId)
        assertEquals(PatternReview.NOT_REVIEWED, card.review)
        assertTrue(card.reviewable)
    }

    @Test
    fun agreeingChangesTheCardAndIsStored() {
        reviewTheFixture()
        val model = modelFor()
        val key = boundaryCard(settled(model)).key
        model.answer(key, PatternReviewAction.ACCEPT)
        assertEquals(PatternReview.ACCEPTED, answered(model, PatternReview.ACCEPTED).review)
        assertEquals(PatternReview.ACCEPTED, storedBoundary().review)
    }

    @Test
    fun notSureIsStored() {
        reviewTheFixture()
        val model = modelFor()
        model.answer(boundaryCard(settled(model)).key, PatternReviewAction.MARK_UNKNOWN)
        answered(model, PatternReview.MARKED_UNKNOWN)
        assertEquals(PatternReview.MARKED_UNKNOWN, storedBoundary().review)
    }

    @Test
    fun rejectingWithAReasonKeepsTheReasonAndIsStored() {
        reviewTheFixture()
        val model = modelFor()
        model.answer(boundaryCard(settled(model)).key, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        val card = answered(model, PatternReview.REJECTED)
        assertEquals(ReviewReason.DUPLICATE, card.reviewReason)
        assertEquals("You said this does not match: Duplicate", patternReviewText(card.review, card.reviewReason).resolve(context.resources))
        assertEquals(PatternReview.REJECTED, storedBoundary().review)
    }

    @Test
    fun rejectingWithoutAReasonShowsNoReason() {
        reviewTheFixture()
        val model = modelFor()
        model.answer(boundaryCard(settled(model)).key, PatternReviewAction.REJECT)
        assertNull(answered(model, PatternReview.REJECTED).reviewReason)
    }

    @Test
    fun theReasonForRejectingIsStillShownByANewViewModelForTheSameCase() {
        reviewTheFixture()
        val first = modelFor()
        first.answer(boundaryCard(settled(first)).key, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        answered(first, PatternReview.REJECTED)

        val card = boundaryCard(settled(modelFor()))
        assertEquals(PatternReview.REJECTED, card.review)
        assertEquals(ReviewReason.DUPLICATE, card.reviewReason)
        assertEquals("You said this does not match: Duplicate", patternReviewText(card.review, card.reviewReason).resolve(context.resources))
    }

    @Test
    fun rejectingWithoutAReasonShowsThePlainLineInANewViewModel() {
        reviewTheFixture()
        val first = modelFor()
        first.answer(boundaryCard(settled(first)).key, PatternReviewAction.REJECT)
        answered(first, PatternReview.REJECTED)

        val card = boundaryCard(settled(modelFor()))
        assertEquals(PatternReview.REJECTED, card.review)
        assertNull(card.reviewReason)
        assertEquals("You said this does not match", patternReviewText(card.review, card.reviewReason).resolve(context.resources))
    }

    @Test
    fun acceptingAfterRejectingWithAReasonClearsTheReasonEvenInANewViewModel() {
        reviewTheFixture()
        val model = modelFor()
        val key = boundaryCard(settled(model)).key
        model.answer(key, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        answered(model, PatternReview.REJECTED)
        model.answer(key, PatternReviewAction.ACCEPT)
        assertNull(answered(model, PatternReview.ACCEPTED).reviewReason)

        val card = boundaryCard(settled(modelFor()))
        assertEquals(PatternReview.ACCEPTED, card.review)
        assertNull(card.reviewReason)
    }

    @Test
    fun undoingAnAnswerGoesBackToNotReviewed() {
        reviewTheFixture()
        val model = modelFor()
        val key = boundaryCard(settled(model)).key
        model.answer(key, PatternReviewAction.ACCEPT)
        answered(model, PatternReview.ACCEPTED)
        model.answer(key, PatternReviewAction.WITHDRAW)
        answered(model, PatternReview.NOT_REVIEWED)
        assertEquals(PatternReview.NOT_REVIEWED, storedBoundary().review)
    }

    @Test
    fun anIdenticalDescriptionKeepsItsAnswerWhenWorkedOutAgain() {
        reviewTheFixture()
        val model = modelFor()
        model.answer(boundaryCard(settled(model)).key, PatternReviewAction.ACCEPT)
        answered(model, PatternReview.ACCEPTED)
        assertEquals(PatternReview.ACCEPTED, boundaryCard(settled(model)).review)
    }

    @Test
    fun aCorrectionWhileTheScreenIsOpenWorksTheDescriptionOutAgainAndAsksAgain() {
        reviewTheFixture()
        val model = modelFor()
        val before = boundaryCard(settled(model))
        model.answer(before.key, PatternReviewAction.ACCEPT)
        answered(model, PatternReview.ACCEPTED)
        correctOneContact()
        val state = await(model.state) { s -> !s.refreshing && s.cards.any { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY && it.storedId != before.storedId } }
        val card = boundaryCard(state)
        assertTrue(card.observed.contains("5"), card.observed)
        assertEquals(PatternReview.NOT_REVIEWED, card.review)
        assertTrue(card.reviewable)
        assertEquals(storedBoundary().id, card.storedId)
    }

    @Test
    fun theCardsStayVisibleWhileTheyAreWorkedOutAgain() {
        reviewTheFixture()
        var calls = 0
        val model = modelFor { id, view, zone -> if (++calls == 2) awaitCancellation() else realRefresh(id, view, zone) }
        settled(model)
        correctOneContact()
        val state = await(model.state) { it.refreshing }
        assertTrue(state.cards.isNotEmpty())
        assertFalse(state.loading)
        assertFalse(state.failed)
    }

    @Test
    fun thePreviewOffersNoReviewAndRecordsNothing() {
        reviewTheFixture()
        val model = modelFor()
        settled(model)
        model.setView(EvidenceView.CANDIDATE_PREVIEW)
        val preview = await(model.state) { !it.loading && it.view == EvidenceView.CANDIDATE_PREVIEW }
        assertTrue(preview.cards.isNotEmpty())
        assertTrue(preview.cards.none { it.reviewable })
        model.answer(preview.cards.first().key, PatternReviewAction.ACCEPT)
        assertTrue(runBlocking { vault.patterns.list(CaseId(caseId), EvidenceView.CANDIDATE_PREVIEW) }.all { it.review == PatternReview.NOT_REVIEWED })
    }

    @Test
    fun onlyDescriptionsOfWhatWasObservedCanBeAnswered() {
        assertEquals(
            setOf(AssessmentStatus.SUPPORTED_DESCRIPTION, AssessmentStatus.CANDIDATE),
            AssessmentStatus.entries.filter { isReviewableStatus(it) }.toSet(),
        )
    }

    @Test
    fun anAnswerToADescriptionThatChangedIsNotRecordedAndTheDescriptionsAreWorkedOutAgain() {
        reviewTheFixture()
        var calls = 0
        val model = modelFor { id, view, zone -> if (++calls == 2) awaitCancellation() else realRefresh(id, view, zone) }
        val key = boundaryCard(settled(model)).key
        correctOneContact()
        await(model.state) { it.refreshing }
        model.answer(key, PatternReviewAction.ACCEPT)
        val state = await(model.state) { !it.refreshing && it.notice == PatternNotice.CHANGED_BEFORE_SAVE }
        assertEquals(PatternReview.NOT_REVIEWED, boundaryCard(state).review)
        assertTrue(runBlocking { vault.patterns.list(CaseId(caseId), EvidenceView.CONFIRMED_ONLY) }.all { it.review == PatternReview.NOT_REVIEWED })
        assertEquals("This description changed before your answer was saved. It has been worked out again.", patternNoticeText(PatternNotice.CHANGED_BEFORE_SAVE).resolve(context.resources))
    }

    @Test
    fun aReasonOutsideTheVocabularyIsRefusedWithANoticeAndNoCrash() {
        reviewTheFixture()
        val model = modelFor()
        val key = boundaryCard(settled(model)).key
        model.answer(key, PatternReviewAction.REJECT, "synthetic-not-a-reason")
        val state = await(model.state) { it.notice == PatternNotice.NOT_SAVED }
        assertEquals(PatternReview.NOT_REVIEWED, boundaryCard(state).review)
        assertNotNull(state.cards.firstOrNull())
    }

    @Test
    fun aFailedRefreshShowsTheFailureOnceAndDoesNotLoop() {
        reviewTheFixture()
        var calls = 0
        val model = modelFor { id, view, zone -> if (++calls >= 2) error("synthetic failure") else realRefresh(id, view, zone) }
        settled(model)
        correctOneContact()
        assertTrue(await(model.state) { it.failed }.cards.isEmpty())
        delay300()
        assertEquals(2, calls)
        model.recompute()
        await(model.state) { it.failed && !it.loading }
        assertEquals(3, calls, "entering the screen again tries once more")
    }

    private fun delay300() = runBlocking { delay(300) }
}
