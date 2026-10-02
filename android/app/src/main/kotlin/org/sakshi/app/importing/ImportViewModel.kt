package org.sakshi.app.importing

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sakshi.acquisition.importer.EvidenceImporter
import org.sakshi.acquisition.importer.ImportFailure
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.ImportReport
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.acquisition.importer.PickerReader
import org.sakshi.acquisition.importer.TextReader
import org.sakshi.app.SessionServices
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.CaseSummary

/**
 * State machine for one import: Idle, Previewing, Saving, then Finished or Cancelled. It lives in the activity's
 * view model store, which is cleared when the session locks, and that cancels a save in progress.
 */
class ImportViewModel(
    private val importer: EvidenceImporter,
    private val repository: CaseRepository,
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = mutableState.asStateFlow()

    /** Every case, so the screen can show titles and offer the active ones. */
    val cases: StateFlow<List<CaseSummary>> =
        repository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var saveJob: Job? = null

    /** Starts from a share. The case is chosen on the preview screen; a single active case is preselected. */
    fun startShare(batch: PendingBatch) {
        viewModelScope.launch {
            val active = repository.observe().first().active()
            val choice = active.singleOrNull()?.let { CaseChoice.Existing(it.id) } ?: CaseChoice.None
            preview(batch, fixedCaseId = null, choice = choice)
        }
    }

    /** Starts from inside a case, where the destination is fixed. */
    fun startForCase(caseId: String, batch: PendingBatch) = preview(batch, caseId, CaseChoice.None)

    fun startPicked(caseId: String, uris: List<Uri>, mechanism: ImportMechanism) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val batch = withContext(io) { PickerReader.fromPickedUris(uris, mechanism, resolver) }
            preview(batch, caseId, CaseChoice.None)
        }
    }

    fun startPasted(caseId: String, text: String) = startForCase(caseId, TextReader.fromPastedText(text))

    private fun preview(batch: PendingBatch, fixedCaseId: String?, choice: CaseChoice) {
        saveJob?.cancel()
        val valid = batch.items.filter { it !is PendingItem.Rejected }.map { it.index }.toSet()
        mutableState.value = ImportUiState.Previewing(batch, valid, fixedCaseId, choice)
    }

    fun toggle(index: Int) = update { current ->
        val selectable = current.batch.items.any { it.index == index && it !is PendingItem.Rejected }
        if (!selectable) return@update current
        val next = if (index in current.selected) current.selected - index else current.selected + index
        ImportUiState.Previewing(current.batch, next, current.fixedCaseId, current.choice)
    }

    fun chooseCase(caseId: String) = choose(CaseChoice.Existing(caseId))

    fun chooseNewCase(title: String = "") = choose(CaseChoice.New(title.take(CaseRepository.MAX_TITLE_LENGTH)))

    private fun choose(choice: CaseChoice) = update { current ->
        ImportUiState.Previewing(current.batch, current.selected, current.fixedCaseId, choice)
    }

    private inline fun update(change: (ImportUiState.Previewing) -> ImportUiState.Previewing) {
        val current = mutableState.value as? ImportUiState.Previewing ?: return
        mutableState.value = change(current)
    }

    /** Drops the batch without saving anything, or leaves a finished or cancelled result. */
    fun dismiss() {
        saveJob?.cancel()
        mutableState.value = ImportUiState.Idle
    }

    fun cancelSaving() {
        saveJob?.cancel()
    }

    fun save() {
        val current = mutableState.value as? ImportUiState.Previewing ?: return
        if (!current.canSave) return
        val indexes = current.selected.sorted()
        mutableState.value = ImportUiState.Saving(0, indexes.size)
        saveJob = viewModelScope.launch { run(current, indexes) }
    }

    private suspend fun run(preview: ImportUiState.Previewing, indexes: List<Int>) {
        val labels = labelsFor(preview.batch, indexes)
        val outcomes = ArrayList<ItemOutcome>(indexes.size)
        var caseId: String? = null
        try {
            caseId = resolveCase(preview.effectiveChoice)
            if (caseId == null) {
                val failed = indexes.map { ItemOutcome.Failed(it, ImportFailure.CASE_UNAVAILABLE) }
                mutableState.value = ImportUiState.Finished(null, ImportReport(failed), labels)
                return
            }
            // One item per call so that a cancel keeps the outcomes of the items already saved.
            for ((position, index) in indexes.withIndex()) {
                outcomes += importer.commit(caseId, preview.batch, setOf(index)).outcomes
                mutableState.value = ImportUiState.Saving(position + 1, indexes.size)
            }
            mutableState.value = ImportUiState.Finished(caseId, ImportReport(outcomes), labels)
        } catch (cancelled: CancellationException) {
            mutableState.update {
                if (it is ImportUiState.Saving) ImportUiState.Cancelled(caseId, ImportReport(outcomes), labels) else it
            }
            throw cancelled
        }
    }

    private suspend fun resolveCase(choice: CaseChoice): String? = when (choice) {
        CaseChoice.None -> null
        is CaseChoice.Existing -> choice.caseId
        is CaseChoice.New -> try {
            repository.create(choice.title.trim()).id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun labelsFor(batch: PendingBatch, indexes: List<Int>): Map<Int, ItemLabel> =
        batch.items.filter { it.index in indexes }.associate { item ->
            item.index to when (item) {
                is PendingItem.Stream -> ItemLabel.File(item.displayNameClaim)
                is PendingItem.Text ->
                    if (batch.mechanism == ImportMechanism.PASTE) ItemLabel.PastedText else ItemLabel.SharedText
                is PendingItem.Rejected -> ItemLabel.Unknown
            }
        }

    companion object {
        fun factory(services: SessionServices): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImportViewModel(services.importer, services.vault.cases, services.resolver, services.io)
            }
        }
    }
}
