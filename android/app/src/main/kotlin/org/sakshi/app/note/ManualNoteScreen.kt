package org.sakshi.app.note

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.sakshi.acquisition.importer.ContentAvailability
import org.sakshi.acquisition.importer.ManualNote
import org.sakshi.app.R
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus

class NoteActions(
    val setText: (String) -> Unit,
    val setIncidentTime: (String) -> Unit,
    val setSender: (String) -> Unit,
    val setApp: (String) -> Unit,
    val setViewOnce: (Boolean) -> Unit,
    val setAvailability: (ContentAvailability) -> Unit,
    val save: () -> Unit,
    val cancel: () -> Unit,
    /** Called once after a successful save. */
    val saved: () -> Unit,
)

@Composable
fun ManualNoteScreen(state: NoteFormState, actions: NoteActions, modifier: Modifier = Modifier) {
    BackHandler(enabled = !state.saving, onBack = actions.cancel)
    LaunchedEffect(state.saved) { if (state.saved) actions.saved() }
    SakshiScaffold(
        title = stringResource(R.string.note_title),
        modifier = modifier,
        onBack = if (state.saving) null else actions.cancel,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SupportingText(stringResource(R.string.note_statement))
            EpistemicBlock(EpistemicStatus.USER_REPORTED) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = actions.setText,
                    label = { Text(stringResource(R.string.note_text_label)) },
                    supportingText = {
                        Text(pluralStringResource(R.plurals.paste_counter, ManualNote.MAX_TEXT_CHARS, state.text.length, ManualNote.MAX_TEXT_CHARS))
                    },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Field(state.incidentTime, actions.setIncidentTime, R.string.note_time_label, R.string.note_time_hint)
            Field(state.sender, actions.setSender, R.string.note_sender_label, R.string.note_sender_hint)
            Field(state.app, actions.setApp, R.string.note_app_label, null)
            ViewOnceSection(state, actions)
            state.problem?.let { StatusNote(NoteKind.Problem, problemText(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (state.saving) {
                StatusNote(NoteKind.Info, stringResource(R.string.note_saving), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton(stringResource(R.string.note_save), actions.save, enabled = !state.saving)
                QuietTextButton(stringResource(R.string.dialog_cancel), actions.cancel, Modifier.fillMaxWidth(), enabled = !state.saving)
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, hint: Int?) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ViewOnceSection(state: NoteFormState, actions: NoteActions) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .toggleable(value = state.viewOnce, role = Role.Switch, onValueChange = actions.setViewOnce),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.note_view_once), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = Spacing.md))
        Switch(checked = state.viewOnce, onCheckedChange = null)
    }
    if (!state.viewOnce) return
    SectionHeader(stringResource(R.string.note_availability_heading))
    Column(Modifier.selectableGroup()) {
        AVAILABILITY_OPTIONS.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget).selectable(
                    selected = state.availability == value,
                    role = Role.RadioButton,
                    onClick = { actions.setAvailability(value) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.availability == value, onClick = null)
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.md))
            }
        }
    }
}

private val AVAILABILITY_OPTIONS = listOf(
    ContentAvailability.NOT_ACQUIRED to R.string.note_availability_not_acquired,
    ContentAvailability.CONTEXT_ONLY to R.string.note_availability_context_only,
    ContentAvailability.INDEPENDENT_RECORDING_OR_COPY to R.string.note_availability_independent,
)

@Composable
private fun problemText(problem: NoteProblem): String = stringResource(
    when (problem) {
        NoteProblem.TEXT_REQUIRED -> R.string.note_problem_text_required
        NoteProblem.TEXT_TOO_LONG -> R.string.note_problem_too_long
        NoteProblem.CHOOSE_AVAILABILITY -> R.string.note_problem_choose
        NoteProblem.SAVE_FAILED -> R.string.note_problem_save_failed
    },
)
