package org.sakshi.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spacing scale and the fixed layout measures. Use these instead of literal dp values in screens. */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Smallest interactive size. */
    val touchTarget: Dp = 48.dp

    /** Widest the content column grows on tablets and in landscape; keeps lines readable. */
    val contentMaxWidth: Dp = 600.dp

    /** Left and right gutter of every screen. */
    val gutter: Dp = lg

    /** Height of the top row. */
    val topRow: Dp = 56.dp
}
