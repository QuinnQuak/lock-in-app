package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "KudosStore"

/** Live kudos tally for one feed item: total count and whether *I* gave one. */
data class KudosState(val count: Int, val mine: Boolean)

private fun kudosCollection(authorUid: String, eventId: String): CollectionReference =
    Firebase.firestore.collection("users").document(authorUid)
        .collection("activity").document(eventId)
        .collection("kudos")

/**
 * Live listener on one item's kudos. Each visible feed card owns one; because
 * LazyColumn only composes on-screen rows, the number of live listeners is
 * bounded by what's visible, not the whole feed.
 */
fun listenKudos(
    authorUid: String,
    eventId: String,
    myUid: String,
    onChange: (KudosState) -> Unit,
): ListenerRegistration {
    return kudosCollection(authorUid, eventId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Kudos listener error for $authorUid/$eventId", error)
            return@addSnapshotListener
        }
        val docs = snapshot?.documents.orEmpty()
        onChange(KudosState(count = docs.size, mine = docs.any { it.id == myUid }))
    }
}

/**
 * Give a kudos: one doc per reactor, id == reactor's uid. firestore.rules only
 * allows this if I'm a friend of the author and never on my own post — so the
 * caller must hide the control on own items (the write would just be denied).
 */
fun giveKudos(authorUid: String, eventId: String, myUid: String, myDisplayName: String) {
    val doc = mapOf(
        "reactorUid" to myUid,
        "displayName" to myDisplayName,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    kudosCollection(authorUid, eventId).document(myUid).set(doc)
        .addOnFailureListener { e -> Log.w(TAG, "giveKudos failed", e) }
}

fun removeKudos(authorUid: String, eventId: String, myUid: String) {
    kudosCollection(authorUid, eventId).document(myUid).delete()
        .addOnFailureListener { e -> Log.w(TAG, "removeKudos failed", e) }
}
