package org.sakshi.app.patterns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.review.reasonText
import org.sakshi.app.timeline.dateTimeText
import org.sakshi.app.ui.components.ChoiceButton
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.FormDialog
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.PatternReviewAction

class PatternsActions(
    val back: () -> Unit,
    val setView: (EvidenceView) -> Unit,
    val openEvent: (String) -> Unit,
    val enter: () -> Unit,
    val answer: (key: String, action: PatternReviewAction, reason: String?) -> Unit,
)

@Composable
fun PatternsScreen(state: PatternsUiState, actions: PatternsActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.back)
    LaunchedEffect(Unit) { actions.enter() }
    SakshiScaffold(
        title = stringResource(R.string.patterns_title),
        modifier = modifier,
        onBack = actions.back,
    ) {
        val locale = LocalConfiguration.current.locales[0]
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "toggle") { ViewToggle(state.view, actions.setView) }
            if (state.view == EvidenceView.CANDIDATE_PREVIEW) {
                item(key = "preview-note") { StatusNote(NoteKind.Caution, stringResource(R.string.patterns_preview_note)) }
            }
            if (state.refreshing) {
                item(key = "refreshing") { StatusNote(NoteKind.Info, stringResource(R.string.patterns_refreshing), Modifier.liveUpdates()) }
            }
            state.notice?.let { notice ->
                item(key = "notice") {
                    val kind = if (notice == PatternNotice.CHANGED_BEFORE_SAVE) NoteKind.Caution else NoteKind.Problem
                    StatusNote(kind, patternNoticeText(notice).text(), Modifier.liveUpdates())
                }
            }
            when {
                state.loading -> item(key = "loading") { SupportingText(stringResource(R.string.patterns_loading)) }
                state.failed -> item(key = "failed") { StatusNote(NoteKind.Problem, stringResource(R.string.patterns_failed)) }
                state.cards.isEmpty() -> item(key = "empty") {
                    EmptyState(stringResource(R.string.patterns_empty_title), stringResource(R.string.patterns_empty_body))
                }
                else -> items(state.cards, key = { it.key }) { card ->
                    PatternCard(card, state, locale, actions.openEvent, actions.answer)
                }
            }
            item(key = "footer") { SupportingText(stringResource(R.string.patterns_footer), Modifier.padding(top = Spacing.md)) }
        }
    }
}

@Composable
private fun ViewToggle(view: EvidenceView, onChoose: (EvidenceView) -> Unit) {
    val selectedWord = stringResource(R.string.filter_selected)
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChoiceButton(
            stringResource(R.string.patterns_confirmed_only),
            selected = view == EvidenceView.CONFIRMED_ONLY,
            selectedWord = selectedWord,
            onClick = { onChoose(EvidenceView.CONFIRMED_ONLY) },
            modifier = Modifier.weight(1f),
        )
        ChoiceButton(
            stringResource(R.string.patterns_include_waiting),
            selected = view == EvidenceView.CANDIDATE_PREVIEW,
            selectedWord = selectedWord,
            onClick = { onChoose(EvidenceView.CANDIDATE_PREVIEW) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PatternCard(
    card: PatternCardView,
    state: PatternsUiState,
    locale: Locale,
    onOpenEvent: (String) -> Unit,
    onAnswer: (key: String, action: PatternReviewAction, reason: String?) -> Unit,
) {
    val zone = state.zone
    var showSupport by remember(card.key) { mutableStateOf(false) }
    EpistemicBlock(EpistemicStatus.PATTERN) {
        Text(card.title.text(), style = MaterialTheme.typography.titleMedium)
        Text(card.statusText.text(), style = MaterialTheme.typography.bodyMedium)
        Text(card.observed, style = MaterialTheme.typography.bodyLarge)
        card.interpretation?.let { Text(interpretationText(it).text(), style = MaterialTheme.typography.bodyLarge) }
        SectionHeader(stringResource(R.string.patterns_limits_heading))
        card.limitations.forEach { Text(stringResource(R.string.patterns_limit_line, it), style = MaterialTheme.typography.bodyMedium) }
        QuietTextButton(supportCountText(card.support.size).text(), { showSupport = !showSupport })
        if (showSupport) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                card.support.forEach { item ->
                    val time = dateTimeText(item.time.label, zone, locale).text()
                    val line = item.snippet?.let { stringResource(R.string.patterns_support_line, time, it) } ?: time
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
                            .clickable(role = Role.Button, onClick = { onOpenEvent(item.eventId) }),
                    )
                }
            }
        }
        when {
            card.reviewable -> ReviewControls(card, enabled = !state.loading && !state.refreshing, onAnswer = onAnswer)
            state.view == EvidenceView.CANDIDATE_PREVIEW && isReviewableStatus(card.status) ->
                SupportingText(stringResource(R.string.patterns_review_preview_only))
        }
    }
}

/** Marks a text whose change is read out when it appears, so a state change is heard as well as seen. */
private fun Modifier.liveUpdates(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/**
 * The person's answer to one description. The answer is shown as words; the buttons are full width so large fonts
 * wrap instead of clipping. Agreeing says only that the description matches what was saved.
 */
@Composable
private fun ReviewControls(
    card: PatternCardView,
    enabled: Boolean,
    onAnswer: (key: String, action: PatternReviewAction, reason: String?) -> Unit,
) {
    var rejecting by remember(card.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            patternReviewText(card.review, card.reviewReason).text(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.liveUpdates(),
        )
        SecondaryButton(stringResource(R.string.patterns_accept), { onAnswer(card.key, PatternReviewAction.ACCEPT, null) }, Modifier.fillMaxWidth(), enabled)
        SecondaryButton(stringResource(R.string.patterns_reject), { rejecting = true }, Modifier.fillMaxWidth(), enabled)
        SecondaryButton(stringResource(R.string.patterns_unsure), { onAnswer(card.key, PatternReviewAction.MARK_UNKNOWN, null) }, Modifier.fillMaxWidth(), enabled)
        if (card.review != PatternReview.NOT_REVIEWED) {
            QuietTextButton(stringResource(R.string.patterns_withdraw), { onAnswer(card.key, PatternReviewAction.WITHDRAW, null) }, Modifier.fillMaxWidth(), enabled)
        }
        SupportingText(stringResource(R.string.patterns_review_explain))
    }
    if (rejecting) {
        RejectDialog(
            onChoose = { reason ->
                rejecting = false
                onAnswer(card.key, PatternReviewAction.REJECT, reason)
            },
            onDismiss = { rejecting = false },
        )
    }
}

@Composable
private fun RejectDialog(onChoose: (String?) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    FormDialog(
        title = stringResource(R.string.patterns_reject_title),
        confirmLabel = stringResource(R.string.review_disagree_confirm),
        onConfirm = { onChoose(reason) },
        onDismiss = onDismiss,
    ) {
        SupportingText(stringResource(R.string.patterns_reject_body))
        Column(Modifier.selectableGroup()) {
            RadioRow(stringResource(R.string.patterns_reject_no_reason), selected = reason == null, onSelect = { reason = null })
            PATTERN_REJECT_REASONS.forEach { code -> RadioRow(reasonText(code).text(), selected = reason == code, onSelect = { reason = code }) }
        }
    }
}
