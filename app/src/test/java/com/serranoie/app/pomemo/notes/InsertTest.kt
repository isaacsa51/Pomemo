package com.serranoie.app.pomemo.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertTest {
    private fun v(text: String, s: Int, e: Int = s) = TextFieldValue(text, TextRange(s, e))
    private val c = MarkdownColors(Color.Red, Color.Green, Color.Gray, Color.LightGray, Color.Blue)

    @Test fun imageGoesOnItsOwnLine() {
        val r = v("before after", 6).insertImage("note_images/a.jpg")
        assertEquals("before\n![](note_images/a.jpg)\n after", r.text)
        assertEquals(TextRange(30), r.selection)
    }

    @Test fun imageAtLineStartAddsNoExtraBreakBefore() =
        assertEquals("a\n![](x)\n", v("a\n", 2).insertImage("x").text)

    @Test fun tableCaretLandsInFirstBodyCell() {
        val r = v("", 0).insertTable("A", "B")
        assertEquals("| A | B |\n| --- | --- |\n|  |  |\n", r.text)
        // Just after "| " on the first body row, so typing fills the cell.
        assertEquals("| A | B |\n| --- | --- |\n| ".length, r.selection.start)
    }

    @Test fun linkWrapsSelectionAndAddsScheme() {
        val r = v("see docs here", 4, 8).insertLink("docs", "example.com")
        assertEquals("see [docs](https://example.com) here", r.text)
        assertEquals(TextRange(31), r.selection)
    }

    @Test fun blankLinkLabelShowsTheUrl() = assertEquals("[https://a.b](https://a.b)", v("", 0).insertLink(" ", "https://a.b").text)

    @Test fun imageLineVanishesWhenRendered() =
        assertEquals("top\nbottom", highlightMarkdown("top\n![pic](note_images/a.jpg)\nbottom", c, rendered = true).text)

    @Test fun inlineImageBecomesMarker() =
        assertEquals("look 🖼 cat now", highlightMarkdown("look ![cat](note_images/a.jpg) now", c, rendered = true).text)

    @Test fun webLinksAreClickableButOtherSchemesAreNot() {
        val ok = highlightMarkdown("[site](https://x.y)", c, rendered = true)
        assertTrue(ok.getLinkAnnotations(0, ok.length).any { (it.item as? LinkAnnotation.Url)?.url == "https://x.y" })
        val bad = highlightMarkdown("[f](file:///data/x)", c, rendered = true)
        assertTrue(bad.getLinkAnnotations(0, bad.length).isEmpty())
    }

    @Test fun titleAndCoverSkipImageLines() {
        val body = "![](note_images/a.jpg)\nTrip"
        assertEquals("Trip", noteTitle(body))
        assertEquals("note_images/a.jpg", firstImage(body))
        assertNull(firstImage("no pictures"))
    }
}
