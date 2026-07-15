package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ActivityStore"

// Per-author cap on how far back the feed reads. The feed fans out on read
// (no Cloud Functions), so this bounds each of the N per-friend queries.
private const val PER_AUTHOR_LIMIT = 20L

/**
 * Writes one friend-visible activity event under users/{uid}/activity when a
 * lock-in ends. This is the Stage 5 social-feed source: unlike the private
 * users/{uid}/sessions log it runs alongside, these docs are readable by
 * friends (see firestore.rules). Fire-and-forget, same offline-queue
 * behaviour as the session write -- a force-stopped session (no onDestroy)
 * never posts, matching the documented Stage 6 loophole.
 *
 * streakAtPost is stamped by the poster because a friend can never compute it
 * themselves (the poster's sessions are private). Wired to the real streak in
 * step 4; 0 until then.
 */
fun recordActivityToCloud(
    uid: String,
    displayName: String,
    startedAtMillis: Long,
    endedAtMillis: Long,
    breakCount: Int,
    groupId: String?,
    groupName: String?,
    streakAtPost: Int = 0,
) {
    val event = mutableMapOf<String, Any>(
        "type" to if (groupId != null) "GROUP" else "SOLO",
        "displayName" to displayName,
        "startedAt" to Timestamp(Date(startedAtMillis)),
        "endedAt" to Timestamp(Date(endedAtMillis)),
        "durationSeconds" to (endedAtMillis - startedAtMillis) / 1000,
        "breakCount" to breakCount,
        "streakAtPost" to streakAtPost,
    )
    // Denormalized so a friend who isn't in this group (and so can't read the
    // group doc) can still show its name in the feed.
    if (groupId != null) event["groupId"] = groupId
    if (groupName != null) event["groupName"] = groupName

    Firebase.firestore.collection("users").document(uid).collection("activity")
        .add(event)
        .addOnFailureListener { e -> Log.w(TAG, "Failed to record activity for $uid", e) }
}

/** One feed row, flattened from an activity doc. */
data class FeedItem(
    val id: String,
    val authorUid: String,
    val displayName: String,
    val type: String,          // "SOLO" | "GROUP"
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val breakCount: Int,
    val groupName: String?,
    val streakAtPost: Int,
)

/**
 * Fans out on read: fetches each author's recent activity in parallel, merges,
 * and returns the combined feed sorted newest-first. `uids` is the caller's own
 * uid plus their friends' — reads are permitted by firestore.rules (owner or
 * isFriendOf). One-time get, not a listener: the feed is refreshed when the
 * screen opens / the friend set changes, not streamed live.
 *
 * Individual author failures are tolerated (logged, skipped) so one unreadable
 * author can't blank the whole feed — the callback fires once every query has
 * settled, success or not.
 */
fun fetchFeed(uids: List<String>, onResult: (List<FeedItem>) -> Unit) {
    if (uids.isEmpty()) {
        onResult(emptyList())
        return
    }
    val db = Firebase.firestore
    // All Firestore callbacks land on the main thread, so this list is only
    // ever touched serially -- no synchronization needed.
    val collected = mutableListOf<FeedItem>()
    val pending = AtomicInteger(uids.size)

    for (authorUid in uids) {
        db.collection("users").document(authorUid).collection("activity")
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(PER_AUTHOR_LIMIT)
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.documents?.forEach { doc ->
                        val started = doc.getTimestamp("startedAt")?.toDate()?.time ?: return@forEach
                        collected += FeedItem(
                            id = doc.id,
                            authorUid = authorUid,
                            displayName = doc.getString("displayName") ?: "Someone",
                            type = doc.getString("type") ?: "SOLO",
                            startedAtMillis = started,
                            durationSeconds = doc.getLong("durationSeconds") ?: 0L,
                            breakCount = (doc.getLong("breakCount") ?: 0L).toInt(),
                            groupName = doc.getString("groupName"),
                            streakAtPost = (doc.getLong("streakAtPost") ?: 0L).toInt(),
                        )
                    }
                } else {
                    Log.w(TAG, "Feed fetch failed for $authorUid", task.exception)
                }
                if (pending.decrementAndGet() == 0) {
                    onResult(collected.sortedByDescending { it.startedAtMillis })
                }
            }
    }
}
