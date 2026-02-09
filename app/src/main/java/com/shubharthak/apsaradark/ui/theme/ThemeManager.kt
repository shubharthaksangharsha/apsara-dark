package com.shubharthak.apsaradark.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages the active theme and persists it to SharedPreferences.
 */
class ThemeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apsara_prefs", Context.MODE_PRIVATE)

    var currentTheme by mutableStateOf(loadTheme())
        private set

    fun setTheme(palette: ApsaraColorPalette) {
        currentTheme = palette
        prefs.edit().putString("theme_name", palette.name).apply()
    }

    private fun loadTheme(): ApsaraColorPalette {
        val savedName = prefs.getString("theme_name", "Dark") ?: "Dark"
        return AppThemes.all.find { it.name == savedName } ?: AppThemes.Dark
    }
}

val LocalThemeManager = compositionLocalOf<ThemeManager> {
    error("ThemeManager not provided")
}
