package org.sakshi.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.sakshi.app.ui.theme.Spacing

/** One option of a single-choice list: a radio button and its words in a row of at least 48 dp. Put several in a column. */
@Composable
fun RadioRow(text: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.md))
    }
}

/** One option of a list where several can be chosen: a checkbox and its words in a row of at least 48 dp. */
@Composable
fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

/** A setting that is on or off, with its words on the left and the switch on the right. */
@Composable
fun SwitchRow(text: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = Spacing.md))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A two-state button for a short row of filters. The chosen one is filled and also says "selected" to TalkBack, so the
 * state never rests on colour alone.
 */
@Composable
fun ChoiceButton(text: String, selected: Boolean, selectedWord: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val semantic = modifier.semantics { if (selected) stateDescription = selectedWord }
    if (selected) PrimaryButton(text, onClick, semantic) else SecondaryButton(text, onClick, semantic)
}

/** A single text field in the design's shape. The caller owns [value]; nothing here saves it. */
@Composable
fun SakshiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
    )
}
