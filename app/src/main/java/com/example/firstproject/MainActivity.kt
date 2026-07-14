package com.example.firstproject

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var usageAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ForegroundAppScreen(
                        usageAccessGranted = usageAccessGranted,
                        onRequestAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )
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
fun ForegroundAppScreen(usageAccessGranted: Boolean, onRequestAccess: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!usageAccessGranted) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Lock-In needs Usage Access to see which app is in the foreground.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRequestAccess) {
                    Text("Grant Usage Access")
                }
            }
        } else {
            val context = LocalContext.current
            var foregroundApp by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                while (true) {
                    foregroundApp = currentForegroundApp(context)
                    delay(1000)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Current foreground app:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = foregroundApp ?: "(unknown)", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
