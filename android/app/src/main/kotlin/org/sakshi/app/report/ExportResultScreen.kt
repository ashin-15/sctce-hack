package org.sakshi.app.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import org.sakshi.app.R
import org.sakshi.app.ui.components.DestructiveButton
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.MoreInfo
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.StatTile
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.Tone
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
        SupportingText(stringResource(R.string.export_caution_control))
        PrimaryButton(stringResource(R.string.export_share), actions.share, Modifier.fillMaxWidth(), icon = Icons.Default.Share)
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
        item(key = "file") { FileCard(export, formatByteSize(export.summary.zipBytes, locale)) }
        item(key = "stats") { Stats(export.summary) }
        item(key = "about") { About(export) }
    }
}

@Composable
private fun FileCard(export: ExportDone, size: String) {
    SakshiCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconTile(ImageVector.vectorResource(R.drawable.ic_document))
                Column(Modifier.weight(1f)) {
                    Text(export.file.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.export_file_line, export.reportVersion, size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(stringResource(R.string.export_chip_ready), tone = Tone.Success, icon = Icons.Default.Check)
            }
            Text(stringResource(R.string.export_key_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(KeyIdFormat.grouped(export.keyId), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Stats(summary: ExportSummary) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        StatTile(summary.eventCount.toString(), stringResource(R.string.report_stat_messages), tile)
        StatTile(summary.originalCount.toString(), stringResource(R.string.report_stat_originals), tile)
        StatTile(summary.pageCount.toString(), stringResource(R.string.export_stat_pages), tile)
    }
}

@Composable
private fun About(export: ExportDone) {
    val summary = export.summary
    MoreInfo(stringResource(R.string.export_about)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SupportingText(stringResource(R.string.export_caution_plain))
            SupportingText(stringResource(R.string.export_key_explanation))
            listOf(
                plural(R.plurals.export_inside_findings, summary.findingCount, summary.findingCount),
                plural(R.plurals.export_inside_patterns, summary.patternCount, summary.patternCount),
                plural(R.plurals.export_inside_left_out, summary.omitted.eventCount, summary.omitted.eventCount),
            ).forEach { SupportingText(it.text()) }
            listOf(R.string.export_check_readme, R.string.export_check_tool, R.string.export_check_compares, R.string.export_check_not_real)
                .forEach { SupportingText(stringResource(it)) }
            Text(
                ReportText.LIMITS_HEADING,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            ReportText.LIMITS.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}
