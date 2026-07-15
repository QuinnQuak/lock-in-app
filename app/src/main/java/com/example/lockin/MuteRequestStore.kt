package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "MuteRequestStore"

// A breaker asks the group to silence their alarm; the room's
// muteApprovalCount decides how many *other* members must agree.
//
// Both collections are flat (rather than approvals nested under a request)
// so a single listener per collection covers the whole group -- the
// composite id mirrors the friendRequests "fromUid_toUid" idiom.
//
// breakId is the millisecond of the COMPLIANT->BREAK transition it belongs
// to. An approval only counts against a matching breakId, so leftover docs
// from an earlier break can never silence a later one -- cleanup below is
// best-effort, and correctness doesn't depend on it running.
data class MuteRequest(val breakerUid: String, val displayName: String, val breakId: Long, val lobbyId: String? = null)

data class MuteApproval(val breakerUid: String, val approverUid: String, val breakId: Long, val lobbyId: String? = null)

private fun requests(groupId: String) =
    Firebase.firestore.collection("groups").document(groupId).collection("muteRequests")

private fun approvals(groupId: String) =
    Firebase.firestore.collection("groups").document(groupId).collection("muteApprovals")

fun requestMute(groupId: String, breakerUid: String, displayName: String, breakId: Long, lobbyId: String?) {
    val doc = mapOf(
        "displayName" to displayName,
        "breakId" to breakId,
        "lobbyId" to lobbyId,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    requests(groupId).document(breakerUid).set(doc)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to request mute", e) }
}

fun approveMute(groupId: String, breakerUid: String, approverUid: String, breakId: Long, lobbyId: String?) {
    val doc = mapOf(
        "breakerUid" to breakerUid,
        "approverUid" to approverUid,
        "breakId" to breakId,
        "lobbyId" to lobbyId,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    approvals(groupId).document("${breakerUid}_$approverUid").set(doc)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to approve mute", e) }
}

// Called when the break ends (or the session does). Best-effort: the
// breakId guard above is what actually keeps stale docs harmless.
fun clearMuteRequest(groupId: String, breakerUid: String) {
    requests(groupId).document(breakerUid).delete()
        .addOnFailureListener { e -> Log.w(TAG, "Failed to clear mute request", e) }
    approvals(groupId).whereEqualTo("breakerUid", breakerUid).get()
        .addOnSuccessListener { snapshot -> snapshot.documents.forEach { it.reference.delete() } }
        .addOnFailureListener { e -> Log.w(TAG, "Failed to clear mute approvals", e) }
}

fun listenMuteRequests(groupId: String, onChange: (List<MuteRequest>) -> Unit): ListenerRegistration {
    return requests(groupId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Mute request listener error", error)
            return@addSnapshotListener
        }
        onChange(
            snapshot?.documents.orEmpty().mapNotNull { doc ->
                val name = doc.getString("displayName") ?: return@mapNotNull null
                val breakId = doc.getLong("breakId") ?: return@mapNotNull null
                MuteRequest(doc.id, name, breakId, doc.getString("lobbyId"))
            }
        )
    }
}

fun listenMuteApprovals(groupId: String, onChange: (List<MuteApproval>) -> Unit): ListenerRegistration {
    return approvals(groupId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Mute approval listener error", error)
            return@addSnapshotListener
        }
        onChange(
            snapshot?.documents.orEmpty().mapNotNull { doc ->
                val breakerUid = doc.getString("breakerUid") ?: return@mapNotNull null
                val approverUid = doc.getString("approverUid") ?: return@mapNotNull null
                val breakId = doc.getLong("breakId") ?: return@mapNotNull null
                MuteApproval(breakerUid, approverUid, breakId, doc.getString("lobbyId"))
            }
        )
    }
}
