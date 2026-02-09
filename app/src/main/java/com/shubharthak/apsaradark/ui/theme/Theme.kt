package com.shubharthak.apsaradark.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ApsaraDarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = SurfaceDark,
    primaryContainer = Purple40,
    onPrimaryContainer = Color.White,
    secondary = Purple60,
    onSecondary = SurfaceDark,
    tertiary = AccentGlow,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceContainerHighest,
    outlineVariant = SurfaceContainerHigh,
)

@Composable
fun ApsaraDarkTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SurfaceDark.toArgb()
            window.navigationBarColor = SurfaceDark.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = ApsaraDarkColorScheme,
        typography = ApsaraDarkTypography,
        content = content
    )
}
