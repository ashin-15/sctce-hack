package org.sakshi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.theme.Spacing

/** The emphasis of a [StatusChip] or [IconTile]. Colour only echoes it: the text or the icon always says it too. */
enum class Tone { Neutral, Brand, Success, Warning, Critical }

private class ToneColours(val container: Color, val content: Color)

@Composable
private fun Tone.colours(): ToneColours {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        Tone.Neutral -> ToneColours(scheme.surfaceVariant, scheme.onSurfaceVariant)
        Tone.Brand -> ToneColours(scheme.primaryContainer, scheme.onPrimaryContainer)
        Tone.Success -> ToneColours(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Tone.Warning -> ToneColours(scheme.secondaryContainer, scheme.onSecondaryContainer)
        Tone.Critical -> ToneColours(scheme.errorContainer, scheme.onErrorContainer)
    }
}

private val CHIP_ICON_SIZE = 14.dp
private val DEFAULT_TILE_SIZE = 40.dp

/** A pill with a short status word or two, in place of a sentence. */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, icon: ImageVector? = null) {
    val colours = tone.colours()
    Surface(modifier = modifier, shape = CircleShape, color = colours.container, contentColor = colours.content) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(CHIP_ICON_SIZE))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A small tinted rounded square that holds a decorative [icon]. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tone: Tone = Tone.Brand, size: Dp = DEFAULT_TILE_SIZE) {
    val colours = tone.colours()
    Box(
        modifier = modifier.size(size).clip(MaterialTheme.shapes.medium).background(colours.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colours.content, modifier = Modifier.size(size / 2))
    }
}

/** A compact tile with a big number and a tiny label, read as one phrase by TalkBack. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A clickable compact card: an [IconTile] above a one or two word [label]. At least 48 dp high. */
@Composable
fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = Spacing.touchTarget)
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconTile(icon, tone = if (enabled) Tone.Brand else Tone.Neutral)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A disclosure row that starts collapsed: [label] and a chevron in a quiet colour, in a 48 dp target. Tapping it
 * shows or hides [content]. TalkBack hears "expanded" or "collapsed". Long explanations live here instead of on the
 * screen itself.
 */
@Composable
fun MoreInfo(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.more_info_expanded else R.string.more_info_collapsed)
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = Spacing.touchTarget)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) Column(Modifier.padding(bottom = Spacing.sm), content = content)
    }
}
