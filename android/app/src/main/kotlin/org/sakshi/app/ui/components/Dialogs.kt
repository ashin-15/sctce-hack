package org.sakshi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.sakshi.app.R
import org.sakshi.app.ui.theme.Spacing

@Composable
private fun DialogButton(text: String, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = Spacing.touchTarget),
        colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
    ) { Text(text) }
}

/**
 * A question with a confirming and a cancelling answer. When [destructive] the confirm word is drawn in the error
 * colour, outlined by nothing else: the word does the work.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { DialogButton(confirmLabel, onConfirm, destructive = destructive) },
        dismissButton = { DialogButton(stringResource(R.string.dialog_cancel), onDismiss) },
    )
}

/**
 * A dialog with one text field. The caller owns [value], so it decides whether the text may ever be saved; nothing
 * here writes it to saved instance state. [supportingText] is usually a character counter.
 */
@Composable
fun TextEntryDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    confirmEnabled: Boolean = value.isNotBlank(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { DialogButton(confirmLabel, onConfirm, enabled = confirmEnabled) },
        dismissButton = { DialogButton(stringResource(R.string.dialog_cancel), onDismiss) },
    )
}

/**
 * A dialog whose body is anything the caller puts in it, such as a list of choices or a few fields. The body scrolls
 * when it is taller than the dialog. The confirm button is disabled until [confirmEnabled].
 */
@Composable
fun FormDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
        },
        confirmButton = { DialogButton(confirmLabel, onConfirm, enabled = confirmEnabled) },
        dismissButton = { DialogButton(stringResource(R.string.dialog_cancel), onDismiss) },
    )
}
