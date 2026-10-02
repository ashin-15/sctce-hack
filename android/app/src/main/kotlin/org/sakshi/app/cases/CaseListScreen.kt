package org.sakshi.app.cases

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import org.sakshi.app.R
import org.sakshi.app.ui.formatCreatedDate
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
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val notice = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { dialog = Dialog.Create }) { Text(stringResource(R.string.cases_new)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopRow(onLock)
            CaseList(
                state = state,
                archivedExpanded = archivedExpanded,
                onToggleArchived = { archivedExpanded = !archivedExpanded },
                onRename = { dialog = Dialog.Rename(it) },
                onArchive = onArchive,
                onUnarchive = onUnarchive,
                onDelete = { dialog = Dialog.Delete(it) },
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

@Composable
private fun TopRow(onLock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onLock, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_lock)) }
    }
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
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        if (state.active.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.cases_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.active, key = { it.id }) { case ->
            CaseRow(case, archived = false, onRename, onArchive, onUnarchive, onDelete)
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-heading") {
                ArchivedHeading(state.archived.size, archivedExpanded, onToggleArchived)
            }
            if (archivedExpanded) {
                items(state.archived, key = { it.id }) { case ->
                    CaseRow(case, archived = true, onRename, onArchive, onUnarchive, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ArchivedHeading(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val action = stringResource(if (expanded) R.string.cases_archived_collapse else R.string.cases_archived_expand)
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = action, onClick = onToggle)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.cases_archived_heading, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
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
) {
    val locale = LocalConfiguration.current.locales[0]
    val created = formatCreatedDate(case.createdAt, locale, ZoneId.systemDefault())
        ?.let { stringResource(R.string.case_created_on, it) }
        ?: stringResource(R.string.case_created_unknown)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(case.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(R.plurals.evidence_count, case.evidenceCount, case.evidenceCount) + " - " + created,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        CaseMenu(
            title = case.title,
            archived = archived,
            onRename = { onRename(case) },
            onToggleArchive = { if (archived) onUnarchive(case.id) else onArchive(case.id) },
            onDelete = { onDelete(case) },
        )
    }
}

@Composable
private fun CaseMenu(title: String, archived: Boolean, onRename: () -> Unit, onToggleArchive: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val description = stringResource(R.string.case_options, title)
        IconButton(
            onClick = { open = true },
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
        ) {
            OverflowGlyph()
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.case_rename)) }, onClick = { open = false; onRename() })
            DropdownMenuItem(
                text = { Text(stringResource(if (archived) R.string.case_unarchive else R.string.case_archive)) },
                onClick = { open = false; onToggleArchive() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.case_delete)) }, onClick = { open = false; onDelete() })
        }
    }
}

/** Three vertical dots. Drawn directly because the icon library is not a dependency. */
@Composable
private fun OverflowGlyph() {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val radius = 2.dp.toPx()
        listOf(5.dp, 12.dp, 19.dp).forEach { y -> drawCircle(colour, radius, Offset(size.width / 2, y.toPx())) }
    }
}
