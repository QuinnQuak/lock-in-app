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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.material3.Text

private sealed class SendState {
    data object Idle : SendState()
    data object Searching : SendState()
    data object Sent : SendState()
    data class Failed(val message: String) : SendState()
}

@Composable
fun FriendsScreen() {
    val context = LocalContext.current
    val currentUser = Firebase.auth.currentUser
    val myUid = currentUser?.uid
    val myDisplayName = currentUser?.displayName ?: "Someone"

    var emailInput by remember { mutableStateOf("") }
    var sendState by remember { mutableStateOf<SendState>(SendState.Idle) }
    var incoming by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }

    DisposableEffect(myUid) {
        val registrations = mutableListOf<ListenerRegistration>()
        if (myUid != null) {
            registrations += listenIncomingRequests(myUid) { incoming = it }
            registrations += listenFriends(myUid) { friends = it }
        }
        onDispose { registrations.forEach { it.remove() } }
    }

    fun sendRequest() {
        val email = emailInput.trim()
        if (email.isEmpty() || myUid == null) return
        sendState = SendState.Searching
        findUidByEmail(email) { foundUid ->
            when {
                foundUid == null -> sendState = SendState.Failed("No user found with that email.")
                foundUid == myUid -> sendState = SendState.Failed("That's your own email.")
                friends.any { it.uid == foundUid } -> sendState = SendState.Failed("Already friends.")
                else -> sendFriendRequest(myUid, myDisplayName, foundUid) { success ->
                    sendState = if (success) SendState.Sent else SendState.Failed("Something went wrong.")
                }
            }
        }
    }

    // A request whose sender is already a friend is stale leftover state
    // (e.g. the accept succeeded but the cleanup delete didn't fire before
    // the app was killed) -- filter it out of the list rather than show a
    // duplicate, and importantly: never key it the same as a friend below,
    // since LazyColumn keys must be unique across the *whole* list, not
    // just within one items() block. Also quietly self-heal by deleting it.
    val friendUids = friends.map { it.uid }.toSet()
    val pendingIncoming = incoming.filterNot { it.fromUid in friendUids }
    LaunchedEffect(incoming, friendUids) {
        incoming.filter { it.fromUid in friendUids }.forEach { declineFriendRequest(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Add a friend",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it; sendState = SendState.Idle },
                    placeholder = { Text("Friend's email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (sendState == SendState.Searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { sendRequest() }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Send friend request",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            when (val state = sendState) {
                is SendState.Sent -> StatusLine("Request sent.", MaterialTheme.colorScheme.secondary)
                is SendState.Failed -> StatusLine(state.message, MaterialTheme.colorScheme.error)
                else -> {}
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        if (pendingIncoming.isNotEmpty()) {
            item {
                Text(
                    text = "Requests",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(pendingIncoming, key = { "request-${it.fromUid}" }) { request ->
                RequestRow(
                    request = request,
                    onAccept = {
                        acceptFriendRequest(request, myDisplayName) { }
                    },
                    onDecline = { declineFriendRequest(request) }
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        item {
            Text(
                text = "Friends",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (friends.isEmpty()) {
            item {
                Text(
                    text = "No friends yet — add one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(friends, key = { "friend-${it.uid}" }) { friend ->
            FriendRow(friend, context)
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun RequestRow(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = request.fromDisplayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAccept) {
            Icon(Icons.Rounded.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = onDecline) {
            Icon(Icons.Rounded.Close, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, context: android.content.Context) {
    var expanded by remember { mutableStateOf(false) }
    var allowlist by remember { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(expanded) {
        if (expanded && allowlist == null) {
            fetchFriendAllowlist(friend.uid) { allowlist = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "Hide allowlist" else "View allowlist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            val list = allowlist
            when {
                list == null -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                list.isEmpty() -> Text(
                    "No allowlisted apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Column {
                    list.forEach { packageName ->
                        Text(
                            text = "• ${appLabelFor(context, packageName)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
