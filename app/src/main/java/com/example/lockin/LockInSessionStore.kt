package com.example.lockin

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

private const val SESSION_PREFS_NAME = "lockin_session_prefs"
private const val KEY_ACTIVE = "session_active"
private const val KEY_START_TIME = "session_start_time"
private const val KEY_GROUP_ID = "session_group_id"

data class LockInSession(val isActive: Boolean, val startTimeMillis: Long, val groupId: String? = null)

fun loadSession(context: Context): LockInSession {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    return LockInSession(
        isActive = prefs.getBoolean(KEY_ACTIVE, false),
        startTimeMillis = prefs.getLong(KEY_START_TIME, 0L),
        groupId = prefs.getString(KEY_GROUP_ID, null)
    )
}

private fun saveSession(context: Context, session: LockInSession) {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean(KEY_ACTIVE, session.isActive)
        .putLong(KEY_START_TIME, session.startTimeMillis)
        .putString(KEY_GROUP_ID, session.groupId)
        .apply()
}

fun startLockInSession(context: Context, groupId: String? = null) {
    saveSession(context, LockInSession(isActive = true, startTimeMillis = System.currentTimeMillis(), groupId = groupId))
    ContextCompat.startForegroundService(context, Intent(context, LockInService::class.java))
}

fun stopLockInSession(context: Context) {
    saveSession(context, LockInSession(isActive = false, startTimeMillis = 0L, groupId = null))
    context.stopService(Intent(context, LockInService::class.java))
}
