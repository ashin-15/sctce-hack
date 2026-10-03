package org.sakshi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.theme.Spacing

/** An action in the top row, shown as an icon button or a text button. */
class TopAction(val label: String, val onClick: () -> Unit, val icon: ImageVector? = null)

/**
 * The frame of every screen: an optional top row (back arrow, [title] of up to two lines, at most two text [actions]),
 * the [content], an optional [bottomBar] pinned for one-handed reach, and a snackbar slot. It paints the background,
 * keeps clear of system bars, cutouts and the keyboard, and centres everything in a column no wider than
 * [Spacing.contentMaxWidth]. With [brand] the top row shows the logo, the [title] with a small [subtitle] under it and the
 * local-only badge (dropped at large font sizes) instead of a plain title. A non-empty [menu] adds a "more options" button, named [menuDescription], at the end of the top
 * row. [onTitleLongPress] is only for the debug design catalogue.
 */
@Composable
fun SakshiScaffold(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    brand: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: List<TopAction> = emptyList(),
    menuDescription: String? = null,
    menu: List<MenuAction> = emptyList(),
    onTitleLongPress: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxWidth().fillMaxHeight()) {
                if (title != null || onBack != null || actions.isNotEmpty() || menu.isNotEmpty()) {
                    TopRow(title, subtitle, brand, onBack, actions, menuDescription, menu, onTitleLongPress)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                if (snackbarHostState != null) SnackbarHost(snackbarHostState, Modifier.padding(horizontal = Spacing.sm))
                bottomBar?.invoke()
            }
        }
    }
}

@Composable
private fun TopRow(
    title: String?,
    subtitle: String?,
    brand: Boolean,
    onBack: (() -> Unit)?,
    actions: List<TopAction>,
    menuDescription: String?,
    menu: List<MenuAction>,
    onTitleLongPress: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.topRow).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) BackButton(onBack)
        val longPress = onTitleLongPress
        val titleModifier = Modifier.then(
            if (longPress == null) {
                Modifier
            } else {
                Modifier.pointerInput(longPress) { detectTapGestures(onLongPress = { longPress() }) }
                    .semantics { onLongClick(label = null) { longPress(); true } }
            },
        ).semantics { heading() }
        if (brand) {
            BrandTitle(title.orEmpty(), subtitle, Modifier.weight(1f), titleModifier)
        } else {
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm).then(titleModifier),
            )
        }
        actions.take(MAX_ACTIONS).forEach { action ->
            if (action.icon != null) {
                IconButton(
                    onClick = action.onClick,
                    modifier = Modifier.sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
                        .semantics { contentDescription = action.label },
                ) {
                    Box(
                        Modifier.size(ACTION_CONTAINER_SIZE).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(ACTION_ICON_SIZE),
                        )
                    }
                }
            } else {
                QuietTextButton(action.label, action.onClick)
            }
        }
        if (menu.isNotEmpty()) OverflowMenuButton(menuDescription.orEmpty(), menu)
    }
}

private const val MAX_ACTIONS = 2

private val ACTION_CONTAINER_SIZE = 40.dp
private val ACTION_ICON_SIZE = 20.dp
private val LOGO_SIZE = 32.dp

/** Font scale from which the local-only badge is dropped so the title keeps its room. */
private const val HIDE_BADGE_FROM_FONT_SCALE = 1.3f

/** The brand block: logo, [title] over a muted [subtitle], then the local-only badge when there is room. */
@Composable
private fun RowScope.BrandTitle(title: String, subtitle: String?, modifier: Modifier, titleModifier: Modifier) {
    Spacer(Modifier.width(Spacing.sm))
    SakshiBrandLogo(size = LOGO_SIZE, modifier = Modifier.clearAndSetSemantics { })
    Row(
        modifier = modifier.padding(start = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (LocalDensity.current.fontScale < HIDE_BADGE_FROM_FONT_SCALE) LocalVaultBadge()
    }
}

/** A left-pointing arrow drawn directly, in a 48 dp target named "Back" for TalkBack. */
@Composable
private fun BackButton(onBack: () -> Unit) {
    val colour = MaterialTheme.colorScheme.onSurface
    val description = stringResource(R.string.action_back)
    IconButton(
        onClick = onBack,
        modifier = Modifier.sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
            val stroke = 2.dp.toPx()
            val path = Path().apply {
                moveTo(size.width * 0.58f, size.height * 0.2f)
                lineTo(size.width * 0.28f, size.height * 0.5f)
                lineTo(size.width * 0.58f, size.height * 0.8f)
            }
            drawPath(path, colour, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawLine(colour, Offset(size.width * 0.3f, size.height * 0.5f), Offset(size.width * 0.8f, size.height * 0.5f), stroke, StrokeCap.Round)
        }
    }
}
