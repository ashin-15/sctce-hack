package org.sakshi.app.ui.catalog

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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.components.AttributionKind
import org.sakshi.app.ui.components.ChoiceButton
import org.sakshi.app.ui.components.ConfirmDialog
import org.sakshi.app.ui.components.DestructiveButton
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.EpistemicLabel
import org.sakshi.app.ui.components.FormDialog
import org.sakshi.app.ui.components.ActionTile
import org.sakshi.app.ui.components.HashStatusBadge
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.LabelValue
import org.sakshi.app.ui.components.LocalVaultBadge
import org.sakshi.app.ui.components.MenuAction
import org.sakshi.app.ui.components.MoreInfo
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.OverflowMenuButton
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.ProgressBlock
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiBrandLogo
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SakshiTextField
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.SensitiveMediaShield
import org.sakshi.app.ui.components.StatTile
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.TextEntryDialog
import org.sakshi.app.ui.components.TimelineAnchorDot
import org.sakshi.app.ui.components.TimelineNodeState
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.components.TriStateAttributionBadge
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus

private enum class CatalogDialog { Confirm, Destructive, Entry, Form }

/**
 * Every component and all five epistemic treatments on one scrolling screen, for reviewing the design on a device.
 * Reached only in debug builds, by a long press on the app name in the case list. Nothing here is saved.
 */
@Composable
fun DesignCatalog(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<CatalogDialog?>(null) }
    var entry by remember { mutableStateOf("") }
    BackHandler(onBack = onClose)
    SakshiScaffold(title = stringResource(R.string.catalog_title), modifier = modifier, onBack = onClose) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            item { Group(R.string.catalog_calm_sanctuary) { CalmSanctuarySamples() } }
            item { Group(R.string.catalog_type) { TypeScale() } }
            item { Group(R.string.catalog_epistemic) { EpistemicSamples() } }
            item { Group(R.string.catalog_buttons) { Buttons { dialog = it } } }
            item { Group(R.string.catalog_notes) { Notes() } }
            item { Group(R.string.catalog_surfaces) { Surfaces() } }
            item { Group(R.string.catalog_stitch) { StitchSamples() } }
            item { Group(R.string.catalog_choices) { Choices() } }
            item {
                Group(R.string.catalog_progress) {
                    ProgressBlock(2, 5, stringResource(R.string.import_saving_progress, 2, 5), onCancel = {})
                    ProgressBlock(0, 0, stringResource(R.string.analysis_running), onCancel = {}, indeterminate = true)
                }
            }
        }
    }
    when (dialog) {
        CatalogDialog.Confirm -> ConfirmDialog(
            title = stringResource(R.string.catalog_dialog_title),
            body = stringResource(R.string.catalog_dialog_body),
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { dialog = null },
            onDismiss = { dialog = null },
            destructive = false,
        )
        CatalogDialog.Destructive -> ConfirmDialog(
            title = stringResource(R.string.catalog_dialog_title),
            body = stringResource(R.string.catalog_dialog_body),
            confirmLabel = stringResource(R.string.case_delete),
            onConfirm = { dialog = null },
            onDismiss = { dialog = null },
            destructive = true,
        )
        CatalogDialog.Entry -> TextEntryDialog(
            title = stringResource(R.string.catalog_dialog_title),
            label = stringResource(R.string.dialog_title_label),
            value = entry,
            onValueChange = { entry = it.take(CATALOG_ENTRY_LIMIT) },
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { dialog = null },
            onDismiss = { dialog = null },
            supportingText = stringResource(R.string.dialog_title_counter, entry.length, CATALOG_ENTRY_LIMIT),
        )
        CatalogDialog.Form -> FormDialog(
            title = stringResource(R.string.catalog_dialog_title),
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { dialog = null },
            onDismiss = { dialog = null },
        ) {
            SupportingText(stringResource(R.string.catalog_form_body))
            RadioRow(stringResource(R.string.catalog_choice_one), selected = true, onSelect = {})
            RadioRow(stringResource(R.string.catalog_choice_two), selected = false, onSelect = {})
        }
        null -> Unit
    }
}

private const val CATALOG_ENTRY_LIMIT = 40

@Composable
private fun Group(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(stringResource(title))
        content()
    }
}

@Composable
private fun TypeScale() {
    val styles: List<Pair<String, TextStyle>> = listOf(
        stringResource(R.string.catalog_style_screen) to MaterialTheme.typography.headlineSmall,
        stringResource(R.string.catalog_style_section) to MaterialTheme.typography.titleMedium,
        stringResource(R.string.catalog_style_body) to MaterialTheme.typography.bodyLarge,
        stringResource(R.string.catalog_style_supporting) to MaterialTheme.typography.bodyMedium,
        stringResource(R.string.catalog_style_label) to MaterialTheme.typography.labelLarge,
    )
    styles.forEach { (name, style) -> Text(name, style = style) }
    Text(stringResource(R.string.catalog_sample_hindi), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.catalog_sample_malayalam), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun EpistemicSamples() {
    val samples = listOf(
        EpistemicStatus.OBSERVED to R.string.catalog_sample_observed,
        EpistemicStatus.USER_REPORTED to R.string.catalog_sample_user_reported,
        EpistemicStatus.INFERRED to R.string.catalog_sample_inferred,
        EpistemicStatus.PATTERN to R.string.catalog_sample_pattern,
        EpistemicStatus.UNKNOWN to R.string.catalog_sample_unknown,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        samples.forEach { (status, text) -> EpistemicBlock(status, stringResource(text)) }
        SupportingText(stringResource(R.string.catalog_labels_alone))
        EpistemicStatus.entries.forEach { EpistemicLabel(it) }
    }
}

@Composable
private fun Buttons(openDialog: (CatalogDialog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton(stringResource(R.string.catalog_primary), onClick = {})
        SecondaryButton(stringResource(R.string.catalog_secondary), onClick = {})
        QuietTextButton(stringResource(R.string.catalog_quiet), onClick = {})
        DestructiveButton(stringResource(R.string.case_delete), onClick = {})
        PrimaryButton(stringResource(R.string.catalog_disabled), onClick = {}, enabled = false)
        SecondaryButton(stringResource(R.string.catalog_open_confirm), onClick = { openDialog(CatalogDialog.Confirm) })
        SecondaryButton(stringResource(R.string.catalog_open_destructive), onClick = { openDialog(CatalogDialog.Destructive) })
        SecondaryButton(stringResource(R.string.catalog_open_entry), onClick = { openDialog(CatalogDialog.Entry) })
        SecondaryButton(stringResource(R.string.catalog_open_form), onClick = { openDialog(CatalogDialog.Form) })
    }
}

@Composable
private fun Choices() {
    var option by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.selectableGroup()) {
            RadioRow(stringResource(R.string.catalog_choice_one), selected = option == 0, onSelect = { option = 0 })
            RadioRow(stringResource(R.string.catalog_choice_two), selected = option == 1, onSelect = { option = 1 })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ChoiceButton(stringResource(R.string.catalog_choice_one), option == 0, stringResource(R.string.filter_selected), { option = 0 }, Modifier.weight(1f))
            ChoiceButton(stringResource(R.string.catalog_choice_two), option == 1, stringResource(R.string.filter_selected), { option = 1 }, Modifier.weight(1f))
        }
        SakshiTextField(text, { text = it }, stringResource(R.string.catalog_field_label))
    }
}

@Composable
private fun Notes() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusNote(NoteKind.Info, stringResource(R.string.catalog_note_info))
        StatusNote(NoteKind.Caution, stringResource(R.string.catalog_note_caution))
        StatusNote(NoteKind.Problem, stringResource(R.string.catalog_note_problem))
    }
}

@Composable
private fun Surfaces() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SakshiCard {
            Row(
                Modifier.padding(start = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ScreenTitle(stringResource(R.string.catalog_card))
                    SupportingText(stringResource(R.string.catalog_card_body))
                    LabelValue(stringResource(R.string.import_label_name), stringResource(R.string.import_reported_none))
                }
                OverflowMenuButton(
                    stringResource(R.string.catalog_menu_description),
                    listOf(MenuAction(stringResource(R.string.case_rename)) {}, MenuAction(stringResource(R.string.case_delete), destructive = true) {}),
                )
            }
        }
        SakshiCard(quiet = true) {
            SupportingText(stringResource(R.string.catalog_card_quiet), Modifier.padding(Spacing.lg))
        }
        EmptyState(stringResource(R.string.catalog_empty_title), stringResource(R.string.catalog_empty_body))
    }
}

@Composable
private fun StitchSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusChip(stringResource(R.string.catalog_chip_neutral))
            StatusChip(stringResource(R.string.catalog_chip_brand), tone = Tone.Brand)
            StatusChip(stringResource(R.string.catalog_chip_success), tone = Tone.Success, icon = Icons.Default.Check)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusChip(stringResource(R.string.catalog_chip_warning), tone = Tone.Warning, icon = Icons.Default.Warning)
            StatusChip(stringResource(R.string.catalog_chip_critical), tone = Tone.Critical)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Tone.entries.forEach { IconTile(ImageVector.vectorResource(R.drawable.ic_document), tone = it) }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val tile = Modifier.weight(1f).fillMaxHeight()
            StatTile("12", stringResource(R.string.catalog_stat_one), tile)
            StatTile("3", stringResource(R.string.catalog_stat_two), tile)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val tile = Modifier.weight(1f).fillMaxHeight()
            ActionTile(ImageVector.vectorResource(R.drawable.ic_timeline), stringResource(R.string.catalog_action_one), {}, tile)
            ActionTile(ImageVector.vectorResource(R.drawable.ic_pattern), stringResource(R.string.catalog_action_two), {}, tile)
            ActionTile(ImageVector.vectorResource(R.drawable.ic_document), stringResource(R.string.catalog_action_disabled), {}, tile, enabled = false)
        }
        MoreInfo(stringResource(R.string.catalog_more_info)) {
            SupportingText(stringResource(R.string.catalog_more_info_body))
        }
    }
}

@Composable
private fun CalmSanctuarySamples() {
    var isShielded by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // Logo & Air-Gap Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SakshiBrandLogo(size = 36.dp)
            Text("Sakshi", style = MaterialTheme.typography.titleLarge)
            LocalVaultBadge()
        }

        // Tri-State Attribution Badges
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TriStateAttributionBadge(AttributionKind.Observed, stringResource(R.string.catalog_badge_observed))
            TriStateAttributionBadge(AttributionKind.Inferred, stringResource(R.string.catalog_badge_inferred))
            TriStateAttributionBadge(AttributionKind.Confirmed, stringResource(R.string.catalog_badge_confirmed))
        }

        // Cryptographic Hash Status Indicator
        HashStatusBadge(hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", verified = true)

        // Timeline progression anchors
        SupportingText(stringResource(R.string.catalog_timeline_anchors))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TimelineAnchorDot(TimelineNodeState.Confirmed)
                Text("Confirmed", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TimelineAnchorDot(TimelineNodeState.WarningEscalation)
                Text("Signal", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TimelineAnchorDot(TimelineNodeState.PassiveMetadata)
                Text("Metadata", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Shielded media / excerpt preview
        SupportingText(stringResource(R.string.catalog_shield_preview))
        SensitiveMediaShield(
            isShielded = isShielded,
            onToggleShield = { isShielded = !isShielded },
        ) {
            SakshiCard {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.catalog_shield_sample_text), style = MaterialTheme.typography.bodyMedium)
                    HashStatusBadge(hash = "3b5f4c20d7e411b2", verified = true)
                }
            }
        }
    }
}

