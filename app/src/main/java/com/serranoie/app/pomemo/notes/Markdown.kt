package com.serranoie.app.pomemo.notes

import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
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
private data class Edit(val start: Int, val end: Int, val replacement: String = "", val check: Int = -1)

/**
 * Styles markdown. With [rendered] = false (editing) every symbol stays, just coloured. With
 * [rendered] = true (cards, preview, alarm notes) the symbols go: `#`, `**`, `_`, `~~`, backticks
 * and fences disappear, list markers become •/☐/☑ and quotes get a bar.
 *
 * With [onCheck], rendered ☐/☑ boxes are tappable links that report the offset of their `[` in [text]
 * (feed it to [toggleCheckbox]).
 */
fun highlightMarkdown(text: String, c: MarkdownColors, rendered: Boolean = false, onCheck: ((Int) -> Unit)? = null): AnnotatedString {
    val edits = mutableListOf<Edit>()
    val styled = styleMarkdown(text, c, edits)
    return if (rendered) applyEdits(styled, edits, c.marker, onCheck) else styled
}

private fun applyEdits(s: AnnotatedString, edits: List<Edit>, color: Color, onCheck: ((Int) -> Unit)?) = buildAnnotatedString {
    var pos = 0
    for (e in edits.sortedBy { it.start }) {
        if (e.start < pos) continue // overlapping syntax: the first one wins
        append(s.subSequence(pos, e.start))
        val style = SpanStyle(color = color)
        when {
            e.replacement.isEmpty() -> Unit
            e.check >= 0 && onCheck != null ->
                withLink(LinkAnnotation.Clickable("check:${e.check}", TextLinkStyles(style)) { onCheck(e.check) }) { append(e.replacement) }
            else -> withStyle(style) { append(e.replacement) }
        }
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
                    val bracket = mark.indexOf('[').let { if (it < 0) -1 else start + it }
                    if (symbol != null) edits += Edit(start, start + m.range.last + 1, indent + symbol, bracket)
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

/** Flips the checklist box whose `[` sits at [bracket]: `[ ]` ↔ `[x]`. Anything else is left alone. */
fun toggleCheckbox(text: String, bracket: Int): String {
    if (text.getOrNull(bracket) != '[' || text.getOrNull(bracket + 2) != ']') return text
    return text.replaceRange(bracket + 1, bracket + 2, if (text[bracket + 1] == ' ') "x" else " ")
}

private val PLAIN = MarkdownColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)

/** The note's first non-blank line without markdown symbols, for pickers and labels. */
fun noteTitle(body: String): String =
    body.lineSequence().firstOrNull { it.isNotBlank() }?.let { highlightMarkdown(it, PLAIN, rendered = true).text.trim() }.orEmpty()

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

private val LIST_FORMATS = setOf(LineFormat.BULLET, LineFormat.NUMBERED, LineFormat.CHECKLIST)

private fun String.indentOf() = takeWhile { it == ' ' || it == '\t' }

fun TextFieldValue.isActive(f: LineFormat): Boolean {
    val start = text.lineStart(selection.min)
    return f.prefixOf(text.substring(start, text.lineEnd(start)).trimStart(' ', '\t')) != null
}

/** A text replacement at [start]: [oldLen] characters became [newLen]. Edits are listed in text order. */
private data class Splice(val start: Int, val oldLen: Int, val newLen: Int)

/** Where offset [p] lands after [splices]; a caret inside a replaced span goes to its end. */
private fun remap(splices: List<Splice>, p: Int): Int {
    var delta = 0
    for ((start, oldLen, newLen) in splices) {
        if (p < start) break
        if (p <= start + oldLen) return start + delta + newLen
        delta += newLen - oldLen
    }
    return p + delta
}

private fun TextFieldValue.spliced(text: String, splices: List<Splice>) =
    TextFieldValue(text, TextRange(remap(splices, selection.start), remap(splices, selection.end)))

fun TextFieldValue.toggle(f: LineFormat): TextFieldValue {
    val turnOff = isActive(f)
    val first = text.lineStart(selection.min)
    val last = text.lineEnd(selection.max)
    val splices = mutableListOf<Splice>()
    val out = StringBuilder(text.substring(0, first))
    var lineStart = first
    for (line in text.substring(first, last).split('\n')) {
        // Nested list items keep their indent; a heading or quote starts at the margin.
        val indent = if (turnOff || f in LIST_FORMATS) line.indentOf() else ""
        val body = line.substring(indent.length)
        val old = LineFormat.entries.firstNotNullOfOrNull { it.prefixOf(body) } ?: ""
        val new = if (turnOff) "" else f.prefix
        out.append(indent).append(new).append(body.substring(old.length)).append('\n')
        splices += Splice(lineStart + indent.length, old.length, new.length)
        lineStart += line.length + 1
    }
    out.setLength(out.length - 1)
    out.append(text.substring(last))
    return spliced(out.toString(), splices).renumbered()
}

// ---------------------------------------------------------------------------------------------
// Lists: Enter continues them, indent nests them, numbers stay in sequence.
// ---------------------------------------------------------------------------------------------

/** indent, bullet, number, number delimiter, checkbox. */
private val LIST_ITEM = Regex("""^([ \t]*)(?:([-*+])|(\d+)([.)]))[ \t](\[[ xX]][ \t])?""")
private const val INDENT = "    "

fun isListLine(line: String) = LIST_ITEM.containsMatchIn(line)

fun TextFieldValue.isInList(): Boolean = isListLine(text.substring(text.lineStart(selection.min), text.lineEnd(selection.min)))

/**
 * If [new] is [old] with Enter pressed on a list item, returns the edit with the list continued:
 * the next bullet, number or empty checkbox at the same indent. Enter on an empty item ends the
 * list instead (its marker is removed). Null when Enter wasn't pressed in a list.
 */
fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
    val s = old.selection.min
    val caret = s + 1
    if (!new.selection.collapsed || new.selection.start != caret) return null
    if (new.text.length != old.text.length - old.selection.length + 1 || new.text.getOrNull(s) != '\n') return null
    if (!new.text.regionMatches(0, old.text, 0, s) || !new.text.regionMatches(caret, old.text, old.selection.max, old.text.length - old.selection.max)) return null

    val lineStart = old.text.lineStart(s)
    val m = LIST_ITEM.find(old.text.substring(lineStart, s)) ?: return null
    val lineEnd = old.text.lineEnd(old.selection.max)
    val content = old.text.substring(lineStart + m.value.length, s) + old.text.substring(old.selection.max, lineEnd)
    if (content.isBlank()) {
        // Second Enter on an empty item: drop the marker and stay on the now-empty line.
        val t = old.text.removeRange(lineStart, lineEnd)
        return TextFieldValue(t, TextRange(lineStart)).renumbered()
    }
    val (_, indent, bullet, number, delim) = m.groupValues
    val checkbox = if (m.groupValues[5].isNotEmpty()) "[ ] " else ""
    val prefix = indent + (if (bullet.isNotEmpty()) "$bullet " else "${number.toInt() + 1}$delim ") + checkbox
    val t = new.text.substring(0, caret) + prefix + new.text.substring(caret)
    return TextFieldValue(t, TextRange(caret + prefix.length)).renumbered()
}

/**
 * After reordering list lines, the list still starts where it did: the new first item takes the old
 * first item's number (renumbering then counts on from it). Only applies when both are numbered.
 */
fun keepListStart(before: List<String>, after: List<String>): List<String> {
    val start = before.firstOrNull()?.let { LIST_ITEM.find(it)?.groups?.get(3) } ?: return after
    val first = after.firstOrNull()?.let { LIST_ITEM.find(it)?.groups?.get(3) } ?: return after
    return listOf(after[0].replaceRange(first.range, start.value)) + after.drop(1)
}

/** Nests ([deeper]) or un-nests every selected list line by one level, then renumbers. */
fun TextFieldValue.indent(deeper: Boolean): TextFieldValue {
    val first = text.lineStart(selection.min)
    val last = text.lineEnd(selection.max)
    val splices = mutableListOf<Splice>()
    val out = StringBuilder(text.substring(0, first))
    var lineStart = first
    for (line in text.substring(first, last).split('\n')) {
        val change = when {
            !isListLine(line) -> 0
            deeper -> INDENT.length
            line.startsWith('\t') -> -1
            else -> -line.indentOf().length.coerceAtMost(INDENT.length)
        }
        if (change > 0) out.append(INDENT).append(line) else out.append(line.substring(-change))
        if (change != 0) splices += Splice(lineStart, maxOf(-change, 0), maxOf(change, 0))
        out.append('\n')
        lineStart += line.length + 1
    }
    out.setLength(out.length - 1)
    out.append(text.substring(last))
    return spliced(out.toString(), splices).renumbered()
}

/**
 * Keeps every numbered list counting up: each run (per indent level) counts on from its first
 * item's number (from 1 when nested). A line that isn't a list item ends all runs; fenced code is left alone.
 */
fun TextFieldValue.renumbered(): TextFieldValue {
    val splices = mutableListOf<Splice>()
    val out = StringBuilder()
    val counters = sortedMapOf<Int, Int>() // indent width → last number at that level
    var inFence = false
    var lineStart = 0
    for ((i, line) in text.split('\n').withIndex()) {
        if (i > 0) out.append('\n')
        val m = if (inFence) null else LIST_ITEM.find(line)
        when {
            FENCE.containsMatchIn(line) -> { inFence = !inFence; counters.clear(); out.append(line) }
            m == null -> { counters.clear(); out.append(line) }
            else -> {
                val level = m.groupValues[1].replace("\t", INDENT).length
                counters.keys.retainAll { it <= level }
                val number = m.groups[3]
                if (number == null) {
                    counters.remove(level)
                    out.append(line)
                } else {
                    // A top-level list may start anywhere ("5."); a nested one counts from 1.
                    val n = counters[level]?.plus(1) ?: if (level > 0) 1 else number.value.toInt()
                    counters[level] = n
                    out.append(line, 0, number.range.first).append(n).append(line, number.range.last + 1, line.length)
                    if (n.toString() != number.value) splices += Splice(lineStart + number.range.first, number.value.length, n.toString().length)
                }
            }
        }
        lineStart += line.length + 1
    }
    return if (splices.isEmpty()) this else spliced(out.toString(), splices)
}
