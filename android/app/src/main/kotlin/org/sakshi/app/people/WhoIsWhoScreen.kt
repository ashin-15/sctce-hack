package org.sakshi.app.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.timeline.formatDay
import org.sakshi.app.ui.components.ConfirmDialog
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.FormDialog
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SakshiTextField
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.SenderSelector
import org.sakshi.core.vault.StoredActor

class WhoIsWhoActions(
    val back: () -> Unit,
    val markOwn: (SenderSelector) -> Unit,
    val assignToPerson: (SenderSelector, ActorId) -> Unit,
    val assignToNewPerson: (SenderSelector, String) -> Unit,
    val undo: (AssignmentView) -> Unit,
    val noticeShown: () -> Unit,
)

private sealed interface PeopleDialog {
    class Name(val selector: SenderSelector) : PeopleDialog

    class Undo(val assignment: AssignmentView) : PeopleDialog
}

@Composable
fun WhoIsWhoScreen(state: WhoIsWhoState, zone: ZoneId, actions: WhoIsWhoActions, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<PeopleDialog?>(null) }
    BackHandler {
        if (dialog != null) {
            dialog = null
        } else {
            actions.back()
        }
    }
    SakshiScaffold(
        title = stringResource(R.string.people_title),
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
        if (state.loaded) Content(state, zone, actions, onDialog = { dialog = it })
    }
    when (val current = dialog) {
        is PeopleDialog.Name -> NameDialog(
            people = state.people.filter { it.associationReview == AssociationReview.CONFIRMED },
            onExisting = {
                dialog = null
                actions.assignToPerson(current.selector, it)
            },
            onNew = {
                dialog = null
                actions.assignToNewPerson(current.selector, it)
            },
            onDismiss = { dialog = null },
        )
        is PeopleDialog.Undo -> ConfirmDialog(
            title = stringResource(R.string.people_undo_title),
            body = stringResource(R.string.people_undo_body),
            confirmLabel = stringResource(R.string.people_undo),
            onConfirm = {
                dialog = null
                actions.undo(current.assignment)
            },
            onDismiss = { dialog = null },
            destructive = false,
        )
        null -> Unit
    }
}

@Composable
private fun Content(state: WhoIsWhoState, zone: ZoneId, actions: WhoIsWhoActions, onDialog: (PeopleDialog) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "statement") { SupportingText(stringResource(R.string.people_statement)) }
        item(key = "claims-heading") { SectionHeader(stringResource(R.string.people_claims_heading), Modifier.padding(top = Spacing.sm)) }
        if (state.claims.isEmpty()) {
            item(key = "claims-empty") { EmptyState(stringResource(R.string.people_claims_empty_title), stringResource(R.string.people_claims_empty_body)) }
        }
        items(state.claims, key = { "claim-${it.selector.conversationScopeId?.value}-${it.selector.sourceApp}-${it.selector.displayLabel}" }) { claim ->
            ClaimCard(claim, zone, locale, actions, onName = { onDialog(PeopleDialog.Name(claim.selector)) })
        }
        if (state.people.isNotEmpty()) {
            item(key = "people-heading") { SectionHeader(stringResource(R.string.people_named_heading), Modifier.padding(top = Spacing.md)) }
            items(state.people, key = { "person-${it.id.value}" }) { person -> PersonRow(person.displayLabel) }
        }
        if (state.assignments.isNotEmpty()) {
            item(key = "linked-heading") { SectionHeader(stringResource(R.string.people_linked_heading), Modifier.padding(top = Spacing.md)) }
            items(state.assignments, key = { "link-${it.actorId.value}-${it.selector.conversationScopeId?.value}-${it.selector.displayLabel}" }) { link ->
                LinkCard(link, zone, locale, onUndo = { onDialog(PeopleDialog.Undo(link)) })
            }
        }
    }
}

@Composable
private fun sourceLine(kind: SourceKind, savedAt: Instant?, zone: ZoneId, locale: Locale): String {
    val date = savedAt?.let { formatDay(it.atZone(zone).toLocalDate(), locale) } ?: stringResource(R.string.detail_date_unknown)
    return stringResource(
        when (kind) {
            SourceKind.SELECTED_EXPORT -> R.string.people_source_export
            SourceKind.SELECTED_TEXT -> R.string.people_source_text
            else -> R.string.people_source_other
        },
        date,
    )
}

@Composable
private fun PersonRow(name: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconTile(Icons.Default.Person, size = PERSON_TILE_SIZE)
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

private val PERSON_TILE_SIZE = 32.dp

@Composable
private fun ClaimCard(claim: SenderClaimView, zone: ZoneId, locale: Locale, actions: WhoIsWhoActions, onName: () -> Unit) {
    SakshiCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconTile(Icons.Default.Person, tone = if (claim.markedOwn) Tone.Success else Tone.Brand)
                Column(Modifier.weight(1f)) {
                    Text(claim.selector.displayLabel.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    SupportingText(sourceLine(claim.sourceKind, claim.savedAt, zone, locale))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusChip(pluralStringResource(R.plurals.people_message_count, claim.messageCount, claim.messageCount))
                if (claim.markedOwn) StatusChip(stringResource(R.string.people_marked_own), tone = Tone.Success, icon = Icons.Default.Check)
                if (claim.focused) StatusChip(stringResource(R.string.people_focused), tone = Tone.Brand)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(stringResource(R.string.people_this_is_me), { actions.markOwn(claim.selector) })
                SecondaryButton(stringResource(R.string.people_name_person), onName)
            }
        }
    }
}

@Composable
private fun LinkCard(link: AssignmentView, zone: ZoneId, locale: Locale, onUndo: () -> Unit) {
    SakshiCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IconTile(Icons.Default.Person)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.people_linked_line, link.selector.displayLabel.orEmpty(), link.personLabel), style = MaterialTheme.typography.titleSmall)
                    SupportingText(sourceLine(link.sourceKind, link.savedAt, zone, locale))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusChip(pluralStringResource(R.plurals.people_message_count, link.eventIds.size, link.eventIds.size))
            }
            SecondaryButton(stringResource(R.string.people_undo), onUndo)
        }
    }
}

@Composable
private fun NameDialog(
    people: List<StoredActor>,
    onExisting: (ActorId) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf<ActorId?>(null) }
    var creating by remember { mutableStateOf(people.isEmpty()) }
    var name by remember { mutableStateOf("") }
    val canConfirm = if (creating) name.isNotBlank() else chosen != null
    FormDialog(
        title = stringResource(R.string.people_name_title),
        confirmLabel = stringResource(R.string.dialog_save),
        onConfirm = { if (creating) onNew(name) else chosen?.let(onExisting) },
        onDismiss = onDismiss,
        confirmEnabled = canConfirm,
    ) {
        SupportingText(stringResource(R.string.people_statement))
        Column(Modifier.selectableGroup()) {
            people.forEach { person ->
                RadioRow(person.displayLabel, selected = !creating && chosen == person.id, onSelect = { creating = false; chosen = person.id })
            }
            RadioRow(stringResource(R.string.people_new_person), selected = creating, onSelect = { creating = true })
        }
        if (creating) SakshiTextField(name, { name = it.take(WhoIsWhoViewModel.MAX_NAME_LENGTH) }, stringResource(R.string.people_name_label))
    }
}
