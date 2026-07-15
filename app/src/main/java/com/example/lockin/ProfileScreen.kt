package com.example.lockin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun ProfileScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    equippedAccessory: MascotAccessory,
    onEquipAccessory: (MascotAccessory) -> Unit,
    onOpenAllowlist: () -> Unit,
    onSignOut: () -> Unit
) {
    val currentUser = Firebase.auth.currentUser
    val myUid = currentUser?.uid
    val myName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "You"

    var streak by remember { mutableStateOf<Int?>(null) }
    var threshold by remember { mutableStateOf<Int?>(null) }
    var achievements by remember { mutableStateOf<List<Achievement>?>(null) }
    var sparkles by remember { mutableStateOf<Long?>(null) }
    // Purchased shop cosmetics; trophy ownership is derived from `achievements`.
    var ownedShop by remember { mutableStateOf<Set<MascotAccessory>>(emptySet()) }
    // The shop item awaiting a buy confirmation, if any.
    var pendingPurchase by remember { mutableStateOf<ShopItem?>(null) }

    LaunchedEffect(myUid) {
        if (myUid == null) return@LaunchedEffect
        fetchStreakInfo(myUid) { info ->
            streak = info.streak
            threshold = info.thresholdMinutes
        }
        fetchAchievements(myUid) { achievements = it }
        // One read covers the Sparkles pill + owned shop items.
        fetchWardrobe(myUid) { w ->
            sparkles = w.sparkles
            ownedShop = w.ownedShop
        }
    }

    fun changeThreshold(delta: Int) {
        val uid = myUid ?: return
        val current = threshold ?: return
        val next = (current + delta).coerceIn(MIN_STREAK_MINUTES, MAX_STREAK_MINUTES)
        if (next == current) return
        threshold = next
        setStreakMinMinutes(uid, next)
        // Threshold changes which days qualify, so recompute streak *and* the
        // streak-shaped achievements (Week Warrior / Flawless Week) against it.
        fetchStreakInfo(uid) { info -> streak = info.streak }
        fetchAchievements(uid) { achievements = it }
    }

    val loadedStreak = streak
    val loadedThreshold = threshold
    if (loadedStreak == null || loadedThreshold == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(mood = MascotMood.IDLE, size = 64.dp)
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(mood = MascotMood.IDLE, size = 80.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = myName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Sparkles balance — the Stage 6 currency (earned 1/min locked in,
        // spent later in the Shop). Shown here so the balance is visible even
        // before there's anywhere to spend it. Rendered once loaded to avoid a
        // 0 → real-value flash on cold open.
        val loadedSparkles = sparkles
        if (loadedSparkles != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "✨", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$loadedSparkles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (loadedSparkles == 1L) "Sparkle" else "Sparkles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Streak hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(30.dp))
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (loadedStreak > 0) "🔥 $loadedStreak" else "🌱",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (loadedStreak) {
                        0 -> "No streak yet — lock in today to start one"
                        1 -> "1 day streak"
                        else -> "$loadedStreak day streak"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Streak goal (threshold) — customizable but friend-visible.
        Text(
            text = "Streak goal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "A day counts at",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { changeThreshold(-5) }) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "$loadedThreshold min",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { changeThreshold(5) }) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Lock in for at least this long to keep your streak alive. Your friends can see this goal — no secretly setting it to a minute.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // Achievements — milestones derived on the fly from own sessions.
        val loadedAchievements = achievements
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loadedAchievements != null) {
                Text(
                    text = "${loadedAchievements.count { it.earned }} of ${loadedAchievements.size} earned",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (loadedAchievements == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.width(24.dp)
            )
        } else {
            loadedAchievements.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { a -> AchievementCell(a, Modifier.weight(1f)) }
                    // Keep the last odd cell half-width instead of stretching it.
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        // Trophy Case — one signature accessory per achievement tier, auto-granted
        // (never bought), index-aligned to the achievements above. Equipping is a
        // single slot shared with the Shop; the "None" chip goes back to a bare blob.
        if (loadedAchievements != null) {
            val unlockedCount = loadedAchievements.take(TROPHY_ACCESSORIES.size).count { it.earned }
            SectionHeader(
                title = "Trophy Case",
                trailing = "$unlockedCount of ${TROPHY_ACCESSORIES.size} unlocked"
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Earn achievements to unlock accessories for your buddy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            val trophyChips = buildList {
                add(
                    AccessoryChipModel(
                        accessory = MascotAccessory.NONE,
                        sublabel = if (equippedAccessory == MascotAccessory.NONE) "Equipped" else "Bare",
                        owned = true,
                        equipped = equippedAccessory == MascotAccessory.NONE,
                        enabled = true,
                        onClick = { onEquipAccessory(MascotAccessory.NONE) },
                    )
                )
                TROPHY_ACCESSORIES.forEachIndexed { i, acc ->
                    val earned = loadedAchievements.getOrNull(i)?.earned == true
                    add(
                        AccessoryChipModel(
                            accessory = acc,
                            sublabel = when {
                                !earned -> "Locked"
                                equippedAccessory == acc -> "Equipped"
                                else -> "Tap to wear"
                            },
                            owned = earned,
                            equipped = earned && equippedAccessory == acc,
                            enabled = earned,
                            onClick = { onEquipAccessory(acc) },
                        )
                    )
                }
            }
            trophyChips.chunked(4).forEach { rowModels ->
                AccessoryChipRow(rowModels)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))
        }

        // Shop — cosmetics bought with Sparkles (earned 1/min locked in). Same
        // single equip slot as the trophy case. Tapping an unowned, affordable
        // item confirms a purchase; an owned item just equips.
        val loadedSparklesForShop = sparkles
        if (loadedSparklesForShop != null) {
            SectionHeader(title = "Shop", trailing = "✨ $loadedSparklesForShop")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Spend Sparkles on extra accessories. You earn 1 per minute locked in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            val shopChips = SHOP_ITEMS.map { item ->
                val owned = item.accessory in ownedShop
                val affordable = loadedSparklesForShop >= item.price
                AccessoryChipModel(
                    accessory = item.accessory,
                    sublabel = when {
                        owned && equippedAccessory == item.accessory -> "Equipped"
                        owned -> "Tap to wear"
                        else -> "✨ ${item.price}"
                    },
                    owned = owned,
                    equipped = owned && equippedAccessory == item.accessory,
                    // Owned -> always tappable (equip); unowned -> only if affordable.
                    enabled = owned || affordable,
                    onClick = {
                        if (owned) onEquipAccessory(item.accessory)
                        else pendingPurchase = item
                    },
                )
            }
            shopChips.chunked(4).forEach { rowModels ->
                AccessoryChipRow(rowModels)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))
        }

        // Buy-confirmation dialog. Spending currency is worth an explicit tap.
        val toBuy = pendingPurchase
        if (toBuy != null) {
            AlertDialog(
                onDismissRequest = { pendingPurchase = null },
                title = { Text("Buy ${toBuy.accessory.displayName}?") },
                text = {
                    Text("This costs ✨ ${toBuy.price}. It'll be added to your wardrobe and equipped right away.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uid = myUid
                        pendingPurchase = null
                        if (uid != null) {
                            purchaseAccessory(uid, toBuy) { success, newBalance ->
                                if (success) {
                                    sparkles = newBalance
                                    ownedShop = ownedShop + toBuy.accessory
                                    onEquipAccessory(toBuy.accessory)
                                }
                            }
                        }
                    }) {
                        Text("Buy", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPurchase = null }) { Text("Cancel") }
                }
            )
        }

        // Theme picker — a curated set of accent skins, not an open picker
        // (see CONTEXT.md's Design Direction). Device-local only (ThemeStore).
        Text(
            text = "Theme",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppTheme.entries.forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = theme == currentTheme,
                    onClick = { onThemeChange(theme) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Settings — the app's config lives under Profile now that Home is just
        // the timer. Allowlist navigates in; sign-out is a terminal action.
        Text(
            text = "Settings",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            icon = Icons.Rounded.Apps,
            label = "Allowlist",
            onClick = onOpenAllowlist,
            showChevron = true
        )
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            icon = Icons.AutoMirrored.Rounded.Logout,
            label = "Sign out",
            onClick = onSignOut,
            tint = MaterialTheme.colorScheme.error,
            showChevron = false
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    showChevron: Boolean,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Preview each skin's own primary, not the current app theme's -- this is
    // the one place a swatch must show a color other than MaterialTheme's.
    val swatchColor = theme.colorScheme(dark = isSystemInDarkTheme()).primary
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RowScope.AchievementCell(a: Achievement, modifier: Modifier = Modifier) {
    val bg =
        if (a.earned) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(bg, RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = a.emoji,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.alpha(if (a.earned) 1f else 0.35f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = a.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (a.earned) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = a.progress,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = if (a.earned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// One cell in the trophy case / shop grid. Shared model so both sections use the
// same chip: a slot-agnostic emoji puck with owned/equipped/locked styling.
private data class AccessoryChipModel(
    val accessory: MascotAccessory,
    val sublabel: String,
    val owned: Boolean,
    val equipped: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AccessoryChipRow(models: List<AccessoryChipModel>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        models.forEach { AccessoryChip(it, Modifier.weight(1f)) }
        // Pad a short final row so chips keep their column width, not stretch.
        repeat(4 - models.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun AccessoryChip(model: AccessoryChipModel, modifier: Modifier = Modifier) {
    val circleBg = if (model.equipped) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = model.enabled, onClick = model.onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(circleBg)
                .then(
                    if (model.equipped) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                )
                .alpha(if (model.owned) 1f else 0.4f),
            contentAlignment = Alignment.Center
        ) {
            if (model.accessory == MascotAccessory.NONE) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(text = model.accessory.emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = model.accessory.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (model.owned) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (model.owned) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = model.sublabel,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (model.equipped) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
