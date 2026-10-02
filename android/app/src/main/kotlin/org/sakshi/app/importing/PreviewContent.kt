package org.sakshi.app.importing

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.app.R
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.LabelValue
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.formatByteSize
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.vault.CaseSummary

/** Step 3 of the import sequence: nothing has been saved yet. */
@Composable
fun PreviewContent(state: ImportUiState.Previewing, cases: List<CaseSummary>, actions: ImportActions, modifier: Modifier = Modifier) {
    SakshiScaffold(
        title = null,
        modifier = modifier,
        onBack = actions.dismiss,
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PrimaryButton(stringResource(R.string.import_save), actions.save, enabled = state.canSave)
                    QuietTextButton(stringResource(R.string.dialog_cancel), actions.dismiss, Modifier.fillMaxWidth())
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ScreenTitle(stringResource(R.string.import_title))
                    Text(stringResource(R.string.import_statement), style = MaterialTheme.typography.bodyLarge)
                    StatusNote(NoteKind.Info, stringResource(R.string.import_reported_note))
                }
            }
            items(state.batch.items, key = { "item-${it.index}" }) { item ->
                ItemRow(item, state.batch.mechanism, item.index in state.selected, actions.toggle)
            }
            item(key = "case") { CaseChooser(state, cases, actions) }
        }
    }
}

@Composable
private fun ItemRow(item: PendingItem, mechanism: ImportMechanism, checked: Boolean, onToggle: (Int) -> Unit) {
    val selectable = item !is PendingItem.Rejected
    SakshiCard(quiet = !selectable) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget).toggleable(
                value = checked,
                enabled = selectable,
                role = Role.Checkbox,
                onValueChange = { onToggle(item.index) },
            ).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = selectable)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (item) {
                    is PendingItem.Stream -> StreamDetails(item)
                    is PendingItem.Text -> TextDetails(item, mechanism)
                    is PendingItem.Rejected -> {
                        Text(stringResource(R.string.import_rejected_heading), style = MaterialTheme.typography.titleSmall)
                        SupportingText(rejectionText(item.reason))
                    }
                }
            }
        }
    }
}

/** Name, type and size are what the sending app claimed, so they sit under a heading that says so. */
@Composable
private fun StreamDetails(item: PendingItem.Stream) {
    val none = stringResource(R.string.import_reported_none)
    val locale = LocalConfiguration.current.locales[0]
    SupportingText(stringResource(R.string.import_reported_group))
    LabelValue(stringResource(R.string.import_label_name), item.displayNameClaim ?: none)
    LabelValue(stringResource(R.string.import_label_type), item.declaredMime ?: none)
    LabelValue(stringResource(R.string.import_label_size), item.sizeClaim?.let { formatByteSize(it, locale) } ?: none)
}

@Composable
private fun TextDetails(item: PendingItem.Text, mechanism: ImportMechanism) {
    val label = if (mechanism == ImportMechanism.PASTE) R.string.import_item_pasted_text else R.string.import_item_shared_text
    val preview = previewOf(item.text)
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
    SupportingText(pluralStringResource(R.plurals.import_text_length, item.text.length, item.text.length))
    EpistemicBlock(EpistemicStatus.OBSERVED, preview)
    if (preview.length < item.text.length) {
        SupportingText(pluralStringResource(R.plurals.import_text_preview_note, item.text.length, preview.length, item.text.length))
    }
}
