package com.serranoie.app.pomemo.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import com.serranoie.app.pomemo.data.Reminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Plays the alarm sound while [Ringer.ringing] is set. Needed because Android 15+ rewrites a
 * notification channel's USAGE_ALARM to USAGE_NOTIFICATION, which silent mode mutes.
 * Follows [Ringer.ringing]: a queued alarm takes over the running sound, none left stops the service.
 */
@AndroidEntryPoint
class RingService : Service() {
    @Inject lateinit var ringer: Ringer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var shown: Long? = null
    private var timeout: Job? = null
    private var lastStartId = 0
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private val torch by lazy { TorchBlinker(this) }
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch { ringer.ringing.collect(::show) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        show(ringer.ringing.value)
        return START_NOT_STICKY
    }

    private fun show(reminder: Reminder?) {
        if (reminder == null) {
            timeout?.cancel()
            torch.stop()
            shown = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Only the latest start: an alarm that fired in the meantime keeps the service alive.
            stopSelf(lastStartId)
            return
        }
        ServiceCompat.startForeground(
            this, reminder.id.toInt(), ringer.notification(reminder),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (reminder.id == shown) return
        // The previous alarm's notification is no longer the foreground one, so it can be removed now.
        shown?.let { nm.cancel(it.toInt()) }
        shown = reminder.id
        if (player == null) {
            player = startPlayer()
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        }
        // Whatever rings (reminder, end of period, auto-start countdown), the camera flash blinks too.
        torch.start(scope)
        timeout?.cancel()
        timeout = scope.launch {
            delay(ringer.loadSettings().ringMin * 60_000L)
            ringer.timeout(reminder.id)
        }
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
        torch.stop()
        scope.cancel()
        player?.release()
        vibrator.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
