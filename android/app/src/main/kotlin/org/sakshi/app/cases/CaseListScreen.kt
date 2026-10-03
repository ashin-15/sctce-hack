package org.sakshi.app.cases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import org.sakshi.app.BuildConfig
import org.sakshi.app.R
import org.sakshi.app.ui.catalog.DesignCatalog
import org.sakshi.app.ui.components.Workspace
import org.sakshi.app.ui.components.WorkspaceNavigation
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.LocalVaultBadge
import org.sakshi.app.ui.components.SakshiBrandLogo
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.MenuAction
import org.sakshi.app.ui.components.OverflowMenuButton
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.TopAction
import org.sakshi.app.ui.formatCreatedDate
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.CaseSummary

/** Dialog the list is currently showing. Holds a case only while its dialog is open. */
private sealed interface Dialog {
    data object Create : Dialog

    class Rename(val case: CaseSummary) : Dialog

    class Delete(val case: CaseSummary) : Dialog
}

@Composable
fun CaseListScreen(
    state: CaseListUiState,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMessageShown: () -> Unit,
    onLock: () -> Unit,
    onOpen: (String) -> Unit,
    onDeleteEverything: () -> Unit,
    onOpenAiModel: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenEvidence: (String) -> Unit = onOpen,
    onOpenTimeline: (String) -> Unit = onOpen,
    onOpenReports: (String) -> Unit = onOpen,
    onObservationSettings: (() -> Unit)? = null,
    onCaptureSettings: (() -> Unit)? = null,
    initialWorkspace: Workspace = Workspace.Home,
) {
    var workspace by rememberSaveable { mutableStateOf(initialWorkspace) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    var catalogOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = dialog != null || archivedExpanded || workspace != Workspace.Home) {
        if (dialog != null) {
            dialog = null
        } else if (archivedExpanded) {
            archivedExpanded = false
        } else {
            workspace = Workspace.Home
        }
    }

    val notice = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onMessageShown()
        }
    }

    if (BuildConfig.DEBUG && catalogOpen) {
        DesignCatalog(onClose = { catalogOpen = false }, modifier = modifier)
        return
    }

    SakshiScaffold(
        title = stringResource(R.string.app_name),
        modifier = modifier,
        actions = listOf(TopAction(stringResource(R.string.action_lock), onLock)),
        menuDescription = stringResource(R.string.cases_menu_options),
        menu = listOf(
            MenuAction(stringResource(R.string.ai_model_menu), onClick = onOpenAiModel),
            MenuAction(stringResource(R.string.delete_all_menu), destructive = true, onClick = onDeleteEverything),
        ),
        onTitleLongPress = if (BuildConfig.DEBUG) ({ catalogOpen = true }) else null,
        snackbarHostState = snackbar,
        bottomBar = {
            Column {
                PrimaryButton(
                    stringResource(R.string.cases_new),
                    { dialog = Dialog.Create },
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                )
                WorkspaceNavigation(workspace, { workspace = it })
            }
        },
    ) {
        CaseList(
            state = state,
            archivedExpanded = archivedExpanded,
            onToggleArchived = { archivedExpanded = !archivedExpanded },
            onRename = { dialog = Dialog.Rename(it) },
            onArchive = onArchive,
            onUnarchive = onUnarchive,
            onDelete = { dialog = Dialog.Delete(it) },
            onOpen = when (workspace) {
                Workspace.Evidence -> onOpenEvidence
                Workspace.Incidents -> onOpenTimeline
                Workspace.Reports -> onOpenReports
                Workspace.Home, Workspace.Vault -> onOpen
            },
            workspace = workspace,
            onObservationSettings = onObservationSettings,
            onCaptureSettings = onCaptureSettings,
        )
    }

    when (val current = dialog) {
        Dialog.Create -> TitleDialog(
            heading = R.string.dialog_new_title,
            confirmLabel = R.string.dialog_create,
            initial = "",
            onConfirm = { dialog = null; onCreate(it) },
            onDismiss = { dialog = null },
        )
        is Dialog.Rename -> TitleDialog(
            heading = R.string.dialog_rename_title,
            confirmLabel = R.string.dialog_save,
            initial = current.case.title,
            onConfirm = { dialog = null; onRename(current.case.id, it) },
            onDismiss = { dialog = null },
        )
        is Dialog.Delete -> DeleteDialog(
            evidenceCount = current.case.evidenceCount,
            onConfirm = { dialog = null; onDelete(current.case.id) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun messageText(message: CaseMessage): String = when (message) {
    CaseMessage.TITLE_BLANK -> stringResource(R.string.message_title_blank)
    CaseMessage.TITLE_TOO_LONG -> stringResource(R.string.message_title_too_long, CaseRepository.MAX_TITLE_LENGTH)
    CaseMessage.SAVE_FAILED -> stringResource(R.string.message_save_failed)
}

@Composable
private fun CaseList(
    state: CaseListUiState,
    archivedExpanded: Boolean,
    onToggleArchived: () -> Unit,
    onRename: (CaseSummary) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (CaseSummary) -> Unit,
    onOpen: (String) -> Unit,
    workspace: Workspace,
    onObservationSettings: (() -> Unit)?,
    onCaptureSettings: (() -> Unit)?,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "workspace-header") {
            WorkspaceHeader(workspace, state, onObservationSettings, onCaptureSettings)
        }
        if (state.active.isEmpty()) {
            item(key = "empty") {
                EmptyState(stringResource(R.string.cases_empty_title), stringResource(R.string.cases_empty_body))
            }
        }
        items(state.active, key = { it.id }) { case ->
            CaseRow(case, archived = false, onRename, onArchive, onUnarchive, onDelete, onOpen)
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-heading") {
                ArchivedHeading(state.archived.size, archivedExpanded, onToggleArchived)
            }
            if (archivedExpanded) {
                items(state.archived, key = { it.id }) { case ->
                    CaseRow(case, archived = true, onRename, onArchive, onUnarchive, onDelete, onOpen)
                }
            }
        }
    }
}

@Composable
private fun ArchivedHeading(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val action = stringResource(if (expanded) R.string.cases_archived_collapse else R.string.cases_archived_expand)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(R.string.cases_archived_heading, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CaseRow(
    case: CaseSummary,
    archived: Boolean,
    onRename: (CaseSummary) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (CaseSummary) -> Unit,
    onOpen: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val created = formatCreatedDate(case.createdAt, locale, ZoneId.systemDefault())
        ?.let { stringResource(R.string.case_created_on, it) }
        ?: stringResource(R.string.case_created_unknown)
    SakshiCard(quiet = archived) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.case_open), role = Role.Button) { onOpen(case.id) }
                    .heightIn(min = Spacing.touchTarget + Spacing.lg)
                    .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(case.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SupportingText(pluralStringResource(R.plurals.evidence_count, case.evidenceCount, case.evidenceCount) + " - " + created)
            }
            OverflowMenuButton(
                contentDescription = stringResource(R.string.case_options, case.title),
                items = listOf(
                    MenuAction(stringResource(R.string.case_rename)) { onRename(case) },
                    MenuAction(stringResource(if (archived) R.string.case_unarchive else R.string.case_archive)) {
                        if (archived) onUnarchive(case.id) else onArchive(case.id)
                    },
                    MenuAction(stringResource(R.string.case_delete), destructive = true) { onDelete(case) },
                ),
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
private fun WorkspaceHeader(
    workspace: Workspace,
    state: CaseListUiState,
    onObservationSettings: (() -> Unit)?,
    onCaptureSettings: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SakshiBrandLogo(size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(workspace.title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                LocalVaultBadge(text = stringResource(R.string.stitch_local))
            }
        }
        when (workspace) {
            Workspace.Home -> {
                SectionHeader(stringResource(R.string.stitch_welcome))
                SupportingText(stringResource(R.string.stitch_welcome_body))
                VaultSummary(state)
                if (onObservationSettings != null || onCaptureSettings != null) {
                    SectionHeader(stringResource(R.string.stitch_collection_heading))
                    SupportingText(stringResource(R.string.stitch_collection_body))
                    onObservationSettings?.let { PrimaryButton(stringResource(R.string.stitch_notifications), it) }
                    onCaptureSettings?.let { PrimaryButton(stringResource(R.string.stitch_capture), it) }
                }
            }
            Workspace.Vault -> VaultSummary(state)
            Workspace.Evidence -> SupportingText(stringResource(R.string.stitch_evidence_body))
            Workspace.Incidents -> SupportingText(stringResource(R.string.stitch_incidents_body))
            Workspace.Reports -> SupportingText(stringResource(R.string.stitch_reports_body))
        }
        SectionHeader(stringResource(R.string.stitch_cases_heading))
    }
}

@Composable
private fun VaultSummary(state: CaseListUiState) {
    SakshiCard {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(stringResource(R.string.stitch_vault_title))
            SupportingText(stringResource(R.string.stitch_vault_body))
            val evidenceCount = state.active.sumOf { it.evidenceCount }
            SupportingText(
                stringResource(
                    R.string.stitch_cases_summary,
                    pluralStringResource(R.plurals.stitch_active_cases, state.active.size, state.active.size),
                    pluralStringResource(R.plurals.stitch_evidence_items, evidenceCount, evidenceCount),
                ),
            )
        }
    }
}
