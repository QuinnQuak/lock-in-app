package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.util.Calendar
import java.util.Locale

private const val TAG = "AchievementsStore"

/** One finished lock-in, reduced to just the fields the milestones derive from. */
data class SessionStat(
    val startedMillis: Long,
    val durationSeconds: Long,
    val breakCount: Long,
)

/** A single milestone, computed on the fly — no persisted "earned" flag anywhere. */
data class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val earned: Boolean,
    /** Short progress line for the grid, e.g. "3 / 10" or "Earned". */
    val progress: String,
)

/**
 * Longest run of consecutive local-days present in [days] (each key already a
 * local-midnight millis from [localMidnightMillis]). Walks with Calendar so it
 * stays DST-correct — the same reason the streak math avoids java.time. Only
 * counts forward from a run's start (a day whose predecessor is absent), so each
 * run is measured once.
 */
internal fun longestConsecutiveDays(days: Set<Long>): Int {
    var best = 0
    val cal = Calendar.getInstance()
    for (day in days) {
        cal.timeInMillis = day
        cal.add(Calendar.DAY_OF_MONTH, -1)
        if (days.contains(cal.timeInMillis)) continue // not a run start
        var run = 0
        cal.timeInMillis = day
        while (days.contains(cal.timeInMillis)) {
            run++
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        if (run > best) best = run
    }
    return best
}

/**
 * Derives all milestones from a user's own session history. Pure so it can be
 * reasoned about without Firestore. Streak-shaped milestones use the *longest
 * ever* consecutive run rather than the current streak, so an achievement, once
 * earned, never un-earns — unlike the live streak shown on the Profile hero.
 */
fun computeAchievements(sessions: List<SessionStat>, thresholdMinutes: Int): List<Achievement> {
    val thresholdSec = thresholdMinutes * 60L
    val count = sessions.size
    val totalSec = sessions.sumOf { it.durationSeconds }
    val maxSec = sessions.maxOfOrNull { it.durationSeconds } ?: 0L

    // A day "qualifies" if it had a lock-in clearing the threshold; it's "flawless"
    // if that qualifying lock-in also had zero breaks. One session per day is enough.
    val qualDays = sessions
        .filter { it.durationSeconds >= thresholdSec }
        .map { localMidnightMillis(it.startedMillis) }
        .toSet()
    val flawlessDays = sessions
        .filter { it.durationSeconds >= thresholdSec && it.breakCount == 0L }
        .map { localMidnightMillis(it.startedMillis) }
        .toSet()
    val bestRun = longestConsecutiveDays(qualDays)
    val bestFlawlessRun = longestConsecutiveDays(flawlessDays)

    return listOf(
        Achievement(
            "🌱", "First Lock-In", "Finish your first lock-in.",
            earned = count >= 1,
            progress = if (count >= 1) "Earned" else "0 / 1",
        ),
        Achievement(
            "🎯", "Getting Consistent", "Complete 10 lock-ins.",
            earned = count >= 10,
            progress = if (count >= 10) "Earned" else "$count / 10",
        ),
        Achievement(
            "🏅", "Half-Century", "Complete 50 lock-ins.",
            earned = count >= 50,
            progress = if (count >= 50) "Earned" else "$count / 50",
        ),
        Achievement(
            "🧠", "Deep Work", "One lock-in of 2 hours or more.",
            earned = maxSec >= 7200,
            progress = if (maxSec >= 7200) "Earned" else "${maxSec / 60} / 120 min",
        ),
        Achievement(
            "⏳", "Ten Hours In", "10 hours locked in, all-time.",
            earned = totalSec >= 36000,
            progress = if (totalSec >= 36000) "Earned"
            else String.format(Locale.US, "%.1f / 10 h", totalSec / 3600.0),
        ),
        Achievement(
            "🔥", "Week Warrior", "7 days in a row, each a qualifying lock-in.",
            earned = bestRun >= 7,
            progress = if (bestRun >= 7) "Earned" else "$bestRun / 7 days",
        ),
        Achievement(
            "✨", "Flawless Week", "7 days in a row, each a break-free lock-in.",
            earned = bestFlawlessRun >= 7,
            progress = if (bestFlawlessRun >= 7) "Earned" else "$bestFlawlessRun / 7 days",
        ),
    )
}

/**
 * Reads the user's threshold + session history and derives the milestone grid.
 * Mirrors [fetchStreakInfo]'s read shape; any failure falls back to computing
 * against no sessions (everything locked) rather than blocking the Profile UI.
 */
fun fetchAchievements(uid: String, onResult: (List<Achievement>) -> Unit) {
    val db = Firebase.firestore
    db.collection("users").document(uid).get()
        .addOnSuccessListener { userDoc ->
            val thresholdMin = (userDoc.getLong("streakMinMinutes")
                ?: DEFAULT_STREAK_MIN_MINUTES.toLong()).toInt()
            db.collection("users").document(uid).collection("sessions").get()
                .addOnSuccessListener { snap ->
                    val sessions = snap.documents.mapNotNull { doc ->
                        val started = doc.getTimestamp("startedAt")?.toDate()?.time
                            ?: return@mapNotNull null
                        SessionStat(
                            startedMillis = started,
                            durationSeconds = doc.getLong("durationSeconds") ?: 0L,
                            breakCount = doc.getLong("breakCount") ?: 0L,
                        )
                    }
                    onResult(computeAchievements(sessions, thresholdMin))
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Sessions read failed for achievements", e)
                    onResult(computeAchievements(emptyList(), thresholdMin))
                }
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "User doc read failed for achievements", e)
            onResult(computeAchievements(emptyList(), DEFAULT_STREAK_MIN_MINUTES))
        }
}
