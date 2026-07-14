package com.example.firstproject

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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

private enum class Screen { Permission, Home, Allowlist }

class MainActivity : ComponentActivity() {
    private var usageAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LockInTheme {
                var showAllowlist by remember { mutableStateOf(false) }
                val currentScreen = when {
                    !usageAccessGranted -> Screen.Permission
                    showAllowlist -> Screen.Allowlist
                    else -> Screen.Home
                }

                Scaffold(
                    topBar = {
                        LockInTopBar(
                            screen = currentScreen,
                            onBack = { showAllowlist = false }
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
                                    Screen.Permission -> PermissionPrompt(
                                        onRequestAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                                    )
                                    Screen.Home -> HomeScreen(onOpenAllowlist = { showAllowlist = true })
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
        usageAccessGranted = hasUsageAccessPermission(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockInTopBar(screen: Screen, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = if (screen == Screen.Allowlist) "Allowlist" else "Lock-In",
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (screen == Screen.Allowlist) {
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
private fun HomeScreen(onOpenAllowlist: () -> Unit) {
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
    }
}

@Composable
private fun SessionControls() {
    val context = LocalContext.current
    var session by remember { mutableStateOf(loadSession(context)) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startLockInSession(context)
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
            Text(
                text = "LOCK-IN ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
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
                containerColor = MaterialTheme.colorScheme.secondary,
                icon = Icons.Rounded.Close,
                text = "Stop Lock-In"
            )
        } else {
            PressableButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startLockInSession(context)
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
private fun PressableButton(
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
