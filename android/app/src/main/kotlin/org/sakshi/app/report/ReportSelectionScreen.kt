package org.sakshi.app.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.analysis.ZonePicker
import org.sakshi.app.evidence.kindText
import org.sakshi.app.timeline.dateTimeText
import org.sakshi.app.timeline.senderLine
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.MoreInfo
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.SwitchRow
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.formatByteSize
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus

class ReportSelectionActions(
    val back: () -> Unit,
    val toggle: (String) -> Unit,
    val selectAll: () -> Unit,
    val selectNone: () -> Unit,
    val selectTagged: () -> Unit,
    val setUnreviewed: (Boolean) -> Unit,
    val toggleOriginal: (String) -> Unit,
    val setZone: (ZoneId) -> Unit,
    val preview: () -> Unit,
)

@Composable
fun ReportSelectionScreen(state: ReportUiState, actions: ReportSelectionActions, modifier: Modifier = Modifier) {
    var pickingZone by remember { mutableStateOf(false) }
    BackHandler { if (pickingZone) pickingZone = false else actions.back() }
    SakshiScaffold(
        title = stringResource(R.string.report_select_title),
        modifier = modifier,
        onBack = { if (pickingZone) pickingZone = false else actions.back() },
        bottomBar = if (state.loaded && !pickingZone) {
            { PreviewBar(state.canPreview, actions.preview) }
        } else {
            null
        },
    ) {
        when {
            !state.loaded -> Unit
            pickingZone -> ZonePicker(state.zone) {
                actions.setZone(it)
                pickingZone = false
            }
            else -> Content(state, actions, onChangeZone = { pickingZone = true })
        }
    }
}

@Composable
private fun PreviewBar(enabled: Boolean, onPreview: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SupportingText(stringResource(R.string.report_export_line))
        PrimaryButton(
            stringResource(R.string.report_preview_action),
            onPreview,
            Modifier.fillMaxWidth(),
            enabled = enabled,
            icon = ImageVector.vectorResource(R.drawable.ic_document),
        )
    }
}

@Composable
private fun Content(state: ReportUiState, actions: ReportSelectionActions, onChangeZone: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        state.earlierExport?.let { earlier ->
            item(key = "earlier") { StatusNote(NoteKind.Info, ExportHistoryMapping.notice(earlier, state.zone, locale).text()) }
        }
        if (state.rows.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.report_empty_title), stringResource(R.string.report_empty_body)) }
        } else {
            item(key = "count") { CountAndHelpers(state, actions) }
            items(state.rows, key = { it.eventId }) { row ->
                EventChoice(row, checked = row.eventId in state.selected, zone = state.zone, locale = locale) { actions.toggle(row.eventId) }
            }
        }
        item(key = "options-heading") { SectionHeader(stringResource(R.string.report_options_heading), Modifier.padding(top = Spacing.sm)) }
        item(key = "unreviewed") { SwitchCard(stringResource(R.string.report_unreviewed_switch), state.includeUnreviewed, actions.setUnreviewed) }
        items(state.originals, key = { "original-${it.evidenceId}" }) { original ->
            SwitchCard(
                stringResource(R.string.report_original_switch, kindText(original.kind), formatByteSize(original.byteSize, locale)),
                original.included,
            ) { actions.toggleOriginal(original.evidenceId) }
        }
        item(key = "zone") { ZoneRow(state.zone, onChangeZone) }
        item(key = "limits") { LimitsBlock(hasOriginals = state.originals.isNotEmpty()) }
    }
}

@Composable
private fun CountAndHelpers(state: ReportUiState, actions: ReportSelectionActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.report_messages_heading))
        Text(
            stringResource(R.string.report_selected_count, state.selected.size, state.selectableCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ShortcutChip(stringResource(R.string.report_select_all), actions.selectAll)
            ShortcutChip(stringResource(R.string.report_select_none), actions.selectNone)
            ShortcutChip(stringResource(R.string.report_select_tagged), actions.selectTagged)
        }
    }
}

@Composable
private fun ShortcutChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}

@Composable
private fun EventChoice(row: ReportEventRow, checked: Boolean, zone: ZoneId, locale: Locale, onToggle: () -> Unit) {
    SakshiCard(quiet = !row.selectable) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
                .toggleable(value = checked, enabled = row.selectable, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = row.selectable)
            Column(Modifier.padding(start = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    dateTimeText(row.row.time.label, zone, locale).text() + " · " + senderLine(row.row),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ReportRows.previewText(row.row.body).text(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                row.reason?.let { StatusChip(ReportRows.blockText(it).text(), tone = Tone.Warning) }
            }
        }
    }
}

private const val PREVIEW_LINES = 2

@Composable
private fun SwitchCard(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SakshiCard {
        SwitchRow(text, checked, onChange, Modifier.padding(horizontal = Spacing.md))
    }
}

@Composable
private fun ZoneRow(zone: ZoneId, onChange: () -> Unit) {
    SakshiCard {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.report_zone_current, zone.id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            ShortcutChip(stringResource(R.string.analysis_zone_change), onChange)
        }
    }
}

@Composable
private fun LimitsBlock(hasOriginals: Boolean) {
    MoreInfo(stringResource(R.string.report_limits_heading)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            EpistemicBlock(EpistemicStatus.UNKNOWN) {
                listOf(R.string.report_limit_saved, R.string.report_limit_sender, R.string.report_limit_real, R.string.report_limit_time).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SupportingText(stringResource(R.string.report_unreviewed_note))
            if (hasOriginals) SupportingText(stringResource(R.string.report_original_caution))
            SupportingText(stringResource(R.string.export_caution_control))
        }
    }
}
