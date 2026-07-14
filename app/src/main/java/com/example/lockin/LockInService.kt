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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "lockin_session"
private const val NOTIFICATION_ID = 1

class LockInService : Service() {

    private var isScreenOn = true
    private val screenStateReceiver = ScreenStateReceiver { isOn -> isScreenOn = isOn }
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                val foregroundApp = if (isScreenOn) currentForegroundApp(this@LockInService) else null
                val allowlist = loadAllowlist(this@LockInService)
                val status = evaluateCompliance(packageName, isScreenOn, foregroundApp, allowlist)
                LockInMonitor.update(status)

                if (status.state == ComplianceState.BREAK) {
                    startAlarm()
                } else {
                    stopAlarm()
                }

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
