package com.example.lockin

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

private const val SESSION_PREFS_NAME = "lockin_session_prefs"
private const val KEY_ACTIVE = "session_active"
private const val KEY_START_TIME = "session_start_time"
private const val KEY_GROUP_ID = "session_group_id"
private const val KEY_GROUP_NAME = "session_group_name"

data class LockInSession(
    val isActive: Boolean,
    val startTimeMillis: Long,
    val groupId: String? = null,
    // Denormalized at start so session teardown can post the group's name to
    // the feed without an async group-doc read while the service is dying.
    val groupName: String? = null,
)

fun loadSession(context: Context): LockInSession {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    return LockInSession(
        isActive = prefs.getBoolean(KEY_ACTIVE, false),
        startTimeMillis = prefs.getLong(KEY_START_TIME, 0L),
        groupId = prefs.getString(KEY_GROUP_ID, null),
        groupName = prefs.getString(KEY_GROUP_NAME, null)
    )
}

private fun saveSession(context: Context, session: LockInSession) {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean(KEY_ACTIVE, session.isActive)
        .putLong(KEY_START_TIME, session.startTimeMillis)
        .putString(KEY_GROUP_ID, session.groupId)
        .putString(KEY_GROUP_NAME, session.groupName)
        .apply()
}

fun startLockInSession(context: Context, groupId: String? = null, groupName: String? = null) {
    saveSession(context, LockInSession(isActive = true, startTimeMillis = System.currentTimeMillis(), groupId = groupId, groupName = groupName))
    ContextCompat.startForegroundService(context, Intent(context, LockInService::class.java))
}

fun stopLockInSession(context: Context) {
    saveSession(context, LockInSession(isActive = false, startTimeMillis = 0L, groupId = null, groupName = null))
    context.stopService(Intent(context, LockInService::class.java))
}
