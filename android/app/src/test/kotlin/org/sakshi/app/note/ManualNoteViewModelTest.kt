package org.sakshi.app.note

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.acquisition.importer.ContentAvailability
import org.sakshi.acquisition.importer.ManualNote
import org.sakshi.acquisition.importer.ManualNoteCodec
import org.sakshi.acquisition.importer.ViewOnceStatus
import org.sakshi.app.support.VaultTestBase
import org.sakshi.core.vault.AcquisitionKind

class ManualNoteViewModelTest : VaultTestBase() {
    private val caseId by lazy { newCase() }
    private val model by lazy { ManualNoteViewModel(caseId, importer, scope) }

    private fun storedNote(): ManualNote {
        val id = runBlocking { vault.evidence.observeForCase(caseId).first().single().id }
        val bytes = runBlocking { vault.evidence.openOriginal(id).use { it.inputStream().readBytes() } }
        return ManualNoteCodec.decode(bytes).note
    }

    @Test
    fun anEmptyOrBlankNoteIsRefusedAndNothingIsSaved() {
        model.save()
        assertEquals(NoteProblem.TEXT_REQUIRED, model.state.value.problem)
        model.setText("   \n ")
        model.save()
        assertEquals(NoteProblem.TEXT_REQUIRED, model.state.value.problem)
        assertEquals(0, evidenceCount(caseId))
    }

    @Test
    fun editingClearsTheProblem() {
        model.save()
        model.setText("synthetic")
        assertNull(model.state.value.problem)
    }

    @Test
    fun fieldsAreLimitedWhileTyping() {
        model.setText("a".repeat(ManualNote.MAX_TEXT_CHARS + 10))
        model.setIncidentTime("b".repeat(ManualNote.MAX_FIELD_CHARS + 10))
        assertEquals(ManualNote.MAX_TEXT_CHARS, model.state.value.text.length)
        assertEquals(ManualNote.MAX_FIELD_CHARS, model.state.value.incidentTime.length)
    }

    @Test
    fun viewOnceNeedsAChoiceOfWhatTheUserHolds() {
        model.setText("synthetic account")
        model.setViewOnce(true)
        model.save()
        assertEquals(NoteProblem.CHOOSE_AVAILABILITY, model.state.value.problem)
        assertEquals(0, evidenceCount(caseId))
    }

    @Test
    fun theViewOnceSwitchMapsToStatusAndAvailability() {
        model.setText("synthetic account")
        model.setViewOnce(true)
        model.setAvailability(ContentAvailability.CONTEXT_ONLY)
        model.save()
        await(model.state) { it.saved }
        val note = storedNote()
        assertEquals(ViewOnceStatus.USER_REPORTED, note.viewOnceStatus)
        assertEquals(ContentAvailability.CONTEXT_ONLY, note.contentAvailability)
    }

    @Test
    fun withTheSwitchOffBothAreNotApplicableWhateverWasChosenBefore() {
        model.setText("synthetic account")
        model.setViewOnce(true)
        model.setAvailability(ContentAvailability.NOT_ACQUIRED)
        model.setViewOnce(false)
        model.save()
        await(model.state) { it.saved }
        val note = storedNote()
        assertEquals(ViewOnceStatus.NOT_APPLICABLE, note.viewOnceStatus)
        assertEquals(ContentAvailability.NOT_APPLICABLE, note.contentAvailability)
    }

    @Test
    fun theNoteIsSavedAsYourOwnWordsInAManualNoteItem() {
        model.setText("  synthetic account  ")
        model.setIncidentTime("synthetic evening")
        model.setSender("synthetic sender")
        model.setApp("synthetic app")
        model.save()
        await(model.state) { it.saved }
        val kind = runBlocking { vault.evidence.observeForCase(caseId).first().single().acquisitionKind }
        assertEquals(AcquisitionKind.MANUAL_NOTE, kind)
        val note = storedNote()
        assertEquals("synthetic account", note.text)
        assertEquals("synthetic evening", note.incidentTimeText)
        assertEquals("synthetic sender", note.claimedSender)
        assertEquals("synthetic app", note.sourceAppClaim)
    }

    @Test
    fun theFormIsClearedAfterSaving() {
        model.setText("synthetic account")
        model.setSender("synthetic sender")
        model.save()
        val after = await(model.state) { it.saved }
        assertEquals(NoteFormState(saved = true), after)
        model.savedAcknowledged()
        assertFalse(model.state.value.saved)
        assertTrue(model.state.value.text.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsWhatWasTyped() {
        val archived = newCase("synthetic archived")
        runBlocking { vault.cases.archive(archived) }
        val failing = ManualNoteViewModel(archived, importer, scope)
        failing.setText("synthetic account")
        failing.save()
        val state = await(failing.state) { it.problem != null }
        assertEquals(NoteProblem.SAVE_FAILED, state.problem)
        assertEquals("synthetic account", state.text)
        assertFalse(state.saving)
    }
}
