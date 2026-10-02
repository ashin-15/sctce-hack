package org.sakshi.app.evidence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.sakshi.acquisition.importer.ImportLimits
import org.sakshi.app.R
import org.sakshi.app.ui.components.TextEntryDialog

/** Multi-line entry for pasted text. The text is held with `remember`, never in saved instance state. */
@Composable
fun PasteDialog(onReview: (String) -> Unit, onDismiss: () -> Unit) {
    val limit = ImportLimits().maxTextChars
    var text by remember { mutableStateOf("") }
    TextEntryDialog(
        title = stringResource(R.string.paste_title),
        label = stringResource(R.string.paste_label),
        value = text,
        onValueChange = { text = it.take(limit) },
        confirmLabel = stringResource(R.string.paste_review),
        onConfirm = { onReview(text) },
        onDismiss = onDismiss,
        supportingText = pluralStringResource(R.plurals.paste_counter, limit, text.length, limit),
        singleLine = false,
        minLines = 5,
        maxLines = 10,
    )
}
