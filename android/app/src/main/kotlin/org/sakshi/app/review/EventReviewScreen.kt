package org.sakshi.app.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.timeline.TimeLabel
import org.sakshi.app.timeline.basisText
import org.sakshi.app.timeline.dateTimeText
import org.sakshi.app.timeline.formatDayTime
import org.sakshi.app.timeline.readTime
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.EpistemicLabel
import org.sakshi.app.ui.components.FormDialog
import org.sakshi.app.ui.components.LabelValue
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.vault.SenderSelector
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview

class EventReviewActions(
    val back: () -> Unit,
    val agree: (Int) -> Unit,
    val disagree: (Int, String) -> Unit,
    val notSure: (Int) -> Unit,
    val addOwnTag: (CategoryLabel) -> Unit,
    val setDirection: (Direction) -> Unit,
    val markWantedness: (UnwantedContact) -> Unit,
    val markBoundary: (BoundaryMarker, ActorId) -> Unit,
    val openWhoIsWho: (SenderSelector?) -> Unit,
    val noticeShown: () -> Unit,
)

/** Dialog the screen is showing. Holds only an index, never text from the evidence. */
private sealed interface ReviewDialog {
    class Disagree(val index: Int) : ReviewDialog

    data object OwnTag : ReviewDialog
}

@Composable
fun EventReviewScreen(state: EventReviewState, zone: ZoneId, actions: EventReviewActions, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<ReviewDialog?>(null) }
    BackHandler(onBack = actions.back)
    SakshiScaffold(
        title = stringResource(R.string.review_title),
        modifier = modifier,
        onBack = actions.back,
        bottomBar = state.notice?.let { notice ->
            {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        StatusNote(notice.kind, notice.text.text(), Modifier.weight(1f))
                        QuietTextButton(stringResource(R.string.notice_dismiss), actions.noticeShown)
                    }
                }
            }
        },
    ) {
        val event = state.event
        when {
            !state.loaded -> Unit
            event == null -> SupportingText(stringResource(R.string.review_event_missing), Modifier.padding(Spacing.gutter))
            else -> Content(state, event, zone, actions, onDialog = { dialog = it })
        }
    }
    when (val current = dialog) {
        is ReviewDialog.Disagree -> DisagreeDialog(
            onChoose = {
                dialog = null
                actions.disagree(current.index, it)
            },
            onDismiss = { dialog = null },
        )
        ReviewDialog.OwnTag -> OwnTagDialog(
            onChoose = {
                dialog = null
                actions.addOwnTag(it)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun Content(state: EventReviewState, event: Event, zone: ZoneId, actions: EventReviewActions, onDialog: (ReviewDialog) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        SavedSection(state, event, zone)
        WhoAndWhenSection(state, event, actions)
        SuggestionsSection(state, actions, onDialog)
        if (state.boundaryOffered) BoundarySection(state, actions)
        if (state.history.isNotEmpty()) HistorySection(state, zone)
    }
}

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun SavedSection(state: EventReviewState, event: Event, zone: ZoneId) {
    var explainFingerprint by remember { mutableStateOf(false) }
    val body = state.body
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ScreenTitle(stringResource(R.string.review_saved_heading))
        if (body == null) {
            EpistemicBlock(EpistemicStatus.UNKNOWN, stringResource(R.string.body_not_available))
        } else {
            EpistemicBlock(EpistemicStatus.OBSERVED) {
                Text(markedBody(body, state.marks), style = MaterialTheme.typography.bodyLarge)
                state.marks.map { it.quote }.distinct().forEach { quote ->
                    Text(stringResource(R.string.review_matched_words, quote), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        LabelValue(stringResource(R.string.review_source_label), stringResource(sourceWords(event.source.kind)))
        val reading = readTime(event.timestamp)
        LabelValue(
            stringResource(R.string.review_time_label),
            dateTimeText(reading.label, zone, locale()).text() +
                if (reading.label is TimeLabel.Unknown) "" else ", " + basisText(reading.basis).text(),
        )
        event.evidenceReferences.firstOrNull()?.sha256?.let { hash ->
            LabelValue(stringResource(R.string.review_fingerprint_label), hash.take(FINGERPRINT_CHARS))
            QuietTextButton(stringResource(R.string.review_fingerprint_what), { explainFingerprint = !explainFingerprint })
            if (explainFingerprint) SupportingText(stringResource(R.string.review_fingerprint_explained))
        }
    }
}

private const val FINGERPRINT_CHARS = 12

private fun sourceWords(kind: SourceKind): Int = when (kind) {
    SourceKind.SELECTED_EXPORT -> R.string.review_source_export
    SourceKind.SELECTED_TEXT -> R.string.review_source_text
    SourceKind.MANUAL_ENTRY -> R.string.review_source_note
    SourceKind.NOTIFICATION_EXCERPT,
    SourceKind.SELECTED_IMAGE,
    SourceKind.SELECTED_AUDIO,
    SourceKind.SELECTED_VIDEO,
    SourceKind.SELECTED_DOCUMENT,
    -> R.string.review_source_other
}

/** The body with each matched span underlined; the list of matched words below it repeats them in words. */
private fun markedBody(body: String, marks: List<CueMark>): AnnotatedString = buildAnnotatedString {
    append(body)
    marks.forEach { addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it.start, it.end) }
}

@Composable
private fun WhoAndWhenSection(state: EventReviewState, event: Event, actions: EventReviewActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ScreenTitle(stringResource(R.string.review_who_heading))
        val sender = event.sender.displayLabel ?: stringResource(R.string.sender_none)
        val status = if (event.sender.associationReview == AssociationReview.CONFIRMED && event.sender.actorId != null) {
            val person = state.people.firstOrNull { it.id == event.sender.actorId }?.displayLabel
            if (person != null) stringResource(R.string.sender_confirmed_as, person) else stringResource(R.string.sender_confirmed)
        } else {
            stringResource(R.string.sender_not_confirmed)
        }
        LabelValue(stringResource(R.string.review_sender_label), "$sender ($status)")
        LabelValue(stringResource(R.string.review_direction_label), directionText(event.direction).text())
        state.senderClaim?.let { claim -> SecondaryButton(stringResource(R.string.review_this_is), { actions.openWhoIsWho(claim) }, Modifier.fillMaxWidth()) }
        SecondaryButton(stringResource(R.string.review_from_me), { actions.setDirection(Direction.OUTGOING) }, Modifier.fillMaxWidth())
        SecondaryButton(stringResource(R.string.review_to_me), { actions.setDirection(Direction.INCOMING) }, Modifier.fillMaxWidth())
        if (event.direction != Direction.OUTGOING) WantednessActions(event, actions)
    }
}

@Composable
private fun WantednessActions(event: Event, actions: EventReviewActions) {
    val current = event.boundary.unwantedContact
    val word = when (current) {
        UnwantedContact.USER_MARKED_UNWANTED -> R.string.review_wantedness_unwanted
        UnwantedContact.USER_MARKED_WANTED -> R.string.review_wantedness_wanted
        UnwantedContact.UNKNOWN, UnwantedContact.NOT_APPLICABLE -> R.string.review_wantedness_none
    }
    LabelValue(stringResource(R.string.review_wantedness_label), stringResource(word))
    SecondaryButton(stringResource(R.string.review_mark_unwanted), { actions.markWantedness(UnwantedContact.USER_MARKED_UNWANTED) }, Modifier.fillMaxWidth())
    SecondaryButton(stringResource(R.string.review_mark_wanted), { actions.markWantedness(UnwantedContact.USER_MARKED_WANTED) }, Modifier.fillMaxWidth())
    if (current == UnwantedContact.USER_MARKED_UNWANTED || current == UnwantedContact.USER_MARKED_WANTED) {
        QuietTextButton(stringResource(R.string.review_clear_wantedness), { actions.markWantedness(UnwantedContact.UNKNOWN) })
    }
}

@Composable
private fun SuggestionsSection(state: EventReviewState, actions: EventReviewActions, onDialog: (ReviewDialog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ScreenTitle(stringResource(R.string.review_signal_heading))
        if (state.categories.isEmpty()) SupportingText(stringResource(R.string.review_no_cue))
        state.categories.forEach { view -> CategoryCard(view, actions, onDisagree = { onDialog(ReviewDialog.Disagree(view.index)) }) }
        SecondaryButton(stringResource(R.string.review_add_tag), { onDialog(ReviewDialog.OwnTag) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CategoryCard(view: CategoryView, actions: EventReviewActions, onDisagree: () -> Unit) {
    SakshiCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (view.isOwnTag) {
                Text(stringResource(R.string.review_your_tag), style = MaterialTheme.typography.labelLarge)
            } else {
                EpistemicLabel(EpistemicStatus.INFERRED)
            }
            Text(categoryLabelText(view.category.label).text(), style = MaterialTheme.typography.titleSmall)
            SupportingText(categoryBasisText(view.category.basis, view.cues.map { it.quote }).text())
            Text(reviewStatusText(view.category.reviewStatus).text(), style = MaterialTheme.typography.bodyMedium)
            if (!view.isOwnTag) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(stringResource(R.string.review_agree), { actions.agree(view.index) }, Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.review_disagree), onDisagree, Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.review_not_sure), { actions.notSure(view.index) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BoundarySection(state: EventReviewState, actions: EventReviewActions) {
    val people = state.people.filter { it.associationReview == AssociationReview.CONFIRMED }
    var chosen by remember { mutableStateOf<ActorId?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ScreenTitle(stringResource(R.string.review_boundary_heading))
        SupportingText(stringResource(R.string.review_boundary_body))
        if (people.isEmpty()) {
            StatusNote(NoteKind.Info, stringResource(R.string.review_boundary_need_person))
            SecondaryButton(stringResource(R.string.review_open_who), { actions.openWhoIsWho(state.senderClaim) }, Modifier.fillMaxWidth())
        } else {
            SectionHeader(stringResource(R.string.review_boundary_person))
            Column(Modifier.selectableGroup()) {
                people.forEach { RadioRow(it.displayLabel, selected = chosen == it.id, onSelect = { chosen = it.id }) }
            }
            BOUNDARY_ACTIONS.forEach { (marker, label) ->
                SecondaryButton(
                    stringResource(label),
                    { chosen?.let { actions.markBoundary(marker, it) } },
                    Modifier.fillMaxWidth(),
                    enabled = chosen != null,
                )
            }
        }
    }
}

private val BOUNDARY_ACTIONS = listOf(
    BoundaryMarker.DO_NOT_CONTACT to R.string.review_boundary_stop,
    BoundaryMarker.USER_DISENGAGEMENT to R.string.review_boundary_stopped_replying,
    BoundaryMarker.LIMITED_CONTACT to R.string.review_boundary_limited,
    BoundaryMarker.USER_RESUMPTION to R.string.review_boundary_resumed,
)

@Composable
private fun HistorySection(state: EventReviewState, zone: ZoneId) {
    val locale = locale()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.review_history_heading))
        state.history.forEach { line ->
            val whenText = line.at?.let { formatDayTime(it, zone, locale) } ?: stringResource(R.string.time_unknown)
            Column(Modifier.fillMaxWidth()) {
                Text(line.text.text(), style = MaterialTheme.typography.bodyMedium)
                line.reason?.let { SupportingText(stringResource(R.string.review_history_reason, it.text())) }
                SupportingText(whenText)
            }
        }
    }
}

@Composable
private fun DisagreeDialog(onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    FormDialog(
        title = stringResource(R.string.review_disagree_title),
        confirmLabel = stringResource(R.string.review_disagree_confirm),
        onConfirm = { reason?.let(onChoose) },
        onDismiss = onDismiss,
        confirmEnabled = reason != null,
    ) {
        SupportingText(stringResource(R.string.review_disagree_body))
        Column(Modifier.selectableGroup()) {
            DISAGREE_REASONS.forEach { code -> RadioRow(reasonText(code).text(), selected = reason == code, onSelect = { reason = code }) }
        }
    }
}

@Composable
private fun OwnTagDialog(onChoose: (CategoryLabel) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf<CategoryLabel?>(null) }
    FormDialog(
        title = stringResource(R.string.review_tag_title),
        confirmLabel = stringResource(R.string.review_tag_confirm),
        onConfirm = { label?.let(onChoose) },
        onDismiss = onDismiss,
        confirmEnabled = label != null,
    ) {
        SupportingText(stringResource(R.string.review_tag_body))
        Column(Modifier.selectableGroup()) {
            OWN_TAG_LABELS.forEach { option -> RadioRow(categoryLabelText(option).text(), selected = label == option, onSelect = { label = option }) }
        }
    }
}
