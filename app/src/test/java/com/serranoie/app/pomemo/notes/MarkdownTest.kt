package com.serranoie.app.pomemo.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditingTest {
    private fun v(text: String, s: Int, e: Int = s) = TextFieldValue(text, TextRange(s, e))

    @Test fun boldWrapsSelectionAndKeepsItSelected() {
        val r = v("make this bold", 5, 9).toggle(InlineFormat.BOLD)
        assertEquals("make **this** bold", r.text)
        assertEquals(TextRange(7, 11), r.selection)
    }

    @Test fun boldWithNoSelectionInsertsPairWithCaretInside() {
        val r = v("hi ", 3).toggle(InlineFormat.BOLD)
        assertEquals("hi ****", r.text)
        assertEquals(TextRange(5), r.selection)
    }

    @Test fun caretInsideBoldIsActiveAndTogglingUnwraps() {
        val inside = v("make **this** bold", 9)
        assertTrue(inside.isActive(InlineFormat.BOLD))
        val r = inside.toggle(InlineFormat.BOLD)
        assertEquals("make this bold", r.text)
        assertEquals(TextRange(7), r.selection)
    }

    @Test fun italicUsesUnderscoreSoItIsNotConfusedWithBold() {
        assertFalse(v("make **this** bold", 9).isActive(InlineFormat.ITALIC))
        assertEquals("_word_", v("word", 0, 4).toggle(InlineFormat.ITALIC).text)
    }

    @Test fun selectingTheWholeSpanIncludingMarkersUnwraps() =
        assertEquals("a strike b", v("a ~~strike~~ b", 2, 12).toggle(InlineFormat.STRIKE).text)

    @Test fun spansDoNotLeakAcrossLines() = assertFalse(v("**open\nnext**", 9).isActive(InlineFormat.BOLD))

    @Test fun headingTogglesOnAndOff() {
        val on = v("Title", 2).toggle(LineFormat.HEADING)
        assertEquals("# Title", on.text)
        assertEquals(TextRange(4), on.selection)
        assertTrue(on.isActive(LineFormat.HEADING))
        assertEquals("Title", on.toggle(LineFormat.HEADING).text)
    }

    @Test fun switchingListTypeReplacesThePrefix() {
        val r = v("- milk", 3).toggle(LineFormat.NUMBERED)
        assertEquals("1. milk", r.text)
        assertEquals(TextRange(4), r.selection)
    }

    @Test fun checklistIsNotAlsoABullet() {
        val c = v("- [ ] task", 7)
        assertTrue(c.isActive(LineFormat.CHECKLIST))
        assertFalse(c.isActive(LineFormat.BULLET))
    }

    @Test fun lineFormatAppliesToEverySelectedLine() {
        val r = v("one\ntwo\nthree", 1, 9).toggle(LineFormat.BULLET)
        assertEquals("- one\n- two\n- three", r.text)
        assertEquals(TextRange(3, 15), r.selection) // "o|ne" and "t|hree" keep their place after the new prefixes
    }
}

class MarkdownHighlightTest {
    private val colors = MarkdownColors(Color.Red, Color.Green, Color.Gray, Color.LightGray, Color.Blue)

    @Test fun syntaxStaysAndContentIsStyled() {
        val a = highlightMarkdown("**b** _i_ ~~s~~", colors)
        assertEquals("**b** _i_ ~~s~~", a.text) // nothing hidden
        val styles = a.spanStyles
        assertTrue(styles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 5 })
        assertTrue(styles.any { it.item.fontStyle == FontStyle.Italic && it.start == 6 && it.end == 9 })
        assertTrue(styles.any { it.item.textDecoration == TextDecoration.LineThrough && it.start == 10 && it.end == 15 })
        assertTrue(styles.any { it.item.color == Color.Red && it.start == 0 && it.end == 2 }) // marker colour
    }

    @Test fun boldIsNotAlsoItalic() =
        assertFalse(highlightMarkdown("**bold**", colors).spanStyles.any { it.item.fontStyle == FontStyle.Italic })

    @Test fun nothingInlineInsideCode() =
        assertFalse(highlightMarkdown("`**x**`", colors).spanStyles.any { it.item.fontWeight == FontWeight.Bold })

    @Test fun headingIsBiggerAndBold() =
        assertTrue(highlightMarkdown("# Title", colors).spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 7 })

    @Test fun renderedHidesSyntax() {
        fun r(t: String) = highlightMarkdown(t, colors, rendered = true).text
        assertEquals("Title", r("# Title"))
        assertEquals("b i s code", r("**b** _i_ ~~s~~ `code`"))
        assertEquals("☐ milk\n☑ bread\n• eggs\n1. tea", r("- [ ] milk\n- [x] bread\n- eggs\n1. tea"))
        assertEquals("▍ quoted", r("> quoted"))
        assertEquals("see docs", r("see [docs](https://x.y)"))
        assertEquals("fun x()\n", r("```\nfun x()\n```\n"))
    }

    @Test fun renderedKeepsTheStyles() {
        val a = highlightMarkdown("**b**", colors, rendered = true)
        assertEquals("b", a.text)
        assertTrue(a.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 1 })
    }
}
