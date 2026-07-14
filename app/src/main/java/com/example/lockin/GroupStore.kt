package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "GroupStore"

data class LockInGroup(
    val id: String,
    val name: String,
    val ownerUid: String,
    val memberUids: List<String>,
    val muteApprovalCount: Int
)

fun createGroup(
    name: String,
    ownerUid: String,
    memberUids: List<String>,
    muteApprovalCount: Int,
    onResult: (Boolean) -> Unit
) {
    val group = mapOf(
        "name" to name,
        "ownerUid" to ownerUid,
        "memberUids" to (memberUids + ownerUid).distinct(),
        "muteApprovalCount" to muteApprovalCount,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    Firebase.firestore.collection("groups").add(group)
        .addOnSuccessListener { onResult(true) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to create group", e)
            onResult(false)
        }
}

fun listenMyGroups(uid: String, onChange: (List<LockInGroup>) -> Unit): ListenerRegistration {
    return Firebase.firestore.collection("groups")
        .whereArrayContains("memberUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Groups listener error", error)
                return@addSnapshotListener
            }
            val groups = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val owner = doc.getString("ownerUid") ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val members = doc.get("memberUids") as? List<String> ?: return@mapNotNull null
                val threshold = (doc.getLong("muteApprovalCount") ?: 1L).toInt()
                LockInGroup(doc.id, name, owner, members, threshold)
            }
            onChange(groups)
        }
}
