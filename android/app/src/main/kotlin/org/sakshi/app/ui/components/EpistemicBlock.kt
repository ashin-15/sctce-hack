package org.sakshi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.EpistemicStatus

private val RULE_WIDTH = 4.dp
private val THIN_RULE_WIDTH = 2.dp
private val DOUBLE_GAP = 3.dp
private val BOX_STROKE = 1.5.dp
private val BOX_RADIUS = 12.dp
private val MARKER_SIZE = 16.dp

@Composable
private fun Accent.color(): Color = when (this) {
    Accent.INK -> MaterialTheme.colorScheme.onSurface
    Accent.GOLD -> MaterialTheme.colorScheme.secondary
    Accent.GREEN -> MaterialTheme.colorScheme.tertiary
    Accent.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun Treatment.ruleWidth(): Dp = when (rule) {
    RuleStyle.NONE -> 0.dp
    RuleStyle.SOLID, RuleStyle.DASHED -> RULE_WIDTH
    RuleStyle.DOUBLE -> THIN_RULE_WIDTH * 2 + DOUBLE_GAP
}

private fun DrawScope.dashEffect(box: BoxStyle): Pair<PathEffect?, StrokeCap> = when (box) {
    BoxStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(0.1f, BOX_STROKE.toPx() * 2.2f)) to StrokeCap.Round
    BoxStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())) to StrokeCap.Butt
    BoxStyle.NONE -> null to StrokeCap.Butt
}

private fun DrawScope.drawRule(rule: RuleStyle, color: Color, startX: Float, top: Float, bottom: Float) {
    when (rule) {
        RuleStyle.NONE -> Unit
        RuleStyle.SOLID -> drawLine(color, Offset(startX + RULE_WIDTH.toPx() / 2, top), Offset(startX + RULE_WIDTH.toPx() / 2, bottom), RULE_WIDTH.toPx())
        RuleStyle.DASHED -> drawLine(
            color,
            Offset(startX + RULE_WIDTH.toPx() / 2, top),
            Offset(startX + RULE_WIDTH.toPx() / 2, bottom),
            RULE_WIDTH.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())),
        )
        RuleStyle.DOUBLE -> {
            val thin = THIN_RULE_WIDTH.toPx()
            drawLine(color, Offset(startX + thin / 2, top), Offset(startX + thin / 2, bottom), thin)
            val second = startX + thin + DOUBLE_GAP.toPx() + thin / 2
            drawLine(color, Offset(second, top), Offset(second, bottom), thin)
        }
    }
}

private fun DrawScope.drawBox(box: BoxStyle, color: Color, fill: Color?, radius: Float) {
    if (fill != null) drawRoundRect(fill, cornerRadius = CornerRadius(radius))
    if (box == BoxStyle.NONE) return
    val inset = BOX_STROKE.toPx() / 2
    val (effect, cap) = dashEffect(box)
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(radius - inset),
        style = Stroke(width = BOX_STROKE.toPx(), cap = cap, pathEffect = effect),
    )
}

/** A tiny drawing of the treatment (rule or box), shown beside the label so the label alone also differs by shape. */
@Composable
private fun TreatmentMarker(treatment: Treatment, color: Color) {
    Canvas(Modifier.size(MARKER_SIZE).clearAndSetSemantics { }) {
        drawRule(treatment.rule, color, startX = 0f, top = 0f, bottom = size.height)
        if (treatment.box != BoxStyle.NONE) {
            val inset = 1.dp.toPx()
            val (effect, cap) = dashEffect(treatment.box)
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = BOX_STROKE.toPx(), cap = cap, pathEffect = effect),
            )
        }
    }
}

/**
 * The small label of [status] alone ("Observed", "Your statement", "Suggestion", "Pattern", "Not known"), with a marker
 * drawn like the block's rule or box. Text carries the meaning; the marker and colour only reinforce it.
 */
@Composable
fun EpistemicLabel(status: EpistemicStatus, modifier: Modifier = Modifier) {
    val treatment = epistemicTreatment(status)
    val color = treatment.accent.color()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TreatmentMarker(treatment, color)
        Text(stringResource(epistemicLabelRes(status)), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * Frames [content] as one of the five kinds of statement, labelled and drawn by [epistemicTreatment]. The label is the
 * first line. See [epistemicTreatment] for the shape each status gets. [status] has no default on purpose.
 */
@Composable
fun EpistemicBlock(status: EpistemicStatus, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val treatment = epistemicTreatment(status)
    val color = treatment.accent.color()
    val fill = if (treatment.filled) MaterialTheme.colorScheme.surfaceVariant else null
    val boxed = treatment.box != BoxStyle.NONE
    val ruleGap = if (treatment.rule == RuleStyle.NONE) 0.dp else Spacing.md
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawBox(treatment.box, color, fill, BOX_RADIUS.toPx())
                drawRule(treatment.rule, color, startX = 0f, top = 0f, bottom = size.height)
            }
            .padding(start = treatment.ruleWidth() + ruleGap)
            .padding(if (boxed) Spacing.md else 0.dp)
            .padding(vertical = if (boxed) 0.dp else Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        EpistemicLabel(status)
        content()
    }
}

/** A block whose content is plain [text], read by TalkBack as one phrase: the label, then the text. */
@Composable
fun EpistemicBlock(status: EpistemicStatus, text: String, modifier: Modifier = Modifier) {
    val treatment = epistemicTreatment(status)
    EpistemicBlock(status, modifier.semantics(mergeDescendants = true) { }) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = if (treatment.italic) FontStyle.Italic else FontStyle.Normal,
        )
    }
}
