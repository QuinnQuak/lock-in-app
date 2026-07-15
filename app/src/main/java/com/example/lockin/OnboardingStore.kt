package com.example.lockin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

private const val ONBOARDING_PREFS_NAME = "lockin_onboarding_prefs"
private const val KEY_COMPLETED = "onboarding_completed"

fun isOnboardingComplete(context: Context): Boolean =
    context.getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_COMPLETED, false)

fun setOnboardingComplete(context: Context) {
    context.getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_COMPLETED, true)
        .apply()
}

// Below Android 13 notifications need no runtime grant, so there is nothing to
// prime -- the onboarding flow skips its notification step entirely.
fun notificationPermissionNeeded(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

fun hasNotificationPermission(context: Context): Boolean {
    if (!notificationPermissionNeeded()) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

// The in-app nudge (see the Home banner) deep-links straight to this app's
// notification settings rather than re-firing the runtime dialog. Once Android
// has suppressed the POST_NOTIFICATIONS dialog after a denial, re-launching it
// silently no-ops; the settings screen always works. Same shape as the Usage
// Access deep-link -- MainActivity re-checks the grant on resume, so returning
// with notifications enabled makes the nudge disappear on its own.
// ACTION_APP_NOTIFICATION_SETTINGS is API 26+; the nudge only shows on API 33+
// (where the grant is a runtime permission), so this is never reached below 26.
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
