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
private const val KEY_LAST_HEARTBEAT = "session_last_heartbeat"

// The service writes a heartbeat every 1s tick; if an "active" session's
// heartbeat is older than this, the service is dead (force-stop / crash / OS
// kill) and the session is a phantom to be voided. Comfortably clears the 1s
// tick cadence, so a merely-backgrounded (still-beating) session never trips it.
const val SESSION_STALE_THRESHOLD_MILLIS = 10_000L

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
    // Ground-truth "is the service alive" signal: refreshed each tick by
    // LockInService. Stale-while-active => force-closed (Stage 7 step 2).
    val lastHeartbeatMillis: Long = 0L,
)

// True when prefs still say a session is active but its heartbeat has gone
// stale -- i.e. the service died without running onDestroy (a force-stop),
// leaving a phantom session that recorded nothing and deserves no credit.
fun LockInSession.isStale(now: Long = System.currentTimeMillis()): Boolean =
    isActive && now - lastHeartbeatMillis > SESSION_STALE_THRESHOLD_MILLIS

fun loadSession(context: Context): LockInSession {
    val prefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    return LockInSession(
        isActive = prefs.getBoolean(KEY_ACTIVE, false),
        startTimeMillis = prefs.getLong(KEY_START_TIME, 0L),
        groupId = prefs.getString(KEY_GROUP_ID, null),
        groupName = prefs.getString(KEY_GROUP_NAME, null),
        lobbyId = prefs.getString(KEY_LOBBY_ID, null),
        endsAtMillis = prefs.getLong(KEY_ENDS_AT, 0L),
        lastHeartbeatMillis = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
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
        .putLong(KEY_LAST_HEARTBEAT, session.lastHeartbeatMillis)
        .apply()
}

// Refreshed by LockInService every tick so a live (or merely backgrounded,
// still-beating) session stays fresh; a stale value means the service died.
fun writeHeartbeat(context: Context) {
    context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis())
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
            // Seed so a just-started session isn't seen as stale before the
            // service's first heartbeat tick lands.
            lastHeartbeatMillis = System.currentTimeMillis(),
        )
    )
    ContextCompat.startForegroundService(context, Intent(context, LockInService::class.java))
}

fun stopLockInSession(context: Context) {
    saveSession(context, LockInSession(isActive = false, startTimeMillis = 0L))
    context.stopService(Intent(context, LockInService::class.java))
}
