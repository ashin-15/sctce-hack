package org.sakshi.app.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.sakshi.app.R
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.support.TEST_ZONE
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.Direction
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.AnalysisWarning
import org.sakshi.processing.analysis.NotAnalysableReason
import org.sakshi.processing.text.DateOrder

class AnalysisViewModelTest : AnalysisTestBase() {
    private fun modelFor(evidenceId: String, analyser: Analyser = analysis::analyse) =
        AnalysisViewModel(evidenceId, analyser, { TEST_ZONE }, scope)

    private fun questions(model: AnalysisViewModel): ExportQuestions =
        assertIs<AnalysisUiState.Questions>(await(model.state) { it is AnalysisUiState.Questions }).questions

    @Test
    fun plainTextGivesAnAnalysedStateWithCounts() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, "you are an Idiot and worthless, and I will hurt you"))
        model.analyse()
        val done = assertIs<AnalysisUiState.Done>(await(model.state) { it is AnalysisUiState.Done })
        assertEquals(1, done.result.eventCount)
        assertTrue(done.result.suggestionCount > 0)
        assertEquals(1, events(caseId).size)
    }

    @Test
    fun anExportAsksTheQuestionsBeforeWritingEvents() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        model.analyse()
        val asked = questions(model)
        assertEquals(setOf(SyntheticChats.OWNER, SyntheticChats.OTHER), asked.needs.senders.toSet())
        assertEquals(listOf("24/09/2026", "25/09/2026", "26/09/2026"), asked.needs.sampleDates)
        assertEquals(TEST_ZONE, asked.answers.zone)
        assertEquals(OwnerChoice.Unanswered, asked.answers.owner)
        assertTrue(events(caseId).isEmpty())
    }

    @Test
    fun continueIsDisabledUntilTheOwnerIsChosenWhenTheFileFixesTheOrder() {
        val model = modelFor(importText(newCase(), SyntheticChats.EIGHT_MESSAGES))
        model.analyse()
        val asked = questions(model)
        assertEquals(DateOrder.DAY_MONTH, asked.fixedDateOrder)
        assertFalse(asked.canContinue)
        model.chooseOwner(OwnerChoice.NoneOfThese)
        assertTrue(assertIs<AnalysisUiState.Questions>(model.state.value).questions.canContinue)
    }

    @Test
    fun continueNeedsADateOrderWhenTheFileAllowsBoth() {
        val model = modelFor(importText(newCase(), SyntheticChats.AMBIGUOUS))
        model.analyse()
        assertNull(questions(model).fixedDateOrder)
        model.chooseOwner(OwnerChoice.Sender(SyntheticChats.OWNER))
        assertFalse(assertIs<AnalysisUiState.Questions>(model.state.value).questions.canContinue)
        model.continueWithAnswers()
        assertIs<AnalysisUiState.Questions>(model.state.value)
        model.chooseDateOrder(DateOrder.MONTH_DAY)
        assertTrue(assertIs<AnalysisUiState.Questions>(model.state.value).questions.canContinue)
    }

    @Test
    fun theDetectedOrderIsFixedWhateverTheAnswerSays() {
        val model = modelFor(importText(newCase(), SyntheticChats.EIGHT_MESSAGES))
        model.analyse()
        questions(model)
        model.chooseDateOrder(DateOrder.MONTH_DAY)
        val after = assertIs<AnalysisUiState.Questions>(model.state.value).questions
        assertEquals(DateOrder.DAY_MONTH, after.effectiveDateOrder)
    }

    @Test
    fun answeringWritesEventsWithTheExpectedDirections() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        model.analyse()
        questions(model)
        model.chooseOwner(OwnerChoice.Sender(SyntheticChats.OWNER))
        model.continueWithAnswers()
        val done = assertIs<AnalysisUiState.Done>(await(model.state) { it is AnalysisUiState.Done })
        assertEquals(8, done.result.eventCount)
        val stored = events(caseId)
        assertEquals(2, stored.count { it.direction == Direction.OUTGOING })
        assertEquals(6, stored.count { it.direction == Direction.INCOMING })
    }

    @Test
    fun noneOfTheseLeavesTheDirectionUnknown() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        model.analyse()
        questions(model)
        model.chooseOwner(OwnerChoice.NoneOfThese)
        model.continueWithAnswers()
        await(model.state) { it is AnalysisUiState.Done }
        assertTrue(events(caseId).all { it.direction == Direction.UNKNOWN })
    }

    @Test
    fun theChosenZoneIsUsed() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        model.analyse()
        questions(model)
        model.chooseZone(java.time.ZoneId.of("UTC"))
        model.chooseOwner(OwnerChoice.NoneOfThese)
        model.continueWithAnswers()
        await(model.state) { it is AnalysisUiState.Done }
        assertTrue(events(caseId).all { it.timestamp.sourceTimezone == "UTC" })
    }

    @Test
    fun everyRefusalReasonHasItsOwnPlainSentence() {
        val texts = NotAnalysableReason.entries.map { refusalText(it).resolve(context.resources) }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(texts.all { it.isNotBlank() && it.endsWith(".") })
    }

    @Test
    fun aManualNoteIsRefusedWithItsReason() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, "synthetic note", AcquisitionKind.MANUAL_NOTE))
        model.analyse()
        val refused = assertIs<AnalysisUiState.Refused>(await(model.state) { it is AnalysisUiState.Refused })
        assertEquals(NotAnalysableReason.MANUAL_NOTE, refused.reason)
        assertTrue(events(caseId).isEmpty())
    }

    @Test
    fun cancelReturnsToIdleAndWritesNoEvents() {
        val caseId = newCase()
        val gate = CompletableDeferred<AnalysisOutcome>()
        val model = modelFor(importText(caseId, "synthetic text")) { _, _ -> gate.await() }
        model.analyse()
        assertEquals(AnalysisUiState.Running, model.state.value)
        model.cancel()
        assertEquals(AnalysisUiState.Idle, model.state.value)
        gate.complete(AnalysisOutcome.NotAnalysable(NotAnalysableReason.NOT_TEXT))
        assertEquals(AnalysisUiState.Idle, model.state.value)
        assertTrue(events(caseId).isEmpty())
    }

    @Test
    fun startOnceDoesNotRestartAfterACancel() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val gate = CompletableDeferred<AnalysisOutcome>()
        val model = modelFor("synthetic-evidence") { _, _ ->
            calls.incrementAndGet()
            gate.await()
        }
        model.startOnce()
        model.cancel()
        model.startOnce()
        assertEquals(1, calls.get())
        assertEquals(AnalysisUiState.Idle, model.state.value)
    }

    @Test
    fun aSecondAnalysisSaysItWasAlreadyDone() {
        val caseId = newCase()
        val model = modelFor(importText(caseId, "synthetic plain text"))
        model.analyse()
        await(model.state) { it is AnalysisUiState.Done }
        model.analyse()
        val refused = assertIs<AnalysisUiState.Refused>(await(model.state) { it is AnalysisUiState.Refused })
        assertEquals(NotAnalysableReason.ALREADY_ANALYSED, refused.reason)
        assertEquals(1, events(caseId).size)
    }

    @Test
    fun anUnexpectedFailureBecomesAFailedStateWithoutDetails() {
        val model = modelFor("synthetic-evidence") { _, _ -> throw IllegalStateException("synthetic detail that must not be shown") }
        model.analyse()
        assertEquals(AnalysisUiState.Failed, await(model.state) { it is AnalysisUiState.Failed })
    }

    @Test
    fun warningsAreSaidInWords() {
        val language = warningText(AnalysisWarning.UNSUPPORTED_LANGUAGE_PRESENT, 1).resolve(context.resources)
        assertEquals(
            "Some messages are in a language this version cannot analyse. They are kept, with no suggestions.",
            language,
        )
        val texts = AnalysisWarning.entries.map { warningText(it, 2).resolve(context.resources) }
        assertEquals(texts.size, texts.toSet().size)
        assertNotEquals(warningText(AnalysisWarning.UNRESOLVED_TIMES, 1).resolve(context.resources), warningText(AnalysisWarning.UNRESOLVED_TIMES, 3).resolve(context.resources))
        assertTrue(context.getString(R.string.analysis_done_no_cue).startsWith("No cue matched"))
    }

    @Test
    fun zoneChoicesFilterByWhatWasTyped() {
        assertTrue(ZoneChoices.all.contains("Asia/Kolkata"))
        assertEquals(ZoneChoices.all, ZoneChoices.filter(""))
        assertTrue(ZoneChoices.filter("kolk").contains("Asia/Kolkata"))
        assertTrue(ZoneChoices.filter("new york").contains("America/New_York"))
        assertTrue(ZoneChoices.filter("no such zone").isEmpty())
    }

    @Test
    fun theAnalysisOfAnEvidenceItemNeverTouchesTheOriginal() = runBlocking<Unit> {
        val caseId = newCase()
        val evidenceId = importText(caseId, SyntheticChats.EIGHT_MESSAGES)
        val before = vault.evidence.details(evidenceId)?.sha256
        val model = modelFor(evidenceId)
        model.analyse()
        questions(model)
        assertEquals(before, vault.evidence.details(evidenceId)?.sha256)
    }
}
