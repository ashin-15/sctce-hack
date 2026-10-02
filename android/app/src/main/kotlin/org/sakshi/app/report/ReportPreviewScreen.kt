package org.sakshi.app.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.ProgressBlock
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.formatByteSize
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing

class ReportPreviewActions(
    val back: () -> Unit,
    val create: () -> Unit,
    val cancelExport: () -> Unit,
    val noticeShown: () -> Unit,
)

@Composable
fun ReportPreviewScreen(state: ReportUiState, actions: ReportPreviewActions, modifier: Modifier = Modifier) {
    val running = state.export as? ExportState.Running
    val leave = { if (running == null) actions.back() }
    BackHandler(onBack = leave)
    val ready = state.preview as? PreviewState.Ready
    SakshiScaffold(
        title = stringResource(R.string.report_preview_title),
        modifier = modifier,
        onBack = if (running == null) actions.back else null,
        bottomBar = if (ready != null && running == null) {
            { ActionBar(actions) }
        } else {
            null
        },
    ) {
        when {
            running != null -> RunningContent(running, actions)
            state.preview is PreviewState.Building -> Column(Modifier.padding(Spacing.gutter)) {
                ProgressBlock(0, 0, stringResource(R.string.report_building), actions.back, indeterminate = true)
            }
            state.preview is PreviewState.Refused -> Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StatusNote(NoteKind.Problem, state.preview.message.text())
                SecondaryButton(stringResource(R.string.report_change_selection), actions.back, Modifier.fillMaxWidth())
            }
            ready != null -> ReadyContent(ready, state.export, actions)
        }
    }
}

@Composable
private fun RunningContent(running: ExportState.Running, actions: ReportPreviewActions) {
    Column(Modifier.padding(Spacing.gutter)) {
        if (running.cancelling) {
            ProgressBlock(0, 0, stringResource(R.string.export_cancelling), {}, indeterminate = true, showCancel = false)
        } else {
            ProgressBlock(0, 0, stringResource(R.string.export_running), actions.cancelExport, indeterminate = true)
        }
    }
}

@Composable
private fun ActionBar(actions: ReportPreviewActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PrimaryButton(stringResource(R.string.report_create_export), actions.create)
        SecondaryButton(stringResource(R.string.report_change_selection), actions.back, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReadyContent(ready: PreviewState.Ready, export: ExportState, actions: ReportPreviewActions) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "intro") { SupportingText(stringResource(R.string.report_preview_intro)) }
        item(key = "notice") { ExportNotice(export, actions.noticeShown) }
        item(key = "summary") { SummaryCard(ready.summary, locale) }
        items(ready.rows, key = { it.key }) { row -> PreviewRowView(row) }
    }
}

@Composable
private fun ExportNotice(export: ExportState, onShown: () -> Unit) {
    val message = when (export) {
        is ExportState.Failed -> export.message.text()
        ExportState.Cancelled -> ReportMessages.cancelled.text()
        else -> return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatusNote(NoteKind.Problem, message)
        SecondaryButton(stringResource(R.string.export_notice_ok), onShown)
    }
}

@Composable
private fun SummaryCard(summary: PreviewSummary, locale: Locale) {
    SakshiCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionHeader(stringResource(R.string.report_summary_heading))
            Text(plural(R.plurals.report_summary_messages, summary.messages, summary.messages).text(), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (summary.originals == 0) {
                    stringResource(R.string.report_summary_no_originals)
                } else {
                    plural(R.plurals.report_summary_originals, summary.originals, summary.originals, formatByteSize(summary.originalBytes, locale)).text()
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(plural(R.plurals.report_summary_left_out, summary.leftOut, summary.leftOut).text(), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (summary.includesUnreviewed) R.string.report_summary_unreviewed_on else R.string.report_summary_unreviewed_off),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun PreviewRowView(row: PreviewRow) {
    when (row) {
        is PreviewRow.Heading -> if (row.large) {
            SectionHeader(row.text, Modifier.padding(top = Spacing.md))
        } else {
            Text(row.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
        }
        is PreviewRow.Lines -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            row.lines.forEach { line ->
                if (row.small) SupportingText(line) else Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is PreviewRow.Block -> EpistemicBlock(row.status) {
            row.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            row.quote?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            row.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
