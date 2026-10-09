package com.kinotv.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MichiDarkColorScheme = darkColorScheme(
    primary = MichiOrange,
    onPrimary = MichiDarkTextPrimary,
    primaryContainer = MichiOrangeDark,
    onPrimaryContainer = MichiDarkTextPrimary,
    secondary = MichiGold,
    onSecondary = MichiDarkBackground,
    background = MichiDarkBackground,
    onBackground = MichiDarkTextPrimary,
    surface = MichiDarkSurface,
    onSurface = MichiDarkTextPrimary,
    surfaceVariant = MichiDarkSurfaceElevated,
    onSurfaceVariant = MichiDarkTextSecondary,
    outline = MichiDarkBorder,
    outlineVariant = MichiDarkTextMuted
)

private val MichiLightColorScheme = lightColorScheme(
    primary = MichiOrange,
    onPrimary = MichiLightTextPrimary,
    primaryContainer = MichiOrangeLight,
    onPrimaryContainer = MichiLightTextPrimary,
    secondary = MichiGold,
    onSecondary = MichiLightBackground,
    background = MichiLightBackground,
    onBackground = MichiLightTextPrimary,
    surface = MichiLightSurface,
    onSurface = MichiLightTextPrimary,
    surfaceVariant = MichiLightSurfaceElevated,
    onSurfaceVariant = MichiLightTextSecondary,
    outline = MichiLightBorder,
    outlineVariant = MichiLightTextMuted
)

@Composable
fun MichiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MichiDarkColorScheme else MichiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MichiTypography,
        content = content
    )
}
