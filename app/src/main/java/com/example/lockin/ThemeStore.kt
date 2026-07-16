package com.example.lockin

import android.content.Context

// Device-local only, unlike the allowlist/streak goal -- theme is a private
// cosmetic preference with no friend-visibility or cross-device requirement
// in CONTEXT.md, so it doesn't need a Firestore round-trip.
private const val THEME_PREFS_NAME = "lockin_theme_prefs"
private const val KEY_THEME = "selected_theme"
private const val KEY_THEME_MODE = "selected_theme_mode"

fun loadAppTheme(context: Context): AppTheme {
    val name = context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_THEME, null) ?: return AppTheme.BUBBLEGUM
    return runCatching { AppTheme.valueOf(name) }.getOrDefault(AppTheme.BUBBLEGUM)
}

fun saveAppTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, theme.name)
        .apply()
}

// Defaults to SYSTEM (follow the OS) so existing installs keep their prior behavior.
fun loadThemeMode(context: Context): ThemeMode {
    val name = context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
    return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
}

fun saveThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME_MODE, mode.name)
        .apply()
}
