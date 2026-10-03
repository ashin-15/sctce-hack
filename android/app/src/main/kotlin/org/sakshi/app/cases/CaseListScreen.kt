package org.sakshi.app.cases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.ZoneId
import org.sakshi.app.BuildConfig
import org.sakshi.app.R
import org.sakshi.app.ui.catalog.DesignCatalog
import org.sakshi.app.ui.components.Workspace
import org.sakshi.app.ui.components.WorkspaceNavigation
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.ActionTile
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.StatTile
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.MenuAction
import org.sakshi.app.ui.components.OverflowMenuButton
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
        subtitle = stringResource(workspace.title),
        brand = true,
        actions = listOf(TopAction(stringResource(R.string.action_lock), onLock, icon = Icons.Default.Lock)),
        menuDescription = stringResource(R.string.cases_menu_options),
        menu = listOf(
            MenuAction(stringResource(R.string.ai_model_menu), onClick = onOpenAiModel),
            MenuAction(stringResource(R.string.delete_all_menu), destructive = true, onClick = onDeleteEverything),
        ),
        onTitleLongPress = if (BuildConfig.DEBUG) ({ catalogOpen = true }) else null,
        snackbarHostState = snackbar,
        bottomBar = { WorkspaceNavigation(workspace, { workspace = it }) },
    ) {
        Box(Modifier.fillMaxSize()) {
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
            ExtendedFloatingActionButton(
                onClick = { dialog = Dialog.Create },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cases_new)) },
            )
        }
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

/** Room left under the last list row so the "New case" button never covers it. */
private val FAB_CLEARANCE = Spacing.touchTarget + Spacing.xxl + Spacing.lg

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
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = FAB_CLEARANCE),
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
        ?: stringResource(R.string.case_created_unknown)
    val meta = stringResource(
        R.string.case_meta,
        pluralStringResource(R.plurals.evidence_count, case.evidenceCount, case.evidenceCount),
        created,
    )
    SakshiCard(quiet = archived) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.case_open), role = Role.Button) { onOpen(case.id) }
                    .heightIn(min = Spacing.touchTarget + Spacing.lg)
                    .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconTile(ImageVector.vectorResource(R.drawable.ic_folder), tone = if (archived) Tone.Neutral else Tone.Brand)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(case.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    SupportingText(meta)
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceHeader(
    workspace: Workspace,
    state: CaseListUiState,
    onObservationSettings: (() -> Unit)?,
    onCaptureSettings: (() -> Unit)?,
) {
    if (workspace != Workspace.Home && workspace != Workspace.Vault) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(bottom = Spacing.sm)) {
        VaultSummary(state)
        when (workspace) {
            Workspace.Home -> {
                if (onObservationSettings != null || onCaptureSettings != null) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        val tile = Modifier.weight(1f).fillMaxHeight()
                        onObservationSettings?.let {
                            ActionTile(Icons.Default.Notifications, stringResource(R.string.stitch_notifications), it, tile)
                        }
                        onCaptureSettings?.let {
                            ActionTile(ImageVector.vectorResource(R.drawable.ic_screen_capture), stringResource(R.string.stitch_capture), it, tile)
                        }
                        if (onObservationSettings == null || onCaptureSettings == null) Spacer(Modifier.weight(1f))
                    }
                }
                SectionHeader(stringResource(R.string.stitch_cases_heading), Modifier.padding(top = Spacing.sm))
            }
            Workspace.Vault -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatusChip(stringResource(R.string.stitch_vault_cipher), tone = Tone.Brand)
                StatusChip(stringResource(R.string.stitch_vault_keystore), tone = Tone.Brand)
                StatusChip(stringResource(R.string.stitch_vault_database), tone = Tone.Brand)
            }
            Workspace.Evidence, Workspace.Incidents, Workspace.Reports -> Unit
        }
    }
}

/** The two headline numbers: active cases and the evidence items inside them. */
@Composable
private fun VaultSummary(state: CaseListUiState) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        StatTile(state.active.size.toString(), stringResource(R.string.stitch_stat_cases), tile)
        StatTile(state.active.sumOf { it.evidenceCount }.toString(), stringResource(R.string.stitch_stat_evidence), tile)
    }
}
