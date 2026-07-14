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
