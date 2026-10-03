package org.sakshi.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Explicit Android sp measurements preserve user font scaling. Tracking is converted from the design's em values. */
private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: TextUnit = 0.sp,
    fontFamily: FontFamily = FontFamily.Default,
) = TextStyle(
    fontFamily = fontFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing,
)

/**
 * The type scale for Calm Sanctuary:
 *
 * - display/hero: `displayLarge` 30/38 bold (-0.02em)
 * - screen title: `headlineSmall` 24/32 bold (-0.01em), `headlineMedium` 28/38 bold
 * - section title: `titleLarge` 20/28 semibold, `titleMedium` 17/24 semibold
 * - item headings: `titleSmall` 15/22 semibold
 * - body: `bodyLarge` 16/24 regular, `bodyMedium` 14/20 regular, `bodySmall` 12/16 regular
 * - label: `labelLarge` 14/20 semibold (+0.01em), `labelMedium` 12/16 medium (+0.02em), `labelSmall` 11/14 semibold (+0.04em)
 * - code/hashes: `codeSmall` 11/14 medium monospaced
 */
internal val SakshiTypography = Typography(
    displayLarge = style(30, 38, FontWeight.Bold, (-0.6).sp),
    displayMedium = style(24, 32, FontWeight.Bold, (-0.24).sp),
    displaySmall = style(20, 28, FontWeight.SemiBold),
    headlineLarge = style(30, 38, FontWeight.Bold, (-0.6).sp),
    headlineMedium = style(28, 38, FontWeight.Bold, (-0.28).sp),
    headlineSmall = style(24, 32, FontWeight.Bold, (-0.24).sp),
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 22, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.sp),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.sp),
    bodySmall = style(12, 16, FontWeight.Normal, 0.sp),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.14.sp),
    labelMedium = style(12, 16, FontWeight.Medium, 0.24.sp),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.44.sp),
)

/** Monospaced code style for SHA-256 tokens and integrity hashes. */
val Typography.codeSmall: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    )
