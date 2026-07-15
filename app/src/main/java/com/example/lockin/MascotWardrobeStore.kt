package com.example.lockin

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

private const val TAG = "MascotWardrobeStore"

/**
 * Reads/writes the mascot wardrobe. Like Sparkles, it rides the already
 * friend-readable, owner-writable users/{uid} doc -- so no new security rule.
 * `equippedAccessory` is a single enum-name string; `ownedAccessories` is an
 * array of the *purchased* shop items (trophy accessories aren't stored -- their
 * ownership is derived on the fly from achievements, same philosophy as the
 * streak/achievement math).
 */

/** Reads equipped + owned + Sparkles balance in one get; all default-empty on failure. */
fun fetchWardrobe(uid: String, onResult: (Wardrobe) -> Unit) {
    Firebase.firestore.collection("users").document(uid).get()
        .addOnSuccessListener { doc ->
            val equipped = accessoryFromId(doc.getString("equippedAccessory"))
            val owned = (doc.get("ownedAccessories") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.map { accessoryFromId(it) }
                ?.filter { it != MascotAccessory.NONE }
                ?.toSet()
                ?: emptySet()
            val sparkles = doc.getLong("sparkles") ?: 0L
            onResult(Wardrobe(equipped, owned, sparkles))
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to read wardrobe for $uid", e)
            onResult(Wardrobe(MascotAccessory.NONE, emptySet(), 0L))
        }
}

/** Reads just the equipped accessory -- used by the composition root on sign-in. */
fun fetchEquippedAccessory(uid: String, onResult: (MascotAccessory) -> Unit) {
    Firebase.firestore.collection("users").document(uid).get()
        .addOnSuccessListener { onResult(accessoryFromId(it.getString("equippedAccessory"))) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Failed to read equipped accessory for $uid", e)
            onResult(MascotAccessory.NONE)
        }
}

/** Persists the equip slot. Fire-and-forget; the UI updates its hoisted state optimistically. */
fun equipAccessory(uid: String, accessory: MascotAccessory) {
    Firebase.firestore.collection("users").document(uid)
        .set(mapOf("equippedAccessory" to accessory.name), SetOptions.merge())
        .addOnFailureListener { e -> Log.w(TAG, "Failed to equip ${accessory.name} for $uid", e) }
}

/**
 * Buys a shop item: in one transaction, verifies the balance covers [price],
 * decrements Sparkles, and adds the item to ownedAccessories. A transaction (not
 * a plain increment) because the guard and the debit must be atomic -- a
 * double-tap or two devices can't overspend or drive the balance negative.
 * [onResult] gets (success, newBalance); success is false on insufficient funds
 * or any failure.
 */
fun purchaseAccessory(
    uid: String,
    item: ShopItem,
    onResult: (success: Boolean, newBalance: Long) -> Unit,
) {
    val db = Firebase.firestore
    val ref = db.collection("users").document(uid)
    db.runTransaction { txn ->
        val snap = txn.get(ref)
        val balance = snap.getLong("sparkles") ?: 0L
        if (balance < item.price) {
            // Signal "can't afford" without throwing: carry the balance back out.
            return@runTransaction Pair(false, balance)
        }
        txn.set(
            ref,
            mapOf(
                "sparkles" to balance - item.price,
                "ownedAccessories" to FieldValue.arrayUnion(item.accessory.name),
            ),
            SetOptions.merge(),
        )
        Pair(true, balance - item.price)
    }.addOnSuccessListener { (success, balance) -> onResult(success, balance) }
        .addOnFailureListener { e ->
            Log.w(TAG, "Purchase of ${item.accessory.name} failed for $uid", e)
            onResult(false, 0L)
        }
}
