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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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

    // Which tab is showing. Lobbies is the default -- the reason you open a room.
    var selectedTab by remember { mutableStateOf(GroupTab.LOBBIES) }

    // Member roster (uid -> display name), resolved once per membership change
    // from the public userSearch directory for the Members tab.
    var memberProfiles by remember { mutableStateOf<List<GroupMemberProfile>>(emptyList()) }
    LaunchedEffect(group.memberUids) {
        fetchGroupMemberProfiles(group.memberUids) { memberProfiles = it }
    }

    // My friends, for the add-member picker (only those not already in the group).
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }

    // App-wide focus presence per member (uid -> state, absent == idle/offline).
    // Drives the Members-roster dot -- app-wide, so a member locked in solo still
    // reads "Locked in" here, same source the friends list uses.
    var presence by remember { mutableStateOf<Map<String, UserPresence>>(emptyMap()) }

    // Members-tab management UI state: the member whose action sheet is open, and
    // whether the add-member picker is showing.
    var actionTarget by remember { mutableStateOf<GroupMemberProfile?>(null) }
    var showAddMember by remember { mutableStateOf(false) }

    // Group settings sheet (rename / threshold / leave / delete). Open to
    // everyone -- plain members still need Leave.
    var showSettings by remember { mutableStateOf(false) }

    DisposableEffect(group.id) {
        val regs = mutableListOf(
            listenLobbies(group.id) { lobbies = it },
            listenGroupLiveStatus(group.id) { memberStatuses = it },
            listenMuteRequests(group.id) { muteRequests = it },
            listenMuteApprovals(group.id) { muteApprovals = it },
            listenGroupMessages(group.id) { messages = it },
        )
        if (myUid != null) regs += listenFriends(myUid) { friends = it }
        onDispose { regs.forEach { it.remove() } }
    }

    // Presence is keyed on the roster, not group.id: MainActivity live-syncs the
    // open group, so members can be added/removed without a re-navigation -- the
    // whereIn presence query must re-subscribe when the uid set changes.
    DisposableEffect(group.memberUids) {
        val reg = listenPresence(group.memberUids) { presence = it }
        onDispose { reg.remove() }
    }

    // My management rights (mirrors firestore.rules): the owner controls
    // everything; an admin manages membership but can't touch roles/ownership.
    val iAmOwner = myUid != null && myUid == group.ownerUid
    val iAmAdmin = myUid != null && myUid in group.adminUids
    val canManage = iAmOwner || iAmAdmin

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

    val lockedInNow = memberStatuses.map { it.uid }.distinct().size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ---- Header: monogram + live summary (the name lives in the top bar) ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group.name.trim().firstOrNull()?.uppercase() ?: "#",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "${group.memberUids.size} members",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (lockedInNow > 0) "$lockedInNow locked in now" else "Nobody locked in",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lockedInNow > 0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Group settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Tabs: Lobbies · Chat · Members ----
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            GroupTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.label,
                            fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                // ---- Lobbies tab ----
                GroupTab.LOBBIES -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
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
                }

                // ---- Chat tab ----
                GroupTab.CHAT -> Column(modifier = Modifier.fillMaxSize()) {
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
                            shape = RoundedCornerShape(26.dp),
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

                // ---- Members tab ----
                GroupTab.MEMBERS -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (canManage) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAddMember = true }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Add member",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    if (memberProfiles.isEmpty()) {
                        item {
                            Text(
                                text = "Loading members…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(memberProfiles, key = { it.uid }) { profile ->
                        val targetIsOwner = profile.uid == group.ownerUid
                        val targetIsAdmin = profile.uid in group.adminUids
                        // Rows I can act on (owner: anyone but self/owner; admin:
                        // only plain members). Self-leave lives in the settings sheet.
                        val actionable = canManage && !targetIsOwner && profile.uid != myUid &&
                            (iAmOwner || !targetIsAdmin)
                        MemberRow(
                            profile = profile,
                            isOwner = targetIsOwner,
                            isAdmin = targetIsAdmin,
                            isMe = profile.uid == myUid,
                            presence = presence[profile.uid],
                            onClick = if (actionable) ({ actionTarget = profile }) else null
                        )
                    }
                }
            }
        }

        // ---- Members-tab management sheets ----
        actionTarget?.let { target ->
            MemberActionSheet(
                target = target,
                targetIsAdmin = target.uid in group.adminUids,
                iAmOwner = iAmOwner,
                onPromote = {
                    promoteAdmin(group.id, target.uid) {}
                    actionTarget = null
                },
                onDemote = {
                    demoteAdmin(group.id, target.uid) {}
                    actionTarget = null
                },
                onRemove = {
                    removeMember(group.id, target.uid) {}
                    actionTarget = null
                },
                onDismiss = { actionTarget = null }
            )
        }
        if (showAddMember) {
            AddMemberSheet(
                friends = friends.filter { it.uid !in group.memberUids },
                onAdd = { uids ->
                    if (uids.isNotEmpty()) addMembers(group.id, uids) {}
                    showAddMember = false
                },
                onDismiss = { showAddMember = false }
            )
        }
        if (showSettings) {
            GroupSettingsSheet(
                group = group,
                canManage = canManage,
                iAmOwner = iAmOwner,
                myUid = myUid,
                onDismiss = { showSettings = false }
            )
        }
    }
}

private enum class GroupTab(val label: String) {
    LOBBIES("Lobbies"),
    CHAT("Chat"),
    MEMBERS("Members"),
}

@Composable
private fun MemberRow(
    profile: GroupMemberProfile,
    isOwner: Boolean,
    isAdmin: Boolean,
    isMe: Boolean,
    presence: UserPresence?,
    onClick: (() -> Unit)?,
) {
    // App-wide focus dot: green locked in, red on break, muted grey idle/offline
    // (effectiveState() folds a stale stamp back to idle). Same source + colours
    // as the friends list.
    val dotColor = presenceDotColor(presence)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isMe) "${profile.displayName} (you)" else profile.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = presenceLabel(presence),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(10.dp))
        if (isOwner) {
            RoleBadge("Owner")
        } else if (isAdmin) {
            RoleBadge("Admin")
        }
    }
}

@Composable
private fun RoleBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberActionSheet(
    target: GroupMemberProfile,
    targetIsAdmin: Boolean,
    iAmOwner: Boolean,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (targetIsAdmin) {
                    Spacer(modifier = Modifier.width(10.dp))
                    RoleBadge("Admin")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Only the owner touches roles; admins can just remove plain members.
            if (iAmOwner) {
                if (targetIsAdmin) {
                    SheetAction("Demote to member", onDemote)
                } else {
                    SheetAction("Promote to admin", onPromote)
                }
            }
            SheetAction("Remove from group", onRemove, destructive = true)
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (destructive) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(
    friends: List<Friend>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                text = "Add member",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (friends.isEmpty()) {
                Text(
                    text = "All your friends are already in this group.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                friends.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (friend.uid in selected) selected - friend.uid
                                else selected + friend.uid
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = friend.uid in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + friend.uid else selected - friend.uid
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.weight(1f))
                PressableButton(
                    onClick = { onAdd(selected.toList()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Rounded.Add,
                    text = if (selected.isEmpty()) "Add" else "Add ${selected.size}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSettingsSheet(
    group: LockInGroup,
    canManage: Boolean,
    iAmOwner: Boolean,
    myUid: String?,
    onDismiss: () -> Unit,
) {
    // Local edit state, seeded from the group. Owner/admin edits write straight
    // through; MainActivity live-sync reflects them back into `group`.
    var nameDraft by remember { mutableStateOf(group.name) }
    var threshold by remember { mutableStateOf(group.muteApprovalCount.coerceAtLeast(1)) }
    // Approvals exclude the breaker, so the ceiling is members − 1.
    val maxThreshold = (group.memberUids.size - 1).coerceAtLeast(1)

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(
                text = "Group settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (canManage) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { if (nameDraft.isNotBlank()) renameGroup(group.id, nameDraft) {} },
                        enabled = nameDraft.isNotBlank() && nameDraft.trim() != group.name
                    ) { Text("Save") }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mute approvals",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Votes needed to silence a break alarm",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            threshold = (threshold - 1).coerceAtLeast(1)
                            setMuteThreshold(group.id, threshold) {}
                        },
                        enabled = threshold > 1
                    ) { Text("−") }
                    Text(
                        text = "$threshold",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            threshold = (threshold + 1).coerceAtMost(maxThreshold)
                            setMuteThreshold(group.id, threshold) {}
                        },
                        enabled = threshold < maxThreshold
                    ) { Text("+") }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Leave: any non-owner member. The owner must transfer or delete.
            if (iAmOwner) {
                SheetAction("Delete group", { showDeleteConfirm = true }, destructive = true)
            } else {
                SheetAction("Leave group", { showLeaveConfirm = true }, destructive = true)
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave ${group.name}?") },
            text = { Text("You'll lose access to this group's lobbies and chat. You can be re-added later.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    val uid = myUid ?: return@TextButton
                    leaveGroup(group.id, uid) {}
                    onDismiss()
                }) { Text("Leave", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${group.name}?") },
            text = { Text("This permanently removes the group, its lobbies, and all messages for everyone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deleteGroup(group.id) {}
                    onDismiss()
                }) { Text("Delete", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
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
        shape = RoundedCornerShape(22.dp),
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
        shape = RoundedCornerShape(22.dp),
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
            shape = RoundedCornerShape(22.dp)
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
