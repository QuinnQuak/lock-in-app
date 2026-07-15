package com.example.lockin

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun FeedScreen() {
    val currentUser = Firebase.auth.currentUser
    val myUid = currentUser?.uid
    val myDisplayName = currentUser?.displayName ?: "Someone"
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    // null = still loading the first fetch; empty list = loaded but nothing.
    var items by remember { mutableStateOf<List<FeedItem>?>(null) }

    DisposableEffect(myUid) {
        val reg: ListenerRegistration? = if (myUid != null) {
            listenFriends(myUid) { friends = it }
        } else null
        onDispose { reg?.remove() }
    }

    // Refetch when the viewer or their friend set changes. The feed is my own
    // activity plus every friend's -- fanned out and merged by fetchFeed.
    LaunchedEffect(myUid, friends) {
        if (myUid == null) {
            items = emptyList()
            return@LaunchedEffect
        }
        val uids = (listOf(myUid) + friends.map { it.uid }).distinct()
        fetchFeed(uids) { items = it }
    }

    val list = items
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
        list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No activity yet.\nFinish a lock-in — or add friends — to fill this up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Doc ids are unique per author; prefix with author so the key is
            // unique across the whole merged list (LazyColumn key gotcha).
            items(list, key = { "${it.authorUid}-${it.id}" }) { item ->
                FeedItemCard(item, myUid, myDisplayName)
            }
        }
    }
}

@Composable
private fun FeedItemCard(item: FeedItem, myUid: String?, myDisplayName: String) {
    val isMine = item.authorUid == myUid
    var kudos by remember(item.id) { mutableStateOf(KudosState(0, false)) }

    // Each visible card owns one kudos listener; bounded by on-screen rows.
    DisposableEffect(item.id, myUid) {
        val reg = if (myUid != null) {
            listenKudos(item.authorUid, item.id, myUid) { kudos = it }
        } else null
        onDispose { reg?.remove() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isMine) "You" else item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (item.streakAtPost > 0) {
                    Text(
                        text = "🔥 ${item.streakAtPost}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Locked in ${formatDuration(item.durationSeconds)} · ${formatBreaks(item.breakCount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.type == "GROUP" && !item.groupName.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "with ${item.groupName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = relativeTime(item.startedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KudosControl(item, myUid, myDisplayName, isMine, kudos)
        }
    }
}

@Composable
private fun KudosControl(
    item: FeedItem,
    myUid: String?,
    myDisplayName: String,
    isMine: Boolean,
    kudos: KudosState,
) {
    // Own posts can't be kudos'd (rules forbid it) — show a read-only tally,
    // and only once someone has actually reacted, to keep own posts uncluttered.
    if (isMine) {
        if (kudos.count == 0) return
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = kudosLabel(kudos.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (myUid == null) return@IconButton
            if (kudos.mine) removeKudos(item.authorUid, item.id, myUid)
            else giveKudos(item.authorUid, item.id, myUid, myDisplayName)
        }) {
            Icon(
                imageVector = if (kudos.mine) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (kudos.mine) "Remove kudos" else "Give kudos",
                tint = if (kudos.mine) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (kudos.count > 0) {
            Text(
                text = kudosLabel(kudos.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun kudosLabel(count: Int): String = if (count == 1) "1 kudos" else "$count kudos"

private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    if (totalMinutes < 1) return "<1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatBreaks(count: Int): String = when (count) {
    0 -> "no breaks"
    1 -> "1 break"
    else -> "$count breaks"
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
