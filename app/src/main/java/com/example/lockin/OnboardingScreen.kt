package com.example.lockin

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Permission priming: Usage Access is a buried, scary-sounding system toggle
// that most people have never touched, and asking for it cold -- with the OS's
// own wording -- loses users before they ever see the core loop. So we explain
// the ask in product terms first, and tell them exactly what they'll be looking
// at once the system screen opens, because that screen is an unlabelled list of
// every app on the device and it's very easy to bounce off.
private enum class OnboardStep { Welcome, Why, Grant, Notifications, Done }

@Composable
fun OnboardingScreen(
    usageAccessGranted: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current

    // A user who already finished onboarding but later revoked Usage Access
    // shouldn't have to sit through the welcome again -- drop them straight on
    // the step that's actually blocking them.
    var step by remember {
        mutableStateOf(
            if (isOnboardingComplete(context)) OnboardStep.Grant else OnboardStep.Welcome
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Granted or denied, the flow moves on: break alerts are the thing that
        // degrades, not the app. Declining is a real choice, not an error.
        step = OnboardStep.Done
    }

    fun advanceFrom(current: OnboardStep) {
        step = when (current) {
            OnboardStep.Welcome -> OnboardStep.Why
            OnboardStep.Why -> OnboardStep.Grant
            OnboardStep.Grant ->
                if (hasNotificationPermission(context)) OnboardStep.Done else OnboardStep.Notifications
            OnboardStep.Notifications -> OnboardStep.Done
            OnboardStep.Done -> OnboardStep.Done
        }
    }

    // The grant lands in the *system* settings app, not here -- MainActivity
    // re-checks on resume, so this is how the flow learns it can move on.
    LaunchedEffect(usageAccessGranted, step) {
        if (usageAccessGranted && step == OnboardStep.Grant) advanceFrom(OnboardStep.Grant)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val direction = if (forward) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { width -> direction * width / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) { width -> -direction * width / 4 } + fadeOut())
            },
            label = "onboardStep"
        ) { current ->
            when (current) {
                OnboardStep.Welcome -> OnboardCard(
                    icon = Icons.Rounded.Lock,
                    title = "Welcome to Lock-In",
                    body = "Start a focus session, solo or with friends. If you pick up your phone " +
                        "and open something you shouldn't, an alarm goes off — and your group " +
                        "finds out.\n\nNo honor system. Nothing to self-report.",
                    primaryLabel = "How it works",
                    onPrimary = { advanceFrom(current) }
                )

                OnboardStep.Why -> OnboardCard(
                    icon = Icons.Rounded.Settings,
                    title = "Why we need Usage Access",
                    body = "To tell your group you stayed focused, Lock-In has to actually know — " +
                        "so it checks which app is in the foreground while a session is running. " +
                        "That check needs a permission called Usage Access.\n\n" +
                        "It's how we keep each other honest instead of taking your word for it.",
                    footnote = "Lock-In only reads which app is open — never what's inside it. " +
                        "Your allowlist is visible to your friends; your session history isn't.",
                    primaryLabel = "Got it",
                    onPrimary = { advanceFrom(current) }
                )

                OnboardStep.Grant -> OnboardCard(
                    icon = Icons.Rounded.Settings,
                    title = "Two taps in Settings",
                    body = "The button below opens an Android settings screen. It's a long list of " +
                        "every app on your phone, and it won't mention Lock-In until you find us:",
                    steps = listOf(
                        "Find Lock-In in the list",
                        "Turn on Permit usage access",
                        "Come back here — we'll notice",
                    ),
                    primaryLabel = "Open Settings",
                    onPrimary = onOpenUsageAccessSettings
                )

                OnboardStep.Notifications -> OnboardCard(
                    icon = Icons.Rounded.Notifications,
                    title = "Hear it when they slip",
                    body = "Break alerts are the whole point of locking in together — when someone " +
                        "in your group breaks, you get pinged.\n\nWithout notifications, you'll " +
                        "still be watched, but you won't be watching.",
                    primaryLabel = "Turn on alerts",
                    onPrimary = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    secondaryLabel = "Not now",
                    onSecondary = { step = OnboardStep.Done }
                )

                OnboardStep.Done -> OnboardCard(
                    icon = Icons.Rounded.Check,
                    title = "You're set",
                    body = "Lock-In can see when you drift. Add a few friends, build your allowlist, " +
                        "and start a session.",
                    primaryLabel = "Start locking in",
                    onPrimary = {
                        setOnboardingComplete(context)
                        onFinish()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        StepDots(current = step)
    }
}

@Composable
private fun OnboardCard(
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    steps: List<String>? = null,
    footnote: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )

        if (steps != null) {
            Spacer(modifier = Modifier.height(20.dp))
            steps.forEachIndexed { index, line ->
                NumberedStep(number = index + 1, text = line)
                if (index != steps.lastIndex) Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (footnote != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        PressableButton(
            onClick = onPrimary,
            containerColor = MaterialTheme.colorScheme.primary,
            icon = icon,
            text = primaryLabel
        )

        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(
                    text = secondaryLabel,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun StepDots(current: OnboardStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OnboardStep.entries.forEachIndexed { index, entry ->
            val isCurrent = entry == current
            val width by animateDpAsState(
                targetValue = if (isCurrent) 22.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                label = "dotWidth"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
            )
            if (index != OnboardStep.entries.lastIndex) Spacer(modifier = Modifier.width(6.dp))
        }
    }
}
