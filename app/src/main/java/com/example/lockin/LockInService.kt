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
    private var breakCount = 0
    private var wasInBreak = false
    private var alarmStartMillis = 0L
    private var alarmCapped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val session = loadSession(this)
        sessionStartMillis = session.startTimeMillis
        groupId = session.groupId
        ownerUid = Firebase.auth.currentUser?.uid
        isScreenOn = getSystemService(PowerManager::class.java).isInteractive
        screenStateReceiver.register(this)
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenStateReceiver.unregister(this)
        serviceScope.cancel()
        stopAlarm()
        LockInMonitor.reset()

        val uid = ownerUid
        if (uid != null && sessionStartMillis > 0) {
            recordSessionToCloud(uid, sessionStartMillis, System.currentTimeMillis(), breakCount)
        }
        val gid = groupId
        if (uid != null && gid != null) {
            clearLiveStatus(gid, uid)
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                val foregroundApp = if (isScreenOn) currentForegroundApp(this@LockInService) else null
                val allowlist = loadAllowlist(this@LockInService)
                val status = evaluateCompliance(packageName, isScreenOn, foregroundApp, allowlist)
                LockInMonitor.update(status)

                val gid = groupId
                val uid = ownerUid
                if (gid != null && uid != null) {
                    val displayName = Firebase.auth.currentUser?.displayName ?: "Someone"
                    pushLiveStatus(gid, uid, displayName, status)
                }

                if (status.state == ComplianceState.BREAK) {
                    if (!wasInBreak) {
                        breakCount++
                        alarmCapped = false
                    }
                    wasInBreak = true
                    val elapsedSinceAlarmStart = if (alarmStartMillis > 0) {
                        System.currentTimeMillis() - alarmStartMillis
                    } else 0
                    if (!alarmCapped && elapsedSinceAlarmStart >= MAX_ALARM_DURATION_MILLIS) {
                        alarmCapped = true
                        stopAlarm()
                    } else if (!alarmCapped) {
                        startAlarm()
                    }
                } else {
                    wasInBreak = false
                    alarmCapped = false
                    stopAlarm()
                }

                delay(1000)
            }
        }
    }

    private fun startAlarm() {
        if (mediaPlayer != null) return
        alarmStartMillis = System.currentTimeMillis()
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
