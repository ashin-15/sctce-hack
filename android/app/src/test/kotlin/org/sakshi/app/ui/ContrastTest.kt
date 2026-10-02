package org.sakshi.app.ui

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.app.ui.components.Accent
import org.sakshi.app.ui.components.epistemicTreatment
import org.sakshi.app.ui.theme.DarkPalette
import org.sakshi.app.ui.theme.LightPalette
import org.sakshi.app.ui.theme.Palette
import org.sakshi.app.ui.theme.darkScheme
import org.sakshi.app.ui.theme.lightScheme
import org.sakshi.app.ui.theme.rgb
import org.sakshi.core.model.EpistemicStatus

/** WCAG 2.x contrast of every foreground and background pair the theme and the epistemic treatments use. */
class ContrastTest {
    private class Pair(val name: String, val foreground: Int, val background: Int)

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int): Double =
        0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    private fun ratio(a: Int, b: Int): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun Palette.accent(accent: Accent): Int = when (accent) {
        Accent.INK -> ink
        Accent.GOLD -> gold
        Accent.GREEN -> green
        Accent.MUTED -> muted
    }

    private val palettes = listOf("light" to LightPalette, "dark" to DarkPalette)

    private fun Palette.surfaces() = listOf("background" to background, "surface" to surface, "quiet surface" to surfaceQuiet)

    private fun Palette.bodyTextPairs(): List<Pair> = buildList {
        for ((surfaceName, surfaceColour) in surfaces()) {
            add(Pair("ink on $surfaceName", ink, surfaceColour))
            add(Pair("muted on $surfaceName", muted, surfaceColour))
            add(Pair("gold on $surfaceName", gold, surfaceColour))
            add(Pair("green on $surfaceName", green, surfaceColour))
            add(Pair("red on $surfaceName", red, surfaceColour))
            add(Pair("primary on $surfaceName", primary, surfaceColour))
        }
        add(Pair("on-primary on primary", onPrimary, primary))
        add(Pair("on-primary-container on primary-container", onPrimaryContainer, primaryContainer))
        add(Pair("on-gold on gold", onGold, gold))
        add(Pair("on-gold-container on gold-container", onGoldContainer, goldContainer))
        add(Pair("on-green on green", onGreen, green))
        add(Pair("on-green-container on green-container", onGreenContainer, greenContainer))
        add(Pair("on-red on red", onRed, red))
        add(Pair("on-red-container on red-container", onRedContainer, redContainer))
        add(Pair("inverse-on-surface on inverse-surface (snackbar)", inverseOnSurface, inverseSurface))
        for (status in EpistemicStatus.entries) {
            val accent = accent(epistemicTreatment(status).accent)
            for ((surfaceName, surfaceColour) in surfaces()) add(Pair("$status label on $surfaceName", accent, surfaceColour))
        }
    }

    private fun Palette.nonTextPairs(): List<Pair> = buildList {
        for ((surfaceName, surfaceColour) in surfaces()) {
            add(Pair("outline-strong on $surfaceName", outlineStrong, surfaceColour))
            add(Pair("destructive outline (red) on $surfaceName", red, surfaceColour))
            for (status in EpistemicStatus.entries) {
                add(Pair("$status rule or box on $surfaceName", accent(epistemicTreatment(status).accent), surfaceColour))
            }
        }
    }

    @Test
    fun bodyTextPairsMeetFourPointFive() {
        for ((theme, palette) in palettes) {
            palette.bodyTextPairs().forEach {
                val value = ratio(it.foreground, it.background)
                assertTrue(value >= 4.5, "$theme ${it.name}: $value")
            }
        }
    }

    @Test
    fun largeTextAndNonTextPairsMeetThree() {
        for ((theme, palette) in palettes) {
            palette.nonTextPairs().forEach {
                val value = ratio(it.foreground, it.background)
                assertTrue(value >= 3.0, "$theme ${it.name}: $value")
            }
        }
    }

    @Test
    fun theDecorativeOutlineIsNeverTheOnlyThingMarkingAControl() {
        // outlineVariant (card edges, dividers) is below 3:1 by design; this pins the fact so nobody uses it for inputs.
        for ((_, palette) in palettes) assertTrue(ratio(palette.outlineVariant, palette.background) < 3.0)
    }

    @Test
    fun theLuminanceFormulaMatchesKnownValues() {
        assertEquals(21.0, ratio(0x000000, 0xFFFFFF), 0.001)
        assertEquals(1.0, ratio(0x777777, 0x777777), 0.001)
    }

    @Test
    fun theColourSchemesUseThePaletteForTheRolesTheComponentsRead() {
        for ((_, palette) in palettes) {
            for (scheme in listOf(palette.lightScheme(), palette.darkScheme())) {
                assertEquals(rgb(palette.ink), scheme.onSurface)
                assertEquals(rgb(palette.muted), scheme.onSurfaceVariant)
                assertEquals(rgb(palette.gold), scheme.secondary)
                assertEquals(rgb(palette.green), scheme.tertiary)
                assertEquals(rgb(palette.red), scheme.error)
                assertEquals(rgb(palette.outlineStrong), scheme.outline)
                assertEquals(rgb(palette.surfaceQuiet), scheme.surfaceVariant)
            }
        }
    }
}
