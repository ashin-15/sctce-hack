package org.sakshi.app.importing

import org.sakshi.acquisition.importer.ImportReport
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.CaseSummary

/** Where the selected items will be saved. */
sealed interface CaseChoice {
    data object None : CaseChoice

    data class Existing(val caseId: String) : CaseChoice

    /** A case that is created when the user presses save, not before. */
    data class New(val title: String) : CaseChoice
}

/** How an item is named in the result list. The name is what the sending app reported. */
sealed interface ItemLabel {
    data class File(val reportedName: String?) : ItemLabel

    data object SharedText : ItemLabel

    data object PastedText : ItemLabel

    data object Unknown : ItemLabel
}

sealed interface ImportUiState {
    data object Idle : ImportUiState

    /** Nothing has been saved in this state. [fixedCaseId] is set when the import started inside a case. */
    class Previewing(
        val batch: PendingBatch,
        val selected: Set<Int>,
        val fixedCaseId: String?,
        val choice: CaseChoice,
    ) : ImportUiState {
        val effectiveChoice: CaseChoice get() = fixedCaseId?.let { CaseChoice.Existing(it) } ?: choice

        val canSave: Boolean
            get() = selected.isNotEmpty() && when (val target = effectiveChoice) {
                CaseChoice.None -> false
                is CaseChoice.Existing -> true
                is CaseChoice.New -> target.title.trim().let { it.isNotEmpty() && it.length <= CaseRepository.MAX_TITLE_LENGTH }
            }
    }

    data class Saving(val done: Int, val total: Int) : ImportUiState

    /** [caseId] is null when the case to save into could not be created. */
    class Finished(val caseId: String?, val report: ImportReport, val labels: Map<Int, ItemLabel>) : ImportUiState

    /** [report] lists only the items that finished before the cancel; they remain saved. */
    class Cancelled(val caseId: String?, val report: ImportReport, val labels: Map<Int, ItemLabel>) : ImportUiState
}

fun List<CaseSummary>.active(): List<CaseSummary> = filter { it.status == CaseStatus.ACTIVE }
