package com.serranoie.app.pomemo.reminders

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

const val ALL_DAYS = 0b1111111
const val WEEKDAYS = 0b0011111
const val WEEKENDS = 0b1100000

fun DayOfWeek.bit(): Int = 1 shl (value - 1)

/** Next time [reminder] should ring strictly after [now], or null if it never will again. */
fun nextTrigger(reminder: Reminder, now: LocalDateTime): LocalDateTime? {
    val start = maxOf(reminder.date ?: now.toLocalDate(), now.toLocalDate())
    if (reminder.days == 0) {
        return (reminder.date ?: now.toLocalDate()).atTime(reminder.time).takeIf { it.isAfter(now) }
    }
    return (0L..7L).asSequence()
        .map { start.plusDays(it) }
        .filter { reminder.days and it.dayOfWeek.bit() != 0 && it != reminder.skipDate }
        .map { it.atTime(reminder.time) }
        .firstOrNull { it.isAfter(now) }
}

/** One-shot date that is not in the past: a passed moment means the next time that clock time comes round. */
fun upcomingDate(date: LocalDate?, time: LocalTime, now: LocalDateTime = LocalDateTime.now()): LocalDate {
    val today = now.toLocalDate()
    val day = maxOf(date ?: today, today)
    return if (day == today && !time.isAfter(now.toLocalTime())) today.plusDays(1) else day
}

/** "Every day" / "Todos los días", "Mon, Wed, Fri" / "lun, mié, vie", "Tomorrow" / "Mañana", "Oct 16, 2026". */
@Composable
fun describeSchedule(days: Int, date: LocalDate?, today: LocalDate = LocalDate.now()): String {
    LocalConfiguration.current // re-read on locale change
    return describeSchedule(LocalContext.current, days, date, today)
}

/** Same as the composable, for places outside composition (toasts, notifications). */
fun describeSchedule(context: Context, days: Int, date: LocalDate?, today: LocalDate = LocalDate.now()): String {
    val locale = context.resources.configuration.locales[0]
    return when {
        days == ALL_DAYS -> context.getString(R.string.schedule_every_day)
        days == WEEKDAYS -> context.getString(R.string.schedule_weekdays)
        days == WEEKENDS -> context.getString(R.string.schedule_weekends)
        days != 0 -> DayOfWeek.entries.filter { days and it.bit() != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        date == null || date == today -> context.getString(R.string.schedule_today)
        date == today.plusDays(1) -> context.getString(R.string.schedule_tomorrow)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}
