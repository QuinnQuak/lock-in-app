package com.example.lockin

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

private const val TAG = "AllowlistSync"

/**
 * Sync model: Firestore (users/{uid}.allowlist) is the source of truth,
 * SharedPreferences stays the synchronous local cache that LockInService
 * polls every second. Toggles write both; the snapshot listener pulls remote
 * changes back into the cache. Conflicts are last-write-wins, which is fine
 * while a user has one device.
 */
fun pushAllowlist(uid: String, allowlist: Set<String>) {
    // merge() so this only touches the allowlist field rather than
    // overwriting the whole profile document (displayName etc.).
    Firebase.firestore.collection("users").document(uid)
        .set(mapOf("allowlist" to allowlist.toList()), SetOptions.merge())
        .addOnFailureListener { e -> Log.w(TAG, "Failed to push allowlist for $uid", e) }
}

fun startAllowlistSync(context: Context, uid: String): ListenerRegistration {
    val appContext = context.applicationContext
    return Firebase.firestore.collection("users").document(uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Allowlist listener error", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val remote = snapshot.get("allowlist")
            if (remote == null) {
                // Account has no allowlist yet (fresh sign-up): seed the cloud
                // from whatever this device already had.
                val local = loadAllowlist(appContext)
                if (local.isNotEmpty()) pushAllowlist(uid, local)
            } else if (remote is List<*>) {
                saveAllowlist(appContext, remote.filterIsInstance<String>().toSet())
            }
        }
}
