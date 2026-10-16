package com.serranoie.app.pomemo.notes

import androidx.compose.ui.draw.clip
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.ui.text.TextRange
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.ui.HapticUtil
import com.serranoie.app.pomemo.ui.components.TooltipIconButton
import com.serranoie.app.pomemo.ui.components.WithTooltip
import com.serranoie.app.pomemo.ui.theme.PomemoTheme

/** One toolbar toggle: checked while the caret sits inside that format, tapping wraps or unwraps it. */
private data class Format(
    val icon: ImageVector,
    val label: Int,
    val isActive: (TextFieldValue) -> Boolean,
    val toggle: (TextFieldValue) -> TextFieldValue,
)

private fun inline(f: InlineFormat, icon: ImageVector, label: Int) = Format(icon, label, { it.isActive(f) }, { it.toggle(f) })
private fun line(f: LineFormat, icon: ImageVector, label: Int) = Format(icon, label, { it.isActive(f) }, { it.toggle(f) })

private val FORMATS = listOf(
    inline(InlineFormat.BOLD, Icons.Rounded.FormatBold, R.string.format_bold),
    inline(InlineFormat.ITALIC, Icons.Rounded.FormatItalic, R.string.format_italic),
    inline(InlineFormat.STRIKE, Icons.Rounded.FormatStrikethrough, R.string.format_strike),
    inline(InlineFormat.CODE, Icons.Rounded.Code, R.string.format_code),
    line(LineFormat.HEADING, Icons.Rounded.Title, R.string.format_heading),
    line(LineFormat.QUOTE, Icons.Rounded.FormatQuote, R.string.format_quote),
    line(LineFormat.BULLET, Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.format_bullet),
    line(LineFormat.NUMBERED, Icons.Rounded.FormatListNumbered, R.string.format_numbered),
    line(LineFormat.CHECKLIST, Icons.Rounded.CheckBox, R.string.format_checklist),
)

private val INNER_CORNER = 8.dp

/** Pill segment shapes: outer ends fully round on the first/last item, tight inner corners, circle when on. */
private fun connectedToggleShapes(index: Int, count: Int): IconToggleButtonShapes {
    val round = CornerSize(50)
    val inner = CornerSize(INNER_CORNER)
    val shape = when (index) {
        0 -> RoundedCornerShape(topStart = round, topEnd = inner, bottomEnd = inner, bottomStart = round)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = round, bottomEnd = round, bottomStart = inner)
        else -> RoundedCornerShape(inner)
    }
    return IconToggleButtonShapes(shape = shape, pressedShape = RoundedCornerShape(INNER_CORNER / 2), checkedShape = CircleShape)
}

/** Full-screen editor. Back, ✕ and Save all save (see [NotesViewModel.close]); only Delete throws away. */
@Composable
fun NoteEditor(state: NoteEditorState, onChange: (TextFieldValue) -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    Dialog(
        onDismissRequest = onSave,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NoteEditorContent(state, onChange, onSave, onDelete)
    }
}

@Composable
fun NoteEditorContent(state: NoteEditorState, onChange: (TextFieldValue) -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    val view = LocalView.current
    val colors = rememberMarkdownColors()
    val focus = remember { FocusRequester() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Edit shows the markdown symbols (styled); Preview hides them and reads like the note cards.
    var preview by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(preview) { if (!preview) focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.close), onClick = onSave)
                Spacer(Modifier.weight(1f))
                ToggleButton(
                    checked = preview,
                    onCheckedChange = { HapticUtil.performLightHaptic(view); preview = it },
                ) {
                    // The label names what tapping does next.
                    Icon(if (preview) Icons.Rounded.Edit else Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(stringResource(if (preview) R.string.edit else R.string.preview))
                }
                if (state.id != 0L) TooltipIconButton(Icons.Rounded.Delete, stringResource(R.string.delete), contentColor = MaterialTheme.colorScheme.error) { confirmDelete = true }
            }

            // The page: editing shows the markdown symbols styled live; preview hides them.
            Surface(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                if (preview) {
                    // Hold a list item to drag it; ticking a box works here too.
                    NotePreview(
                        state.value.text,
                        colors,
                        onTextChange = { t -> onChange(TextFieldValue(t, TextRange(state.value.selection.start.coerceAtMost(t.length))).renumbered()) },
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    )
                } else {
                    val placeholder = stringResource(R.string.note_placeholder)
                    BasicTextField(
                        value = state.value,
                        onValueChange = onChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = MarkdownTransformation(colors),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .focusRequester(focus)
                            .semantics { contentDescription = placeholder },
                        decorationBox = { inner ->
                            Box {
                                if (state.value.text.isEmpty()) {
                                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            // Toolbar above the keyboard: scrollable format toggles + a big Save.
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One connected pill: rounded outer ends on the first/last button only, small inner corners,
                // and an active format morphs into a circle. Clipped to a stadium so it scrolls inside the pill.
                // Nest / un-nest, only while the caret sits on a list item.
                if (!preview && state.value.isInList()) {
                    listOf(
                        Triple(Icons.AutoMirrored.Rounded.FormatIndentDecrease, R.string.outdent, false),
                        Triple(Icons.AutoMirrored.Rounded.FormatIndentIncrease, R.string.indent, true),
                    ).forEach { (icon, label, deeper) ->
                        val text = stringResource(label)
                        WithTooltip(text) {
                            FilledTonalIconButton(
                                onClick = { HapticUtil.performLightHaptic(view); onChange(state.value.indent(deeper)) },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier
                                    .size(IconButtonDefaults.smallContainerSize())
                                    .focusProperties { canFocus = false },
                            ) { Icon(icon, contentDescription = text) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (!preview) Row(
                        Modifier.clip(CircleShape).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        FORMATS.forEachIndexed { i, f ->
                            val label = stringResource(f.label)
                            WithTooltip(label) {
                                FilledTonalIconToggleButton(
                                    checked = f.isActive(state.value),
                                    onCheckedChange = {
                                        HapticUtil.performLightHaptic(view)
                                        onChange(f.toggle(state.value))
                                    },
                                    shapes = connectedToggleShapes(i, FORMATS.size),
                                    // Never take focus from the text field, or the keyboard would close on every tap.
                                    modifier = Modifier
                                        .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
                                        .focusProperties { canFocus = false },
                                ) { Icon(f.icon, contentDescription = label) }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                val save = stringResource(R.string.save)
                WithTooltip(save) {
                    MediumFloatingActionButton(
                        onClick = { HapticUtil.performConfirmHaptic(view); onSave() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    ) { Icon(Icons.Rounded.Save, contentDescription = save, modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)) }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_note_title)) },
        text = { Text(stringResource(R.string.delete_note_text)) },
        confirmButton = {
            TextButton(
                onClick = { HapticUtil.performRejectHaptic(view); confirmDelete = false; onDelete() },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton({ confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
    )
}

@PreviewLightDark
@Composable
private fun NoteEditorPreview() = PomemoTheme {
    val text = "# Markdown guide\nUnderstands **bold**, _italic_, ~~strike~~ and `code`.\n\n### Quotes\n> A paragraph starting with `>` is a quote.\n\n- [ ] milk\n- [x] bread"
    NoteEditorContent(NoteEditorState(1, TextFieldValue(text), text), {}, {}, {})
}
