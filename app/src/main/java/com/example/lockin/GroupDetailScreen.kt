package com.example.lockin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

@Composable
fun GroupDetailScreen(group: LockInGroup) {
    val context = LocalContext.current
    val myUid = Firebase.auth.currentUser?.uid
    // A blank (not just null) display name slips past a plain `?:`, so guard on
    // isNotBlank -- test accounts created via REST have an empty Auth profile name.
    val myName = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"

    var lobbies by remember { mutableStateOf<List<LockInLobby>>(emptyList()) }
    var memberStatuses by remember { mutableStateOf<List<MemberStatus>>(emptyList()) }
    var muteRequests by remember { mutableStateOf<List<MuteRequest>>(emptyList()) }
    var muteApprovals by remember { mutableStateOf<List<MuteApproval>>(emptyList()) }
    var messages by remember { mutableStateOf<List<GroupMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var session by remember { mutableStateOf(loadSession(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Lobby-create panel state (shown when starting a new lobby).
    var showCreate by remember { mutableStateOf(false) }
    var newMode by remember { mutableStateOf(LobbyMode.CONCURRENT) }
    var newDurationMin by remember { mutableStateOf(25) }

    // My own break, published in-process by the service on this device.
    val myBreakId by LockInMonitor.breakId.collectAsState()

    DisposableEffect(group.id) {
        val regs = listOf(
            listenLobbies(group.id) { lobbies = it },
            listenGroupLiveStatus(group.id) { memberStatuses = it },
            listenMuteRequests(group.id) { muteRequests = it },
            listenMuteApprovals(group.id) { muteApprovals = it },
            listenGroupMessages(group.id) { messages = it },
        )
        onDispose { regs.forEach { it.remove() } }
    }

    // The session is owned by the foreground service; poll so Join/Stop taken
    // elsewhere (Home) and shared-round auto-stops are reflected here.
    LaunchedEffect(Unit) {
        while (true) {
            session = loadSession(context)
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // A lobby is dead once it has no members and either isn't brand-new (host
    // never showed / everyone left) or is a shared round past its end. Hidden
    // from the list, and closed once (best-effort) so dead rooms don't pile up.
    fun isDead(lobby: LockInLobby, graceMillis: Long): Boolean {
        val hasMembers = memberStatuses.any { it.lobbyId == lobby.id }
        if (hasMembers) return false
        val staleEmpty = now - lobby.startedAtMillis > graceMillis
        val sharedEnded = lobby.mode == LobbyMode.SHARED && lobby.endsAtMillis in 1..now
        return staleEmpty || sharedEnded
    }
    val visibleLobbies = lobbies.filter { !isDead(it, graceMillis = 12_000) }

    val closedLobbyIds = remember { mutableSetOf<String>() }
    LaunchedEffect(lobbies, memberStatuses, now) {
        lobbies.forEach { lobby ->
            if (lobby.id !in closedLobbyIds && isDead(lobby, graceMillis = 15_000)) {
                closedLobbyIds.add(lobby.id)
                closeLobby(group.id, lobby.id)
            }
        }
    }

    val threshold = group.muteApprovalCount.coerceAtLeast(1)
    fun approvalsFor(breakerUid: String, breakId: Long) = muteApprovals.count {
        it.breakerUid == breakerUid && it.breakId == breakId && it.approverUid != breakerUid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${group.memberUids.size} members · mute needs ${group.muteApprovalCount} approval(s)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---- Lobbies ----
        Text(
            text = "LOBBIES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (visibleLobbies.isEmpty()) {
            Text(
                text = "No active lobby. Start one to lock in together.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            visibleLobbies.forEach { lobby ->
                LobbyCard(
                    lobby = lobby,
                    members = memberStatuses.filter { it.lobbyId == lobby.id },
                    now = now,
                    myUid = myUid,
                    myName = myName,
                    myBreakId = myBreakId,
                    session = session,
                    threshold = threshold,
                    approvalsFor = ::approvalsFor,
                    muteRequests = muteRequests,
                    muteApprovals = muteApprovals,
                    onJoin = {
                        startLockInSession(context, group.id, group.name, lobby.id, lobby.endsAtMillis)
                        session = loadSession(context)
                    },
                    onRequestMute = { breakId ->
                        val uid = myUid ?: return@LobbyCard
                        requestMute(group.id, uid, myName, breakId, lobby.id)
                    },
                    onApproveMute = { breakerUid, breakId ->
                        val uid = myUid ?: return@LobbyCard
                        approveMute(group.id, breakerUid, uid, breakId, lobby.id)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (!session.isActive) {
            Spacer(modifier = Modifier.height(4.dp))
            if (!showCreate) {
                PressableButton(
                    onClick = { showCreate = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Rounded.Add,
                    text = "Start a lobby"
                )
            } else {
                LobbyCreatePanel(
                    mode = newMode,
                    onModeChange = { newMode = it },
                    durationMin = newDurationMin,
                    onDurationChange = { newDurationMin = it },
                    onCancel = { showCreate = false },
                    onOpen = {
                        val uid = myUid ?: return@LobbyCreatePanel
                        val duration = if (newMode == LobbyMode.SHARED) newDurationMin else 0
                        openLobby(group.id, uid, name = "", mode = newMode, durationMinutes = duration) { lobbyId, endsAtMillis ->
                            if (lobbyId != null) {
                                startLockInSession(context, group.id, group.name, lobbyId, endsAtMillis)
                                session = loadSession(context)
                                showCreate = false
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ---- Chat ----
        Text(
            text = "CHAT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "No messages yet — say hi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg = msg, isMine = msg.senderUid == myUid)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val uid = myUid ?: return@IconButton
                    if (draft.isBlank()) return@IconButton
                    sendGroupMessage(group.id, uid, myName, draft)
                    draft = ""
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LobbyCard(
    lobby: LockInLobby,
    members: List<MemberStatus>,
    now: Long,
    myUid: String?,
    myName: String,
    myBreakId: Long,
    session: LockInSession,
    threshold: Int,
    approvalsFor: (String, Long) -> Int,
    muteRequests: List<MuteRequest>,
    muteApprovals: List<MuteApproval>,
    onJoin: () -> Unit,
    onRequestMute: (Long) -> Unit,
    onApproveMute: (String, Long) -> Unit,
) {
    val imInThisLobby = session.isActive && session.lobbyId == lobby.id
    val isShared = lobby.mode == LobbyMode.SHARED
    val remainingMillis = if (isShared) (lobby.endsAtMillis - now).coerceAtLeast(0) else 0L
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isShared) "Shared round" else "Concurrent lobby",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isShared) {
                        Text(
                            text = if (remainingMillis > 0) "Ends in ${formatMmSs(remainingMillis)}" else "Round over",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!session.isActive) {
                    // A finished shared round can't be joined.
                    if (!(isShared && remainingMillis <= 0)) {
                        TextButton(onClick = onJoin) { Text("Join") }
                    }
                } else if (imInThisLobby) {
                    Text(
                        text = "You're in",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (members.isEmpty()) {
                Text(
                    text = "Waiting for someone to join…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            members.forEach { member ->
                val dotColor = if (member.state == ComplianceState.BREAK) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(member.displayName, style = MaterialTheme.typography.bodyMedium)

                    // A pending request, not a red dot, is what asks for your
                    // approval -- the breaker's alarm keeps sounding even once
                    // they're compliant again.
                    val request = muteRequests.find { it.breakerUid == member.uid }
                    if (member.uid != myUid && myUid != null && request != null) {
                        val approvals = approvalsFor(member.uid, request.breakId)
                        val alreadyApproved = muteApprovals.any {
                            it.breakerUid == member.uid && it.approverUid == myUid && it.breakId == request.breakId
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$approvals/$threshold",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!alreadyApproved && approvals < threshold) {
                            TextButton(onClick = { onApproveMute(member.uid, request.breakId) }) {
                                Text("Approve mute")
                            }
                        }
                    }
                }
            }

            // My own alarm controls, when I'm the breaker in this lobby. Keyed
            // off breakId (not live compliance) because in a group the alarm
            // outlives the break and I reach this screen by opening Lock-In,
            // which counts as compliant.
            if (imInThisLobby && myUid != null && myBreakId > 0L) {
                val myRequest = muteRequests.find { it.breakerUid == myUid && it.breakId == myBreakId }
                val approvals = approvalsFor(myUid, myBreakId)
                Spacer(modifier = Modifier.height(8.dp))
                if (myRequest == null) {
                    TextButton(onClick = { onRequestMute(myBreakId) }) {
                        Text("Ask the group to mute my alarm")
                    }
                } else {
                    Text(
                        text = if (approvals >= threshold) "Alarm muted by the group"
                        else "Waiting on the group — $approvals/$threshold approved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LobbyCreatePanel(
    mode: LobbyMode,
    onModeChange: (LobbyMode) -> Unit,
    durationMin: Int,
    onDurationChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "New lobby",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                ModePill("Concurrent", mode == LobbyMode.CONCURRENT) { onModeChange(LobbyMode.CONCURRENT) }
                Spacer(modifier = Modifier.width(8.dp))
                ModePill("Shared", mode == LobbyMode.SHARED) { onModeChange(LobbyMode.SHARED) }
            }
            Text(
                text = if (mode == LobbyMode.CONCURRENT) {
                    "Everyone locks in together, each on their own clock."
                } else {
                    "One synced round — everyone starts now and ends together."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (mode == LobbyMode.SHARED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Round length",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDurationChange((durationMin - 5).coerceAtLeast(5)) }) { Text("−") }
                    Text(
                        text = "$durationMin min",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { onDurationChange((durationMin + 5).coerceAtMost(180)) }) { Text("+") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                PressableButton(
                    onClick = onOpen,
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Rounded.Lock,
                    text = "Open & join"
                )
            }
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private fun formatMmSs(millis: Long): String {
    val totalSec = millis / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun MessageBubble(msg: GroupMessage, isMine: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text(
                text = msg.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
