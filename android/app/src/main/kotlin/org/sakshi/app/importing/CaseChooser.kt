package org.sakshi.app.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.sakshi.app.R
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.CaseSummary

/** Fixed destination inside a case, or a choice among active cases with inline creation of a new one. */
@Composable
fun CaseChooser(state: ImportUiState.Previewing, cases: List<CaseSummary>, actions: ImportActions) {
    if (state.fixedCaseId != null) {
        val title = cases.firstOrNull { it.id == state.fixedCaseId }?.title.orEmpty()
        SectionHeader(stringResource(R.string.import_case_fixed, title))
        return
    }
    val active = cases.active()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionHeader(stringResource(R.string.import_case_heading))
        if (active.isEmpty()) SupportingText(stringResource(R.string.import_case_none))
        Column(Modifier.selectableGroup()) {
            active.forEach { case ->
                ChoiceRow(
                    label = case.title,
                    selected = (state.choice as? CaseChoice.Existing)?.caseId == case.id,
                    onSelect = { actions.chooseCase(case.id) },
                )
            }
            ChoiceRow(
                label = stringResource(R.string.import_case_new),
                selected = state.choice is CaseChoice.New,
                onSelect = { if (state.choice !is CaseChoice.New) actions.chooseNewCase("") },
            )
        }
        (state.choice as? CaseChoice.New)?.let { new ->
            OutlinedTextField(
                value = new.title,
                onValueChange = actions.chooseNewCase,
                label = { Text(stringResource(R.string.dialog_title_label)) },
                supportingText = {
                    Text(stringResource(R.string.dialog_title_counter, new.title.length, CaseRepository.MAX_TITLE_LENGTH))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.choice == CaseChoice.None) {
            StatusNote(NoteKind.Info, stringResource(R.string.import_case_required))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.md))
    }
}
