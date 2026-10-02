package org.sakshi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SakshiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * The app theme: a fixed palette (no dynamic colour, so every phone looks the same), the type scale and soft shapes.
 * Follows the system dark setting unless [darkTheme] says otherwise.
 */
@Composable
fun SakshiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette.darkScheme() else LightPalette.lightScheme(),
        typography = SakshiTypography,
        shapes = SakshiShapes,
        content = content,
    )
}
