package org.sakshi.app.analysis

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.ZoneId
import org.sakshi.app.R
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.ProgressBlock
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SakshiTextField
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.text.DateOrder

private const val MAX_SAMPLE_DATES = 5
private const val PERCENT_TOTAL = 100

class AnalysisActions(
    val back: () -> Unit,
    val analyse: () -> Unit,
    val cancel: () -> Unit,
    val chooseDateOrder: (DateOrder) -> Unit,
    val chooseZone: (ZoneId) -> Unit,
    val chooseOwner: (OwnerChoice) -> Unit,
    val continueWithAnswers: () -> Unit,
    val openTimeline: () -> Unit,
    val startOnce: () -> Unit,
    /** Receives the speech model file the person chose. */
    val importSpeechModel: (Uri) -> Unit = {},
    /** Called just before the system file picker opens, so the session is not locked while it is showing. */
    val onPickerOpening: () -> Unit = {},
    /** Called when the file picker came back, with or without a file. */
    val onPickerClosed: () -> Unit = {},
)

@Composable
fun AnalysisScreen(state: AnalysisUiState, actions: AnalysisActions, modifier: Modifier = Modifier) {
    var pickingZone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.startOnce() }
    BackHandler {
        if (pickingZone) pickingZone = false else actions.back()
    }
    SakshiScaffold(
        title = stringResource(R.string.analysis_title),
        modifier = modifier,
        onBack = { if (pickingZone) pickingZone = false else actions.back() },
    ) {
        when {
            pickingZone && state is AnalysisUiState.Questions -> ZonePicker(
                selected = state.questions.answers.zone,
                onPick = {
                    actions.chooseZone(it)
                    pickingZone = false
                },
            )
            else -> when (state) {
                AnalysisUiState.Idle -> IdleContent(actions.analyse)
                AnalysisUiState.Running -> RunningContent(actions.cancel)
                is AnalysisUiState.Questions -> QuestionsContent(state.questions, actions, onChangeZone = { pickingZone = true })
                is AnalysisUiState.Done -> DoneContent(state.result, actions.openTimeline)
                is AnalysisUiState.Refused -> MessageContent(refusalText(state.reason).text(), NoteKind.Info, actions.back)
                is AnalysisUiState.SpeechSetupNeeded -> SpeechSetupContent(state.setup, actions)
                AnalysisUiState.Failed -> MessageContent(stringResource(R.string.analysis_failed), NoteKind.Problem, actions.back)
            }
        }
    }
}

@Composable
private fun IdleContent(onAnalyse: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SupportingText(stringResource(R.string.analysis_idle_body))
        PrimaryButton(stringResource(R.string.analysis_start), onAnalyse)
    }
}

@Composable
private fun RunningContent(onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter, vertical = Spacing.xl)) {
        ProgressBlock(0, 0, stringResource(R.string.analysis_running), onCancel, indeterminate = true)
    }
}

/** Explains the one-time speech model preparation and lets the person pick the file. Nothing is downloaded. */
@Composable
private fun SpeechSetupContent(setup: SpeechSetup, actions: AnalysisActions) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        actions.onPickerClosed()
        if (uri != null) actions.importSpeechModel(uri)
    }
    val choose = {
        actions.onPickerOpening()
        picker.launch(arrayOf("*/*"))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        when (setup) {
            is SpeechSetup.Installing -> ProgressBlock(setup.percent, PERCENT_TOTAL, stringResource(R.string.speech_setup_installing), actions.cancel)
            SpeechSetup.Installed -> {
                StatusNote(NoteKind.Info, stringResource(R.string.speech_setup_ready))
                PrimaryButton(stringResource(R.string.speech_setup_analyse_again), actions.analyse)
                SecondaryButton(stringResource(R.string.analysis_back_to_case), actions.back)
            }
            SpeechSetup.Needed, SpeechSetup.WrongFile, SpeechSetup.TooLarge, SpeechSetup.Failed -> {
                ScreenTitle(stringResource(R.string.speech_setup_title))
                Text(stringResource(R.string.speech_setup_body), style = MaterialTheme.typography.bodyLarge)
                when (setup) {
                    SpeechSetup.WrongFile -> StatusNote(NoteKind.Problem, stringResource(R.string.speech_setup_wrong_file))
                    SpeechSetup.TooLarge -> StatusNote(NoteKind.Problem, stringResource(R.string.speech_setup_too_large))
                    SpeechSetup.Failed -> StatusNote(NoteKind.Problem, stringResource(R.string.speech_setup_failed))
                    else -> Unit
                }
                PrimaryButton(stringResource(R.string.speech_setup_choose), choose)
                SecondaryButton(stringResource(R.string.analysis_back_to_case), actions.back)
            }
        }
    }
}

@Composable
private fun MessageContent(message: String, kind: NoteKind, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        StatusNote(kind, message)
        SecondaryButton(stringResource(R.string.analysis_back_to_case), onBack)
    }
}

@Composable
private fun DoneContent(result: AnalysisOutcome.Analysed, onOpenTimeline: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ScreenTitle(stringResource(R.string.analysis_done_title))
        Text(
            pluralStringResource(R.plurals.analysis_done_messages, result.eventCount, result.eventCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (result.suggestionCount == 0) {
            Text(stringResource(R.string.analysis_done_no_cue), style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                pluralStringResource(R.plurals.analysis_done_suggestions, result.suggestionCount, result.suggestionCount),
                style = MaterialTheme.typography.bodyLarge,
            )
            SupportingText(stringResource(R.string.analysis_done_suggestion_note))
        }
        warningTexts(result).forEach { StatusNote(NoteKind.Caution, it.text()) }
        PrimaryButton(stringResource(R.string.analysis_open_timeline), onOpenTimeline)
    }
}

@Composable
private fun QuestionsContent(questions: ExportQuestions, actions: AnalysisActions, onChangeZone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ScreenTitle(stringResource(R.string.analysis_questions_title))
        Text(stringResource(R.string.analysis_questions_intro), style = MaterialTheme.typography.bodyLarge)
        DateOrderQuestion(questions, actions.chooseDateOrder)
        ZoneQuestion(questions.answers.zone, onChangeZone)
        OwnerQuestion(questions, actions.chooseOwner)
        PrimaryButton(stringResource(R.string.analysis_continue), actions.continueWithAnswers, enabled = questions.canContinue)
    }
}

@Composable
private fun DateOrderQuestion(questions: ExportQuestions, onChoose: (DateOrder) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.analysis_dates_heading))
        SupportingText(stringResource(R.string.analysis_dates_samples))
        EpistemicBlock(EpistemicStatus.OBSERVED) {
            questions.needs.sampleDates.take(MAX_SAMPLE_DATES).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
        when (questions.fixedDateOrder) {
            DateOrder.DAY_MONTH -> Text(stringResource(R.string.analysis_dates_fixed_day), style = MaterialTheme.typography.bodyLarge)
            DateOrder.MONTH_DAY -> Text(stringResource(R.string.analysis_dates_fixed_month), style = MaterialTheme.typography.bodyLarge)
            else -> Column(Modifier.selectableGroup()) {
                RadioRow(
                    stringResource(R.string.analysis_dates_day_first),
                    selected = questions.answers.dateOrder == DateOrder.DAY_MONTH,
                    onSelect = { onChoose(DateOrder.DAY_MONTH) },
                )
                RadioRow(
                    stringResource(R.string.analysis_dates_month_first),
                    selected = questions.answers.dateOrder == DateOrder.MONTH_DAY,
                    onSelect = { onChoose(DateOrder.MONTH_DAY) },
                )
            }
        }
    }
}

@Composable
private fun ZoneQuestion(zone: ZoneId, onChange: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.analysis_zone_heading))
        Text(stringResource(R.string.analysis_zone_current, zone.id), style = MaterialTheme.typography.bodyLarge)
        SecondaryButton(stringResource(R.string.analysis_zone_change), onChange)
    }
}

@Composable
private fun OwnerQuestion(questions: ExportQuestions, onChoose: (OwnerChoice) -> Unit) {
    val owner = questions.answers.owner
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.analysis_owner_heading))
        SupportingText(stringResource(R.string.analysis_owner_note))
        Column(Modifier.selectableGroup()) {
            questions.needs.senders.forEach { name ->
                RadioRow(name, selected = owner == OwnerChoice.Sender(name), onSelect = { onChoose(OwnerChoice.Sender(name)) })
            }
            RadioRow(
                stringResource(R.string.analysis_owner_none),
                selected = owner == OwnerChoice.NoneOfThese,
                onSelect = { onChoose(OwnerChoice.NoneOfThese) },
            )
        }
    }
}

/** A plain text filter and the list of zone ids that match it. */
@Composable
internal fun ZonePicker(selected: ZoneId, onPick: (ZoneId) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { ZoneChoices.filter(query) }
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        SakshiTextField(query, { query = it }, stringResource(R.string.analysis_zone_search))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(matches, key = { it }) { id ->
                RadioRow(id, selected = id == selected.id, onSelect = { onPick(ZoneId.of(id)) })
            }
        }
    }
}
