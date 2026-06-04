package com.vsd.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vsd.app.R
import com.vsd.app.audio.AudioCapture
import com.vsd.app.audio.VADProcessor
import kotlinx.coroutines.*

/**
 * Android Foreground Service that captures ambient audio and runs VAD in a loop.
 *
 * Runs with a persistent notification so the system does not kill it.
 * All audio processing is on-device; no data leaves the phone.
 */
class AudioForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var audioCapture: AudioCapture
    private lateinit var vad: VADProcessor

    /** Callback invoked when a new speech segment is detected */
    var onSpeechDetected: ((ShortArray) -> Unit)? = null

    /** Callback invoked when threat level changes (0 = no threat) */
    var onThreatLevelChanged: ((Int) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        audioCapture = AudioCapture()
        vad = VADProcessor()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { captureLoop() }
        return START_STICKY
    }

    private suspend fun captureLoop() {
        if (!audioCapture.startCapture()) {
            stopSelf()
            return
        }

        while (isActive) {
            val buffer = audioCapture.readBuffer()
            if (buffer != null) {
                val speaking = vad.analyzeFrame(buffer)

                if (vad.isSpeechStart()) {
                    // Speech just started — caller can begin collecting segment
                    onSpeechDetected?.invoke(buffer)
                } else if (speaking) {
                    // Ongoing speech — accumulate for STT
                    onSpeechDetected?.invoke(buffer)
                }
            }
            delay(20) // ~50Hz polling; adjust for battery vs latency
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        audioCapture.stopCapture()
        vad.reset()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.listening_text))
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vsd_audio_service"
    }
}
