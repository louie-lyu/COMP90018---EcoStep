package com.ecostep.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = EcoGreen,
    onPrimary = EcoOnGreen,

    primaryContainer = EcoGreenContainer,
    onPrimaryContainer = EcoOnGreenContainer,

    secondary = EcoGold,
    onSecondary = EcoOnGold,

    secondaryContainer = EcoGoldContainer,
    onSecondaryContainer = EcoOnGoldContainer,

    background = EcoBackground,
    onBackground = EcoOnBackground,

    surface = EcoSurface,
    onSurface = EcoOnSurface,

    surfaceVariant = EcoSurfaceVariant,
    onSurfaceVariant = EcoOnSurfaceVariant,

    outline = EcoOutline,
    outlineVariant = EcoOutlineVariant,

    error = EcoError,
    onError = EcoOnError,

    errorContainer = EcoErrorContainer,
    onErrorContainer = EcoOnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = EcoDarkGreen,
    onPrimary = EcoDarkOnGreen,

    primaryContainer = EcoDarkGreenContainer,
    onPrimaryContainer = EcoDarkOnGreenContainer,

    secondary = EcoDarkGold,
    onSecondary = EcoDarkOnGold,

    background = EcoDarkBackground,
    onBackground = EcoDarkOnBackground,

    surface = EcoDarkSurface,
    onSurface = EcoDarkOnSurface,

    surfaceVariant = EcoDarkSurfaceVariant,
    onSurfaceVariant = EcoDarkOnSurfaceVariant,

    outline = EcoDarkOutline,

    error = EcoErrorContainer,
    onError = EcoOnErrorContainer,
)

@Composable
fun EcoStepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EcoStepTypography,
        content = content,
    )
}