package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

private const val TAG = "SparklesStore"

/**
 * Sparkles: the Stage 6 passively-earned currency — 1 Sparkle per whole minute
 * locked in (solo or group), spent later in the Shop (step 6). Stored as a
 * counter field on users/{uid} and bumped with FieldValue.increment so awards
 * never clobber each other via read-modify-write, and so it works before the
 * field exists (increment seeds it). Rides the already-friend-readable users
 * doc, so no new security rule — the balance isn't shown to friends anyway.
 */

/**
 * Awards Sparkles for a just-finished session: floor(durationSeconds / 60), so
 * a sub-minute session earns nothing (the "1 per minute" rule lives here only).
 * Fire-and-forget from LockInService teardown, alongside the session/activity
 * writes; a force-stopped session (no onDestroy) awards nothing, matching the
 * documented loophole.
 */
fun awardSparkles(uid: String, durationSeconds: Long) {
    val minutes = durationSeconds / 60
    if (minutes <= 0) return
    Firebase.firestore.collection("users").document(uid)
        .set(mapOf("sparkles" to FieldValue.increment(minutes)), SetOptions.merge())
        .addOnFailureListener { e -> Log.w(TAG, "Failed to award $minutes sparkles to $uid", e) }
}

/** Reads the user's Sparkles balance; 0 on a missing field or any read failure. */
fun fetchSparkles(uid: String, onResult: (Long) -> Unit) {
    Firebase.firestore.collection("users").document(uid).get()
        .addOnSuccessListener { onResult(it.getLong("sparkles") ?: 0L) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to read sparkles for $uid", e)
            onResult(0L)
        }
}
