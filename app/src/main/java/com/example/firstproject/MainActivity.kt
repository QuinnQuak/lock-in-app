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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var usageAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LockInTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showAllowlist by remember { mutableStateOf(false) }
                    if (showAllowlist) {
                        AllowlistScreen(onBack = { showAllowlist = false })
                    } else {
                        ForegroundAppScreen(
                            usageAccessGranted = usageAccessGranted,
                            onRequestAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            onOpenAllowlist = { showAllowlist = true }
                        )
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

@Composable
fun ForegroundAppScreen(usageAccessGranted: Boolean, onRequestAccess: () -> Unit, onOpenAllowlist: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!usageAccessGranted) {
            PermissionPrompt(onRequestAccess)
        } else {
            ForegroundAppStatus(onOpenAllowlist)
        }
    }
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
private fun ForegroundAppStatus(onOpenAllowlist: () -> Unit) {
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
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "CURRENTLY OPEN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Text(
                text = foregroundApp?.let { appLabelFor(context, it) } ?: "…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startLockInSession(context)
        session = loadSession(context)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (session.isActive) {
            Text(
                text = "LOCK-IN ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    stopLockInSession(context)
                    session = loadSession(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Stop Lock-In")
            }
        } else {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startLockInSession(context)
                        session = loadSession(context)
                    }
                }
            ) {
                Text("Start Lock-In")
            }
        }
    }
}
