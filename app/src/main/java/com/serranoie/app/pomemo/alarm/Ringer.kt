package com.serranoie.app.pomemo.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.timer.PomodoroTimer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "what is ringing". Plain reminders are just a notification; ringing
 * ones start [RingService] (sound) and open [AlarmActivity] (UI), both of which follow [ringing].
 */
@Singleton
class Ringer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val _ringing = MutableStateFlow<Reminder?>(null)
    val ringing: StateFlow<Reminder?> = _ringing.asStateFlow()

    /** "Continue to Short break": set by the timer when a phase ends, shown by the notification and AlarmActivity. */
    var continueLabel: String = ""
        private set

    fun ringTimer(reminder: Reminder, continueLabel: String) {
        this.continueLabel = continueLabel
        ring(reminder)
    }

    fun ring(reminder: Reminder) {
        createChannels()
        if (!reminder.ring) return nm.notify(reminder.id.toInt(), notification(reminder))
        // ponytail: a second alarm replaces the one ringing; queue them if overlaps become common
        _ringing.value?.let { if (it.id != reminder.id) nm.cancel(it.id.toInt()) }
        _ringing.value = reminder
        ContextCompat.startForegroundService(context, Intent(context, RingService::class.java))
    }

    fun stop(id: Long) {
        nm.cancel(id.toInt())
        if (_ringing.value?.id == id) {
            _ringing.value = null
            context.stopService(Intent(context, RingService::class.java))
        }
    }

    fun snooze(id: Long) {
        stop(id)
        // ponytail: hardcoded snooze length until settings exist
        scheduler.snooze(id, SNOOZE_MINUTES)
    }

    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()

    fun notification(reminder: Reminder): Notification {
        val id = reminder.id
        val builder = NotificationCompat.Builder(context, if (reminder.ring) CH_RINGING else CH_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(reminder.note.ifBlank { null })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(activityIntent(id))
            .setDeleteIntent(receiverIntent(AlarmReceiver.ACTION_DISMISS, id))
        if (id == PomodoroTimer.RING_ID) {
            // Both stop the ring: starting the next phase or resetting the timer calls ringer.stop.
            builder.addAction(0, continueLabel, receiverIntent(AlarmReceiver.ACTION_TIMER_START, id))
            builder.addAction(0, context.getString(R.string.end_period), receiverIntent(AlarmReceiver.ACTION_TIMER_RESET, id))
        } else {
            builder.addAction(0, context.getString(R.string.snooze), receiverIntent(AlarmReceiver.ACTION_SNOOZE, id))
            builder.addAction(0, context.getString(R.string.dismiss), receiverIntent(AlarmReceiver.ACTION_DISMISS, id))
        }
        if (reminder.ring) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(activityIntent(id), true)
        } else {
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true)
        }
        return builder.build()
    }

    private fun createChannels() {
        // Silent: RingService plays the sound with USAGE_ALARM and vibrates.
        nm.createNotificationChannel(
            NotificationChannel(CH_RINGING, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun activityIntent(id: Long) = PendingIntent.getActivity(
        context, 0,
        Intent(context, AlarmActivity::class.java)
            .setData("pomemo://ring/$id".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun receiverIntent(action: String, id: Long) = AlarmReceiver.broadcast(context, action, "pomemo://ring/$id")

    companion object {
        const val CH_RINGING = "ringing"
        const val CH_REMINDER = "reminders"
        const val SNOOZE_MINUTES = 10
    }
}
