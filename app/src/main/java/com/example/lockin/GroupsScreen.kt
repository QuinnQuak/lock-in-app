package com.example.lockin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun GroupsScreen(onOpenGroup: (LockInGroup) -> Unit) {
    val myUid = Firebase.auth.currentUser?.uid

    var groups by remember { mutableStateOf<List<LockInGroup>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }

    DisposableEffect(myUid) {
        val regs = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        if (myUid != null) {
            regs += listenMyGroups(myUid) { groups = it }
            regs += listenFriends(myUid) { friends = it }
        }
        onDispose { regs.forEach { it.remove() } }
    }

    if (showCreate) {
        CreateGroupPanel(
            friends = friends,
            onCancel = { showCreate = false },
            onCreated = { showCreate = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your groups",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create group", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (groups.isEmpty()) {
                Text(
                    text = "No groups yet — create one from your friends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(groups, key = { it.id }) { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGroup(group) }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${group.memberUids.size} members · mute needs ${group.muteApprovalCount} approval(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateGroupPanel(friends: List<Friend>, onCancel: () -> Unit, onCreated: () -> Unit) {
    val myUid = Firebase.auth.currentUser?.uid
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var threshold by remember { mutableStateOf("1") }
    var isSaving by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Group name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Members",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(friends, key = { it.uid }) { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected.contains(friend.uid),
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + friend.uid else selected - friend.uid
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        OutlinedTextField(
            value = threshold,
            onValueChange = { threshold = it.filter(Char::isDigit) },
            label = { Text("Approvals needed to mute a break") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            PressableButton(
                onClick = {
                    val uid = myUid ?: return@PressableButton
                    if (name.isBlank() || isSaving) return@PressableButton
                    isSaving = true
                    createGroup(
                        name = name.trim(),
                        ownerUid = uid,
                        memberUids = selected.toList(),
                        muteApprovalCount = threshold.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    ) { isSaving = false; onCreated() }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Add,
                text = "Create"
            )
        }
    }
}
