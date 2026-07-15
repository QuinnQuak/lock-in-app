package com.example.lockin

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

private const val SESSION_PREFS_NAME = "lockin_session_prefs"
private const val KEY_ACTIVE = "session_active"
private const val KEY_START_TIME = "session_start_time"
private const val KEY_GROUP_ID = "session_group_id"
private const val KEY_GROUP_NAME = "session_group_name"
private const val KEY_LOBBY_ID = "session_lobby_id"
private const val KEY_ENDS_AT = "session_ends_at"

data class LockInSession(
    val isActive: Boolean,
    val startTimeMillis: Long,
    val groupId: String? = null,
    // Denormalized at start so session teardown can post the group's name to
    // the feed without an async group-doc read while the service is dying.
    val groupName: String? = null,
    // Which lobby this session joined (a group session is always a lobby now).
    val lobbyId: String? = null,
    // SHARED-mode round end; 0 = open-ended (solo or a CONCURRENT lobby).
    val endsAtMillis: Long = 0L,
)

fun loadSession(context: Context): LockInSession {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    return LockInSession(
        isActive = prefs.getBoolean(KEY_ACTIVE, false),
        startTimeMillis = prefs.getLong(KEY_START_TIME, 0L),
        groupId = prefs.getString(KEY_GROUP_ID, null),
        groupName = prefs.getString(KEY_GROUP_NAME, null),
        lobbyId = prefs.getString(KEY_LOBBY_ID, null),
        endsAtMillis = prefs.getLong(KEY_ENDS_AT, 0L)
    )
}

private fun saveSession(context: Context, session: LockInSession) {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean(KEY_ACTIVE, session.isActive)
        .putLong(KEY_START_TIME, session.startTimeMillis)
        .putString(KEY_GROUP_ID, session.groupId)
        .putString(KEY_GROUP_NAME, session.groupName)
        .putString(KEY_LOBBY_ID, session.lobbyId)
        .putLong(KEY_ENDS_AT, session.endsAtMillis)
        .apply()
}

fun startLockInSession(
    context: Context,
    groupId: String? = null,
    groupName: String? = null,
    lobbyId: String? = null,
    endsAtMillis: Long = 0L,
) {
    saveSession(
        context,
        LockInSession(
            isActive = true,
            startTimeMillis = System.currentTimeMillis(),
            groupId = groupId,
            groupName = groupName,
            lobbyId = lobbyId,
            endsAtMillis = endsAtMillis,
        )
    )
    ContextCompat.startForegroundService(context, Intent(context, LockInService::class.java))
}

fun stopLockInSession(context: Context) {
    saveSession(context, LockInSession(isActive = false, startTimeMillis = 0L))
    context.stopService(Intent(context, LockInService::class.java))
}
