package com.kinotv.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MichiDarkColorScheme = darkColorScheme(
    primary = MichiOrange,
    onPrimary = MichiTextPrimary,
    primaryContainer = MichiOrangeDark,
    onPrimaryContainer = MichiTextPrimary,
    secondary = MichiGold,
    onSecondary = MichiBackground,
    background = MichiBackground,
    onBackground = MichiTextPrimary,
    surface = MichiSurface,
    onSurface = MichiTextPrimary,
    surfaceVariant = MichiSurfaceElevated,
    onSurfaceVariant = MichiTextSecondary,
    outline = MichiBorder
)

@Composable
fun MichiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MichiDarkColorScheme,
        typography = MichiTypography,
        content = content
    )
}
