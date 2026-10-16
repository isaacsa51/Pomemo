package com.serranoie.app.pomemo.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListEditingTest {
    private fun v(text: String, s: Int, e: Int = s) = TextFieldValue(text, TextRange(s, e))

    /** Simulates pressing Enter at the caret of [text]. */
    private fun enter(text: String, caret: Int = text.length): TextFieldValue? {
        val old = v(text, caret)
        return continueList(old, v(text.substring(0, caret) + "\n" + text.substring(caret), caret + 1))
    }

    @Test fun enterContinuesNumberedList() {
        val r = enter("1. milk")!!
        assertEquals("1. milk\n2. ", r.text)
        assertEquals(TextRange(11), r.selection)
    }

    @Test fun enterContinuesBulletAndChecklistAtSameIndent() {
        assertEquals("- a\n- ", enter("- a")!!.text)
        assertEquals("    - [x] a\n    - [ ] ", enter("    - [x] a")!!.text)
    }

    @Test fun enterInMiddleRenumbersTheRest() {
        val r = enter("1. a\n2. b", 4)!!
        assertEquals("1. a\n2. \n3. b", r.text)
        assertEquals(TextRange(8), r.selection)
    }

    @Test fun enterOnEmptyItemEndsTheList() {
        val r = enter("1. a\n2. ")!!
        assertEquals("1. a\n", r.text)
        assertEquals(TextRange(5), r.selection)
    }

    @Test fun enterOutsideListIsLeftAlone() = assertNull(enter("plain"))

    @Test fun typingIsNotEnter() = assertNull(continueList(v("1. a", 4), v("1. ab", 5)))

    @Test fun indentNestsAndRestartsNumbering() {
        val r = v("1. a\n2. b\n3. c", 6).indent(deeper = true)
        assertEquals("1. a\n    1. b\n2. c", r.text)
        assertEquals("1. a\n2. b", v("1. a\n    2. b", 10).indent(deeper = false).text)
    }

    @Test fun renumberFixesSequencePerLevel() =
        assertEquals("3. a\n4. b\n    1. x\n    2. y\n5. c", v("3. a\n1. b\n    1. x\n    1. y\n1. c", 0).renumbered().text)

    @Test fun togglingNumberedOnSeveralLinesCounts() =
        assertEquals("1. a\n2. b\n3. c", v("a\nb\nc", 0, 5).toggle(LineFormat.NUMBERED).text)

    @Test fun bulletToggleKeepsNestedIndent() =
        assertEquals("    1. a", v("    - a", 6).toggle(LineFormat.NUMBERED).text)

    @Test fun movingFirstItemKeepsListStart() {
        val moved = keepListStart(listOf("1. a", "2. b", "3. c"), listOf("2. b", "3. c", "1. a"))
        assertEquals("1. b\n2. c\n3. a", v(moved.joinToString("\n"), 0).renumbered().text)
    }
}
