package com.shubharthak.apsaradark.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.shubharthak.apsaradark.data.LiveSettingsManager
import com.shubharthak.apsaradark.data.LocalLiveSettings

private fun buildColorScheme(palette: ApsaraColorPalette) = if (palette.isLight) {
    lightColorScheme(
        primary = palette.accent,
        onPrimary = Color.White,
        primaryContainer = palette.accent,
        onPrimaryContainer = Color.White,
        secondary = palette.accent,
        onSecondary = Color.White,
        tertiary = palette.accent,
        background = palette.surface,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceContainer,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceContainerHighest,
        outlineVariant = palette.surfaceContainerHigh,
    )
} else {
    darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.surface,
        primaryContainer = palette.accent,
        onPrimaryContainer = Color.White,
        secondary = palette.accent,
        onSecondary = palette.surface,
        tertiary = palette.accent,
        background = palette.surface,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceContainer,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.surfaceContainerHighest,
        outlineVariant = palette.surfaceContainerHigh,
    )
}

@Composable
fun ApsaraDarkTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val liveSettingsManager = remember { LiveSettingsManager(context) }
    val palette = themeManager.currentTheme
    val colorScheme = buildColorScheme(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.surface.toArgb()
            window.navigationBarColor = palette.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    CompositionLocalProvider(
        LocalThemeManager provides themeManager,
        LocalLiveSettings provides liveSettingsManager
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ApsaraDarkTypography,
            content = content
        )
    }
}
