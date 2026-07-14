package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

private const val TAG = "UserProfileStore"

/**
 * Creates the users/{uid} document for a freshly signed-up account.
 *
 * Fire-and-forget: Firestore's offline persistence queues the write if the
 * device is offline, so a failure here is logged rather than surfaced —
 * the profile doc can be re-created/merged by later sync work if it's missing.
 */
fun createUserProfile(uid: String, displayName: String, email: String) {
    val profile = mapOf(
        "displayName" to displayName,
        "email" to email,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    Firebase.firestore.collection("users").document(uid)
        .set(profile)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to create user profile for $uid", e) }
}
