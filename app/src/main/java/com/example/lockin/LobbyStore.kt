package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "LobbyStore"

// A lobby is an ephemeral live room inside a group. Multiple can run at once
// (like several voice channels), each with its own mode. Presence isn't stored
// on the lobby doc -- it's the group's liveStatus docs tagged with this
// lobbyId, so a member's single active session identifies which lobby they're in.
enum class LobbyMode { CONCURRENT, SHARED }

data class LockInLobby(
    val id: String,
    val hostUid: String,
    val name: String,
    val mode: LobbyMode,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    // 0 for CONCURRENT (open-ended); for SHARED it's the synced round end.
    val endsAtMillis: Long,
)

private fun lobbies(groupId: String) =
    Firebase.firestore.collection("groups").document(groupId).collection("lobbies")

// onResult returns the new lobby id and its endsAtMillis, so the host can start
// their own session with the exact same round end that was stored on the doc.
fun openLobby(
    groupId: String,
    hostUid: String,
    name: String,
    mode: LobbyMode,
    durationMinutes: Int,
    onResult: (String?, Long) -> Unit,
) {
    val startMillis = System.currentTimeMillis()
    val endsAtMillis = if (mode == LobbyMode.SHARED && durationMinutes > 0) {
        startMillis + durationMinutes * 60_000L
    } else 0L
    val doc = mapOf(
        "hostUid" to hostUid,
        "name" to name,
        "mode" to mode.name,
        "startedAt" to FieldValue.serverTimestamp(),
        "startedAtMillis" to startMillis,
        "durationMinutes" to durationMinutes,
        "endsAtMillis" to endsAtMillis,
    )
    lobbies(groupId).add(doc)
        .addOnSuccessListener { ref -> onResult(ref.id, endsAtMillis) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to open lobby", e)
            onResult(null, 0L)
        }
}

fun listenLobbies(groupId: String, onChange: (List<LockInLobby>) -> Unit): ListenerRegistration {
    return lobbies(groupId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Lobbies listener error", error)
            return@addSnapshotListener
        }
        val result = snapshot?.documents.orEmpty().mapNotNull { doc ->
            val hostUid = doc.getString("hostUid") ?: return@mapNotNull null
            val modeStr = doc.getString("mode") ?: LobbyMode.CONCURRENT.name
            val mode = runCatching { LobbyMode.valueOf(modeStr) }.getOrDefault(LobbyMode.CONCURRENT)
            LockInLobby(
                id = doc.id,
                hostUid = hostUid,
                name = doc.getString("name").orEmpty(),
                mode = mode,
                startedAtMillis = doc.getLong("startedAtMillis") ?: 0L,
                durationMinutes = (doc.getLong("durationMinutes") ?: 0L).toInt(),
                endsAtMillis = doc.getLong("endsAtMillis") ?: 0L,
            )
        }
        onChange(result)
    }
}

// Best-effort: called when the last member leaves a lobby so a dead room
// doesn't linger. Correctness never depends on it running.
fun closeLobby(groupId: String, lobbyId: String) {
    lobbies(groupId).document(lobbyId).delete()
        .addOnFailureListener { e -> Log.w(TAG, "Failed to close lobby", e) }
}
