package org.sakshi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.sakshi.app.ui.theme.Spacing

/** One entry of an [OverflowMenuButton]. [destructive] entries are drawn in the error colour. */
class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * Three vertical dots in a 48 dp target that opens a menu of [items]. Drawn directly because the icon library is not a
 * dependency. [contentDescription] names what the menu belongs to, for TalkBack.
 */
@Composable
fun OverflowMenuButton(contentDescription: String, items: List<MenuAction>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
                val radius = 2.dp.toPx()
                listOf(5.dp, 12.dp, 19.dp).forEach { y -> drawCircle(colour, radius, Offset(size.width / 2, y.toPx())) }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
