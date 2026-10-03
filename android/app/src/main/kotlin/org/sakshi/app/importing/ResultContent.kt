package org.sakshi.app.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import org.sakshi.acquisition.importer.AnalysisState
import org.sakshi.acquisition.importer.ImportReport
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.app.R
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.theme.Spacing

/** Per-item result of a save, finished or cancelled. Every outcome is a chip with a word, never colour alone. */
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
                    icon = Icons.Default.Check,
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val title = if (cancelled) R.string.result_cancelled_title else R.string.result_finished_title
                    ScreenTitle(stringResource(title))
                    val savedCount = report.outcomes.count { it is ItemOutcome.Saved }
                    if (report.outcomes.isEmpty() || savedCount == 0) {
                        SupportingText(stringResource(R.string.result_nothing_saved))
                    } else {
                        SupportingText(pluralStringResource(R.plurals.result_summary, report.outcomes.size, savedCount, report.outcomes.size))
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
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md).semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            val (icon, tone) = when (outcome) {
                is ItemOutcome.Saved -> Icons.Default.Check to Tone.Success
                is ItemOutcome.Skipped -> Icons.Default.Warning to Tone.Warning
                is ItemOutcome.Failed -> Icons.Default.Warning to Tone.Critical
            }
            IconTile(icon, tone = tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                when (outcome) {
                    is ItemOutcome.Saved -> SavedDetails(outcome, label, onAnalyse)
                    is ItemOutcome.Skipped -> NotSaved(rejectionText(outcome.reason), Tone.Warning)
                    is ItemOutcome.Failed -> NotSaved(failureText(outcome.reason), Tone.Critical)
                }
            }
        }
    }
}

@Composable
private fun SavedDetails(outcome: ItemOutcome.Saved, label: ItemLabel, onAnalyse: (String) -> Unit) {
    val next = when (outcome.analysisState) {
        AnalysisState.READY_FOR_TEXT_ANALYSIS -> R.string.result_saved_waiting
        AnalysisState.PRESERVED_NOT_ANALYSED -> R.string.result_saved_kept
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatusChip(stringResource(R.string.result_saved), tone = Tone.Success)
        if (outcome.duplicateOf.isNotEmpty()) StatusChip(stringResource(R.string.result_duplicate), tone = Tone.Warning)
        StatusChip(stringResource(next))
    }
    if (isAnalysableText(outcome, label)) {
        SecondaryButton(stringResource(R.string.result_analyse_now), { onAnalyse(outcome.evidenceId) })
    }
}

/** The chip says what happened; the reason says why, in one sentence. */
@Composable
private fun NotSaved(reason: String, tone: Tone) {
    StatusChip(stringResource(R.string.result_not_saved_chip), tone = tone)
    SupportingText(reason)
}

/**
 * A saved item that is waiting for text analysis: shared or pasted text, a file that is declared as text, or an image
 * whose bytes are JPEG, PNG or WebP, which on-device text recognition reads.
 */
internal fun isAnalysableText(outcome: ItemOutcome.Saved, label: ItemLabel): Boolean {
    if (outcome.analysisState != AnalysisState.READY_FOR_TEXT_ANALYSIS) return false
    return when (label) {
        ItemLabel.SharedText, ItemLabel.PastedText -> true
        is ItemLabel.File -> outcome.detectedMime in RECOGNISED_IMAGE_TYPES ||
            (outcome.detectedMime == null && outcome.declaredMime?.trim()?.lowercase()?.startsWith("text/") == true)
        ItemLabel.Unknown -> false
    }
}

private val RECOGNISED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
