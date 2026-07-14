package com.example.lockin

import android.content.Context

private const val PREFS_NAME = "lockin_prefs"
private const val KEY_ALLOWLIST = "allowlist_packages"

fun loadAllowlist(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_ALLOWLIST, emptySet()) ?: emptySet()
}

fun saveAllowlist(context: Context, allowlist: Set<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(KEY_ALLOWLIST, allowlist).apply()
}
