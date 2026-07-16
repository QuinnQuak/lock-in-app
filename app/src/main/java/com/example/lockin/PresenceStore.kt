package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "PresenceStore"

// How old a presence stamp may be before we treat the user as idle/offline.
// The service refreshes it every 1s tick; a force-stop or crash freezes it, so
// a stamp this stale means "not actually locked in right now".
private const val PRESENCE_STALE_MILLIS = 30_000L

enum class PresenceState { LOCKED_IN, BREAK, IDLE }

data class UserPresence(
    val uid: String,
    val displayName: String,
    val state: PresenceState,
    val lastSeenMillis: Long,
) {
    // A frozen stamp (service died without tearing down) reads as idle even if
    // the last written state was LOCKED_IN/BREAK.
    fun effectiveState(now: Long = System.currentTimeMillis()): PresenceState =
        if (now - lastSeenMillis > PRESENCE_STALE_MILLIS) PresenceState.IDLE else state
}

// Written by LockInService each tick while a session runs. COMPLIANT locked-in
// time is LOCKED_IN; a break is BREAK. Solo and group sessions both write it, so
// friends see focus status regardless of any shared group.
fun pushPresence(uid: String, displayName: String, complianceState: ComplianceState) {
    val presenceState = when (complianceState) {
        ComplianceState.BREAK -> PresenceState.BREAK
        ComplianceState.COMPLIANT -> PresenceState.LOCKED_IN
    }
    writePresence(uid, displayName, presenceState)
}

// On teardown: mark idle (keeps the doc so friends see "recently online" vs a
// user who has simply never locked in and has no doc at all).
fun clearPresence(uid: String, displayName: String) {
    writePresence(uid, displayName, PresenceState.IDLE)
}

private fun writePresence(uid: String, displayName: String, state: PresenceState) {
    val doc = mapOf(
        "displayName" to displayName,
        "state" to state.name,
        "lastSeenMillis" to System.currentTimeMillis(),
    )
    Firebase.firestore.collection("presence").document(uid)
        .set(doc)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to write presence", e) }
}

// Listen to the presence of a set of uids (a friends list or a group's roster).
// Firestore caps an `in`/documentId query at 10 values, so chunk and merge; the
// returned registration removes every inner listener. A uid with no doc is
// simply absent from the map -- callers treat that as idle/offline.
fun listenPresence(uids: List<String>, onChange: (Map<String, UserPresence>) -> Unit): ListenerRegistration {
    val distinct = uids.distinct()
    if (distinct.isEmpty()) {
        onChange(emptyMap())
        return ListenerRegistration { }
    }
    val merged = HashMap<String, UserPresence>()
    val chunks = distinct.chunked(10)
    val registrations = chunks.map { chunk ->
        Firebase.firestore.collection("presence")
            .whereIn(FieldPath.documentId(), chunk)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Presence listener error", error)
                    return@addSnapshotListener
                }
                // Replace this chunk's slice of the merged map so removed docs drop out.
                chunk.forEach { merged.remove(it) }
                snapshot?.documents.orEmpty().forEach { doc ->
                    val name = doc.getString("displayName") ?: return@forEach
                    val stateStr = doc.getString("state") ?: return@forEach
                    val state = runCatching { PresenceState.valueOf(stateStr) }.getOrNull() ?: return@forEach
                    val lastSeen = doc.getLong("lastSeenMillis") ?: 0L
                    merged[doc.id] = UserPresence(doc.id, name, state, lastSeen)
                }
                onChange(HashMap(merged))
            }
    }
    return ListenerRegistration { registrations.forEach { it.remove() } }
}
