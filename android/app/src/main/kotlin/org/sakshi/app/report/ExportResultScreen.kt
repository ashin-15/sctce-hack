package org.sakshi.app.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import org.sakshi.app.R
import org.sakshi.app.ui.components.DestructiveButton
import org.sakshi.app.ui.components.LabelValue
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.formatByteSize
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.export.report.ExportSummary
import org.sakshi.export.report.ReportText

class ExportResultActions(
    /** Removes the export file and returns to the case. */
    val leave: () -> Unit,
    val share: () -> Unit,
)

@Composable
fun ExportResultScreen(export: ExportDone, actions: ExportResultActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.leave)
    SakshiScaffold(
        title = stringResource(R.string.export_result_title),
        modifier = modifier,
        onBack = actions.leave,
        bottomBar = { ActionBar(actions) },
    ) {
        Content(export)
    }
}

@Composable
private fun ActionBar(actions: ExportResultActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PrimaryButton(stringResource(R.string.export_share), actions.share)
        DestructiveButton(stringResource(R.string.export_discard), actions.leave, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Content(export: ExportDone) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "ready") { ScreenTitle(stringResource(R.string.export_ready)) }
        item(key = "size") { LabelValue(stringResource(R.string.export_file_size), formatByteSize(export.summary.zipBytes, locale)) }
        item(key = "key") { SigningKey(export.keyId) }
        item(key = "inside") { Inside(export.summary) }
        item(key = "cautions") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusNote(NoteKind.Caution, stringResource(R.string.export_caution_control))
                StatusNote(NoteKind.Caution, stringResource(R.string.export_caution_plain))
            }
        }
        item(key = "check") { HowToCheck() }
    }
}

@Composable
private fun SigningKey(keyId: String) {
    var explained by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.export_key_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(KeyIdFormat.grouped(keyId), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        QuietTextButton(stringResource(R.string.export_key_what), { explained = !explained })
        if (explained) SupportingText(stringResource(R.string.export_key_explanation))
    }
}

@Composable
private fun Inside(summary: ExportSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.export_inside_heading))
        listOf(
            plural(R.plurals.export_inside_events, summary.eventCount, summary.eventCount),
            plural(R.plurals.export_inside_findings, summary.findingCount, summary.findingCount),
            plural(R.plurals.export_inside_patterns, summary.patternCount, summary.patternCount),
            plural(R.plurals.export_inside_originals, summary.originalCount, summary.originalCount),
            plural(R.plurals.export_inside_pages, summary.pageCount, summary.pageCount),
            plural(R.plurals.export_inside_left_out, summary.omitted.eventCount, summary.omitted.eventCount),
        ).forEach { Text(it.text(), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun HowToCheck() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.export_check_heading))
        listOf(R.string.export_check_readme, R.string.export_check_tool, R.string.export_check_compares, R.string.export_check_not_real)
            .forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
        Text(
            ReportText.LIMITS_HEADING,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        ReportText.LIMITS.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }
    }
}
