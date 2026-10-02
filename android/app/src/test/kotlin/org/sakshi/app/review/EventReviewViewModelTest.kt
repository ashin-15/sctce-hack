package org.sakshi.app.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.slice
import org.sakshi.core.vault.ReviewProblem
import org.sakshi.core.vault.ReviewReason
import org.sakshi.core.vault.ReviewResult

class EventReviewViewModelTest : AnalysisTestBase() {
    private lateinit var caseId: String

    private fun analysedCase(text: String = SyntheticChats.EIGHT_MESSAGES): List<Event> {
        caseId = newCase()
        analyseExport(importText(caseId, text))
        return events(caseId)
    }

    private fun modelFor(event: Event, forCase: String = caseId) = EventReviewViewModel(forCase, event.eventId.value, vault, scope)

    private fun bodyOf(event: Event): String = runBlocking { org.sakshi.processing.analysis.EventText(vault).bodyOf(event).orEmpty() }

    private fun byBody(list: List<Event>, body: String): Event = list.first { bodyOf(it) == body }

    private fun insult(list: List<Event>) = list.first { it.categories.any { c -> c.label == CategoryLabel.VERBAL_ABUSE } }

    @Test
    fun theScreenLoadsTheBodyTheCuesAndTheCategories() {
        val event = insult(analysedCase())
        val state = await(modelFor(event).state) { it.loaded }
        assertEquals("you are an idiot\nand this line continues\non a third line", state.body)
        assertEquals(listOf("idiot"), state.marks.map { it.quote })
        val category = state.categories.single()
        assertEquals(listOf("idiot"), category.cues.map { it.quote })
        assertEquals(CategoryReviewStatus.UNREVIEWED, category.category.reviewStatus)
        assertFalse(state.boundaryOffered)
    }

    @Test
    fun agreeingChangesTheLatestRevisionAndAddsAHistoryEntry() {
        val event = insult(analysedCase())
        val model = modelFor(event)
        await(model.state) { it.loaded }
        model.agree(0)
        val state = await(model.state) { it.notice != null }
        assertEquals(CategoryReviewStatus.ACCEPTED, state.categories.single().category.reviewStatus)
        assertEquals(2, state.event?.revision)
        assertEquals(1, state.history.size)
        assertEquals(
            "You agreed with the suggestion: Insulting or degrading wording",
            state.history.single().text.resolve(context.resources),
        )
        assertEquals(NoteKind.Info, state.notice?.kind)
    }

    @Test
    fun disagreeingKeepsTheReasonAndSaysItDoesNotMakeTheMessageFine() {
        val event = insult(analysedCase())
        val model = modelFor(event)
        await(model.state) { it.loaded }
        model.disagree(0, ReviewReason.SIGNAL_ABSENT)
        val state = await(model.state) { it.notice != null }
        assertEquals(CategoryReviewStatus.REJECTED, state.categories.single().category.reviewStatus)
        val line = state.history.single()
        assertEquals("The words are not there", line.reason?.resolve(context.resources))
        assertEquals(
            "Recorded. Disagreeing with a suggestion does not label the message as fine.",
            state.notice?.text?.resolve(context.resources),
        )
        val stored = runBlocking { vault.review.decisions(org.sakshi.core.vault.DecisionTargets.category(event.eventId, 2, 0)) }
        assertEquals(listOf(ReviewReason.SIGNAL_ABSENT), stored.map { it.reasonCode })
    }

    @Test
    fun notSureMarksTheCategoryUncertain() {
        val event = insult(analysedCase())
        val model = modelFor(event)
        await(model.state) { it.loaded }
        model.notSure(0)
        val state = await(model.state) { it.notice != null }
        assertEquals(CategoryReviewStatus.UNCERTAIN, state.categories.single().category.reviewStatus)
        assertEquals(1, state.history.size)
    }

    @Test
    fun theSameDecisionTwiceSaysItWasAlreadyRecorded() {
        val event = insult(analysedCase())
        val model = modelFor(event)
        await(model.state) { it.loaded }
        model.agree(0)
        await(model.state) { it.notice != null }
        model.noticeShown()
        await(model.state) { it.notice == null }
        model.agree(0)
        val state = await(model.state) { it.notice != null }
        assertEquals("Already recorded.", state.notice?.text?.resolve(context.resources))
        assertEquals(2, state.event?.revision)
    }

    @Test
    fun anOwnTagIsAppliedToTheWholeBodyAndShownAsTheUsersOwn() {
        val event = byBody(analysedCase(), "hello there")
        val model = modelFor(event)
        await(model.state) { it.loaded }
        model.addOwnTag(CategoryLabel.CONTROLLING_REQUEST)
        val state = await(model.state) { it.categories.isNotEmpty() }
        val view = state.categories.single()
        assertTrue(view.isOwnTag)
        assertEquals(CategoryBasis.USER_TAG, view.category.basis)
        assertEquals(listOf(ReferenceId("body")), view.category.evidenceReferenceIds)
        assertEquals(CategoryReviewStatus.ACCEPTED, view.category.reviewStatus)
        assertTrue(view.cues.isEmpty())
        assertEquals("You added your own tag: Controlling demand", state.history.single().text.resolve(context.resources))
    }

    @Test
    fun theDirectionCanBeSet() {
        val event = byBody(analysedCase(), "hello there")
        val model = modelFor(event)
        await(model.state) { it.loaded }
        assertEquals(Direction.INCOMING, model.state.value.event?.direction)
        model.setDirection(Direction.OUTGOING)
        val state = await(model.state) { it.event?.direction == Direction.OUTGOING }
        assertTrue(state.boundaryOffered)
        assertEquals("You set the direction: from you", state.history.single().text.resolve(context.resources))
    }

    @Test
    fun wantednessCanBeMarkedAndCleared() {
        val event = byBody(analysedCase(), "hello there")
        val model = modelFor(event)
        await(model.state) { it.loaded }
        model.markWantedness(UnwantedContact.USER_MARKED_UNWANTED)
        await(model.state) { it.event?.boundary?.unwantedContact == UnwantedContact.USER_MARKED_UNWANTED }
        model.markWantedness(UnwantedContact.USER_MARKED_WANTED)
        await(model.state) { it.event?.boundary?.unwantedContact == UnwantedContact.USER_MARKED_WANTED }
        model.markWantedness(UnwantedContact.UNKNOWN)
        val state = await(model.state) { it.event?.boundary?.unwantedContact == UnwantedContact.UNKNOWN && it.history.size == 3 }
        assertEquals(
            listOf(
                "You marked this contact as unwanted.",
                "You marked this contact as wanted.",
                "You cleared the wanted or unwanted mark.",
            ),
            state.history.map { it.text.resolve(context.resources) },
        )
    }

    @Test
    fun boundaryActionsAreOnlyOfferedForOutgoingMessages() {
        val list = analysedCase()
        val incoming = modelFor(byBody(list, "hello there"))
        val outgoing = modelFor(byBody(list, "hi, please stop sending these"))
        assertFalse(await(incoming.state) { it.loaded }.boundaryOffered)
        assertTrue(await(outgoing.state) { it.loaded }.boundaryOffered)
    }

    @Test
    fun aBoundaryNeedsAConfirmedPerson() {
        val list = analysedCase()
        val model = modelFor(byBody(list, "hi, please stop sending these"))
        await(model.state) { it.loaded }
        model.markBoundary(BoundaryMarker.DO_NOT_CONTACT, ActorId("synthetic-nobody"))
        val state = await(model.state) { it.notice != null }
        assertEquals(NoteKind.Problem, state.notice?.kind)
        assertEquals("That person is not in this case.", state.notice?.text?.resolve(context.resources))
        assertEquals(BoundaryMarker.NONE, state.event?.boundary?.marker)
    }

    @Test
    fun aBoundaryOnAnIncomingMessageIsRefused() {
        val list = analysedCase()
        val actor = runBlocking {
            vault.actors.create(CaseId(caseId), "synthetic-person", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)
        }
        val model = modelFor(byBody(list, "hello there"))
        await(model.state) { it.loaded }
        model.markBoundary(BoundaryMarker.DO_NOT_CONTACT, actor)
        val state = await(model.state) { it.notice != null }
        assertEquals("Boundaries can only be set on your own messages.", state.notice?.text?.resolve(context.resources))
        assertEquals(BoundaryMarker.NONE, state.event?.boundary?.marker)
    }

    @Test
    fun aBoundaryOnAnOwnMessageForAConfirmedPersonIsRecorded() {
        val list = analysedCase()
        val actor = runBlocking {
            vault.actors.create(CaseId(caseId), "synthetic-person", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)
        }
        val model = modelFor(byBody(list, "hi, please stop sending these"))
        await(model.state) { it.loaded }
        assertEquals(listOf(actor), await(model.state) { it.people.isNotEmpty() }.people.map { it.id })
        model.markBoundary(BoundaryMarker.DO_NOT_CONTACT, actor)
        val state = await(model.state) { it.event?.boundary?.marker == BoundaryMarker.DO_NOT_CONTACT }
        assertEquals(actor, state.event?.boundary?.actorId)
        assertEquals("You marked this message as a boundary.", state.history.single().text.resolve(context.resources))
    }

    @Test
    fun anEventOfAnotherCaseIsNotFound() {
        val event = insult(analysedCase())
        val other = newCase()
        val state = await(modelFor(event, other).state) { it.loaded }
        assertNull(state.event)
    }

    @Test
    fun theSenderClaimIsOfferedForWhoIsWho() {
        val event = byBody(analysedCase(), "hello there")
        val claim = assertNotNull(await(modelFor(event).state) { it.loaded }.senderClaim)
        assertEquals(SyntheticChats.OTHER, claim.displayLabel)
        assertEquals(event.source.conversationScopeId, claim.conversationScopeId)
    }

    @Test
    fun everyReviewResultMapsToItsOwnMessage() {
        val resources = context.resources
        val applied = org.sakshi.app.ui.res(org.sakshi.app.R.string.review_done_tag)
        val ok = ReviewMessages.of(ReviewResult.Applied(emptyList()), applied)
        assertEquals(NoteKind.Info, ok.kind)
        assertEquals("Your tag was added.", ok.text.resolve(resources))
        assertEquals("Already recorded.", ReviewMessages.of(ReviewResult.NoChange, applied).text.resolve(resources))
        val notFound = ReviewMessages.of(ReviewResult.NotFound, applied)
        assertEquals(NoteKind.Problem, notFound.kind)
        val problems = ReviewProblem.entries.map { ReviewMessages.of(ReviewResult.Invalid(emptyList(), it), applied) }
        assertTrue(problems.all { it.kind == NoteKind.Problem && it.text.resolve(resources).isNotBlank() })
        assertTrue(ReviewMessages.of(ReviewResult.Invalid(emptyList(), null), applied).text.resolve(resources).isNotBlank())
        val all = listOf(notFound.text) + problems.map { it.text }
        assertTrue(all.map { it.resolve(resources) }.none { it.contains("Invalid", ignoreCase = true) || it.contains("code") })
    }

    @Test
    fun cueSpansMapToUtf16AfterAnEmojiAndMalayalam() {
        val prefix = "😀 ഇത് "
        val body = prefix + "you are an idiot"
        val cuePoints = CodePointSpan(body.codePointCount(0, prefix.length) + "you are an ".length, body.codePointCount(0, body.length))
        val shift = 40
        fun reference(id: String, start: Int, end: Int) = EvidenceReference(
            ReferenceId(id),
            org.sakshi.core.model.ArtifactId("synthetic-derivative"),
            null,
            org.sakshi.core.model.Representation.PRESERVED_IMPORT,
            Locator.Text(start + shift, end + shift),
        )
        val base = analysedCase().first()
        val event = base.copy(
            evidenceReferences = listOf(
                reference("body", 0, body.codePointCount(0, body.length)),
                reference("cue-1", cuePoints.start, cuePoints.end),
            ),
        )
        val marks = CueMarks.of(event, body)
        val mark = marks.single()
        assertEquals("idiot", mark.quote)
        assertEquals(body.length - "idiot".length, mark.start)
        assertEquals(body.length, mark.end)
        assertEquals("idiot", body.substring(mark.start, mark.end))
        assertEquals(mark.quote, cuePoints.slice(body))
        assertTrue(mark.start > body.codePointCount(0, body.length) - "idiot".length)
    }

    @Test
    fun cueMarksOfAnAnalysedMessageWithAnEmojiBeforeTheCueLandOnTheRightWord() {
        caseId = newCase()
        val text = "24/09/2026, 21:03 - ${SyntheticChats.OTHER}: 😀 hello you are an idiot\n24/09/2026, 21:04 - ${SyntheticChats.OWNER}: ok\n"
        analyseExport(importText(caseId, text))
        val event = events(caseId).first { it.categories.isNotEmpty() }
        val model = modelFor(event)
        val state = await(model.state) { it.loaded }
        val mark = state.marks.single()
        assertEquals("idiot", mark.quote)
        assertEquals("idiot", state.body?.substring(mark.start, mark.end))
    }

    @Test
    fun theReasonsAreTheFiveCodesInWords() {
        assertEquals(5, DISAGREE_REASONS.size)
        val words = DISAGREE_REASONS.map { reasonText(it).resolve(context.resources) }
        assertEquals(5, words.toSet().size)
        assertEquals("Wrong sender or quote", words.first())
        assertIs<String>(words.last())
    }
}
