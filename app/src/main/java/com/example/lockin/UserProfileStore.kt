package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

private const val TAG = "UserProfileStore"

/**
 * Creates the users/{uid} document for a freshly signed-up account, plus a
 * userSearch/{uid} entry — a separate public directory doc (email + display
 * name only) so friend-search can look someone up without read access to
 * their private profile.
 *
 * Fire-and-forget: Firestore's offline persistence queues the write if the
 * device is offline, so a failure here is logged rather than surfaced —
 * the profile doc can be re-created/merged by later sync work if it's missing.
 */
fun createUserProfile(uid: String, displayName: String, email: String) {
    val db = Firebase.firestore
    val profile = mapOf(
        "displayName" to displayName,
        "email" to email,
        "createdAt" to FieldValue.serverTimestamp(),
        // Per-user streak threshold: a day only counts toward a streak if a
        // lock-in reaches this many minutes. Customizable but friend-visible
        // (this doc is friend-readable), mirroring the allowlist transparency
        // rule -- you can't secretly lower it to farm streaks.
        "streakMinMinutes" to 30,
    )
    db.collection("users").document(uid)
        .set(profile)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to create user profile for $uid", e) }

    val searchEntry = mapOf(
        "emailLower" to email.trim().lowercase(),
        "displayName" to displayName,
    )
    db.collection("userSearch").document(uid)
        .set(searchEntry)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to create search entry for $uid", e) }
}
