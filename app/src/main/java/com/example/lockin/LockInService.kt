package com.example.lockin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "lockin_session"
private const val NOTIFICATION_ID = 1

// An unresponsive group shouldn't mean a real multi-minute alarm at odd
// hours -- the alarm auto-silences after this many ms regardless of group
// response. The BREAK state itself is unaffected; only the sound stops.
private const val MAX_ALARM_DURATION_MILLIS = 2 * 60 * 1000L

class LockInService : Service() {

    private var isScreenOn = true
    private val screenStateReceiver = ScreenStateReceiver { isOn -> isScreenOn = isOn }
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null

    // Captured at start rather than read in onDestroy: stopLockInSession()
    // clears the session store *before* stopping the service, and sign-out
    // clears the auth state right after — by teardown time both are gone.
    private var sessionStartMillis = 0L
    private var ownerUid: String? = null
    private var groupId: String? = null
    private var groupName: String? = null
    private var lobbyId: String? = null
    // SHARED-mode synced round end; 0 = open-ended (solo or CONCURRENT lobby).
    private var endsAtMillis = 0L
    private var displayName: String = "Someone"
    private var breakCount = 0
    private var wasInBreak = false
    private var alarmStartMillis = 0L
    private var alarmCapped = false
    private var alarmActive = false

    // Group mute-approval state. Written by Firestore listener callbacks on
    // the main thread, read by the polling loop on a background dispatcher.
    @Volatile private var currentBreakId = 0L
    @Volatile private var muteGranted = false
    @Volatile private var muteApprovals: List<MuteApproval> = emptyList()
    // Left at MAX_VALUE until the group doc loads: a failed threshold fetch
    // must never accidentally grant a mute.
    @Volatile private var muteThreshold = Int.MAX_VALUE
    private var muteApprovalListener: ListenerRegistration? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val session = loadSession(this)
        sessionStartMillis = session.startTimeMillis
        groupId = session.groupId
        groupName = session.groupName
        lobbyId = session.lobbyId
        endsAtMillis = session.endsAtMillis
        ownerUid = Firebase.auth.currentUser?.uid
        // Captured now, not in onDestroy: sign-out clears auth right after the
        // session stops, so the display name would be gone by teardown. Guard on
        // isNotBlank since an empty (not null) name slips past a plain `?:`.
        displayName = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"
        isScreenOn = getSystemService(PowerManager::class.java).isInteractive
        screenStateReceiver.register(this)
        watchMuteApprovals()
        startMonitoring()
    }

    // Only meaningful in a group session: pull the room's approval threshold
    // once, then track approvals aimed at this device's user.
    private fun watchMuteApprovals() {
        val gid = groupId ?: return
        val uid = ownerUid ?: return

        Firebase.firestore.collection("groups").document(gid).get()
            .addOnSuccessListener { doc ->
                muteThreshold = (doc.getLong("muteApprovalCount") ?: 1L).toInt().coerceAtLeast(1)
            }
        muteApprovalListener = listenMuteApprovals(gid) { approvals ->
            muteApprovals = approvals.filter { it.breakerUid == uid }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenStateReceiver.unregister(this)
        serviceScope.cancel()
        muteApprovalListener?.remove()
        stopAlarm()
        LockInMonitor.reset()

        val uid = ownerUid
        if (uid != null && sessionStartMillis > 0) {
            val endedAt = System.currentTimeMillis()
            val durationSeconds = (endedAt - sessionStartMillis) / 1000
            recordSessionToCloud(uid, sessionStartMillis, endedAt, breakCount)
            // Sparkles currency: 1 per whole minute locked in (solo or group).
            awardSparkles(uid, durationSeconds)
            // The friend-visible feed copy, stamped with the streak *including*
            // this just-finished session (which may not have hit Firestore yet).
            // The read runs on the Firestore SDK's own threads, not serviceScope
            // (already cancelled), and the app process outlives the service, so
            // the callback still fires; on failure it posts with streak 0 rather
            // than dropping the feed event.
            fetchStreakInfo(uid, sessionStartMillis, durationSeconds) { info ->
                recordActivityToCloud(
                    uid, displayName, sessionStartMillis, endedAt, breakCount,
                    groupId, groupName, info.streak
                )
            }
        }
        val gid = groupId
        if (uid != null && gid != null) {
            clearLiveStatus(gid, uid)
            clearMuteRequest(gid, uid)
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                // SHARED lobby: the synced round ends for everyone at endsAtMillis.
                // Tear down our own session (records history, clears liveStatus)
                // and mark the prefs inactive so Home/room reflect it within ~1s.
                if (endsAtMillis > 0 && System.currentTimeMillis() >= endsAtMillis) {
                    stopLockInSession(this@LockInService)
                    return@launch
                }

                val foregroundApp = if (isScreenOn) currentForegroundApp(this@LockInService) else null
                val allowlist = loadAllowlist(this@LockInService)
                val status = evaluateCompliance(packageName, isScreenOn, foregroundApp, allowlist)
                LockInMonitor.update(status)

                val gid = groupId
                val uid = ownerUid
                if (gid != null && uid != null) {
                    val displayName = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"
                    pushLiveStatus(gid, uid, displayName, status, lobbyId)
                }

                val inBreak = status.state == ComplianceState.BREAK
                if (inBreak && !wasInBreak) {
                    breakCount++
                    currentBreakId = System.currentTimeMillis()
                    alarmStartMillis = currentBreakId
                    alarmActive = true
                    alarmCapped = false
                    muteGranted = false
                    LockInMonitor.setBreakId(currentBreakId)
                }
                wasInBreak = inBreak

                // Solo: the user manages their own alarm, so returning to a
                // compliant app silences it. Group: the alarm deliberately
                // outlives the break -- only group approval or the cap stops
                // it. Without that, a breaker could silence the alarm just by
                // opening Lock-In to ask for a mute (this app's own package
                // counts as compliant), which would make approval pointless.
                if (alarmActive && gid == null && !inBreak) {
                    alarmActive = false
                }

                if (alarmActive) {
                    // Approvals count only for *this* break, and never the
                    // breaker's own -- same guard as the security rules.
                    if (!muteGranted) {
                        val approvals = muteApprovals.count {
                            it.breakId == currentBreakId && it.approverUid != uid
                        }
                        if (approvals >= muteThreshold) muteGranted = true
                    }
                    if (System.currentTimeMillis() - alarmStartMillis >= MAX_ALARM_DURATION_MILLIS) {
                        alarmCapped = true
                    }
                    // Muting silences the alarm only. The BREAK state, the
                    // group's red dot, and breakCount all stand -- the group
                    // can forgive the noise, not the record.
                    if (muteGranted || alarmCapped) alarmActive = false else startAlarm()
                }

                if (!alarmActive) {
                    stopAlarm()
                    // The break episode is only over once the alarm has
                    // stopped *and* the user is actually compliant again;
                    // until then the pending mute request stays live.
                    if (!inBreak && currentBreakId != 0L) {
                        currentBreakId = 0L
                        muteGranted = false
                        LockInMonitor.setBreakId(0L)
                        if (gid != null && uid != null) clearMuteRequest(gid, uid)
                    }
                }

                // Surface the settled alarm state so the Home header can show
                // "ALARM SOUNDING" even when the breaker is technically
                // compliant again (group sticky alarm).
                LockInMonitor.setAlarmSounding(alarmActive)

                delay(1000)
            }
        }
    }

    private fun startAlarm() {
        if (mediaPlayer != null) return
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@LockInService, alarmUri)
            isLooping = true
            prepare()
            start()
        }
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        vibrator().cancel()
    }

    private fun vibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lock-In session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while a lock-in session is active"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lock-In session active")
            .setContentText("Stay on your allowlisted apps to keep it going.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}
