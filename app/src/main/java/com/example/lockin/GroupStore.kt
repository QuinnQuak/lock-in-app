package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

private const val TAG = "GroupStore"

data class LockInGroup(
    val id: String,
    val name: String,
    val ownerUid: String,
    val memberUids: List<String>,
    val muteApprovalCount: Int,
    val adminUids: List<String> = emptyList()
) {
    /** Owner always counts as an admin for management-permission checks. */
    fun canManage(uid: String): Boolean = uid == ownerUid || uid in adminUids
}

fun createGroup(
    name: String,
    ownerUid: String,
    memberUids: List<String>,
    muteApprovalCount: Int,
    onResult: (Boolean) -> Unit
) {
    val group = mapOf(
        "name" to name,
        "ownerUid" to ownerUid,
        "memberUids" to (memberUids + ownerUid).distinct(),
        "muteApprovalCount" to muteApprovalCount,
        "createdAt" to FieldValue.serverTimestamp(),
    )
    Firebase.firestore.collection("groups").add(group)
        .addOnSuccessListener { onResult(true) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to create group", e)
            onResult(false)
        }
}

data class GroupMemberProfile(val uid: String, val displayName: String)

/**
 * Resolves a group's member uids to display names via the public userSearch
 * directory (readable by any signed-in user per firestore.rules). One-time
 * per-doc reads -- membership is small, so a fan-out of gets is simpler than a
 * batched whereIn (which also caps at 10). Names that can't be resolved fall
 * back to "Member" so the roster still renders. Order follows the input uids.
 */
fun fetchGroupMemberProfiles(uids: List<String>, onResult: (List<GroupMemberProfile>) -> Unit) {
    if (uids.isEmpty()) {
        onResult(emptyList())
        return
    }
    val db = Firebase.firestore
    val names = mutableMapOf<String, String>()
    var remaining = uids.size
    uids.forEach { uid ->
        db.collection("userSearch").document(uid).get()
            .addOnCompleteListener { task ->
                task.result?.getString("displayName")?.takeIf { it.isNotBlank() }?.let { names[uid] = it }
                remaining--
                if (remaining == 0) {
                    onResult(uids.map { GroupMemberProfile(it, names[it] ?: "Member") })
                }
            }
    }
}

// ---------------------------------------------------------------------------
// Management backend (Stage 8 step 2). No UI yet -- these are the write paths
// the Members tab + settings sheet will call. Permission is enforced twice:
// firestore.rules is the real boundary (owner = full control; admins may edit
// name/threshold/memberUids but never ownerUid/adminUids; a member may only
// remove themselves), and callers gate the UI with LockInGroup.canManage.
// ---------------------------------------------------------------------------

private fun groupDoc(groupId: String) =
    Firebase.firestore.collection("groups").document(groupId)

private fun updateGroup(
    groupId: String,
    fields: Map<String, Any>,
    onResult: (Boolean) -> Unit
) {
    groupDoc(groupId).update(fields)
        .addOnSuccessListener { onResult(true) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Group update failed ($groupId): $fields", e)
            onResult(false)
        }
}

/** Rename the group (owner or admin). */
fun renameGroup(groupId: String, name: String, onResult: (Boolean) -> Unit) =
    updateGroup(groupId, mapOf("name" to name.trim()), onResult)

/** Change how many approvals a mute needs (owner or admin). */
fun setMuteThreshold(groupId: String, count: Int, onResult: (Boolean) -> Unit) =
    updateGroup(groupId, mapOf("muteApprovalCount" to count.coerceAtLeast(1)), onResult)

/** Add members (owner or admin). Union-merge so concurrent adds don't clobber. */
fun addMembers(groupId: String, uids: List<String>, onResult: (Boolean) -> Unit) {
    if (uids.isEmpty()) { onResult(true); return }
    updateGroup(groupId, mapOf("memberUids" to FieldValue.arrayUnion(*uids.toTypedArray())), onResult)
}

/**
 * Remove another member (owner or admin). Also strips them from adminUids so a
 * removed admin doesn't linger with rights. A demote-on-remove is an adminUids
 * write, so per the rules this path is owner-only when the target is an admin;
 * for a plain member either owner or admin may call it.
 */
fun removeMember(groupId: String, uid: String, onResult: (Boolean) -> Unit) =
    updateGroup(
        groupId,
        mapOf(
            "memberUids" to FieldValue.arrayRemove(uid),
            "adminUids" to FieldValue.arrayRemove(uid),
        ),
        onResult,
    )

/** Leave a group (any non-owner member). Owner must transfer or delete instead. */
fun leaveGroup(groupId: String, uid: String, onResult: (Boolean) -> Unit) =
    updateGroup(
        groupId,
        mapOf(
            "memberUids" to FieldValue.arrayRemove(uid),
            "adminUids" to FieldValue.arrayRemove(uid),
        ),
        onResult,
    )

/** Promote a member to admin (owner only per rules). */
fun promoteAdmin(groupId: String, uid: String, onResult: (Boolean) -> Unit) =
    updateGroup(groupId, mapOf("adminUids" to FieldValue.arrayUnion(uid)), onResult)

/** Demote an admin back to member (owner only per rules). */
fun demoteAdmin(groupId: String, uid: String, onResult: (Boolean) -> Unit) =
    updateGroup(groupId, mapOf("adminUids" to FieldValue.arrayRemove(uid)), onResult)

/**
 * Delete a group and its subcollections (owner only). Firestore doesn't cascade,
 * so we best-effort delete the known subcollections (lobbies, messages,
 * liveStatus, muteRequests, muteApprovals) before removing the parent doc. Any
 * straggler docs are orphaned but unreachable once the group is gone.
 */
fun deleteGroup(groupId: String, onResult: (Boolean) -> Unit) {
    val doc = groupDoc(groupId)
    val subcollections = listOf("lobbies", "messages", "liveStatus", "muteRequests", "muteApprovals")
    var remaining = subcollections.size
    var anyFailed = false
    subcollections.forEach { sub ->
        doc.collection(sub).get()
            .addOnCompleteListener { task ->
                val docs = task.result?.documents.orEmpty()
                val batch = Firebase.firestore.batch()
                docs.forEach { batch.delete(it.reference) }
                val finishSub = {
                    remaining--
                    if (remaining == 0) {
                        doc.delete()
                            .addOnSuccessListener { onResult(!anyFailed) }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Group delete failed ($groupId)", e)
                                onResult(false)
                            }
                    }
                }
                if (docs.isEmpty()) {
                    finishSub()
                } else {
                    batch.commit()
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Subcollection cleanup failed ($groupId/$sub)", e)
                            anyFailed = true
                        }
                        .addOnCompleteListener { finishSub() }
                }
            }
    }
}

fun listenMyGroups(uid: String, onChange: (List<LockInGroup>) -> Unit): ListenerRegistration {
    return Firebase.firestore.collection("groups")
        .whereArrayContains("memberUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Groups listener error", error)
                return@addSnapshotListener
            }
            val groups = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val owner = doc.getString("ownerUid") ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val members = doc.get("memberUids") as? List<String> ?: return@mapNotNull null
                val threshold = (doc.getLong("muteApprovalCount") ?: 1L).toInt()
                @Suppress("UNCHECKED_CAST")
                val admins = doc.get("adminUids") as? List<String> ?: emptyList()
                LockInGroup(doc.id, name, owner, members, threshold, admins)
            }
            onChange(groups)
        }
}
