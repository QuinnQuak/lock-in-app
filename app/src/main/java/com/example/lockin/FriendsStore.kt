package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "FriendsStore"

data class Friend(val uid: String, val displayName: String)

fun listenFriends(uid: String, onChange: (List<Friend>) -> Unit): ListenerRegistration {
    return Firebase.firestore.collection("users").document(uid).collection("friends")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Friends listener error", error)
                return@addSnapshotListener
            }
            val friends = snapshot?.documents.orEmpty().map { doc ->
                Friend(uid = doc.id, displayName = doc.getString("displayName") ?: "Friend")
            }
            onChange(friends)
        }
}

/**
 * Removes a friendship from both sides. Two independent deletes, not one batch:
 * a batch commits atomically, so a denied reciprocal delete would roll back my
 * own-side delete too -- but my side must land regardless. My own doc
 * (users/{myUid}/friends/{friendUid}) is always deletable by me; the reciprocal
 * (users/{friendUid}/friends/{myUid}) is deletable by me only under the Stage-8
 * rule letting the removed party clear itself from the other's list -- symmetric
 * with the reciprocal *create* on accept, and deploy-gated until those rules ship.
 */
fun removeFriend(myUid: String, friendUid: String) {
    val db = Firebase.firestore
    db.collection("users").document(myUid).collection("friends").document(friendUid)
        .delete()
        .addOnFailureListener { e -> Log.w(TAG, "Failed to delete own friend doc for $friendUid", e) }
    db.collection("users").document(friendUid).collection("friends").document(myUid)
        .delete()
        .addOnFailureListener { e -> Log.w(TAG, "Failed to delete reciprocal friend doc under $friendUid", e) }
}

/**
 * One-time read, not a listener: a friend's allowlist is only fetched when
 * its row is expanded, and is allowed by firestore.rules' isFriendOf() check
 * on the users/{uid} doc.
 */
fun fetchFriendAllowlist(friendUid: String, onResult: (Set<String>) -> Unit) {
    Firebase.firestore.collection("users").document(friendUid).get()
        .addOnSuccessListener { doc ->
            val list = doc.get("allowlist") as? List<*>
            onResult(list.orEmpty().filterIsInstance<String>().toSet())
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to fetch allowlist for $friendUid", e)
            onResult(emptySet())
        }
}
