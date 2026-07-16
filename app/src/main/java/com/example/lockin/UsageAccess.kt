package com.example.lockin

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
fun hasUsageAccessPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

@Suppress("DEPRECATION")
fun currentForegroundApp(context: Context, sessionStartMillis: Long = 0L): String? {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    // A wide lookback window, not just "recent" -- an app can sit in the foreground
    // for a long time with zero new MOVE_TO_FOREGROUND events, and a short window
    // would let that event age out and wrongly report "no foreground app."
    val oneHourAgo = endTime - TimeUnit.HOURS.toMillis(1)
    // Widen to the session start only when the session is older than an hour (an
    // app foregrounded before the 1h window would age out and report a false
    // null). Never NARROWS below 1h: a <1h session, or no session (0L, the Home
    // caller), keeps the 1h floor.
    val startTime = if (sessionStartMillis in 1 until oneHourAgo) sessionStartMillis else oneHourAgo
    val events = usageStatsManager.queryEvents(startTime, endTime)

    val event = UsageEvents.Event()
    var lastForegroundPackage: String? = null
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            lastForegroundPackage = event.packageName
        }
    }
    return lastForegroundPackage
}

fun appLabelFor(context: Context, packageName: String): String {
    val packageManager = context.packageManager
    return try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

fun appIconFor(context: Context, packageName: String): Drawable? {
    return try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
