package org.sakshi.app.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import org.sakshi.acquisition.importer.AnalysisState
import org.sakshi.acquisition.importer.ImportReport
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.app.R
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing

/** Per-item result of a save, finished or cancelled. Everything is stated as text. */
@Composable
fun ResultContent(
    cancelled: Boolean,
    caseId: String?,
    report: ImportReport,
    labels: Map<Int, ItemLabel>,
    onDone: (String?) -> Unit,
    onAnalyse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiScaffold(
        title = null,
        modifier = modifier,
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PrimaryButton(
                    stringResource(R.string.result_done),
                    { onDone(caseId) },
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val title = if (cancelled) R.string.result_cancelled_title else R.string.result_finished_title
                    ScreenTitle(stringResource(title))
                    if (cancelled) Text(stringResource(R.string.result_cancelled_body), style = MaterialTheme.typography.bodyLarge)
                    val savedCount = report.outcomes.count { it is ItemOutcome.Saved }
                    if (report.outcomes.isEmpty() || (cancelled && savedCount == 0)) {
                        Text(stringResource(R.string.result_nothing_saved), style = MaterialTheme.typography.bodyLarge)
                    } else if (!cancelled) {
                        Text(
                            pluralStringResource(R.plurals.result_summary, report.outcomes.size, savedCount, report.outcomes.size),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(report.outcomes, key = { "outcome-${it.index}" }) { outcome ->
                OutcomeRow(outcome, labels[outcome.index] ?: ItemLabel.Unknown, onAnalyse)
            }
        }
    }
}

@Composable
private fun OutcomeRow(outcome: ItemOutcome, label: ItemLabel, onAnalyse: (String) -> Unit) {
    val number = outcome.index + 1
    val title = when (label) {
        is ItemLabel.File ->
            label.reportedName?.let { stringResource(R.string.result_item_file, number, it) }
                ?: stringResource(R.string.result_item_file_no_name, number)
        ItemLabel.SharedText -> stringResource(R.string.result_item_shared_text, number)
        ItemLabel.PastedText -> stringResource(R.string.result_item_pasted_text, number)
        ItemLabel.Unknown -> stringResource(R.string.result_item_unknown, number)
    }
    SakshiCard {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md).semantics(mergeDescendants = true) { },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            when (outcome) {
                is ItemOutcome.Saved -> {
                    Text(stringResource(R.string.result_saved), style = MaterialTheme.typography.bodyLarge)
                    val next = when (outcome.analysisState) {
                        AnalysisState.READY_FOR_TEXT_ANALYSIS -> R.string.result_saved_waiting
                        AnalysisState.PRESERVED_NOT_ANALYSED -> R.string.result_saved_kept
                    }
                    SupportingText(stringResource(next))
                    if (outcome.duplicateOf.isNotEmpty()) StatusNote(NoteKind.Info, stringResource(R.string.result_duplicate))
                    if (isAnalysableText(outcome, label)) {
                        SecondaryButton(stringResource(R.string.result_analyse_now), { onAnalyse(outcome.evidenceId) }, Modifier.padding(top = Spacing.xs))
                    }
                }
                is ItemOutcome.Skipped ->
                    StatusNote(NoteKind.Caution, stringResource(R.string.result_not_saved, rejectionText(outcome.reason)))
                is ItemOutcome.Failed ->
                    StatusNote(NoteKind.Problem, stringResource(R.string.result_not_saved, failureText(outcome.reason)))
            }
        }
    }
}

/** A saved text item that is waiting for text analysis: shared or pasted text, or a file that is declared as text. */
internal fun isAnalysableText(outcome: ItemOutcome.Saved, label: ItemLabel): Boolean {
    if (outcome.analysisState != AnalysisState.READY_FOR_TEXT_ANALYSIS) return false
    return when (label) {
        ItemLabel.SharedText, ItemLabel.PastedText -> true
        is ItemLabel.File -> outcome.detectedMime == null && outcome.declaredMime?.trim()?.lowercase()?.startsWith("text/") == true
        ItemLabel.Unknown -> false
    }
}
