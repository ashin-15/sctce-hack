package org.sakshi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.theme.Spacing

/** How serious a [StatusNote] is. A caution or problem says it in a word; colour and icon only echo it. */
enum class NoteKind { Info, Caution, Problem }

private val NOTE_ICON_SIZE = 18.dp

/**
 * A quiet inline note. [NoteKind.Info] is just the text in the muted colour. A caution or problem adds a small icon and
 * a bold word ("Caution", "Problem") before the text. Not a coloured banner.
 */
@Composable
fun StatusNote(kind: NoteKind, text: String, modifier: Modifier = Modifier) {
    if (kind == NoteKind.Info) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics(mergeDescendants = true) { },
        )
        return
    }
    val label = stringResource(if (kind == NoteKind.Caution) R.string.status_label_caution else R.string.status_label_problem)
    val tone = if (kind == NoteKind.Caution) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = tone, fontWeight = FontWeight.SemiBold)) { append(label) }
        append(". ")
        append(text)
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (kind == NoteKind.Caution) Icons.Default.Info else Icons.Default.Warning,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(NOTE_ICON_SIZE),
        )
        Text(annotated, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
