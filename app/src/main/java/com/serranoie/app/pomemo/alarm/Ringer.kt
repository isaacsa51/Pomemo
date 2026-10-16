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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.timer.PomodoroTimer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class AlarmSettings(val snoozeMin: Int = 10, val ringMin: Int = 10)

private val Context.alarmStore by preferencesDataStore("alarms")

/**
 * Single source of truth for "what is ringing". Plain reminders are just a notification; ringing
 * ones start [RingService] (sound) and open [AlarmActivity] (UI), both of which follow [ringing].
 * An alarm that fires while another rings waits in a queue and rings when the current one stops.
 */
@Singleton
class Ringer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val store = context.alarmStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ringing = MutableStateFlow<Reminder?>(null)
    val ringing: StateFlow<Reminder?> = _ringing.asStateFlow()
    // ponytail: queued alarms stay invisible until their turn and are lost if the process dies
    private val queue = ArrayDeque<Reminder>()

    val settings: StateFlow<AlarmSettings> = store.data.map { it.settings() }.stateIn(scope, SharingStarted.Eagerly, AlarmSettings())

    /** Reads the store, not [settings], so a cold start right after boot doesn't see the defaults. */
    suspend fun loadSettings(): AlarmSettings = store.data.first().settings()

    fun updateSettings(new: AlarmSettings) = scope.launch {
        store.edit { it[SNOOZE_MIN] = new.snoozeMin; it[RING_MIN] = new.ringMin }
    }

    /** "Continue to Short break": set by the timer when a phase ends, shown by the notification and AlarmActivity. Blank = no button. */
    var continueLabel: String = ""
        private set

    /** When set, the timer starts the next phase on its own at this time; AlarmActivity counts down to it. */
    @Volatile var autoStartAt: Long? = null
        private set

    fun ringTimer(reminder: Reminder, continueLabel: String, autoStartAt: Long? = null) {
        this.continueLabel = continueLabel
        this.autoStartAt = autoStartAt
        ring(reminder)
        // The full-screen intent only takes over a locked or off screen; with the app open, show the countdown too.
        // Android drops this silently when the app is in the background, leaving the heads-up notification.
        if (autoStartAt != null) runCatching {
            context.startActivity(Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Synchronized
    fun ring(reminder: Reminder) {
        createChannels()
        if (!reminder.ring) return nm.notify(reminder.id.toInt(), notification(reminder))
        val current = _ringing.value
        if (current != null && current.id != reminder.id) {
            if (queue.none { it.id == reminder.id }) queue.addLast(reminder)
            return
        }
        _ringing.value = reminder
        ContextCompat.startForegroundService(context, Intent(context, RingService::class.java))
    }

    /** Stops [id] wherever it is; if it was ringing, the next queued alarm takes over (RingService follows). */
    @Synchronized
    fun stop(id: Long) {
        nm.cancel(id.toInt())
        queue.removeAll { it.id == id }
        if (_ringing.value?.id == id) _ringing.value = queue.removeFirstOrNull()
    }

    fun snooze(id: Long): Job {
        stop(id)
        return scope.launch { scheduler.snooze(id, loadSettings().snoozeMin) }
    }

    /** Rang for the whole ring length unanswered: stop it and leave a quiet "Missed" notification. */
    fun timeout(id: Long) {
        val missed = _ringing.value?.takeIf { it.id == id } ?: return
        stop(id)
        nm.notify(
            MISSED_TAG, id.toInt(),
            NotificationCompat.Builder(context, CH_REMINDER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.missed, missed.title))
                .setContentText(missed.note.ifBlank { null })
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(appIntent())
                .build(),
        )
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
            // AlarmActivity closes itself when nothing rings, so plain notifications open the app.
            .setContentIntent(if (reminder.ring) activityIntent(id) else appIntent())
            .setDeleteIntent(receiverIntent(AlarmReceiver.ACTION_DISMISS, id))
        if (id == PomodoroTimer.RING_ID) {
            // Both stop the ring: starting the next phase or resetting the timer calls ringer.stop.
            if (continueLabel.isNotBlank()) builder.addAction(0, continueLabel, receiverIntent(AlarmReceiver.ACTION_TIMER_START, id))
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

    private fun appIntent() = PendingIntent.getActivity(
        context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun receiverIntent(action: String, id: Long) = AlarmReceiver.broadcast(context, action, "pomemo://ring/$id")

    private fun Preferences.settings() = AlarmSettings(snoozeMin = this[SNOOZE_MIN] ?: 10, ringMin = this[RING_MIN] ?: 10)

    companion object {
        const val CH_RINGING = "ringing"
        const val CH_REMINDER = "reminders"
        private const val MISSED_TAG = "missed"
        private val SNOOZE_MIN = intPreferencesKey("snooze_min")
        private val RING_MIN = intPreferencesKey("ring_min")
    }
}
