package com.serranoie.app.pomemo

import androidx.compose.ui.graphics.Color
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.notes.MarkdownColors
import com.serranoie.app.pomemo.notes.highlightMarkdown
import com.serranoie.app.pomemo.notes.noteTitle
import com.serranoie.app.pomemo.notes.toggleCheckbox
import com.serranoie.app.pomemo.reminders.ALL_DAYS
import com.serranoie.app.pomemo.reminders.nextTrigger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SkipAndChecklistTest {
    private val morning = LocalDateTime.of(2026, 10, 14, 7, 0)
    private val daily = Reminder(1, "Pills", time = LocalTime.of(21, 0), days = ALL_DAYS)

    @Test fun skipTodayMovesToTomorrow() {
        assertEquals(morning.withHour(21), nextTrigger(daily, morning))
        assertEquals(morning.plusDays(1).withHour(21), nextTrigger(daily.copy(skipDate = morning.toLocalDate()), morning))
    }

    @Test fun pastSkipDateDoesNothing() =
        assertEquals(morning.withHour(21), nextTrigger(daily.copy(skipDate = LocalDate.of(2026, 10, 1)), morning))

    @Test fun checkboxToggles() {
        val body = "# List\n- [ ] milk\n- [x] bread"
        val milk = body.indexOf("[ ]")
        val bread = body.indexOf("[x]")
        assertEquals("# List\n- [x] milk\n- [x] bread", toggleCheckbox(body, milk))
        assertEquals("# List\n- [ ] milk\n- [ ] bread", toggleCheckbox(body, bread))
        assertEquals(body, toggleCheckbox(body, 0)) // not a box: untouched
    }

    @Test fun renderedBoxesAreLinksToTheirBracket() {
        val body = "- [ ] milk\n  - [x] bread"
        val c = Color.Red
        val hits = mutableListOf<Int>()
        val a = highlightMarkdown(body, MarkdownColors(c, c, c, c, c), rendered = true, onCheck = { hits += it })
        val links = a.getLinkAnnotations(0, a.length)
        assertEquals(listOf("☐ ", "  ☑ "), links.map { a.text.substring(it.start, it.end) })
        assertEquals(listOf("check:${body.indexOf("[ ]")}", "check:${body.indexOf("[x]")}"), links.map { (it.item as androidx.compose.ui.text.LinkAnnotation.Clickable).tag })
    }

    @Test fun titleIsFirstLineWithoutSyntax() {
        assertEquals("Groceries", noteTitle("\n### **Groceries**\n- [ ] milk"))
        assertEquals("", noteTitle("   "))
    }
}
