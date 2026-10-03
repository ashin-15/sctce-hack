package org.sakshi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.theme.CalmSanctuaryTokens
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.app.ui.theme.codeSmall

/**
 * Calm Sanctuary Local-Only Vault Badge:
 * Pill container in #E0F2FE with #0369A1 label and lock/shield indicator.
 */
@Composable
fun LocalVaultBadge(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.stitch_local),
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = CalmSanctuaryTokens.PrivacyBadgeBg,
        contentColor = CalmSanctuaryTokens.PrivacyBadgeText,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Canvas(Modifier.size(12.dp)) {
                // Shield / lock mini indicator
                val stroke = 1.5.dp.toPx()
                val path = Path().apply {
                    moveTo(size.width * 0.5f, 0f)
                    lineTo(size.width, size.height * 0.25f)
                    lineTo(size.width * 0.8f, size.height * 0.85f)
                    lineTo(size.width * 0.5f, size.height)
                    lineTo(size.width * 0.2f, size.height * 0.85f)
                    lineTo(0f, size.height * 0.25f)
                    close()
                }
                drawPath(path, CalmSanctuaryTokens.PrivacyBadgeText, style = Stroke(width = stroke))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Attribution state for tri-state badge. */
enum class AttributionKind {
    Observed,
    Inferred,
    Confirmed,
}

/**
 * Calm Sanctuary Tri-State Attribution Badge:
 * - Observed: Bordered #CBD5E1, neutral gray background #F1F5F9, #0F172A text.
 * - AI Inference: Dotted outline #0F766E, #F0FDFA background, #0F766E text with spark indicator.
 * - User Confirmed: Solid #0F766E surface with #FFFFFF text and checkmark indicator.
 */
@Composable
fun TriStateAttributionBadge(
    kind: AttributionKind,
    label: String,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        AttributionKind.Observed -> {
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = CalmSanctuaryTokens.SurfaceSubtle,
                contentColor = CalmSanctuaryTokens.TextPrimary,
                border = BorderStroke(1.dp, CalmSanctuaryTokens.BorderStrong),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
        AttributionKind.Inferred -> {
            val teal = CalmSanctuaryTokens.DeepTeal
            Box(
                modifier = modifier
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        val half = stroke / 2
                        drawRoundRect(
                            color = teal,
                            topLeft = Offset(half, half),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(size.height / 2),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            ),
                        )
                    }
                    .clip(CircleShape)
                    .background(Color(0xFFF0FDFA))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        // Four-point sparkle icon
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val path = Path().apply {
                            moveTo(cx, 0f)
                            lineTo(cx + 2.dp.toPx(), cy - 2.dp.toPx())
                            lineTo(size.width, cy)
                            lineTo(cx + 2.dp.toPx(), cy + 2.dp.toPx())
                            lineTo(cx, size.height)
                            lineTo(cx - 2.dp.toPx(), cy + 2.dp.toPx())
                            lineTo(0f, cy)
                            lineTo(cx - 2.dp.toPx(), cy - 2.dp.toPx())
                            close()
                        }
                        drawPath(path, teal)
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = teal,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        AttributionKind.Confirmed -> {
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = CalmSanctuaryTokens.DeepTeal,
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        // Checkmark
                        val stroke = 1.8.dp.toPx()
                        val path = Path().apply {
                            moveTo(size.width * 0.2f, size.height * 0.5f)
                            lineTo(size.width * 0.45f, size.height * 0.78f)
                            lineTo(size.width * 0.85f, size.height * 0.22f)
                        }
                        drawPath(path, Color.White, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Calm Sanctuary Cryptographic Hash Status Indicator:
 * Shows fingerprint icon, truncated SHA-256 token, and verification state.
 */
@Composable
fun HashStatusBadge(
    hash: String,
    modifier: Modifier = Modifier,
    verified: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Canvas(Modifier.size(14.dp)) {
            val color = if (verified) CalmSanctuaryTokens.StatusSuccess else CalmSanctuaryTokens.TextTertiary
            val stroke = 1.4.dp.toPx()
            // Fingerprint concentric curves
            drawCircle(color, radius = size.minDimension * 0.2f, style = Stroke(width = stroke))
            drawCircle(color, radius = size.minDimension * 0.42f, style = Stroke(width = stroke))
        }
        val displayHash = if (hash.length > 16) "${hash.take(8)}...${hash.takeLast(6)}" else hash
        Text(
            text = "SHA-256: $displayHash",
            style = MaterialTheme.typography.codeSmall,
            color = if (verified) CalmSanctuaryTokens.StatusSuccess else CalmSanctuaryTokens.TextSecondary,
        )
    }
}

/** Hidden previews are not composed, so their content cannot leak through accessibility semantics. */
@Composable
fun SensitiveMediaShield(
    isShielded: Boolean,
    onToggleShield: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val action = stringResource(if (isShielded) R.string.stitch_reveal_preview else R.string.stitch_hide_preview)
    Column(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (isShielded) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClickLabel = action, role = Role.Button, onClick = onToggleShield)
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SupportingText(stringResource(R.string.stitch_hidden_preview))
                    Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            content()
            QuietTextButton(action, onToggleShield)
        }
    }
}

/** Anchor state for incident timeline node. */
enum class TimelineNodeState {
    Confirmed,
    WarningEscalation,
    PassiveMetadata,
}

/**
 * Calm Sanctuary Incident Timeline Node:
 * - Left-aligned continuous vertical line (2px solid #E2E8F0).
 * - Timeline anchor dot: 12dp circle with:
 *   #0F766E fill for user-confirmed items,
 *   #D97706 for unreviewed escalation signals,
 *   #CBD5E1 for passive metadata records.
 */
@Composable
fun TimelineAnchorDot(
    state: TimelineNodeState,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (state) {
        TimelineNodeState.Confirmed -> CalmSanctuaryTokens.DeepTeal
        TimelineNodeState.WarningEscalation -> CalmSanctuaryTokens.StatusWarning
        TimelineNodeState.PassiveMetadata -> CalmSanctuaryTokens.BorderStrong
    }
    Canvas(modifier.size(12.dp)) {
        drawCircle(color = dotColor)
        drawCircle(color = Color.White, radius = size.minDimension * 0.35f)
    }
}

/**
 * Calm Sanctuary Sakshi Brand Emblem:
 * Vector shield of privacy with the open witness eye in deep teal (#0F766E) and mint tones (#2DD4BF).
 */
@Composable
fun SakshiBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Icon(
        painter = painterResource(R.drawable.ic_sakshi_logo),
        contentDescription = "Sakshi",
        tint = Color.Unspecified,
        modifier = modifier.size(size),
    )
}
