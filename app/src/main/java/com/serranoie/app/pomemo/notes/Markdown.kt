package com.serranoie.app.pomemo.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em

// ---------------------------------------------------------------------------------------------
// Highlighting: the markdown stays visible (like an editor), only styled. No parser library:
// line rules (fences, headings, quotes, lists, rules) + inline regexes (code, bold, italic, strike, links).
// ---------------------------------------------------------------------------------------------

data class MarkdownColors(
    val marker: Color,
    val heading: Color,
    val quote: Color,
    val codeBackground: Color,
    val link: Color,
)

@Composable
fun rememberMarkdownColors(): MarkdownColors {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        MarkdownColors(
            marker = cs.primary,
            heading = cs.tertiary,
            quote = cs.onSurfaceVariant,
            codeBackground = cs.surfaceContainerHighest,
            link = cs.primary,
        )
    }
}

/** Sizes for # … ######, relative to the body text. */
private val HEADING_SIZES = listOf(1.6f, 1.4f, 1.25f, 1.15f, 1.05f, 1f)

private val FENCE = Regex("""^\s*```""")
private val HEADING = Regex("""^(#{1,6})\s+\S""")
private val QUOTE = Regex("""^\s*>""")
private val LIST_MARK = Regex("""^\s*(?:[-*+]|\d+[.)])\s(?:\[[ xX]]\s)?""")
private val RULE = Regex("""^\s*(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""")
private val CODE_SPAN = Regex("""`([^`\n]+)`""")
private val BOLD = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
private val ITALIC = Regex("""(?<![*_\w])([*_])(?![*_\s])(.+?)(?<![\s*_])\1(?![*_\w])""")
private val STRIKE = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
private val LINK = Regex("""\[([^\]\n]+)]\(([^)\n]+)\)""")

/** A piece of syntax to drop (or swap for [replacement]) when rendering without markdown symbols. */
private data class Edit(val start: Int, val end: Int, val replacement: String = "")

/**
 * Styles markdown. With [rendered] = false (editing) every symbol stays, just coloured. With
 * [rendered] = true (cards, preview, alarm notes) the symbols go: `#`, `**`, `_`, `~~`, backticks
 * and fences disappear, list markers become •/☐/☑ and quotes get a bar.
 */
fun highlightMarkdown(text: String, c: MarkdownColors, rendered: Boolean = false): AnnotatedString {
    val edits = mutableListOf<Edit>()
    val styled = styleMarkdown(text, c, edits)
    return if (rendered) applyEdits(styled, edits, c.marker) else styled
}

private fun applyEdits(s: AnnotatedString, edits: List<Edit>, color: Color) = buildAnnotatedString {
    var pos = 0
    for (e in edits.sortedBy { it.start }) {
        if (e.start < pos) continue // overlapping syntax: the first one wins
        append(s.subSequence(pos, e.start))
        if (e.replacement.isNotEmpty()) withStyle(SpanStyle(color = color)) { append(e.replacement) }
        pos = e.end
    }
    append(s.subSequence(pos, s.length))
}

private fun styleMarkdown(text: String, c: MarkdownColors, edits: MutableList<Edit>): AnnotatedString = buildAnnotatedString {
    append(text)
    val marker = SpanStyle(color = c.marker)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBackground)
    var inFence = false
    var start = 0
    for (line in text.split('\n')) {
        val end = start + line.length
        when {
            FENCE.containsMatchIn(line) -> {
                addStyle(code, start, end)
                addStyle(marker, start, end)
                edits += Edit(start, minOf(end + 1, text.length)) // the whole ``` line, newline included
                inFence = !inFence
            }
            inFence -> addStyle(code, start, end)
            RULE.matches(line) -> {
                addStyle(marker, start, end)
                edits += Edit(start, end, "────────────")
            }
            else -> {
                HEADING.find(line)?.let { m ->
                    val level = m.groupValues[1].length
                    addStyle(SpanStyle(color = c.heading, fontWeight = FontWeight.Bold, fontSize = HEADING_SIZES[level - 1].em), start, end)
                    addStyle(marker, start, start + level)
                    edits += Edit(start, start + m.range.last) // "## " up to the first word
                }
                QUOTE.find(line)?.let { m ->
                    addStyle(SpanStyle(color = c.quote, fontStyle = FontStyle.Italic), start, end)
                    addStyle(marker, start, start + m.range.last + 1)
                    val after = start + m.range.last + 1
                    edits += Edit(start, if (line.getOrNull(m.range.last + 1) == ' ') after + 1 else after, "▍ ")
                }
                LIST_MARK.find(line)?.let { m ->
                    addStyle(marker, start, start + m.range.last + 1)
                    val mark = m.value
                    val indent = mark.takeWhile { it == ' ' || it == '\t' }
                    val symbol = when {
                        "[ ]" in mark -> "☐ "
                        "[x]" in mark || "[X]" in mark -> "☑ "
                        mark.trim().first().isDigit() -> null // numbers read fine as they are
                        else -> "• "
                    }
                    if (symbol != null) edits += Edit(start, start + m.range.last + 1, indent + symbol)
                }
                inline(line, start, marker, code, c, edits)
            }
        }
        start = end + 1
    }
}

private fun AnnotatedString.Builder.inline(
    line: String,
    offset: Int,
    marker: SpanStyle,
    code: SpanStyle,
    c: MarkdownColors,
    edits: MutableList<Edit>,
) {
    // Code spans first; nothing else is styled inside them.
    val codeRanges = CODE_SPAN.findAll(line).map { it.range }.toList()
    fun inCode(r: IntRange) = codeRanges.any { r.first >= it.first && r.last <= it.last }
    for (r in codeRanges) {
        addStyle(code, offset + r.first, offset + r.last + 1)
        addStyle(marker, offset + r.first, offset + r.first + 1)
        addStyle(marker, offset + r.last, offset + r.last + 1)
        edits += Edit(offset + r.first, offset + r.first + 1)
        edits += Edit(offset + r.last, offset + r.last + 1)
    }
    fun wrapped(regex: Regex, style: SpanStyle) {
        for (m in regex.findAll(line)) {
            if (inCode(m.range)) continue
            val markerLen = (m.range.last + 1 - m.range.first - m.groupValues.last().length) / 2
            addStyle(style, offset + m.range.first, offset + m.range.last + 1)
            addStyle(marker, offset + m.range.first, offset + m.range.first + markerLen)
            addStyle(marker, offset + m.range.last + 1 - markerLen, offset + m.range.last + 1)
            edits += Edit(offset + m.range.first, offset + m.range.first + markerLen)
            edits += Edit(offset + m.range.last + 1 - markerLen, offset + m.range.last + 1)
        }
    }
    wrapped(BOLD, SpanStyle(fontWeight = FontWeight.Bold))
    wrapped(ITALIC, SpanStyle(fontStyle = FontStyle.Italic))
    wrapped(STRIKE, SpanStyle(textDecoration = TextDecoration.LineThrough))
    for (m in LINK.findAll(line)) {
        if (inCode(m.range)) continue
        addStyle(marker, offset + m.range.first, offset + m.range.last + 1)
        val text = m.groups[1]!!.range
        addStyle(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline), offset + text.first, offset + text.last + 1)
        edits += Edit(offset + m.range.first, offset + text.first) // "["
        edits += Edit(offset + text.last + 1, offset + m.range.last + 1) // "](url)"
    }
}

/** Live highlighting in the editor; offsets map 1:1 because no characters are added or hidden. */
data class MarkdownTransformation(val colors: MarkdownColors) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlightMarkdown(text.text, colors), OffsetMapping.Identity)
}

// ---------------------------------------------------------------------------------------------
// Toolbar toggles. Inline formats wrap/unwrap the selection (or the word span around the cursor);
// line formats add/remove a prefix on every selected line. Italic uses `_` so it never collides
// with bold's `**`.
// ---------------------------------------------------------------------------------------------

enum class InlineFormat(val marker: String) { BOLD("**"), ITALIC("_"), STRIKE("~~"), CODE("`") }

enum class LineFormat(val prefix: String, private val matcher: Regex) {
    HEADING("# ", Regex("""^#{1,6} """)),
    QUOTE("> ", Regex("""^> """)),
    BULLET("- ", Regex("""^[-*+] (?!\[[ xX]] )""")),
    NUMBERED("1. ", Regex("""^\d+\. """)),
    CHECKLIST("- [ ] ", Regex("""^[-*+] \[[ xX]] """));

    fun prefixOf(line: String): String? = matcher.find(line)?.value
}

private fun String.lineStart(pos: Int) = lastIndexOf('\n', pos - 1) + 1
private fun String.lineEnd(pos: Int) = indexOf('\n', pos).let { if (it < 0) length else it }

private fun String.countOf(marker: String): Int {
    var n = 0
    var i = indexOf(marker)
    while (i >= 0) { n++; i = indexOf(marker, i + marker.length) }
    return n
}

/** The marker positions around the selection on its line, if it sits inside a [f] span. */
private fun TextFieldValue.enclosing(f: InlineFormat): Pair<Int, Int>? {
    val m = f.marker
    val s = selection.min
    val e = selection.max
    val lineStart = text.lineStart(s)
    val lineEnd = text.lineEnd(e)
    val before = text.substring(lineStart, s)
    if (before.countOf(m) % 2 == 0) return null
    val open = lineStart + before.lastIndexOf(m)
    val close = text.indexOf(m, e).takeIf { it in e..lineEnd - m.length } ?: return null
    return open to close
}

fun TextFieldValue.isActive(f: InlineFormat): Boolean = enclosing(f) != null

fun TextFieldValue.toggle(f: InlineFormat): TextFieldValue {
    val m = f.marker
    val s = selection.min
    val e = selection.max
    enclosing(f)?.let { (open, close) ->
        val t = text.removeRange(close, close + m.length).removeRange(open, open + m.length)
        fun shift(p: Int) = p - (if (p > open) m.length else 0) - (if (p > close) m.length else 0)
        return copy(text = t, selection = TextRange(shift(s), shift(e)))
    }
    val selected = text.substring(s, e)
    if (selected.length >= 2 * m.length && selected.startsWith(m) && selected.endsWith(m)) {
        val inner = selected.substring(m.length, selected.length - m.length)
        return copy(text = text.replaceRange(s, e, inner), selection = TextRange(s, s + inner.length))
    }
    // Wrap the selection (or insert an empty pair) and keep the caret/selection inside.
    return copy(text = text.replaceRange(s, e, m + selected + m), selection = TextRange(s + m.length, e + m.length))
}

fun TextFieldValue.isActive(f: LineFormat): Boolean {
    val start = text.lineStart(selection.min)
    return f.prefixOf(text.substring(start, text.lineEnd(start))) != null
}

fun TextFieldValue.toggle(f: LineFormat): TextFieldValue {
    val turnOff = isActive(f)
    val first = text.lineStart(selection.min)
    val last = text.lineEnd(selection.max)
    // (old line start, old prefix length, new prefix length) for each touched line, in order.
    val edits = mutableListOf<Triple<Int, Int, Int>>()
    val out = StringBuilder(text.substring(0, first))
    var lineStart = first
    for (line in text.substring(first, last).split('\n')) {
        val old = LineFormat.entries.firstNotNullOfOrNull { it.prefixOf(line) } ?: ""
        val new = if (turnOff) "" else f.prefix
        out.append(new).append(line.substring(old.length)).append('\n')
        edits += Triple(lineStart, old.length, new.length)
        lineStart += line.length + 1
    }
    out.setLength(out.length - 1)
    out.append(text.substring(last))

    fun map(p: Int): Int {
        var delta = 0
        for ((start, oldLen, newLen) in edits) {
            if (p < start) break
            if (p <= start + oldLen) return start + delta + newLen // caret was in the prefix: put it after the new one
            delta += newLen - oldLen
        }
        return p + delta
    }
    return copy(text = out.toString(), selection = TextRange(map(selection.start), map(selection.end)))
}
