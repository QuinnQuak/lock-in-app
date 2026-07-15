package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.util.Calendar

private const val TAG = "ProfileStore"

const val DEFAULT_STREAK_MIN_MINUTES = 30
const val MIN_STREAK_MINUTES = 5
const val MAX_STREAK_MINUTES = 120

/** Current streak plus the threshold it was computed against (for the UI). */
data class StreakInfo(val streak: Int, val thresholdMinutes: Int)

/**
 * Normalizes an instant to the UTC-millis of its *local* midnight. Using a
 * zeroed Calendar (rather than java.time, unavailable below API 26 without
 * desugaring) keeps this DST-correct: the same normalization is used both to
 * bucket sessions into days and to walk days backward, so the Long keys line up.
 */
internal fun localMidnightMillis(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * Consecutive-day streak from a set of qualifying local-midnight keys. Today
 * not yet having a qualifying lock-in doesn't break the streak — it just isn't
 * counted until it does; the streak then anchors on yesterday.
 */
fun streakFromDays(days: Set<Long>): Int {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    // Anchor on today if it qualifies, else yesterday, else the streak is broken.
    if (!days.contains(cal.timeInMillis)) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
        if (!days.contains(cal.timeInMillis)) return 0
    }
    var streak = 0
    while (days.contains(cal.timeInMillis)) {
        streak++
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    return streak
}

/**
 * Reads the user's threshold + session history and computes the current streak.
 * `extraStartMillis`/`extraDurationSeconds` optionally fold in one more session
 * (the just-finished one, which may not have reached Firestore yet) — it counts
 * only if it clears the threshold. Any read failure falls back to streak 0 so a
 * caller (e.g. the feed post) is never blocked.
 */
fun fetchStreakInfo(
    uid: String,
    extraStartMillis: Long? = null,
    extraDurationSeconds: Long = 0,
    onResult: (StreakInfo) -> Unit,
) {
    val db = Firebase.firestore
    db.collection("users").document(uid).get()
        .addOnSuccessListener { userDoc ->
            val thresholdMin = (userDoc.getLong("streakMinMinutes")
                ?: DEFAULT_STREAK_MIN_MINUTES.toLong()).toInt()
            val thresholdSec = thresholdMin * 60L
            db.collection("users").document(uid).collection("sessions").get()
                .addOnSuccessListener { snap ->
                    val days = snap.documents.mapNotNull { doc ->
                        val dur = doc.getLong("durationSeconds") ?: 0L
                        val started = doc.getTimestamp("startedAt")?.toDate()?.time
                        if (dur >= thresholdSec && started != null) localMidnightMillis(started) else null
                    }.toMutableSet()
                    if (extraStartMillis != null && extraDurationSeconds >= thresholdSec) {
                        days += localMidnightMillis(extraStartMillis)
                    }
                    onResult(StreakInfo(streakFromDays(days), thresholdMin))
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Sessions read failed for streak", e)
                    onResult(StreakInfo(0, thresholdMin))
                }
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "User doc read failed for streak", e)
            onResult(StreakInfo(0, DEFAULT_STREAK_MIN_MINUTES))
        }
}

/** Merge-write the friend-visible streak threshold (clamped to sane bounds). */
fun setStreakMinMinutes(uid: String, minutes: Int) {
    val clamped = minutes.coerceIn(MIN_STREAK_MINUTES, MAX_STREAK_MINUTES)
    Firebase.firestore.collection("users").document(uid)
        .set(mapOf("streakMinMinutes" to clamped), SetOptions.merge())
        .addOnFailureListener { e -> Log.w(TAG, "Failed to set streakMinMinutes", e) }
}
