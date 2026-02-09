package com.shubharthak.apsaradark.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VS Code-inspired theme palettes for Apsara Dark.
 */
data class ApsaraColorPalette(
    val name: String,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSubtle: Color,
    val isLight: Boolean = false
)

object AppThemes {

    val Dark = ApsaraColorPalette(
        name = "Dark",
        surface = Color(0xFF0D0D0D),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF222222),
        surfaceContainerHighest = Color(0xFF2A2A2A),
        textPrimary = Color(0xFFE8E8E8),
        textSecondary = Color(0xFF9E9E9E),
        textTertiary = Color(0xFF616161),
        accent = Color(0xFFB388FF),
        accentSubtle = Color(0x33B388FF)
    )

    val Monokai = ApsaraColorPalette(
        name = "Monokai",
        surface = Color(0xFF272822),
        surfaceContainer = Color(0xFF2E2E28),
        surfaceContainerHigh = Color(0xFF3E3D32),
        surfaceContainerHighest = Color(0xFF49483E),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFFA6A28C),
        textTertiary = Color(0xFF75715E),
        accent = Color(0xFFA6E22E),
        accentSubtle = Color(0x33A6E22E)
    )

    val Nightly = ApsaraColorPalette(
        name = "Nightly",
        surface = Color(0xFF011627),
        surfaceContainer = Color(0xFF071E34),
        surfaceContainerHigh = Color(0xFF0D2942),
        surfaceContainerHighest = Color(0xFF13344F),
        textPrimary = Color(0xFFD6DEEB),
        textSecondary = Color(0xFF7F9AB5),
        textTertiary = Color(0xFF5F7E97),
        accent = Color(0xFF82AAFF),
        accentSubtle = Color(0x3382AAFF)
    )

    val Solarized = ApsaraColorPalette(
        name = "Solarized",
        surface = Color(0xFF002B36),
        surfaceContainer = Color(0xFF073642),
        surfaceContainerHigh = Color(0xFF0A3F4C),
        surfaceContainerHighest = Color(0xFF0E4957),
        textPrimary = Color(0xFF93A1A1),
        textSecondary = Color(0xFF839496),
        textTertiary = Color(0xFF657B83),
        accent = Color(0xFF2AA198),
        accentSubtle = Color(0x332AA198)
    )

    val Dracula = ApsaraColorPalette(
        name = "Dracula",
        surface = Color(0xFF282A36),
        surfaceContainer = Color(0xFF2D2F3D),
        surfaceContainerHigh = Color(0xFF343746),
        surfaceContainerHighest = Color(0xFF44475A),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFFBDBDBD),
        textTertiary = Color(0xFF6272A4),
        accent = Color(0xFFBD93F9),
        accentSubtle = Color(0x33BD93F9)
    )

    val Nord = ApsaraColorPalette(
        name = "Nord",
        surface = Color(0xFF2E3440),
        surfaceContainer = Color(0xFF343A48),
        surfaceContainerHigh = Color(0xFF3B4252),
        surfaceContainerHighest = Color(0xFF434C5E),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFFD8DEE9),
        textTertiary = Color(0xFF7B88A1),
        accent = Color(0xFF88C0D0),
        accentSubtle = Color(0x3388C0D0)
    )

    val Light = ApsaraColorPalette(
        name = "Light",
        surface = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF0F0F0),
        surfaceContainerHigh = Color(0xFFE6E6E6),
        surfaceContainerHighest = Color(0xFFD9D9D9),
        textPrimary = Color(0xFF1E1E1E),
        textSecondary = Color(0xFF616161),
        textTertiary = Color(0xFF9E9E9E),
        accent = Color(0xFF6200EE),
        accentSubtle = Color(0x336200EE),
        isLight = true
    )

    val Monochrome = ApsaraColorPalette(
        name = "Monochrome",
        surface = Color(0xFF111111),
        surfaceContainer = Color(0xFF1C1C1C),
        surfaceContainerHigh = Color(0xFF262626),
        surfaceContainerHighest = Color(0xFF333333),
        textPrimary = Color(0xFFCCCCCC),
        textSecondary = Color(0xFF888888),
        textTertiary = Color(0xFF555555),
        accent = Color(0xFFCCCCCC),
        accentSubtle = Color(0x33CCCCCC)
    )

    val all = listOf(Dark, Monokai, Nightly, Solarized, Dracula, Nord, Light, Monochrome)
}
