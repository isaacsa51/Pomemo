package com.serranoie.app.pomemo.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderParserTest {
    // Wednesday 2026-10-14, 12:00
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0)
    private val today = now.toLocalDate()
    private val tomorrow = today.plusDays(1)

    private fun check(input: String, title: String, time: String?, days: Int = 0, date: LocalDate? = null) =
        assertEquals(input, ReminderDraft(title, time?.let(LocalTime::parse), days, date), ReminderParser.parse(input, now))

    @Test fun table() {
        check("stretch every weekday at 10:30", "Stretch", "10:30", WEEKDAYS)
        check("water plants mon wed fri 8pm", "Water plants", "20:00", MONDAY.bit() or WEDNESDAY.bit() or FRIDAY.bit())
        check("water plants mon, wed and fri at 8 pm", "Water plants", "20:00", MONDAY.bit() or WEDNESDAY.bit() or FRIDAY.bit())
        check("take out trash every tuesday night", "Take out trash", "21:00", TUESDAY.bit())
        check("call mom tomorrow at 6", "Call mom", "18:00", date = tomorrow)
        check("drink water daily 9am", "Drink water", "09:00", ALL_DAYS)
        check("pay rent on weekends at noon", "Pay rent", "12:00", WEEKENDS)
    }

    @Test fun oneShots() {
        check("Remind me to call Mom on Friday at 6pm", "Call Mom", "18:00", date = LocalDate.of(2026, 10, 16))
        check("dentist on wednesday at 11am", "Dentist", "11:00", date = LocalDate.of(2026, 10, 21)) // today's 11:00 passed
        check("dentist on wednesday at 3pm", "Dentist", "15:00", date = today)
        check("buy milk at 9", "Buy milk", "09:00", date = tomorrow) // 9:00 already passed today
        check("stand up in 20 minutes", "Stand up", "12:20", date = today)
        check("check oven in 2 hours", "Check oven", "14:00", date = today)
        check("read tonight", "Read", "21:00", date = today)
        check("pills at 7:30 a.m.", "Pills", "07:30", date = tomorrow)
    }

    @Test fun recurringWords() {
        check("meditate every morning", "Meditate", "09:00", ALL_DAYS)
        check("gym mondays and thursdays 7pm", "Gym", "19:00", MONDAY.bit() or java.time.DayOfWeek.THURSDAY.bit())
        check("journal every day at 22:00", "Journal", "22:00", ALL_DAYS)
    }

    @Test fun noTimeYet() {
        check("buy milk", "Buy milk", null)
        check("water the plants every sunday", "Water the plants", null, java.time.DayOfWeek.SUNDAY.bit())
    }

    @Test fun titleWordsAreNotEatenByDayAbbreviations() {
        check("put on sunscreen at 2pm", "Put on sunscreen", "14:00", date = today)
    }
}

class SpanishReminderParserTest {
    // Wednesday 2026-10-14, 12:00
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0)
    private val today = now.toLocalDate()
    private val tomorrow = today.plusDays(1)
    private val mwf = MONDAY.bit() or WEDNESDAY.bit() or FRIDAY.bit()

    private fun check(input: String, title: String, time: String?, days: Int = 0, date: LocalDate? = null) =
        assertEquals(input, ReminderDraft(title, time?.let(LocalTime::parse), days, date), ReminderParser.parse(input, now))

    @Test fun recurring() {
        check("regar las plantas lun mié vie 8pm", "Regar las plantas", "20:00", mwf)
        check("regar las plantas lunes, miercoles y viernes a las 8 de la noche", "Regar las plantas", "20:00", mwf)
        check("estirarme entre semana a las 10:30", "Estirarme", "10:30", WEEKDAYS)
        check("sacar la basura todos los martes en la noche", "Sacar la basura", "21:00", TUESDAY.bit())
        check("tomar agua diario 9am", "Tomar agua", "09:00", ALL_DAYS)
        check("pagar la renta los fines de semana al mediodía", "Pagar la renta", "12:00", WEEKENDS)
        check("meditar cada mañana", "Meditar", "09:00", ALL_DAYS)
        check("gimnasio los lunes y jueves 7pm", "Gimnasio", "19:00", MONDAY.bit() or java.time.DayOfWeek.THURSDAY.bit())
        check("Revisar correo de lunes a viernes a las 9", "Revisar correo", "09:00", WEEKDAYS)
        check("leer todas las noches", "Leer", "21:00", ALL_DAYS)
        check("regar el jardín cada domingo", "Regar el jardín", null, java.time.DayOfWeek.SUNDAY.bit())
    }

    @Test fun oneShots() {
        check("llamar a mamá mañana a las 6", "Llamar a mamá", "18:00", date = tomorrow)
        check("Recuérdame que llame al doctor el viernes a las 6 de la tarde", "Llame al doctor", "18:00", date = LocalDate.of(2026, 10, 16))
        check("dentista el miércoles a las 11 de la mañana", "Dentista", "11:00", date = LocalDate.of(2026, 10, 21))
        check("pastillas a las 7 y media de la mañana", "Pastillas", "07:30", date = tomorrow)
        check("cita con el dentista pasado mañana a las 11", "Cita con el dentista", "11:00", date = LocalDate.of(2026, 10, 16))
        check("levantarme en 20 minutos", "Levantarme", "12:20", date = today)
        check("revisar el horno dentro de 2 horas", "Revisar el horno", "14:00", date = today)
        check("leer esta noche", "Leer", "21:00", date = today)
        check("junta a las 4 menos cuarto", "Junta", "15:45", date = today)
        check("no olvides comprar pan hoy a la 1", "Comprar pan", "13:00", date = today)
        check("MAÑANA a las 8:15 p. m. cine", "Cine", "20:15", date = tomorrow)
    }

    @Test fun ordinaryWordsStayInTheTitle() {
        check("no llegar tarde a la junta mañana a las 9", "No llegar tarde a la junta", "09:00", date = tomorrow)
        check("comprar leche", "Comprar leche", null)
    }
}
