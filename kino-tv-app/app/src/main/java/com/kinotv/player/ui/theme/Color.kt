package com.kinotv.player.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Fondo y superficies MichiTV (Obsidian Black & Midnight Surfaces)
val MichiBackground = Color(0xFF08090D)
val MichiSurface = Color(0xFF12141C)
val MichiSurfaceElevated = Color(0xFF191D28)
val MichiCardBackground = Color(0xFF151822)
val MichiBorder = Color(0xFF232838)
val MichiBorderFocused = Color(0xFFFF6D00)

// Acentos Naranja Cálido & Vibrante
val MichiOrange = Color(0xFFFF6D00)
val MichiOrangeBright = Color(0xFFFF851B)
val MichiOrangeLight = Color(0xFFFF9E3D)
val MichiOrangeDark = Color(0xFFE65100)

// Acentos complementarios
val MichiGold = Color(0xFFFFB300)
val MichiGreen = Color(0xFF00E676)
val MichiCyan = Color(0xFF00E5FF)
val MichiCyanBright = Color(0xFF18FFFF)

// Textos
val MichiTextPrimary = Color(0xFFFFFFFF)
val MichiTextSecondary = Color(0xFFA0ABBD)
val MichiTextMuted = Color(0xFF677284)

// Gradientes
val MichiOrangeGradient = Brush.horizontalGradient(
    colors = listOf(MichiOrange, MichiOrangeBright)
)

val MichiOrangeVerticalGradient = Brush.verticalGradient(
    colors = listOf(MichiOrangeBright, MichiOrange)
)

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
