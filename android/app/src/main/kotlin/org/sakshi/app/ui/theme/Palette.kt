package org.sakshi.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app uses, as plain 0xRRGGBB integers so the contrast test can read them without Compose.
 *
 * [outlineVariant] is decorative only (card edges, dividers). Anything that identifies a control or a block
 * (input borders, button outlines, epistemic rules) uses [outlineStrong], which meets 3:1 against every surface.
 */
internal class Palette(
    val background: Int,
    val surface: Int,
    val surfaceQuiet: Int,
    val ink: Int,
    val muted: Int,
    val outlineVariant: Int,
    val outlineStrong: Int,
    val gold: Int,
    val green: Int,
    val red: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val onGold: Int,
    val goldContainer: Int,
    val onGoldContainer: Int,
    val onGreen: Int,
    val greenContainer: Int,
    val onGreenContainer: Int,
    val onRed: Int,
    val redContainer: Int,
    val onRedContainer: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
)

internal val LightPalette = Palette(
    background = 0xF8FAFC,
    surface = 0xFFFFFF,
    surfaceQuiet = 0xF1F5F9,
    ink = 0x0F172A,
    muted = 0x475569,
    outlineVariant = 0xE2E8F0,
    outlineStrong = 0x64748B,
    gold = 0x92400E,
    green = 0x15803D,
    red = 0xB91C1C,
    primary = 0x0F766E,
    onPrimary = 0xFFFFFF,
    primaryContainer = 0xCCFBF1,
    onPrimaryContainer = 0x115E59,
    onGold = 0xFFFFFF,
    goldContainer = 0xFEF3C7,
    onGoldContainer = 0x78350F,
    onGreen = 0xFFFFFF,
    greenContainer = 0xDCFCE7,
    onGreenContainer = 0x14532D,
    onRed = 0xFFFFFF,
    redContainer = 0xFEE2E2,
    onRedContainer = 0x7F1D1D,
    inverseSurface = 0x2D3133,
    inverseOnSurface = 0xEFF1F3,
)

internal val DarkPalette = Palette(
    background = 0x0F172A,
    surface = 0x1E293B,
    surfaceQuiet = 0x2A384C,
    ink = 0xF8FAFC,
    muted = 0xCBD5E1,
    outlineVariant = 0x475569,
    outlineStrong = 0x94A3B8,
    gold = 0xFCD34D,
    green = 0x86EFAC,
    red = 0xFCA5A5,
    primary = 0x5EEAD4,
    onPrimary = 0x0F172A,
    primaryContainer = 0x115E59,
    onPrimaryContainer = 0xCCFBF1,
    onGold = 0x0F172A,
    goldContainer = 0x451A03,
    onGoldContainer = 0xFEF3C7,
    onGreen = 0x0F172A,
    greenContainer = 0x052E16,
    onGreenContainer = 0xDCFCE7,
    onRed = 0x0F172A,
    redContainer = 0x450A0A,
    onRedContainer = 0xFEE2E2,
    inverseSurface = 0xF8FAFC,
    inverseOnSurface = 0x0F172A,
)

/**
 * Calm Sanctuary semantic color tokens.
 */
object CalmSanctuaryTokens {
    val DeepTeal = Color(0xFF0F766E)
    val DarkTeal = Color(0xFF115E59)
    val MintIris = Color(0xFF2DD4BF)
    val MintLight = Color(0xFFE6FFFA)

    val StatusWarning = Color(0xFFD97706)
    val StatusWarningSurface = Color(0xFFFEF3C7)
    val StatusCritical = Color(0xFFB91C1C)
    val StatusCriticalSurface = Color(0xFFFEE2E2)
    val StatusSuccess = Color(0xFF15803D)
    val StatusSuccessSurface = Color(0xFFDCFCE7)

    val PrivacyBadgeBg = Color(0xFFE0F2FE)
    val PrivacyBadgeText = Color(0xFF0369A1)

    val SurfaceCard = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF1F5F9)
    val BorderSubtle = Color(0xFFE2E8F0)
    val BorderStrong = Color(0xFFCBD5E1)

    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextTertiary = Color(0xFF64748B)
}

private const val OPAQUE = 0xFF000000.toInt()

/** An opaque Compose colour from a 0xRRGGBB integer. */
internal fun rgb(value: Int): Color = Color(OPAQUE or value)

/** Maps the palette onto Material 3 roles. Gold is secondary, green tertiary, red error. */
internal fun Palette.lightScheme(): ColorScheme = lightColorScheme(
    primary = rgb(primary),
    onPrimary = rgb(onPrimary),
    primaryContainer = rgb(primaryContainer),
    onPrimaryContainer = rgb(onPrimaryContainer),
    inversePrimary = rgb(primaryContainer),
    secondary = rgb(gold),
    onSecondary = rgb(onGold),
    secondaryContainer = rgb(goldContainer),
    onSecondaryContainer = rgb(onGoldContainer),
    tertiary = rgb(green),
    onTertiary = rgb(onGreen),
    tertiaryContainer = rgb(greenContainer),
    onTertiaryContainer = rgb(onGreenContainer),
    background = rgb(background),
    onBackground = rgb(ink),
    surface = rgb(surface),
    onSurface = rgb(ink),
    surfaceVariant = rgb(surfaceQuiet),
    onSurfaceVariant = rgb(muted),
    surfaceTint = rgb(primary),
    inverseSurface = rgb(inverseSurface),
    inverseOnSurface = rgb(inverseOnSurface),
    error = rgb(red),
    onError = rgb(onRed),
    errorContainer = rgb(redContainer),
    onErrorContainer = rgb(onRedContainer),
    outline = rgb(outlineStrong),
    outlineVariant = rgb(outlineVariant),
    scrim = Color.Black,
    surfaceBright = rgb(surface),
    surfaceDim = rgb(surfaceQuiet),
    surfaceContainerLowest = rgb(surface),
    surfaceContainerLow = rgb(surface),
    surfaceContainer = rgb(surface),
    surfaceContainerHigh = rgb(surfaceQuiet),
    surfaceContainerHighest = rgb(surfaceQuiet),
)

/** Same mapping as [lightScheme], built with the dark baseline. */
internal fun Palette.darkScheme(): ColorScheme = darkColorScheme(
    primary = rgb(primary),
    onPrimary = rgb(onPrimary),
    primaryContainer = rgb(primaryContainer),
    onPrimaryContainer = rgb(onPrimaryContainer),
    inversePrimary = rgb(onPrimary),
    secondary = rgb(gold),
    onSecondary = rgb(onGold),
    secondaryContainer = rgb(goldContainer),
    onSecondaryContainer = rgb(onGoldContainer),
    tertiary = rgb(green),
    onTertiary = rgb(onGreen),
    tertiaryContainer = rgb(greenContainer),
    onTertiaryContainer = rgb(onGreenContainer),
    background = rgb(background),
    onBackground = rgb(ink),
    surface = rgb(surface),
    onSurface = rgb(ink),
    surfaceVariant = rgb(surfaceQuiet),
    onSurfaceVariant = rgb(muted),
    surfaceTint = rgb(primary),
    inverseSurface = rgb(inverseSurface),
    inverseOnSurface = rgb(inverseOnSurface),
    error = rgb(red),
    onError = rgb(onRed),
    errorContainer = rgb(redContainer),
    onErrorContainer = rgb(onRedContainer),
    outline = rgb(outlineStrong),
    outlineVariant = rgb(outlineVariant),
    scrim = Color.Black,
    surfaceBright = rgb(surface),
    surfaceDim = rgb(surfaceQuiet),
    surfaceContainerLowest = rgb(surface),
    surfaceContainerLow = rgb(surface),
    surfaceContainer = rgb(surface),
    surfaceContainerHigh = rgb(surfaceQuiet),
    surfaceContainerHighest = rgb(surfaceQuiet),
)
