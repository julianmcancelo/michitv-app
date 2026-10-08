package com.kinotv.player.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Acentos Naranja Cálido & Vibrante
val MichiOrange = Color(0xFFFF6D00)
val MichiOrangeBright = Color(0xFFFF851B)
val MichiOrangeLight = Color(0xFFFF9E3D)
val MichiOrangeDark = Color(0xFFE65100)

val MichiGold = Color(0xFFFFB300)
val MichiCyan = Color(0xFF00E5FF)
val MichiCyanBright = Color(0xFF18FFFF)
val MichiGreen = Color(0xFF00E676)

// --- DARK THEME COLORS ---
val MichiDarkBackground = Color(0xFF08090D)
val MichiDarkSurface = Color(0xFF12141C)
val MichiDarkSurfaceElevated = Color(0xFF191D28)
val MichiDarkBorder = Color(0xFF232838)
val MichiDarkTextPrimary = Color(0xFFFFFFFF)
val MichiDarkTextSecondary = Color(0xFFA0ABBD)
val MichiDarkTextMuted = Color(0xFF677284)

// --- LIGHT THEME COLORS ---
val MichiLightBackground = Color(0xFFF0F2F5)
val MichiLightSurface = Color(0xFFFFFFFF)
val MichiLightSurfaceElevated = Color(0xFFE4E7EB)
val MichiLightBorder = Color(0xFFD1D6DD)
val MichiLightTextPrimary = Color(0xFF1A1D24)
val MichiLightTextSecondary = Color(0xFF4A5568)
val MichiLightTextMuted = Color(0xFF718096)

// Compatibilidad (Aliasing)
val MichiBorder = MichiDarkBorder
val MichiCardBackground = Color(0xFF151822)
val MichiBackground = MichiDarkBackground
val MichiSurface = MichiDarkSurface
val MichiSurfaceElevated = MichiDarkSurfaceElevated
val MichiTextPrimary = MichiDarkTextPrimary
val MichiTextSecondary = MichiDarkTextSecondary
val MichiTextMuted = MichiDarkTextMuted

// Gradientes Universales
val MichiOrangeGradient = Brush.horizontalGradient(colors = listOf(MichiOrange, MichiOrangeBright))
val MichiOrangeVerticalGradient = Brush.verticalGradient(colors = listOf(MichiOrangeBright, MichiOrange))

val CardBottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0xCC08090D), Color(0xF608090D))
)

val HeroBottomScrim = Brush.verticalGradient(
    colors = listOf(
        Color(0x0508090D),
        Color(0x6608090D),
        Color(0xCC08090D),
        Color(0xFF08090D)
    )
)

val HeroLeftScrim = Brush.horizontalGradient(
    colors = listOf(
        Color(0xF608090D),
        Color(0xD908090D),
        Color(0x7708090D),
        Color.Transparent
    )
)
