package com.serranoie.app.pomemo.reminders

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.regex.Pattern

/** What the parser understood. Null time = not found yet; days 0 = one-shot on [date]. */
data class ReminderDraft(
    val title: String,
    val time: LocalTime? = null,
    val days: Int = 0,
    val date: LocalDate? = null,
)

/**
 * Turns "water plants mon wed fri 8pm" or "regar las plantas lun mié vie 8pm" into a
 * [ReminderDraft]. English and Spanish are understood at the same time (no language switch), so
 * either can be typed regardless of the phone's locale. Recognised phrases are cut out of the text
 * one by one; whatever is left is the title. Accents are optional ("miercoles", "manana").
 */
object ReminderParser {
    private val DAY_NAMES = linkedMapOf(
        "monday" to MONDAY, "mon" to MONDAY, "lunes" to MONDAY, "lun" to MONDAY,
        "tuesday" to TUESDAY, "tues" to TUESDAY, "tue" to TUESDAY, "martes" to TUESDAY, "mar" to TUESDAY,
        "wednesday" to WEDNESDAY, "wed" to WEDNESDAY,
        "miércoles" to WEDNESDAY, "miercoles" to WEDNESDAY, "mié" to WEDNESDAY, "mie" to WEDNESDAY,
        "thursday" to THURSDAY, "thurs" to THURSDAY, "thur" to THURSDAY, "thu" to THURSDAY,
        "jueves" to THURSDAY, "jue" to THURSDAY,
        "friday" to FRIDAY, "fri" to FRIDAY, "viernes" to FRIDAY, "vie" to FRIDAY,
        "saturday" to SATURDAY, "sat" to SATURDAY, "sábado" to SATURDAY, "sabado" to SATURDAY,
        "sáb" to SATURDAY, "sab" to SATURDAY,
        "sunday" to SUNDAY, "sun" to SUNDAY, "domingo" to SUNDAY, "dom" to SUNDAY,
    )
    private val MORNING = LocalTime.of(9, 0)
    private val AFTERNOON = LocalTime.of(15, 0)
    private val EVENING = LocalTime.of(19, 0)
    private val NIGHT = LocalTime.of(21, 0)

    /** Words that are a time of day on their own. Spanish "mañana" is not here: alone it means tomorrow. */
    private val PARTS_OF_DAY = mapOf(
        "noon" to LocalTime.NOON, "midday" to LocalTime.NOON, "midnight" to LocalTime.MIDNIGHT,
        "morning" to MORNING, "afternoon" to AFTERNOON, "evening" to EVENING, "night" to NIGHT, "tonight" to NIGHT,
        "mediodía" to LocalTime.NOON, "mediodia" to LocalTime.NOON, "medianoche" to LocalTime.MIDNIGHT,
        "tarde" to AFTERNOON, "noche" to NIGHT,
    )
    /** "tarde"/"noche" are also ordinary words ("llegar tarde"), so they only count after "en la", "esta"… */
    private val NEEDS_PREFIX = setOf("tarde", "noche")
    private val PART = PARTS_OF_DAY.keys.filter { it !in NEEDS_PREFIX }.joinToString("|")
    private val PREFIXED_PART = NEEDS_PREFIX.joinToString("|")
    private val DAY = DAY_NAMES.keys.joinToString("|")
    private const val MANANA = "ma[ñn]ana"

    /** Connectors trimmed from the edges of the title only, so "llamar a mamá" keeps its "a". */
    private val FILLER = setOf(
        "every", "each", "on", "at", "and", "&", "to", "in", "the", "by",
        "cada", "todos", "todas", "los", "las", "el", "la", "al", "a", "en", "de", "y", "e", "por", "que", "para",
    )

    fun parse(text: String, now: LocalDateTime = LocalDateTime.now()): ReminderDraft {
        var rest = " $text "
        fun take(pattern: String): MatchResult? =
            regex(pattern).find(rest)?.also { rest = rest.replaceRange(it.range, " ") }

        val today = now.toLocalDate()
        var time: LocalTime? = null
        var date: LocalDate? = null
        var days = 0

        // "in 20 minutes", "en 2 horas", "dentro de 15 min"
        take("""\b(?:in|en|dentro\s+de)\s+(\d+)\s*(minutes?|minutos?|mins?|m|hours?|horas?|hrs?|h)\b""")?.let {
            val n = it.groupValues[1].toLong()
            val at = if (it.groupValues[2].startsWith("h", true)) now.plusHours(n) else now.plusMinutes(n)
            time = at.toLocalTime().withSecond(0).withNano(0)
            date = at.toLocalDate()
        }
        // "a las 8 y media de la noche", "7 de la mañana": the part of day decides am/pm.
        if (time == null) take(
            """\b(?:a\s+las?\s+)?(\d{1,2})(?::(\d{2}))?(?:\s+(y\s+media|y\s+cuarto|menos\s+cuarto))?\s+(?:de|en|por)\s+la\s+($MANANA|madrugada|tarde|noche)\b"""
        )?.let {
            val (h, m) = withQuarter(it.groupValues[1].toInt(), it.groupValues[2], it.groupValues[3])
            val part = it.groupValues[4].lowercase()
            val hour = when {
                part.startsWith("ma") -> h % 12 // mañana / madrugada are always am
                part == "noche" && h == 12 -> 0
                else -> if (h < 12) h + 12 else h // tarde / noche
            }
            time = timeOrNull(hour, m)
        }
        // "8pm", "at 6:30 a.m.", "a las 8 p. m."
        if (time == null) take("""\b(?:at\s+|a\s+las?\s+)?(\d{1,2})(?::(\d{2}))?\s*([ap])\.?\s?m\b\.?""")?.let {
            val h = it.groupValues[1].toInt() % 12 + if (it.groupValues[3].equals("p", true)) 12 else 0
            time = timeOrNull(h, it.groupValues[2].ifEmpty { "0" }.toInt())
        }
        // "10:30", "at 18:00", "a las 20:15"
        if (time == null) take("""\b(?:at\s+|a\s+las?\s+)?(\d{1,2}):(\d{2})\b""")?.let {
            time = timeOrNull(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        // "at 6", "a las 6 y media": nobody sets a reminder for 6 am without saying so
        if (time == null) take("""\b(?:at|a\s+las?)\s+(\d{1,2})(?:\s+(y\s+media|y\s+cuarto|menos\s+cuarto))?\b""")?.let {
            val (h, m) = withQuarter(it.groupValues[1].toInt(), "", it.groupValues[2])
            time = timeOrNull(if (h in 1..6) h + 12 else h, m)
        }
        // "every morning", "cada mañana", "todas las noches" = daily
        take("""\b(?:every|each|cada|todas\s+las)\s+(morning|afternoon|evening|night|${MANANA}s?|tardes?|noches?)\b""")?.let {
            days = ALL_DAYS
            val w = it.groupValues[1].lowercase()
            time = time ?: when {
                w.startsWith("ma") -> MORNING
                w.startsWith("tard") -> AFTERNOON
                w.startsWith("noch") -> NIGHT
                else -> PARTS_OF_DAY.getValue(w)
            }
        }
        // "en la mañana", "por la mañana", "esta mañana" (bare "mañana" is tomorrow, handled below)
        take("""\b(?:(?:en|por|de)\s+la|(esta))\s+$MANANA\b""")?.let {
            if (it.groupValues[1].isNotEmpty()) date = today
            time = time ?: MORNING
        }
        (take("""\b(?:(?:en|por|de|a)\s+la\s+|(esta)\s+)($PREFIXED_PART)\b""")
            ?: take("""\b(?:in\s+the\s+|at\s+|this\s+|(?:en|por|de|a)\s+la\s+|al\s+|(esta)\s+)?($PART)\b"""))?.let {
            val word = it.groupValues[2].lowercase()
            if (word == "tonight" || it.groupValues[1].isNotEmpty()) date = today // "esta noche"
            time = time ?: PARTS_OF_DAY.getValue(word)
        }

        if (take("""\b(?:every\s*day|daily|each\s+day|todos\s+los\s+d[ií]as|cada\s+d[ií]a|a\s+diario|diariamente|diario)\b""") != null) days = ALL_DAYS
        if (take("""\b(?:(?:on\s+|every\s+)?weekdays?|entre\s+semana|de\s+lunes\s+a\s+viernes|d[ií]as\s+(?:h[aá]biles|laborables))\b""") != null) days = days or WEEKDAYS
        if (take("""\b(?:(?:on\s+|every\s+)?weekends?|(?:los\s+|cada\s+|en\s+)?fin(?:es)?\s+de\s+semana)\b""") != null) days = days or WEEKENDS

        // Day names. Several, plural, "every"/"cada"/"los"/"todos los" = recurring;
        // a single one ("friday", "el viernes") = the next such date.
        val named = mutableListOf<DayOfWeek>()
        var recurring = false
        while (true) {
            val m = take("""\b(every\s+|each\s+|cada\s+|todos\s+los\s+|los\s+|el\s+|on\s+)?($DAY)(s)?\b""") ?: break
            named += DAY_NAMES.getValue(m.groupValues[2].lowercase())
            val prefix = m.groupValues[1].trim().lowercase()
            if (prefix in setOf("every", "each", "cada", "todos los", "los") || m.groupValues[3].isNotEmpty()) recurring = true
        }
        if (named.size == 1 && !recurring && days == 0) {
            var d = today.with(TemporalAdjusters.nextOrSame(named[0]))
            if (d == today && time?.isAfter(now.toLocalTime()) != true) d = d.plusWeeks(1)
            date = d
        } else {
            named.forEach { days = days or it.bit() }
        }

        if (take("""\bpasado\s+$MANANA\b""") != null) date = today.plusDays(2)
        if (take("""\b(?:tomorrow|$MANANA)\b""") != null) date = today.plusDays(1)
        if (take("""\b(?:today|hoy)\b""") != null) date = today

        if (days != 0) date = null
        else if (date == null && time != null) date = if (time!!.isAfter(now.toLocalTime())) today else today.plusDays(1)

        return ReminderDraft(title = cleanTitle(rest), time = time, days = days, date = date)
    }

    /** "y media" = :30, "y cuarto" = :15, "menos cuarto" = 15 minutes before the hour. */
    private fun withQuarter(h: Int, minutes: String, quarter: String): Pair<Int, Int> = when {
        quarter.endsWith("media", true) -> h to 30
        quarter.startsWith("y", true) -> h to 15
        quarter.startsWith("menos", true) -> (h + 23) % 24 to 45
        else -> h to (minutes.ifEmpty { "0" }.toInt())
    }

    /**
     * Case-insensitive for accented letters too ("MAÑANA", "Miércoles"), and with a word boundary that
     * counts á/é/ñ as letters: since JDK 19 `\b` is ASCII-only on the JVM while Android's engine is
     * Unicode, so `\b` is rewritten to an explicit letter/digit boundary that behaves the same on both.
     */
    private fun regex(pattern: String): Regex = Pattern.compile(
        pattern.replace("""\b""", WORD_BOUNDARY),
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    ).toRegex()

    private const val WORD_BOUNDARY = """(?:(?<![\p{L}\p{N}])(?=[\p{L}\p{N}])|(?<=[\p{L}\p{N}])(?![\p{L}\p{N}]))"""

    private fun timeOrNull(h: Int, m: Int) = if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null

    private fun cleanTitle(rest: String): String {
        val words = rest
            .replace(
                regex("""^\s*(?:remind\s+me\s+(?:to\s+)?|don'?t\s+forget\s+(?:to\s+)?|recu[ée]rdame\s+(?:que\s+|de\s+)?|recordarme\s+(?:que\s+)?|no\s+olvides\s+(?:de\s+)?|acu[ée]rdate\s+(?:de\s+)?)"""),
                " ",
            )
            .split(Regex("""[\s,;.!¡¿?]+""")).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in FILLER) words.removeAt(0)
        while (words.isNotEmpty() && words.last().lowercase() in FILLER) words.removeAt(words.lastIndex)
        return words.joinToString(" ").replaceFirstChar { it.uppercase() }
    }
}
