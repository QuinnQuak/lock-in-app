package com.example.lockin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.ListenerRegistration

private const val CHANNEL_ID = "group_break_alerts"
private var nextNotificationId = 2000

/**
 * Mocks a push notification: no server/Cloud Function is involved (that
 * needs a paid Blaze plan), so this only fires while this device's app
 * process is alive to run the Firestore listener -- unlike real FCM,
 * which can wake a fully force-closed app. Documented scope choice, same
 * spirit as the project's other accepted PoC limitations.
 */
fun ensureBreakAlertChannel(context: Context) {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Group break alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when a group member breaks their lock-in"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

private fun notifyBreak(context: Context, memberName: String, groupName: String) {
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("$memberName broke their lock-in")
        .setContentText("in $groupName")
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(nextNotificationId++, notification)
}

/** Watches every group's live status for other members transitioning into BREAK. */
fun watchGroupsForBreaks(context: Context, myUid: String, groups: List<LockInGroup>): List<ListenerRegistration> {
    return groups.map { group ->
        val previousStates = mutableMapOf<String, ComplianceState>()
        listenGroupLiveStatus(group.id) { statuses ->
            statuses.forEach { member ->
                if (member.uid != myUid) {
                    val was = previousStates[member.uid]
                    if (member.state == ComplianceState.BREAK && was != ComplianceState.BREAK) {
                        notifyBreak(context, member.displayName, group.name)
                    }
                }
                previousStates[member.uid] = member.state
            }
        }
    }
}
