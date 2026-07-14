package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "FriendRequestStore"

data class FriendRequest(
    val fromUid: String,
    val toUid: String,
    val fromDisplayName: String
)

/** Looks up a user's uid from their email via the public userSearch directory. */
fun findUidByEmail(email: String, onResult: (String?) -> Unit) {
    Firebase.firestore.collection("userSearch")
        .whereEqualTo("emailLower", email.trim().lowercase())
        .limit(1)
        .get()
        .addOnSuccessListener { snapshot -> onResult(snapshot.documents.firstOrNull()?.id) }
        .addOnFailureListener { e ->
            Log.w(TAG, "User search failed for $email", e)
            onResult(null)
        }
}

/** Doc id "fromUid_toUid"; its mere existence is the pending state (see firestore.rules). */
fun sendFriendRequest(fromUid: String, fromDisplayName: String, toUid: String, onResult: (Boolean) -> Unit) {
    val request = mapOf(
        "fromUid" to fromUid,
        "toUid" to toUid,
        "fromDisplayName" to fromDisplayName,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    Firebase.firestore.collection("friendRequests").document("${fromUid}_$toUid")
        .set(request)
        .addOnSuccessListener { onResult(true) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to send friend request $fromUid -> $toUid", e)
            onResult(false)
        }
}

fun listenIncomingRequests(uid: String, onChange: (List<FriendRequest>) -> Unit): ListenerRegistration {
    return Firebase.firestore.collection("friendRequests")
        .whereEqualTo("toUid", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Incoming requests listener error", error)
                return@addSnapshotListener
            }
            val requests = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val from = doc.getString("fromUid") ?: return@mapNotNull null
                val to = doc.getString("toUid") ?: return@mapNotNull null
                val name = doc.getString("fromDisplayName") ?: "Someone"
                FriendRequest(from, to, name)
            }
            onChange(requests)
        }
}

/**
 * Two sequential steps, not one atomic batch: Firestore evaluates a
 * batch's security rules against the *pre-batch* state, so the reciprocal
 * friends-doc write (gated on the request existing) can't be combined
 * with deleting that same request in one commit. So: create both friend
 * docs first (the request still exists, satisfying the rule), then delete
 * the request as a separate cleanup step.
 */
fun acceptFriendRequest(request: FriendRequest, myDisplayName: String, onResult: (Boolean) -> Unit) {
    val db = Firebase.firestore
    val now = FieldValue.serverTimestamp()
    val batch = db.batch()
    batch.set(
        db.collection("users").document(request.toUid).collection("friends").document(request.fromUid),
        mapOf("displayName" to request.fromDisplayName, "since" to now)
    )
    batch.set(
        db.collection("users").document(request.fromUid).collection("friends").document(request.toUid),
        mapOf("displayName" to myDisplayName, "since" to now)
    )
    batch.commit()
        .addOnSuccessListener {
            db.collection("friendRequests").document("${request.fromUid}_${request.toUid}").delete()
            onResult(true)
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to accept friend request from ${request.fromUid}", e)
            onResult(false)
        }
}

fun declineFriendRequest(request: FriendRequest) {
    Firebase.firestore.collection("friendRequests")
        .document("${request.fromUid}_${request.toUid}")
        .delete()
        .addOnFailureListener { e -> Log.w(TAG, "Failed to decline friend request from ${request.fromUid}", e) }
}
