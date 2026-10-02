package org.sakshi.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.sakshi.app.R

/** How serious a [StatusNote] is. The word in front of the text says it; colour only echoes it. */
enum class NoteKind { Info, Caution, Problem }

/** A quiet inline note: a bold word ("Note", "Caution", "Problem") followed by the text. Not a coloured banner. */
@Composable
fun StatusNote(kind: NoteKind, text: String, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (kind) {
            NoteKind.Info -> R.string.status_label_info
            NoteKind.Caution -> R.string.status_label_caution
            NoteKind.Problem -> R.string.status_label_problem
        },
    )
    val labelColor = when (kind) {
        NoteKind.Info -> MaterialTheme.colorScheme.onSurfaceVariant
        NoteKind.Caution -> MaterialTheme.colorScheme.secondary
        NoteKind.Problem -> MaterialTheme.colorScheme.error
    }
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.SemiBold)) { append(label) }
        append(". ")
        append(text)
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium, modifier = modifier.semantics(mergeDescendants = true) { })
}
