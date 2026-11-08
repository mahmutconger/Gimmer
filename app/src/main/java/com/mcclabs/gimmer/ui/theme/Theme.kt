package com.mcclabs.gimmer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GimmerColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color(0xFF0B1C36),
    primaryContainer = DeepBlue,
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFFAABCE0),
    onSecondary = Color(0xFF18253D),
    secondaryContainer = DeepBlue,
    onSecondaryContainer = Color(0xFFD9E7FF),
    tertiary = Color(0xFFAACDFF),
    onTertiary = Color(0xFF142840),
    tertiaryContainer = Color(0xFF223A56),
    onTertiaryContainer = Color(0xFFD8EBFF),
    background = DarkBackground,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    surfaceDim = DarkBackground,
    surfaceBright = Color(0xFF2A3241),
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = Color(0xFF101319),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = RaisedSurface,
    surfaceContainerHighest = Color(0xFF262D3A),
    surfaceVariant = RaisedSurface,
    onSurfaceVariant = MutedText,
    outline = Color(0xFF69768C),
    outlineVariant = Color(0xFF303847),
    inverseSurface = LightText,
    inverseOnSurface = DarkBackground,
    inversePrimary = Color(0xFF204E94),
)

private val GimmerShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GimmerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GimmerColors,
        shapes = GimmerShapes,
        typography = GimmerTypography,
        content = content,
    )
}
