package com.serranoie.app.pomemo.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Plays the alarm sound while [Ringer.ringing] is set. Needed because Android 15+ rewrites a
 * notification channel's USAGE_ALARM to USAGE_NOTIFICATION, which silent mode mutes.
 */
@AndroidEntryPoint
class RingService : Service() {
    @Inject lateinit var ringer: Ringer

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminder = ringer.ringing.value ?: run { stopSelf(); return START_NOT_STICKY }
        ServiceCompat.startForeground(
            this, reminder.id.toInt(), ringer.notification(reminder),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (player == null) {
            player = startPlayer()
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        }
        handler.removeCallbacksAndMessages(null)
        // ponytail: fixed ring timeout, move to settings together with snooze length
        handler.postDelayed({ ringer.stop(reminder.id) }, RING_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun startPlayer(): MediaPlayer? = try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setDataSource(this@RingService, uri)
            isLooping = true
            prepare()
            start()
        }
    } catch (e: Exception) {
        null // no ringtone available: vibration + full-screen UI still work
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        vibrator.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val RING_TIMEOUT_MS = 10 * 60_000L
    }
}
