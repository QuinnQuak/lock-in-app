package com.example.lockin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
