package com.serranoie.app.pomemo.notes

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.ui.HapticUtil

private enum class Kind { TEXT, LIST, IMAGE, TABLE }

/** A run of consecutive lines drawn one way: text, a reorderable list, a picture or a table. */
private data class Block(val lines: List<String>, val kind: Kind)

private val TABLE_RULE = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")

/** Splits a note into blocks. Lines inside ``` fences are always text; each picture is its own block. */
private fun blocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    var inFence = false
    for (line in text.split('\n')) {
        val fence = line.trimStart().startsWith("```")
        val kind = when {
            inFence || fence -> Kind.TEXT
            IMAGE_LINE.matches(line) -> Kind.IMAGE
            isListLine(line) -> Kind.LIST
            line.trimStart().startsWith("|") -> Kind.TABLE
            else -> Kind.TEXT
        }
        if (fence) inFence = !inFence
        val last = out.lastOrNull()
        if (last != null && last.kind == kind && kind != Kind.IMAGE) out[out.lastIndex] = last.copy(lines = last.lines + line)
        else out += Block(listOf(line), kind)
    }
    // Pipes without a --- rule under the first row aren't a table.
    return out.map { if (it.kind == Kind.TABLE && (it.lines.size < 2 || !TABLE_RULE.matches(it.lines[1]))) it.copy(kind = Kind.TEXT) else it }
}

/**
 * Rendered note. Hold a list item and drag it to move it within its list; numbers follow. Checkboxes
 * tick in place. Every change goes out as the note's new full text through [onTextChange].
 */
@Composable
fun NotePreview(text: String, colors: MarkdownColors, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val parts = remember(text) { blocks(text) }
    Column(modifier) {
        parts.forEachIndexed { b, block ->
            fun commit(lines: List<String>) =
                onTextChange(parts.mapIndexed { i, p -> if (i == b) lines else p.lines }.flatten().joinToString("\n"))
            when (block.kind) {
                Kind.LIST -> ReorderableLines(block.lines, colors, ::commit)
                Kind.IMAGE -> IMAGE_LINE.find(block.lines[0])?.let { m ->
                    NoteImage(m.groupValues[2], m.groupValues[1], Modifier.padding(vertical = 8.dp))
                }
                Kind.TABLE -> TableBlock(block.lines, colors)
                Kind.TEXT -> Text(
                    remember(block, colors) { highlightMarkdown(block.lines.joinToString("\n"), colors, rendered = true) },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/** Header row tinted and bold, equal-width columns, thin dividers. The --- rule row isn't drawn. */
@Composable
private fun TableBlock(lines: List<String>, colors: MarkdownColors) {
    val rows = remember(lines) { lines.filterIndexed { i, _ -> i != 1 }.map(::cells) }
    val columns = rows.maxOf { it.size }
    val border = MaterialTheme.colorScheme.outlineVariant
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(shape).border(1.dp, border, shape)) {
        rows.forEachIndexed { r, row ->
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .background(if (r == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
            ) {
                repeat(columns) { c ->
                    if (c > 0) VerticalDivider(color = border)
                    val cell = row.getOrElse(c) { "" }
                    Text(
                        remember(cell, colors) { highlightMarkdown(cell, colors, rendered = true) },
                        style = if (r == 0) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            if (r < rows.lastIndex) HorizontalDivider(color = border)
        }
    }
}

@Composable
private fun ReorderableLines(lines: List<String>, colors: MarkdownColors, onCommit: (List<String>) -> Unit) {
    val view = LocalView.current
    // Gestures outlive recompositions; always commit through the latest callback so other blocks' edits survive.
    val commit by rememberUpdatedState(onCommit)
    // Ids keep each row's state attached while rows swap places mid-drag.
    val items = remember(lines) { lines.withIndex().map { it.index to it.value }.toMutableStateList() }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)

    fun move(from: Int, to: Int) = items.add(to, items.removeAt(from))
    fun finish() {
        dragged = null
        offset = 0f
        val now = items.map { it.second }
        if (now != lines) commit(keepListStart(lines, now))
    }

    Column {
        items.forEachIndexed { index, (id, line) ->
            key(id) {
                val isDragged = dragged == id
                val elevation by animateDpAsState(if (isDragged) 6.dp else 0.dp, label = "dragElevation")
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    shadowElevation = elevation,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) offset else 0f }
                        .onSizeChanged { heights[id] = it.height }
                        .pointerInput(id, items) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    HapticUtil.performConfirmHaptic(view)
                                    dragged = id
                                    offset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    // Swap with a neighbour once dragged past half its height.
                                    val i = items.indexOfFirst { it.first == id }
                                    val below = items.getOrNull(i + 1)?.let { heights[it.first] }
                                    val above = items.getOrNull(i - 1)?.let { heights[it.first] }
                                    if (below != null && offset > below / 2f) {
                                        move(i, i + 1); offset -= below; HapticUtil.performLightHaptic(view)
                                    } else if (above != null && -offset > above / 2f) {
                                        move(i, i - 1); offset += above; HapticUtil.performLightHaptic(view)
                                    }
                                },
                                onDragEnd = ::finish,
                                onDragCancel = ::finish,
                            )
                        }
                        // TalkBack can't drag: offer the same moves as actions.
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index, index - 1); finish(); true })
                                if (index < items.lastIndex) add(CustomAccessibilityAction(moveDown) { move(index, index + 1); finish(); true })
                            }
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val rendered = remember(line, colors, items) {
                            highlightMarkdown(line, colors, rendered = true, onCheck = { bracket ->
                                commit(items.map { if (it.first == id) toggleCheckbox(line, bracket) else it.second })
                            })
                        }
                        Text(rendered, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 2.dp))
                        Icon(Icons.Rounded.DragIndicator, contentDescription = null, modifier = Modifier.size(18.dp).alpha(0.38f))
                    }
                }
            }
        }
    }
}
