package org.sakshi.app.importing

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.sakshi.core.vault.CaseSummary

/** What the import screens can ask for. */
class ImportActions(
    val toggle: (Int) -> Unit,
    val chooseCase: (String) -> Unit,
    val chooseNewCase: (String) -> Unit,
    val save: () -> Unit,
    val dismiss: () -> Unit,
    val cancelSaving: () -> Unit,
    /** Called with the case the import went into, or null if there was none. */
    val done: (String?) -> Unit,
    /** Called with the case and the saved item to read now. */
    val analyse: (caseId: String, evidenceId: String) -> Unit,
)

/** Shows the preview, the progress or the result, depending on [state]. */
@Composable
fun ImportScreen(state: ImportUiState, cases: List<CaseSummary>, actions: ImportActions, modifier: Modifier = Modifier) {
    when (state) {
        ImportUiState.Idle -> Unit
        is ImportUiState.Previewing -> {
            BackHandler(onBack = actions.dismiss)
            PreviewContent(state, cases, actions, modifier)
        }
        is ImportUiState.Saving -> {
            // Leaving mid-save would hide the Cancel button; the user cancels explicitly.
            BackHandler {}
            ProgressContent(state, actions.cancelSaving, modifier)
        }
        is ImportUiState.Finished -> {
            BackHandler { actions.done(state.caseId) }
            ResultContent(
                cancelled = false,
                caseId = state.caseId,
                report = state.report,
                labels = state.labels,
                onDone = actions.done,
                onAnalyse = { evidenceId -> state.caseId?.let { actions.analyse(it, evidenceId) } },
                modifier = modifier,
            )
        }
        is ImportUiState.Cancelled -> {
            BackHandler { actions.done(state.caseId) }
            ResultContent(
                cancelled = true,
                caseId = state.caseId,
                report = state.report,
                labels = state.labels,
                onDone = actions.done,
                onAnalyse = { evidenceId -> state.caseId?.let { actions.analyse(it, evidenceId) } },
                modifier = modifier,
            )
        }
    }
}
