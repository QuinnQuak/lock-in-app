package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

private const val TAG = "ChatStore"

data class GroupMessage(
    val id: String,
    val senderUid: String,
    val displayName: String,
    val text: String,
    val createdAtMillis: Long,
)

fun sendGroupMessage(groupId: String, senderUid: String, displayName: String, text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    val doc = mapOf(
        "senderUid" to senderUid,
        "displayName" to displayName,
        "text" to trimmed,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    Firebase.firestore.collection("groups").document(groupId).collection("messages")
        .add(doc)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to send message", e) }
}

fun listenGroupMessages(groupId: String, onChange: (List<GroupMessage>) -> Unit): ListenerRegistration {
    return Firebase.firestore.collection("groups").document(groupId).collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .limitToLast(50)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Messages listener error", error)
                return@addSnapshotListener
            }
            val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val sender = doc.getString("senderUid") ?: return@mapNotNull null
                val name = doc.getString("displayName") ?: "Someone"
                val text = doc.getString("text") ?: return@mapNotNull null
                // The server timestamp is null on the local echo until the write
                // is confirmed; fall back to now so a just-sent message still
                // sorts to the bottom instead of jumping.
                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                GroupMessage(doc.id, sender, name, text, createdAt)
            }
            onChange(messages)
        }
}
