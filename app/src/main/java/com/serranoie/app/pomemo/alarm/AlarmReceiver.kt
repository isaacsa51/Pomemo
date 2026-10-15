package com.serranoie.app.pomemo.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.serranoie.app.pomemo.data.ReminderDao
import com.serranoie.app.pomemo.timer.PomodoroTimer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: ReminderDao
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var ringer: Ringer
    @Inject lateinit var timer: PomodoroTimer

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment?.toLongOrNull()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> if (id != null) fire(id, isSnooze = intent.data?.host == "snooze")
                    ACTION_SNOOZE -> if (id != null) ringer.snooze(id)
                    ACTION_DISMISS -> if (id != null) ringer.stop(id)
                    ACTION_TIMER_END -> timer.onPhaseEnd().join()
                    ACTION_TIMER_TICK -> timer.refresh().join()
                    ACTION_TIMER_START -> timer.start().join()
                    ACTION_TIMER_PAUSE -> timer.pause().join()
                    ACTION_TIMER_RESET -> timer.reset().join()
                    else -> {
                        // A running timer's alarm doesn't survive a reboot.
                        if (intent.action == Intent.ACTION_BOOT_COMPLETED) timer.reset().join()
                        // Boot, app update, clock or timezone change: pending alarms are gone or stale.
                        dao.getEnabled().forEach(scheduler::schedule)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(id: Long, isSnooze: Boolean) {
        val reminder = dao.get(id)?.takeIf { it.enabled } ?: return
        ringer.ring(reminder)
        if (isSnooze) return
        if (reminder.days == 0) dao.update(reminder.copy(enabled = false))
        else scheduler.schedule(reminder)
    }

    companion object {
        const val ACTION_FIRE = "com.serranoie.app.pomemo.FIRE"
        const val ACTION_SNOOZE = "com.serranoie.app.pomemo.SNOOZE"
        const val ACTION_DISMISS = "com.serranoie.app.pomemo.DISMISS"
        const val ACTION_TIMER_END = "com.serranoie.app.pomemo.TIMER_END"
        const val ACTION_TIMER_TICK = "com.serranoie.app.pomemo.TIMER_TICK"
        const val ACTION_TIMER_START = "com.serranoie.app.pomemo.TIMER_START"
        const val ACTION_TIMER_PAUSE = "com.serranoie.app.pomemo.TIMER_PAUSE"
        const val ACTION_TIMER_RESET = "com.serranoie.app.pomemo.TIMER_RESET"

        /** The data URI keeps PendingIntents distinct per target, no request-code math. */
        fun broadcast(context: Context, action: String, data: String): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java).setAction(action).setData(data.toUri()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
