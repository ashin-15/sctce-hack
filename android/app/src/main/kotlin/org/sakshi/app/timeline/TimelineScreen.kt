package org.sakshi.app.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.review.categoryLabelText
import org.sakshi.app.review.directionText
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.components.ChoiceButton
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.EpistemicLabel
import org.sakshi.app.ui.components.FormDialog
import org.sakshi.app.ui.components.MenuAction
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.OverflowMenuButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SakshiTextField
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.TopAction
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.res
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.temporal.GapReason

class TimelineActions(
    val back: () -> Unit,
    val openEvent: (String) -> Unit,
    val openPatterns: () -> Unit,
    val openWhoIsWho: () -> Unit,
    val setFilter: (TimelineFilter) -> Unit,
    val addGap: (String, String, GapReason) -> Unit,
    val gapOutcomeShown: () -> Unit,
)

@Composable
fun TimelineScreen(state: TimelineUiState, gapOutcome: GapOutcome, actions: TimelineActions, modifier: Modifier = Modifier) {
    var addingGap by remember { mutableStateOf(false) }
    BackHandler(onBack = actions.back)
    LaunchedEffect(gapOutcome) {
        if (gapOutcome == GapOutcome.Saved) {
            addingGap = false
            actions.gapOutcomeShown()
        }
    }
    SakshiScaffold(
        title = stringResource(R.string.timeline_title),
        modifier = modifier,
        onBack = actions.back,
        actions = listOf(TopAction(stringResource(R.string.timeline_patterns), actions.openPatterns)),
    ) {
        if (state.loaded) Content(state, actions, onAddGap = { addingGap = true })
    }
    if (addingGap) {
        AddGapDialog(
            zone = state.zone,
            problem = (gapOutcome as? GapOutcome.Problem)?.problem,
            onSave = actions.addGap,
            onDismiss = {
                addingGap = false
                actions.gapOutcomeShown()
            },
        )
    }
}

@Composable
private fun Content(state: TimelineUiState, actions: TimelineActions, onAddGap: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "header") { Header(state, actions, onAddGap) }
        if (state.view.total == 0) {
            item(key = "empty") {
                EmptyState(stringResource(R.string.timeline_empty_title), stringResource(R.string.timeline_empty_body))
            }
        } else if (state.view.items.isEmpty()) {
            item(key = "no-match") { SupportingText(stringResource(R.string.timeline_filter_empty)) }
        }
        items(state.view.items, key = { it.key }) { item ->
            when (item) {
                is TimelineItem.DayHeader -> SectionHeader(
                    item.date?.let { formatDay(it, locale) } ?: stringResource(R.string.timeline_undated_heading),
                    Modifier.padding(top = Spacing.md),
                )
                is TimelineItem.GapItem -> GapBlock(item, state.zone, locale)
                is TimelineItem.EventItem -> EventCard(item.row, state.zone, locale, onOpen = { actions.openEvent(item.row.eventId) })
            }
        }
    }
}

@Composable
private fun Header(state: TimelineUiState, actions: TimelineActions, onAddGap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SupportingText(
                plural(R.plurals.timeline_count, state.view.total, state.view.total, state.view.needsReview).text(),
                Modifier.weight(1f),
            )
            OverflowMenuButton(
                stringResource(R.string.timeline_menu_options),
                listOf(
                    MenuAction(stringResource(R.string.timeline_add_gap)) { onAddGap() },
                    MenuAction(stringResource(R.string.timeline_who_is_who)) { actions.openWhoIsWho() },
                ),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val selectedWord = stringResource(R.string.filter_selected)
            listOf(
                TimelineFilter.ALL to R.string.filter_all,
                TimelineFilter.NEEDS_REVIEW to R.string.filter_needs_review,
                TimelineFilter.TAGGED to R.string.filter_tagged,
            ).forEach { (filter, label) ->
                ChoiceButton(
                    stringResource(label),
                    selected = state.filter == filter,
                    selectedWord = selectedWord,
                    onClick = { actions.setFilter(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GapBlock(gap: TimelineItem.GapItem, zone: ZoneId, locale: Locale) {
    EpistemicBlock(EpistemicStatus.UNKNOWN) {
        Text(stringResource(R.string.gap_statement), style = MaterialTheme.typography.bodyLarge)
        SupportingText(gapPeriodText(gap.start, gap.end, zone, locale).text())
    }
}

@Composable
private fun EventCard(row: TimelineEventRow, zone: ZoneId, locale: Locale, onOpen: () -> Unit) {
    val open = stringResource(R.string.timeline_open_event)
    SakshiCard(Modifier.clickable(onClickLabel = open, role = Role.Button, onClick = onOpen)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TimeLines(row, zone, locale)
            Text(senderLine(row), style = MaterialTheme.typography.bodyMedium)
            BodyBlock(row.body)
            row.tags.forEach { TagLineView(it) }
        }
    }
}

@Composable
private fun TimeLines(row: TimelineEventRow, zone: ZoneId, locale: Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.semantics(mergeDescendants = true) { }) {
        Text(timeText(row.time.label, zone, locale).text(), style = MaterialTheme.typography.titleSmall)
        if (row.time.label !is TimeLabel.Unknown) SupportingText(basisText(row.time.basis).text())
        if (row.orderNote) SupportingText(stringResource(R.string.timeline_order_note))
    }
}

@Composable
internal fun senderLine(row: TimelineEventRow): String {
    val sender = row.sender
    val name = sender.label ?: stringResource(R.string.sender_none)
    val status = when {
        sender.status == SenderStatus.CONFIRMED && sender.personLabel != null ->
            stringResource(R.string.sender_confirmed_as, sender.personLabel)
        sender.status == SenderStatus.CONFIRMED -> stringResource(R.string.sender_confirmed)
        sender.label == null -> null
        else -> stringResource(R.string.sender_not_confirmed)
    }
    val direction = directionText(row.direction).text()
    return listOfNotNull(name, status, direction).joinToString("; ")
}

@Composable
private fun BodyBlock(body: BodyView) {
    when (body) {
        BodyView.MediaOmitted -> EpistemicBlock(EpistemicStatus.OBSERVED, stringResource(R.string.body_media_omitted))
        BodyView.NotAvailable -> EpistemicBlock(EpistemicStatus.UNKNOWN, stringResource(R.string.body_not_available))
        is BodyView.Message -> {
            var expanded by remember(body.full) { mutableStateOf(false) }
            EpistemicBlock(EpistemicStatus.OBSERVED) {
                Text(if (expanded) body.full else body.preview, style = MaterialTheme.typography.bodyLarge)
                if (body.truncated) {
                    QuietTextButton(
                        stringResource(if (expanded) R.string.body_show_less else R.string.body_show_all),
                        onClick = { expanded = !expanded },
                    )
                }
            }
        }
    }
}

@Composable
fun TagLineView(line: TagLine, modifier: Modifier = Modifier) {
    val label = categoryLabelText(line.label).text()
    when (line.kind) {
        TagKind.SUGGESTION -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            EpistemicLabel(EpistemicStatus.INFERRED)
            Text(stringResource(R.string.tag_needs_review, label), style = MaterialTheme.typography.bodyMedium)
        }
        TagKind.ACCEPTED -> Text(stringResource(R.string.tag_accepted, label), modifier, style = MaterialTheme.typography.bodyMedium)
        TagKind.OWN -> Text(stringResource(R.string.tag_own, label), modifier, style = MaterialTheme.typography.bodyMedium)
        TagKind.DISAGREED -> Text(stringResource(R.string.tag_disagreed, label), modifier, style = MaterialTheme.typography.bodyMedium)
        TagKind.NOT_SURE -> Text(stringResource(R.string.tag_not_sure, label), modifier, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AddGapDialog(zone: ZoneId, problem: GapProblem?, onSave: (String, String, GapReason) -> Unit, onDismiss: () -> Unit) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(GapReason.IMPORT_SELECTION) }
    FormDialog(
        title = stringResource(R.string.gap_dialog_title),
        confirmLabel = stringResource(R.string.dialog_save),
        onConfirm = { onSave(start, end, reason) },
        onDismiss = onDismiss,
    ) {
        SupportingText(stringResource(R.string.gap_dialog_body, zone.id))
        SakshiTextField(start, { start = it }, stringResource(R.string.gap_start_label), supportingText = GapInput.PATTERN_HINT)
        SakshiTextField(end, { end = it }, stringResource(R.string.gap_end_label), supportingText = stringResource(R.string.gap_bounds_hint))
        problem?.let { StatusNote(NoteKind.Problem, gapProblemText(it).text()) }
        SectionHeader(stringResource(R.string.gap_reason_heading))
        Column(Modifier.selectableGroup()) {
            GAP_REASONS.forEach { option ->
                RadioRow(gapReasonText(option).text(), selected = reason == option, onSelect = { reason = option })
            }
        }
    }
}

fun gapReasonText(reason: GapReason): UiText = res(
    when (reason) {
        GapReason.IMPORT_SELECTION -> R.string.gap_reason_import_selection
        GapReason.ACCESS_REVOKED -> R.string.gap_reason_access_revoked
        GapReason.LISTENER_DISCONNECTED -> R.string.gap_reason_listener
        GapReason.PROCESS_DEATH -> R.string.gap_reason_process
        GapReason.QUEUE_OVERFLOW -> R.string.gap_reason_overflow
        GapReason.KEY_UNAVAILABLE -> R.string.gap_reason_key
        GapReason.UNKNOWN -> R.string.gap_reason_unknown
    },
)

fun gapProblemText(problem: GapProblem): UiText = res(
    when (problem) {
        GapProblem.NEITHER_BOUND -> R.string.gap_problem_neither
        GapProblem.START_FORMAT -> R.string.gap_problem_start
        GapProblem.END_FORMAT -> R.string.gap_problem_end
        GapProblem.END_BEFORE_START -> R.string.gap_problem_order
        GapProblem.NOT_SAVED -> R.string.gap_problem_not_saved
    },
    GapInput.EXAMPLE,
)
