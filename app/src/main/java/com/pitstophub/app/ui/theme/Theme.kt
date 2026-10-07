package com.pitstophub.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PitColorScheme = darkColorScheme(
    primary = PitColors.Primary,
    onPrimary = PitColors.OnPrimary,
    primaryContainer = PitColors.PrimaryContainer,
    onPrimaryContainer = PitColors.OnPrimaryContainer,
    inversePrimary = PitColors.InversePrimary,
    secondary = PitColors.Secondary,
    secondaryContainer = PitColors.SecondaryContainer,
    tertiary = PitColors.Tertiary,
    tertiaryContainer = PitColors.TertiaryContainer,
    background = PitColors.Carbon,
    onBackground = PitColors.OnSurface,
    surface = PitColors.Surface,
    onSurface = PitColors.OnSurface,
    surfaceVariant = PitColors.SurfaceHighest,
    onSurfaceVariant = PitColors.OnSurfaceVariant,
    surfaceBright = PitColors.SurfaceBright,
    surfaceContainerLowest = PitColors.SurfaceLowest,
    surfaceContainerLow = PitColors.SurfaceLow,
    surfaceContainer = PitColors.SurfaceContainer,
    surfaceContainerHigh = PitColors.SurfaceHigh,
    surfaceContainerHighest = PitColors.SurfaceHighest,
    outline = PitColors.Outline,
    outlineVariant = PitColors.OutlineVariant,
    error = PitColors.Error,
    errorContainer = PitColors.ErrorContainer,
)

/** O design define apenas o modo escuro ("default state is Dark Mode"), entao o app e sempre escuro. */
@Composable
fun PitStopHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PitColorScheme,
        typography = PitTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = PitShapes.Sharp,
            small = PitShapes.Card,
            medium = PitShapes.Soft,
        ),
        content = content,
    )
}
