package com.example.lockin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

private enum class Screen { Auth, Permission, Home, Allowlist, Friends, Groups }

class MainActivity : ComponentActivity() {
    private var usageAccessGranted by mutableStateOf(false)
    private var signedIn by mutableStateOf(false)

    // Fires immediately on registration with the current state, then again on
    // every sign-in/sign-out — so `signedIn` needs no separate initialization.
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        signedIn = auth.currentUser != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Firebase.auth.addAuthStateListener(authStateListener)
        setContent {
            LockInTheme {
                // While signed in, mirror the Firestore allowlist into the
                // local SharedPreferences cache; torn down on sign-out.
                DisposableEffect(signedIn) {
                    val registration = if (signedIn) {
                        Firebase.auth.currentUser?.uid?.let { startAllowlistSync(this@MainActivity, it) }
                    } else null
                    onDispose { registration?.remove() }
                }

                // Mock push: watches groupmates' live status for breaks and
                // raises a local notification. Only fires while this process
                // is alive -- see MockBreakNotifier.kt for why.
                var myGroups by remember { mutableStateOf<List<LockInGroup>>(emptyList()) }
                DisposableEffect(signedIn) {
                    val reg = if (signedIn) {
                        Firebase.auth.currentUser?.uid?.let { listenMyGroups(it) { g -> myGroups = g } }
                    } else null
                    onDispose { reg?.remove() }
                }
                DisposableEffect(myGroups) {
                    val uid = Firebase.auth.currentUser?.uid
                    val regs = if (uid != null && myGroups.isNotEmpty()) {
                        ensureBreakAlertChannel(this@MainActivity)
                        watchGroupsForBreaks(this@MainActivity, uid, myGroups)
                    } else emptyList()
                    onDispose { regs.forEach { it.remove() } }
                }

                var subScreen by remember { mutableStateOf<Screen?>(null) }
                val currentScreen = when {
                    !signedIn -> Screen.Auth
                    !usageAccessGranted -> Screen.Permission
                    subScreen != null -> subScreen!!
                    else -> Screen.Home
                }

                Scaffold(
                    topBar = {
                        LockInTopBar(
                            screen = currentScreen,
                            onBack = { subScreen = null }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { contentPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val forward = targetState.ordinal >= initialState.ordinal
                                val direction = if (forward) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                ) { width -> direction * width / 4 } + fadeIn()) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                    ) { width -> -direction * width / 4 } + fadeOut())
                            },
                            label = "screenTransition"
                        ) { screen ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when (screen) {
                                    Screen.Auth -> AuthScreen()
                                    Screen.Permission -> PermissionPrompt(
                                        onRequestAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                    )
                                    Screen.Home -> HomeScreen(
                                        onOpenAllowlist = { subScreen = Screen.Allowlist },
                                        onOpenFriends = { subScreen = Screen.Friends },
                                        onOpenGroups = { subScreen = Screen.Groups },
                                        onSignOut = {
                                            // An active session can't outlive its owner: stop
                                            // monitoring before the account goes away.
                                            if (loadSession(this@MainActivity).isActive) {
                                                stopLockInSession(this@MainActivity)
                                            }
                                            Firebase.auth.signOut()
                                        }
                                    )
                                    Screen.Allowlist -> AllowlistScreen()
                                    Screen.Friends -> FriendsScreen()
                                    Screen.Groups -> GroupsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccessGranted = hasUsageAccessPermission(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Firebase.auth.removeAuthStateListener(authStateListener)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockInTopBar(screen: Screen, onBack: () -> Unit) {
    val isSubScreen = screen == Screen.Allowlist || screen == Screen.Friends || screen == Screen.Groups
    TopAppBar(
        title = {
            Text(
                text = when (screen) {
                    Screen.Allowlist -> "Allowlist"
                    Screen.Friends -> "Friends"
                    Screen.Groups -> "Groups"
                    else -> "Lock-In"
                },
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (isSubScreen) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun PermissionPrompt(onRequestAccess: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(32.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Let's keep you honest",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Lock-In needs Usage Access to see which app is open, so it can tell your group if you break a session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequestAccess,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Grant Usage Access")
        }
    }
}

@Composable
private fun HomeScreen(onOpenAllowlist: () -> Unit, onOpenFriends: () -> Unit, onOpenGroups: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    var foregroundApp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            foregroundApp = currentForegroundApp(context)
            delay(1000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SessionControls()
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "CURRENTLY OPEN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pkg = foregroundApp
                if (pkg != null) {
                    val icon = remember(pkg) { appIconFor(context, pkg) }
                    if (icon != null) {
                        Image(
                            bitmap = remember(pkg) { icon.toBitmap().asImageBitmap() },
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
                Text(
                    text = pkg?.let { appLabelFor(context, it) } ?: "…",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onOpenAllowlist) {
            Text("Manage Allowlist")
        }
        TextButton(onClick = onOpenFriends) {
            Text("Friends")
        }
        TextButton(onClick = onOpenGroups) {
            Text("Groups")
        }
        TextButton(onClick = onSignOut) {
            Text("Sign Out", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionControls() {
    val context = LocalContext.current
    var session by remember { mutableStateOf(loadSession(context)) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var groups by remember { mutableStateOf<List<LockInGroup>>(emptyList()) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var memberStatuses by remember { mutableStateOf<List<MemberStatus>>(emptyList()) }
    var muteRequests by remember { mutableStateOf<List<MuteRequest>>(emptyList()) }
    var muteApprovals by remember { mutableStateOf<List<MuteApproval>>(emptyList()) }

    val myUid = Firebase.auth.currentUser?.uid
    DisposableEffect(myUid) {
        val reg = myUid?.let { listenMyGroups(it) { g -> groups = g } }
        onDispose { reg?.remove() }
    }
    DisposableEffect(session.groupId) {
        val gid = session.groupId
        val regs = if (gid != null) {
            listOf(
                listenGroupLiveStatus(gid) { memberStatuses = it },
                listenMuteRequests(gid) { muteRequests = it },
                listenMuteApprovals(gid) { muteApprovals = it },
            )
        } else emptyList()
        onDispose { regs.forEach { it.remove() } }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startLockInSession(context, selectedGroupId)
        session = loadSession(context)
    }

    LaunchedEffect(session.isActive, session.startTimeMillis) {
        while (session.isActive) {
            elapsedSeconds = (System.currentTimeMillis() - session.startTimeMillis) / 1000
            delay(1000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (session.isActive) {
            val complianceStatus by LockInMonitor.complianceState.collectAsState()
            val isBreak = complianceStatus.state == ComplianceState.BREAK
            val statusColor = if (isBreak) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

            Text(
                text = if (isBreak) "BREAK DETECTED" else "LOCK-IN ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatElapsed(elapsedSeconds),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))
            PressableButton(
                onClick = {
                    stopLockInSession(context)
                    session = loadSession(context)
                },
                containerColor = statusColor,
                icon = Icons.Rounded.Close,
                text = "Stop Lock-In"
            )

            val groupId = session.groupId
            if (groupId != null) {
                // How many *other* members must approve, per the room's setting.
                val threshold = (groups.find { it.id == groupId }?.muteApprovalCount ?: 1)
                    .coerceAtLeast(1)
                // An approval only counts for the break it was cast in, and a
                // breaker's own approval never counts -- mirrors the service
                // and the security rules.
                fun approvalsFor(breakerUid: String, breakId: Long) = muteApprovals.count {
                    it.breakerUid == breakerUid && it.breakId == breakId && it.approverUid != breakerUid
                }

                // Keyed off breakId, not live compliance: in a group the alarm
                // outlives the break, and the breaker reaches this screen by
                // opening Lock-In (which counts as compliant).
                val myBreakId by LockInMonitor.breakId.collectAsState()
                if (myUid != null && myBreakId > 0L) {
                    val myRequest = muteRequests.find { it.breakerUid == myUid && it.breakId == myBreakId }
                    val approvals = approvalsFor(myUid, myBreakId)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (myRequest == null) {
                        TextButton(
                            onClick = {
                                requestMute(
                                    groupId,
                                    myUid,
                                    Firebase.auth.currentUser?.displayName ?: "Someone",
                                    myBreakId
                                )
                            }
                        ) {
                            Text("Ask the group to mute my alarm")
                        }
                    } else {
                        Text(
                            text = if (approvals >= threshold) {
                                "Alarm muted by the group"
                            } else {
                                "Waiting on the group — $approvals/$threshold approved"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (memberStatuses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "GROUP STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    memberStatuses.forEach { member ->
                        val dotColor = if (member.state == ComplianceState.BREAK) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(dotColor, RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(member.displayName, style = MaterialTheme.typography.bodyMedium)

                            // A pending request, not a red dot, is what asks for
                            // your approval -- the breaker's alarm keeps
                            // sounding even once they're compliant again.
                            val request = muteRequests.find { it.breakerUid == member.uid }
                            if (member.uid != myUid && myUid != null && request != null) {
                                val approvals = approvalsFor(member.uid, request.breakId)
                                val alreadyApproved = muteApprovals.any {
                                    it.breakerUid == member.uid &&
                                        it.approverUid == myUid &&
                                        it.breakId == request.breakId
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$approvals/$threshold",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!alreadyApproved && approvals < threshold) {
                                    TextButton(
                                        onClick = {
                                            approveMute(groupId, member.uid, myUid, request.breakId)
                                        }
                                    ) {
                                        Text("Approve mute")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (groups.isNotEmpty()) {
                Text(
                    text = "Session: ${groups.find { it.id == selectedGroupId }?.name ?: "Solo"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val ids = listOf<String?>(null) + groups.map { it.id }
                        val nextIndex = (ids.indexOf(selectedGroupId) + 1) % ids.size
                        selectedGroupId = ids[nextIndex]
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            PressableButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startLockInSession(context, selectedGroupId)
                        session = loadSession(context)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Lock,
                text = "Start Lock-In"
            )
        }
    }
}

@Composable
internal fun PressableButton(
    onClick: () -> Unit,
    containerColor: Color,
    icon: ImageVector,
    text: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = Modifier.scale(scale)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
