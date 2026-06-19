package com.family.plantcare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PlantGreen,
    onPrimary = Color.White,
    primaryContainer = PlantGreenContainer,
    onPrimaryContainer = OnPlantGreenContainer,
    secondary = SageGreen,
    onSecondary = Color.White,
    secondaryContainer = SageGreenContainer,
    onSecondaryContainer = Color(0xFF1A3020),
    tertiary = EarthBrown,
    onTertiary = Color.White,
    tertiaryContainer = EarthBrownContainer,
    onTertiaryContainer = Color(0xFF2E1A08),
    background = NeutralBackground,
    onBackground = TextPrimary,
    surface = NeutralSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NeutralOutline,
    error = Color(0xFFB00020),
    onError = Color.White,
)

@Composable
fun PlantCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
