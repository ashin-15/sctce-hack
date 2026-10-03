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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.analysis.ZonePicker
import org.sakshi.app.evidence.kindText
import org.sakshi.app.timeline.dateTimeText
import org.sakshi.app.timeline.senderLine
import org.sakshi.app.ui.components.CheckRow
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.SwitchRow
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
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.md)) {
        PrimaryButton(stringResource(R.string.report_preview_action), onPreview, enabled = enabled)
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
        item(key = "intro") { SupportingText(stringResource(R.string.report_select_intro)) }
        state.earlierExport?.let { earlier ->
            item(key = "earlier") { StatusNote(NoteKind.Info, ExportHistoryMapping.notice(earlier, state.zone, locale).text()) }
        }
        item(key = "messages-heading") { SectionHeader(stringResource(R.string.report_messages_heading), Modifier.padding(top = Spacing.sm)) }
        if (state.rows.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.report_empty_title), stringResource(R.string.report_empty_body)) }
        } else {
            item(key = "count") { CountAndHelpers(state, actions) }
            items(state.rows, key = { it.eventId }) { row ->
                EventChoice(row, checked = row.eventId in state.selected, zone = state.zone, locale = locale) { actions.toggle(row.eventId) }
            }
        }
        item(key = "unreviewed") { UnreviewedSection(state.includeUnreviewed, actions.setUnreviewed) }
        item(key = "originals-heading") { SectionHeader(stringResource(R.string.report_originals_heading), Modifier.padding(top = Spacing.md)) }
        if (state.originals.isEmpty()) {
            item(key = "originals-none") { SupportingText(stringResource(R.string.report_originals_none)) }
        } else {
            item(key = "originals-note") { SupportingText(stringResource(R.string.report_originals_note)) }
            items(state.originals, key = { "original-${it.evidenceId}" }) { original -> OriginalChoiceRow(original, locale) { actions.toggleOriginal(original.evidenceId) } }
        }
        item(key = "zone") { ZoneSection(state.zone, onChangeZone) }
        item(key = "limits") { LimitsBlock() }
    }
}

@Composable
private fun CountAndHelpers(state: ReportUiState, actions: ReportSelectionActions) {
    Column {
        SupportingText(stringResource(R.string.report_selected_count, state.selected.size, state.selectableCount))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            QuietTextButton(stringResource(R.string.report_select_all), actions.selectAll)
            QuietTextButton(stringResource(R.string.report_select_none), actions.selectNone)
        }
        QuietTextButton(stringResource(R.string.report_select_tagged), actions.selectTagged)
    }
}

@Composable
private fun EventChoice(row: ReportEventRow, checked: Boolean, zone: ZoneId, locale: Locale, onToggle: () -> Unit) {
    SakshiCard(quiet = !row.selectable) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
                .toggleable(value = checked, enabled = row.selectable, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = row.selectable)
            Column(Modifier.padding(start = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(dateTimeText(row.row.time.label, zone, locale).text(), style = MaterialTheme.typography.titleSmall)
                Text(senderLine(row.row), style = MaterialTheme.typography.bodyMedium)
                Text(ReportRows.previewText(row.row.body).text(), style = MaterialTheme.typography.bodyLarge)
                row.reason?.let { SupportingText(ReportRows.blockText(it).text()) }
            }
        }
    }
}

@Composable
private fun UnreviewedSection(include: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.report_unreviewed_heading))
        SwitchRow(stringResource(R.string.report_unreviewed_switch), include, onChange)
        SupportingText(stringResource(R.string.report_unreviewed_note))
    }
}

@Composable
private fun OriginalChoiceRow(original: OriginalChoice, locale: Locale, onToggle: () -> Unit) {
    SakshiCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CheckRow(stringResource(R.string.report_original_include), original.included, onToggle)
            SupportingText(stringResource(R.string.report_original_detail, kindText(original.kind), formatByteSize(original.byteSize, locale)))
            StatusNote(NoteKind.Caution, stringResource(R.string.report_original_caution))
        }
    }
}

@Composable
private fun ZoneSection(zone: ZoneId, onChange: () -> Unit) {
    Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.report_zone_heading))
        Text(stringResource(R.string.report_zone_current, zone.id), style = MaterialTheme.typography.bodyLarge)
        SecondaryButton(stringResource(R.string.analysis_zone_change), onChange)
    }
}

@Composable
private fun LimitsBlock() {
    Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.report_limits_heading))
        EpistemicBlock(EpistemicStatus.UNKNOWN) {
            listOf(R.string.report_limit_saved, R.string.report_limit_sender, R.string.report_limit_real, R.string.report_limit_time).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
