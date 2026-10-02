package org.sakshi.app.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sakshi.acquisition.importer.ContentAvailability
import org.sakshi.acquisition.importer.EvidenceImporter
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.acquisition.importer.ManualNote
import org.sakshi.acquisition.importer.Rejection
import org.sakshi.acquisition.importer.ViewOnceStatus

enum class NoteProblem { TEXT_REQUIRED, TEXT_TOO_LONG, CHOOSE_AVAILABILITY, SAVE_FAILED }

/**
 * What the user has typed. It lives in the view model, which is cleared when the session locks, and is never
 * written to saved instance state.
 */
data class NoteFormState(
    val text: String = "",
    val incidentTime: String = "",
    val sender: String = "",
    val app: String = "",
    val viewOnce: Boolean = false,
    val availability: ContentAvailability? = null,
    val problem: NoteProblem? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

class ManualNoteViewModel(
    private val caseId: String,
    private val importer: EvidenceImporter,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(NoteFormState())
    val state: StateFlow<NoteFormState> = mutableState.asStateFlow()

    fun setText(value: String) = edit { it.copy(text = value.take(ManualNote.MAX_TEXT_CHARS)) }

    fun setIncidentTime(value: String) = edit { it.copy(incidentTime = value.take(ManualNote.MAX_FIELD_CHARS)) }

    fun setSender(value: String) = edit { it.copy(sender = value.take(ManualNote.MAX_FIELD_CHARS)) }

    fun setApp(value: String) = edit { it.copy(app = value.take(ManualNote.MAX_FIELD_CHARS)) }

    fun setViewOnce(value: Boolean) = edit { it.copy(viewOnce = value) }

    fun setAvailability(value: ContentAvailability) = edit { it.copy(availability = value) }

    private fun edit(change: (NoteFormState) -> NoteFormState) {
        mutableState.update { if (it.saving) it else change(it).copy(problem = null) }
    }

    fun save() {
        val form = mutableState.value
        if (form.saving) return
        val problem = problemIn(form)
        if (problem != null) {
            mutableState.update { it.copy(problem = problem) }
            return
        }
        mutableState.update { it.copy(saving = true, problem = null) }
        viewModelScope.launch {
            val outcome = try {
                importer.commitNote(caseId, noteFrom(form))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            mutableState.value = when (outcome) {
                is ItemOutcome.Saved -> NoteFormState(saved = true)
                is ItemOutcome.Skipped -> form.copy(saving = false, problem = problemFor(outcome.reason))
                else -> form.copy(saving = false, problem = NoteProblem.SAVE_FAILED)
            }
        }
    }

    /** Called once the screen has left after a save. */
    fun savedAcknowledged() = mutableState.update { it.copy(saved = false) }

    private fun problemIn(form: NoteFormState): NoteProblem? = when {
        form.viewOnce && form.availability == null -> NoteProblem.CHOOSE_AVAILABILITY
        else -> noteFrom(form).problem()?.let(::problemFor)
    }

    private fun problemFor(reason: Rejection): NoteProblem =
        if (reason == Rejection.EMPTY_TEXT) NoteProblem.TEXT_REQUIRED else NoteProblem.TEXT_TOO_LONG

    private fun noteFrom(form: NoteFormState) = ManualNote(
        text = form.text,
        incidentTimeText = form.incidentTime,
        claimedSender = form.sender,
        sourceAppClaim = form.app,
        viewOnceStatus = if (form.viewOnce) ViewOnceStatus.USER_REPORTED else ViewOnceStatus.NOT_APPLICABLE,
        contentAvailability = if (form.viewOnce) form.availability ?: ContentAvailability.NOT_ACQUIRED else ContentAvailability.NOT_APPLICABLE,
    )

    companion object {
        fun factory(caseId: String, importer: EvidenceImporter): ViewModelProvider.Factory =
            viewModelFactory { initializer { ManualNoteViewModel(caseId, importer) } }
    }
}
