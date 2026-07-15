package com.example.lockin

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

// Ordered for the screen-transition slide: the five bottom-bar tabs run
// left-to-right in bar order, with Allowlist last since it slides in from the
// right of Profile (its parent).
private enum class Screen { Auth, Onboarding, Home, Feed, Friends, Groups, Profile, Allowlist, GroupDetail }

private val BottomTabs = listOf(Screen.Home, Screen.Feed, Screen.Friends, Screen.Groups, Screen.Profile)

private val Screen.tabLabel: String
    get() = when (this) {
        Screen.Home -> "Home"
        Screen.Feed -> "Feed"
        Screen.Friends -> "Friends"
        Screen.Groups -> "Groups"
        else -> "Profile"
    }

private val Screen.tabIcon: ImageVector
    get() = when (this) {
        Screen.Home -> Icons.Rounded.Home
        Screen.Feed -> Icons.Rounded.DynamicFeed
        Screen.Friends -> Icons.Rounded.People
        Screen.Groups -> Icons.Rounded.Groups
        else -> Icons.Rounded.Person
    }

class MainActivity : ComponentActivity() {
    private var usageAccessGranted by mutableStateOf(false)
    private var onboardingComplete by mutableStateOf(false)
    private var signedIn by mutableStateOf(false)
    // Re-checked on resume so returning from the notification settings screen
    // (via the Home nudge) makes the nudge vanish once alerts are on.
    private var notificationsGranted by mutableStateOf(false)
    // Device-local (ThemeStore, not Firestore) -- loaded once in onCreate below.
    private var appTheme by mutableStateOf(AppTheme.BUBBLEGUM)

    // Fires immediately on registration with the current state, then again on
    // every sign-in/sign-out — so `signedIn` needs no separate initialization.
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        signedIn = auth.currentUser != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Firebase.auth.addAuthStateListener(authStateListener)
        appTheme = loadAppTheme(this)
        setContent {
            LockInTheme(theme = appTheme) {
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

                // The selected bottom-bar tab (or a nested screen: Allowlist
                // under Profile, GroupDetail under Groups). Auth/Onboarding gate
                // in ahead of it below.
                var current by remember { mutableStateOf(Screen.Home) }

                // The group whose room (GroupDetail) is open, if any.
                var selectedGroup by remember { mutableStateOf<LockInGroup?>(null) }

                // Dismissing the Home notification nudge hides it for this
                // session only; hoisted here (not inside HomeScreen) so a tab
                // switch doesn't bring it back, while an app relaunch does.
                var notificationNudgeDismissed by remember { mutableStateOf(false) }
                val currentScreen = when {
                    !signedIn -> Screen.Auth
                    // Usage Access is a hard requirement, so revoking it later
                    // drops the user back into onboarding (at the grant step).
                    !usageAccessGranted || !onboardingComplete -> Screen.Onboarding
                    else -> current
                }
                val showBottomBar = currentScreen in BottomTabs

                // System back mirrors the nav hierarchy the UI already implies:
                // the nested Allowlist returns to its parent Profile (same as the
                // top-bar arrow); any other non-Home tab returns to Home, the
                // start destination. Home (and the Auth/Onboarding gates) fall
                // through to the system default, i.e. leave the app.
                BackHandler(enabled = currentScreen == Screen.Allowlist) {
                    current = Screen.Profile
                }
                BackHandler(enabled = currentScreen == Screen.GroupDetail) {
                    current = Screen.Groups
                }
                BackHandler(enabled = currentScreen in BottomTabs && currentScreen != Screen.Home) {
                    current = Screen.Home
                }

                Scaffold(
                    topBar = {
                        LockInTopBar(
                            screen = currentScreen,
                            // GroupDetail shows the group's name; other screens
                            // derive their own title from the screen.
                            groupName = selectedGroup?.name,
                            // The two nested screens return to their parent tab:
                            // Allowlist -> Profile, GroupDetail -> Groups.
                            onBack = {
                                current = if (currentScreen == Screen.GroupDetail) Screen.Groups
                                else Screen.Profile
                            }
                        )
                    },
                    bottomBar = {
                        if (showBottomBar) {
                            LockInBottomBar(current = currentScreen, onSelect = { current = it })
                        }
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
                                    Screen.Onboarding -> OnboardingScreen(
                                        usageAccessGranted = usageAccessGranted,
                                        onOpenUsageAccessSettings = {
                                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        },
                                        onFinish = { onboardingComplete = true }
                                    )
                                    Screen.Home -> HomeScreen(
                                        // Only relevant on API 33+, where notifications
                                        // are a runtime grant that can be declined.
                                        showNotificationNudge = notificationPermissionNeeded() &&
                                            !notificationsGranted && !notificationNudgeDismissed,
                                        onOpenNotificationSettings = {
                                            startActivity(appNotificationSettingsIntent(this@MainActivity))
                                        },
                                        onDismissNudge = { notificationNudgeDismissed = true },
                                        // Deep-link from the Home "group lock-in"
                                        // hint into that group's room.
                                        onOpenGroup = { gid ->
                                            myGroups.find { it.id == gid }?.let {
                                                selectedGroup = it
                                                current = Screen.GroupDetail
                                            }
                                        }
                                    )
                                    Screen.Feed -> FeedScreen()
                                    Screen.Friends -> FriendsScreen()
                                    Screen.Groups -> GroupsScreen(
                                        onOpenGroup = { group ->
                                            selectedGroup = group
                                            current = Screen.GroupDetail
                                        }
                                    )
                                    Screen.GroupDetail -> selectedGroup?.let { GroupDetailScreen(group = it) }
                                    Screen.Profile -> ProfileScreen(
                                        currentTheme = appTheme,
                                        onThemeChange = { theme ->
                                            appTheme = theme
                                            saveAppTheme(this@MainActivity, theme)
                                        },
                                        onOpenAllowlist = { current = Screen.Allowlist },
                                        onSignOut = {
                                            // An active session can't outlive its owner: stop
                                            // monitoring before the account goes away.
                                            if (loadSession(this@MainActivity).isActive) {
                                                stopLockInSession(this@MainActivity)
                                            }
                                            // Reset so the next sign-in lands on Home, not
                                            // the Profile tab we signed out from.
                                            current = Screen.Home
                                            Firebase.auth.signOut()
                                        }
                                    )
                                    Screen.Allowlist -> AllowlistScreen()
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
        // The Usage Access grant happens in the system settings app, so resume
        // is the only place we learn about it.
        usageAccessGranted = hasUsageAccessPermission(this)
        onboardingComplete = isOnboardingComplete(this)
        notificationsGranted = hasNotificationPermission(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Firebase.auth.removeAuthStateListener(authStateListener)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockInTopBar(screen: Screen, groupName: String?, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = when (screen) {
                    Screen.Allowlist -> "Allowlist"
                    Screen.Friends -> "Friends"
                    Screen.Groups -> "Groups"
                    Screen.GroupDetail -> groupName ?: "Group"
                    Screen.Feed -> "Feed"
                    Screen.Profile -> "Profile"
                    else -> "Lock-In"
                },
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            // Tabs are reachable via the bottom bar; only the nested Allowlist
            // and GroupDetail screens need a back affordance.
            if (screen == Screen.Allowlist || screen == Screen.GroupDetail) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun LockInBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.tabIcon, contentDescription = tab.tabLabel) },
                label = { Text(tab.tabLabel) },
                colors = NavigationBarItemDefaults.colors(
                    // Tonal amber pill on the active tab; muted neutrals otherwise.
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun HomeScreen(
    showNotificationNudge: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onDismissNudge: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val context = LocalContext.current
    var foregroundApp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            foregroundApp = currentForegroundApp(context)
            delay(1000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showNotificationNudge) {
            NotificationNudge(onEnable = onOpenNotificationSettings, onDismiss = onDismissNudge)
            Spacer(modifier = Modifier.height(24.dp))
        }
        SessionControls(onOpenGroup = onOpenGroup)
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "CURRENTLY OPEN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(30.dp))
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
                                .clip(RoundedCornerShape(14.dp))
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
    }
}

// Shown on Home only when notifications were declined (during onboarding or
// later) on API 33+. The whole point of a group lock-in is hearing when someone
// slips, so a silent decline is worth one honest, dismissible reminder. Tapping
// "Turn on alerts" deep-links to system settings rather than re-firing the
// runtime dialog, which Android suppresses after a denial.
@Composable
private fun NotificationNudge(onEnable: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(26.dp))
            .padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notifications are off",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "You won't hear when your group breaks. Turn alerts on to stay in the loop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            TextButton(
                onClick = onEnable,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = "Turn on alerts",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SessionControls(onOpenGroup: (String) -> Unit) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(loadSession(context)) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(session.isActive, session.startTimeMillis) {
        while (session.isActive) {
            elapsedSeconds = (System.currentTimeMillis() - session.startTimeMillis) / 1000
            delay(1000)
            // Reflect an auto-stop (a shared round ended) or a stop taken from
            // the group room, so Home doesn't show a phantom running session.
            session = loadSession(context)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (session.isActive) {
            val complianceStatus by LockInMonitor.complianceState.collectAsState()
            val isBreak = complianceStatus.state == ComplianceState.BREAK
            // The alarm can outlive the break in a group session: a breaker who
            // returns to a compliant app is COMPLIANT again while the alarm still
            // blares. Show that alert state instead of a misleading green header.
            val isAlarmSounding by LockInMonitor.alarmSounding.collectAsState()
            val isAlert = isBreak || isAlarmSounding
            val statusColor = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

            Text(
                text = when {
                    isBreak -> "BREAK DETECTED"
                    isAlarmSounding -> "ALARM SOUNDING"
                    else -> "LOCK-IN ACTIVE"
                },
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

            // Group lock-ins now live in the group room. From Home you see only
            // your own status + Stop, plus a shortcut into the room to see the
            // group and manage a mute.
            val gid = session.groupId
            if (gid != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Group lock-in" + (session.groupName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onOpenGroup(gid) }) {
                    Text("Open group room")
                }
            }
        } else {
            // Home starts a solo lock-in only; group lock-ins are joined from
            // the group room. Notifications were primed during onboarding, so
            // starting no longer detours through a permission dialog.
            PressableButton(
                onClick = {
                    startLockInSession(context)
                    session = loadSession(context)
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
        shape = RoundedCornerShape(26.dp),
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
