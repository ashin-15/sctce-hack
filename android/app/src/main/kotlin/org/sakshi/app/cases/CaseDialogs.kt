package org.sakshi.app.cases

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.sakshi.app.R
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(heading)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(CaseRepository.MAX_TITLE_LENGTH) },
                label = { Text(stringResource(R.string.dialog_title_label)) },
                supportingText = {
                    Text(
                        stringResource(R.string.dialog_title_counter, text.length, CaseRepository.MAX_TITLE_LENGTH),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
fun DeleteDialog(evidenceCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(pluralStringResource(R.plurals.dialog_delete_body, evidenceCount, evidenceCount)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.case_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
