package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "GroupSessionStore"

data class MemberStatus(
    val uid: String,
    val displayName: String,
    val state: ComplianceState,
    // Which lobby this member is currently in, so the room can group dots
    // by lobby (multiple lobbies can run in one group at once).
    val lobbyId: String? = null,
)

fun pushLiveStatus(groupId: String, uid: String, displayName: String, status: ComplianceStatus, lobbyId: String?) {
    val doc = mapOf(
        "displayName" to displayName,
        "state" to status.state.name,
        "lobbyId" to lobbyId,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
    Firebase.firestore.collection("groups").document(groupId).collection("liveStatus").document(uid)
        .set(doc)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to push live status", e) }
}

fun clearLiveStatus(groupId: String, uid: String) {
    Firebase.firestore.collection("groups").document(groupId).collection("liveStatus").document(uid)
        .delete()
        .addOnFailureListener { e -> Log.w(TAG, "Failed to clear live status", e) }
}

fun listenGroupLiveStatus(groupId: String, onChange: (List<MemberStatus>) -> Unit): ListenerRegistration {
    return Firebase.firestore.collection("groups").document(groupId).collection("liveStatus")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Live status listener error", error)
                return@addSnapshotListener
            }
            val statuses = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val name = doc.getString("displayName") ?: return@mapNotNull null
                val stateStr = doc.getString("state") ?: return@mapNotNull null
                val state = runCatching { ComplianceState.valueOf(stateStr) }.getOrNull() ?: return@mapNotNull null
                MemberStatus(doc.id, name, state, doc.getString("lobbyId"))
            }
            onChange(statuses)
        }
}
