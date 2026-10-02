package org.sakshi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.sakshi.app.ui.theme.Spacing

/** A section title. Announced as a heading, so TalkBack users can jump between sections. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** A screen title inside the content, larger than a section title. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = textAlign, modifier = modifier.semantics { heading() })
}

/** Quiet explanatory text in the muted colour. */
@Composable
fun SupportingText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** A label above a value, stacked so neither is squeezed at large font sizes. Read as one phrase by TalkBack. */
@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Shown when a list has nothing in it yet: a plain title and a sentence, and optionally the next step. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionHeader(title)
        SupportingText(body)
        action?.invoke()
    }
}
