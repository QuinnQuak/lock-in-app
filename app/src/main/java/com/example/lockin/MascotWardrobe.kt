package com.example.lockin

import androidx.compose.runtime.staticCompositionLocalOf

// Stage 6 mascot economy: cosmetics the blob buddy can wear. Two sources feed
// the same single equip slot -- the Trophy Case (auto-granted per achievement,
// never bought) and the Shop (bought with Sparkles). An accessory is drawn as an
// emoji overlaid on the mascot at a slot-determined spot (see Mascot.kt), rather
// than hand-drawn in Canvas: far less code per item, instantly readable, and it
// matches the mood-emoji overlays (✨/💧/💤) already there. The tradeoff is that
// emoji don't recolor with the theme like the blob does -- an acceptable pop of
// colour for a large catalog.

/** Where on the mascot an accessory sits. Kept off the corner mood overlays. */
enum class AccessorySlot { HEAD, FACE, NECK }

/**
 * The full cosmetic catalog. [NONE] is the bare blob (default equip). The rest
 * split into trophy accessories ([TROPHY_ACCESSORIES], index-aligned to
 * [computeAchievements]) and shop accessories ([SHOP_ITEMS]). Stored on
 * users/{uid} by enum [name]; unknown names decode to [NONE] (forward-compatible
 * if the catalog ever shrinks).
 */
enum class MascotAccessory(
    val emoji: String,
    val displayName: String,
    val slot: AccessorySlot,
) {
    NONE("", "None", AccessorySlot.HEAD),

    // --- Trophy accessories: one per achievement tier, auto-granted, never sold.
    STARTER_BOW("🎀", "Starter Bow", AccessorySlot.NECK),
    FOCUS_CAP("🧢", "Focus Cap", AccessorySlot.HEAD),
    TOP_HAT("🎩", "Top Hat", AccessorySlot.HEAD),
    STUDY_GLASSES("👓", "Study Glasses", AccessorySlot.FACE),
    HEADPHONES("🎧", "Headphones", AccessorySlot.HEAD),
    COOL_SHADES("🕶️", "Cool Shades", AccessorySlot.FACE),
    CHAMPION_CROWN("👑", "Champion Crown", AccessorySlot.HEAD),

    // --- Shop accessories: bought with Sparkles (see SHOP_ITEMS for prices).
    FLOWER("🌸", "Flower", AccessorySlot.HEAD),
    STAR("⭐", "Star", AccessorySlot.HEAD),
    MUSHROOM_CAP("🍄", "Mushroom Cap", AccessorySlot.HEAD),
    NECKTIE("👔", "Necktie", AccessorySlot.NECK),
    BUTTERFLY("🦋", "Butterfly Friend", AccessorySlot.NECK),
    RAINBOW("🌈", "Rainbow", AccessorySlot.HEAD),
}

/**
 * Trophy accessories in achievement order -- index i is granted when
 * computeAchievements()[i].earned is true. Length must stay equal to the
 * achievement roster (7); the trophy-case UI zips the two together, so a
 * mismatch would simply drop the tail rather than crash.
 */
val TROPHY_ACCESSORIES: List<MascotAccessory> = listOf(
    MascotAccessory.STARTER_BOW,     // First Lock-In
    MascotAccessory.FOCUS_CAP,       // Getting Consistent
    MascotAccessory.TOP_HAT,         // Half-Century
    MascotAccessory.STUDY_GLASSES,   // Deep Work
    MascotAccessory.HEADPHONES,      // Ten Hours In
    MascotAccessory.COOL_SHADES,     // Week Warrior
    MascotAccessory.CHAMPION_CROWN,  // Flawless Week
)

/** A purchasable cosmetic and its Sparkles price. */
data class ShopItem(val accessory: MascotAccessory, val price: Long)

/** Shop stock, cheapest first. Prices escalate; all reachable at 1 Sparkle/min. */
val SHOP_ITEMS: List<ShopItem> = listOf(
    ShopItem(MascotAccessory.FLOWER, 15),
    ShopItem(MascotAccessory.STAR, 25),
    ShopItem(MascotAccessory.MUSHROOM_CAP, 30),
    ShopItem(MascotAccessory.NECKTIE, 45),
    ShopItem(MascotAccessory.BUTTERFLY, 55),
    ShopItem(MascotAccessory.RAINBOW, 70),
)

/** Decodes a stored accessory id; null/unknown -> [MascotAccessory.NONE]. */
fun accessoryFromId(id: String?): MascotAccessory =
    id?.let { runCatching { MascotAccessory.valueOf(it) }.getOrNull() } ?: MascotAccessory.NONE

/**
 * The currently-equipped accessory, provided at the composition root so *every*
 * Mascot (Home, Profile, loading states) wears it without threading a param
 * through each call site -- the same hoisting idea as the theme, but sourced
 * from Firestore. Static because it changes rarely (only on equip).
 */
val LocalEquippedAccessory = staticCompositionLocalOf { MascotAccessory.NONE }

/** A user's cosmetic state, read together from their users/{uid} doc. */
data class Wardrobe(
    val equipped: MascotAccessory,
    val ownedShop: Set<MascotAccessory>,
    val sparkles: Long,
)
