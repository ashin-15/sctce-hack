package org.sakshi.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sakshi.app.R

/** Top-level workspaces; routes remain owned by the session navigator. */
enum class Workspace(@param:StringRes val title: Int) {
    Home(R.string.stitch_home),
    Evidence(R.string.stitch_evidence),
    Incidents(R.string.stitch_incidents),
    Vault(R.string.stitch_vault),
    Reports(R.string.stitch_reports),
}

@Composable
fun WorkspaceNavigation(selected: Workspace, onSelect: (Workspace) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Workspace.entries.forEach { workspace ->
            NavigationBarItem(
                selected = selected == workspace,
                onClick = { onSelect(workspace) },
                icon = { WorkspaceIcon(workspace) },
                label = { Text(stringResource(workspace.title), style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** A consistent outline icon set drawn locally without a downloadable font. */
@Composable
private fun WorkspaceIcon(workspace: Workspace) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val path = Path()
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        when (workspace) {
            Workspace.Home -> {
                path.moveTo(size.width * .15f, size.height * .45f)
                path.lineTo(size.width * .5f, size.height * .15f)
                path.lineTo(size.width * .85f, size.height * .45f)
                path.lineTo(size.width * .85f, size.height * .85f)
                path.lineTo(size.width * .6f, size.height * .85f)
                path.lineTo(size.width * .6f, size.height * .6f)
                path.lineTo(size.width * .4f, size.height * .6f)
                path.lineTo(size.width * .4f, size.height * .85f)
                path.lineTo(size.width * .15f, size.height * .85f)
                path.close()
                drawPath(path, color, style = stroke)
            }
            Workspace.Evidence -> {
                drawRect(color, point(.22f, .15f), Size(size.width * .56f, size.height * .7f), style = stroke)
                listOf(.35f, .5f, .65f).forEach { y -> drawLine(color, point(.35f, y), point(.65f, y), stroke.width, StrokeCap.Round) }
            }
            Workspace.Incidents -> {
                path.moveTo(size.width * .15f, size.height * .75f)
                path.lineTo(size.width * .4f, size.height * .5f)
                path.lineTo(size.width * .6f, size.height * .6f)
                path.lineTo(size.width * .85f, size.height * .25f)
                drawPath(path, color, style = stroke)
                listOf(point(.15f, .75f), point(.4f, .5f), point(.6f, .6f), point(.85f, .25f)).forEach {
                    drawCircle(color, radius = 2.dp.toPx(), center = it)
                }
            }
            Workspace.Vault -> {
                drawRect(color, point(.2f, .42f), Size(size.width * .6f, size.height * .43f), style = stroke)
                drawArc(color, 180f, 180f, false, point(.32f, .12f), Size(size.width * .36f, size.height * .6f), style = stroke)
                drawLine(color, point(.5f, .59f), point(.5f, .7f), stroke.width, StrokeCap.Round)
            }
            Workspace.Reports -> {
                drawRect(color, point(.22f, .12f), Size(size.width * .56f, size.height * .76f), style = stroke)
                listOf(.33f, .49f, .65f).forEach { y -> drawLine(color, point(.35f, y), point(.65f, y), stroke.width, StrokeCap.Round) }
            }
        }
    }
}
