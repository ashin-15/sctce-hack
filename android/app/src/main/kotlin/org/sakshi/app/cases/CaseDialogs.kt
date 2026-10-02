package org.sakshi.app.cases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.sakshi.app.R
import org.sakshi.app.ui.components.ConfirmDialog
import org.sakshi.app.ui.components.TextEntryDialog
import org.sakshi.core.vault.CaseRepository

/**
 * Title entry for creating or renaming a case. The text is held with `remember`, not `rememberSaveable`, so a case
 * title is never written to saved instance state.
 */
@Composable
fun TitleDialog(
    heading: Int,
    confirmLabel: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    TextEntryDialog(
        title = stringResource(heading),
        label = stringResource(R.string.dialog_title_label),
        value = text,
        onValueChange = { text = it.take(CaseRepository.MAX_TITLE_LENGTH) },
        confirmLabel = stringResource(confirmLabel),
        onConfirm = { onConfirm(text) },
        onDismiss = onDismiss,
        supportingText = stringResource(R.string.dialog_title_counter, text.length, CaseRepository.MAX_TITLE_LENGTH),
    )
}

@Composable
fun DeleteDialog(evidenceCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.dialog_delete_title),
        body = pluralStringResource(R.plurals.dialog_delete_body, evidenceCount, evidenceCount),
        confirmLabel = stringResource(R.string.case_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}
