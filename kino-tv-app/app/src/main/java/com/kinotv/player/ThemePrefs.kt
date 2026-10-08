package com.kinotv.player

import android.content.Context

object ThemePrefs {
    private const val PREFS_NAME = "michi_theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode"

    fun isDarkMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }
}
