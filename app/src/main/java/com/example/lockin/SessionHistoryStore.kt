package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import java.util.Date

private const val TAG = "SessionHistoryStore"

/**
 * Records one finished session under users/{uid}/sessions. Fire-and-forget:
 * Firestore's offline persistence queues the write when the device is
 * offline. Sessions ended by force-stop never reach this (no onDestroy) —
 * that's the documented force-stop loophole, deferred to Stage 6.
 */
fun recordSessionToCloud(uid: String, startedAtMillis: Long, endedAtMillis: Long, breakCount: Int) {
    val record = mapOf(
        "startedAt" to Timestamp(Date(startedAtMillis)),
        "endedAt" to Timestamp(Date(endedAtMillis)),
        "durationSeconds" to (endedAtMillis - startedAtMillis) / 1000,
        "breakCount" to breakCount,
    )
    Firebase.firestore.collection("users").document(uid).collection("sessions")
        .add(record)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to record session for $uid", e) }
}
