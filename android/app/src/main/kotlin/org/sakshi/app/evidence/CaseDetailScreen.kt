package org.sakshi.app.evidence

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.sakshi.app.R
import org.sakshi.app.ui.components.ActionTile
import org.sakshi.app.ui.components.ConfirmDialog
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.MoreInfo
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.TopAction
import org.sakshi.app.ui.theme.Spacing

/** Dialog the screen is currently showing. Holds only an evidence id, never text from the evidence. */
private sealed interface Dialog {
    data object Paste : Dialog

    class Delete(val evidenceId: String) : Dialog
}

class CaseDetailActions(
    val back: () -> Unit,
    val lock: () -> Unit,
    val check: (String) -> Unit,
    val delete: (String) -> Unit,
    val messageShown: () -> Unit,
    val pasted: (String) -> Unit,
    val add: AddEvidenceCallbacks,
    val analyse: (String) -> Unit,
    val openTimeline: () -> Unit,
    val openPatterns: () -> Unit,
    val openReport: () -> Unit,
    val openSearch: () -> Unit,
)

@Composable
fun CaseDetailScreen(state: CaseDetailUiState, actions: CaseDetailActions, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler {
        if (dialog != null) {
            dialog = null
        } else {
            actions.back()
        }
    }

    val notice = state.message?.let { stringResource(R.string.message_delete_failed) }
    LaunchedEffect(state.message) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            actions.messageShown()
        }
    }

    val canAdd = state.loaded && state.title != null
    SakshiScaffold(
        title = state.title.orEmpty(),
        modifier = modifier,
        onBack = actions.back,
        actions = listOf(
            TopAction(stringResource(R.string.action_search), actions.openSearch, icon = Icons.Default.Search),
            TopAction(stringResource(R.string.action_lock), actions.lock, icon = Icons.Default.Lock),
        ),
        snackbarHostState = snackbar,
        bottomBar = if (canAdd) {
            { AddEvidenceActions(enabled = !state.archived, callbacks = actions.add, onPaste = { dialog = Dialog.Paste }) }
        } else {
            null
        },
    ) {
        when {
            !state.loaded -> Unit
            state.title == null -> SupportingText(stringResource(R.string.detail_missing), Modifier.padding(Spacing.gutter))
            else -> Content(state, actions, onDelete = { dialog = Dialog.Delete(it) })
        }
    }

    when (val current = dialog) {
        Dialog.Paste -> PasteDialog(onReview = { dialog = null; actions.pasted(it) }, onDismiss = { dialog = null })
        is Dialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.delete_item_title),
            body = stringResource(R.string.delete_item_body),
            confirmLabel = stringResource(R.string.evidence_delete),
            onConfirm = { dialog = null; actions.delete(current.evidenceId) },
            onDismiss = { dialog = null },
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun Content(state: CaseDetailUiState, actions: CaseDetailActions, onDelete: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "views") {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                ActionTile(ImageVector.vectorResource(R.drawable.ic_timeline), stringResource(R.string.detail_timeline), actions.openTimeline, tile)
                ActionTile(ImageVector.vectorResource(R.drawable.ic_pattern), stringResource(R.string.detail_patterns), actions.openPatterns, tile)
                ActionTile(ImageVector.vectorResource(R.drawable.ic_document), stringResource(R.string.detail_make_report), actions.openReport, tile)
            }
        }
        if (state.archived) {
            item(key = "archived") { StatusNote(NoteKind.Info, stringResource(R.string.detail_archived)) }
        }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(stringResource(R.string.detail_empty_title), stringResource(R.string.detail_empty_body))
            }
        } else {
            item(key = "heading") { SectionHeader(stringResource(R.string.detail_evidence_heading), Modifier.padding(top = Spacing.sm)) }
        }
        items(state.items, key = { it.id }) { row ->
            EvidenceItem(
                row = row,
                integrity = state.integrity[row.id],
                onCheck = { actions.check(row.id) },
                onDelete = { onDelete(row.id) },
                onAnalyse = { actions.analyse(row.id) },
            )
        }
        if (state.items.isNotEmpty()) {
            item(key = "integrity-about") {
                MoreInfo(stringResource(R.string.detail_integrity_about)) {
                    SupportingText(stringResource(R.string.detail_integrity_caveat))
                }
            }
        }
    }
}
