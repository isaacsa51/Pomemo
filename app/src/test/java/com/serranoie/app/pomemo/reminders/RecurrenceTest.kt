package com.serranoie.app.pomemo.reminders

import com.serranoie.app.pomemo.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {
    // 2026-10-14 is a Wednesday
    private val wedNoon = LocalDateTime.of(2026, 10, 14, 12, 0)
    private val mwf = MONDAY.bit() or WEDNESDAY.bit() or FRIDAY.bit()

    private fun r(time: String, days: Int = 0, date: LocalDate? = null) =
        Reminder(title = "t", time = LocalTime.parse(time), days = days, date = date)

    @Test fun recurringLaterToday() =
        assertEquals(wedNoon.withHour(20), nextTrigger(r("20:00", mwf), wedNoon))

    @Test fun recurringAlreadyPassedTodayGoesToNextDay() =
        assertEquals(LocalDateTime.of(2026, 10, 16, 8, 0), nextTrigger(r("08:00", mwf), wedNoon))

    @Test fun recurringExactlyNowIsNotNow() =
        assertEquals(LocalDateTime.of(2026, 10, 21, 12, 0), nextTrigger(r("12:00", WEDNESDAY.bit()), wedNoon))

    @Test fun recurringRespectsFutureStartDate() =
        assertEquals(
            LocalDateTime.of(2026, 10, 26, 9, 0),
            nextTrigger(r("09:00", MONDAY.bit(), LocalDate.of(2026, 10, 20)), wedNoon),
        )

    @Test fun oneShotTodayFutureAndPast() {
        assertEquals(wedNoon.withHour(18), nextTrigger(r("18:00"), wedNoon))
        assertNull(nextTrigger(r("09:00"), wedNoon))
    }

    @Test fun oneShotOnDate() =
        assertEquals(
            LocalDateTime.of(2026, 10, 15, 6, 0),
            nextTrigger(r("06:00", date = LocalDate.of(2026, 10, 15)), wedNoon),
        )
}
