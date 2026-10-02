package org.sakshi.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Line height as a multiple of the font size. Indic scripts need the taller end, so nothing here is below 1.4. */
private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing,
)

/**
 * The type scale, on the platform sans family.
 *
 * - screen title: `headlineSmall` 24/34 semibold (`headlineMedium` 28/40 for the lock screen)
 * - top row title: `titleMedium` 17/26 semibold
 * - section title: `titleMedium` 17/26 semibold, `titleSmall` 15/23 semibold for item headings
 * - body: `bodyLarge` 17/26 regular
 * - supporting: `bodyMedium` 15/23 regular, `bodySmall` 13/20
 * - label: `labelLarge` 15/23 medium (buttons), `labelMedium` 13/20 medium, `labelSmall` 12/18
 */
internal val SakshiTypography = Typography(
    displayLarge = style(40, 56, FontWeight.SemiBold),
    displayMedium = style(34, 48, FontWeight.SemiBold),
    displaySmall = style(30, 42, FontWeight.SemiBold),
    headlineLarge = style(30, 42, FontWeight.SemiBold),
    headlineMedium = style(28, 40, FontWeight.SemiBold),
    headlineSmall = style(24, 34, FontWeight.SemiBold),
    titleLarge = style(20, 30, FontWeight.SemiBold),
    titleMedium = style(17, 26, FontWeight.SemiBold),
    titleSmall = style(15, 23, FontWeight.SemiBold),
    bodyLarge = style(17, 26, FontWeight.Normal, 0.1.sp),
    bodyMedium = style(15, 23, FontWeight.Normal, 0.1.sp),
    bodySmall = style(13, 20, FontWeight.Normal, 0.1.sp),
    labelLarge = style(15, 23, FontWeight.Medium, 0.1.sp),
    labelMedium = style(13, 20, FontWeight.Medium, 0.2.sp),
    labelSmall = style(12, 18, FontWeight.Medium, 0.2.sp),
)
