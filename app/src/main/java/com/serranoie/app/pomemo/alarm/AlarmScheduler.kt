package com.serranoie.app.pomemo.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.reminders.nextTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps AlarmManager. Deliberately uses setExactAndAllowWhileIdle, not setAlarmClock, so reminders
 * never show up as the system "next alarm" next to the user's Clock alarms.
 */
@Singleton
class AlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Schedules the next occurrence, or cancels if disabled / no future occurrence. */
    fun schedule(reminder: Reminder) {
        val next = if (reminder.enabled) nextTrigger(reminder, LocalDateTime.now()) else null
        if (next == null) return cancel(reminder.id)
        setAt(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), fireIntent(reminder.id, snooze = false))
    }

    fun snooze(id: Long, minutes: Int) =
        setAt(System.currentTimeMillis() + minutes * 60_000L, fireIntent(id, snooze = true))

    fun setTimer(epochMillis: Long) = setAt(epochMillis, timerIntent())

    fun cancelTimer() = alarmManager.cancel(timerIntent())

    /** Auto-start: starts the next phase when the full-screen countdown runs out. */
    fun setAutoStart(epochMillis: Long) = setAt(epochMillis, autoStartIntent())

    fun cancelAutoStart() = alarmManager.cancel(autoStartIntent())

    /** Repost of the live notification's progress bar; exact so the bar moves on the minute. */
    fun setTimerTick(epochMillis: Long) = setAt(epochMillis, tickIntent())

    fun cancelTimerTick() = alarmManager.cancel(tickIntent())

    fun cancel(id: Long) {
        alarmManager.cancel(fireIntent(id, snooze = false))
        alarmManager.cancel(fireIntent(id, snooze = true))
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAt(epochMillis: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pi)
        }
    }

    private fun fireIntent(id: Long, snooze: Boolean) =
        AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_FIRE, "pomemo://${if (snooze) "snooze" else "fire"}/$id")

    private fun tickIntent() = AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_TIMER_TICK, "pomemo://timer/tick")

    private fun autoStartIntent() = AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_TIMER_START, "pomemo://timer/auto")

    private fun timerIntent() = AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_TIMER_END, "pomemo://timer")
}
