package com.serranoie.app.pomemo.reminders

import com.serranoie.app.pomemo.notes.noteTitle
import com.serranoie.app.pomemo.data.Note
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.InputChip
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.LocalView
import com.serranoie.app.pomemo.ui.HapticUtil
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.ui.components.TooltipIconButton
import com.serranoie.app.pomemo.ui.theme.PomemoTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle


/** Sheet + its dialogs. All state lives in [Composer]; edits go out through [onUpdate]. */
@Composable
fun ReminderComposerSheet(
    composer: Composer,
    notes: List<Note>,
    onUpdate: (Composer.() -> Composer) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val draft = remember(composer) { composer.draft() }
    val view = LocalView.current
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickNote by rememberSaveable { mutableStateOf(false) }
    val attached = notes.firstOrNull { it.id == composer.noteId }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ReminderComposerContent(
            composer, draft, onUpdate, onSave,
            onPickTime = { pickTime = true },
            onPickDate = { pickDate = true },
            onDelete = { confirmDelete = true },
            attachedTitle = attached?.let { noteTitle(it.body) },
            onPickNote = { pickNote = true },
        )
    }
    if (pickNote) NoteDialog(notes, attached?.id, { pickNote = false }) { id -> onUpdate { copy(noteId = id) } }
    if (pickTime) TimeDialog(draft.time ?: LocalTime.now().plusHours(1).withMinute(0), { pickTime = false }) { t ->
        onUpdate { copy(time = t) }
    }
    if (pickDate) DateDialog(draft.date ?: LocalDate.now(), { pickDate = false }) { d ->
        onUpdate { copy(date = d, days = 0) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_text, draft.title)) },
        confirmButton = {
            TextButton(
                onClick = { HapticUtil.performRejectHaptic(view); onDelete(); confirmDelete = false },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton({ confirmDelete = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Sheet body, stateless so it previews without the sheet popup. */
@Composable
fun ReminderComposerContent(
    composer: Composer,
    draft: ReminderDraft,
    onUpdate: (Composer.() -> Composer) -> Unit,
    onSave: () -> Unit,
    onPickTime: () -> Unit,
    onPickDate: () -> Unit,
    onDelete: () -> Unit,
    attachedTitle: String? = null,
    onPickNote: () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val view = LocalView.current
    val editing = composer.editingId != 0L
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!editing) focus.requestFocus() }
    Column(
        Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (editing) R.string.edit_reminder else R.string.new_reminder),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        val summary = draft.time?.let {
            stringResource(
                R.string.summary,
                draft.title.ifBlank { stringResource(R.string.default_title) },
                describeSchedule(draft.days, draft.date),
                it.format(timeFormat),
            )
        } ?: stringResource(R.string.add_time_to_save)
        OutlinedTextField(
            value = composer.text,
            onValueChange = { t -> onUpdate { copy(text = t) } },
            label = { Text(stringResource(R.string.composer_label)) },
            placeholder = { Text(stringResource(R.string.composer_placeholder)) },
            // What the parser understood, read out with the field.
            supportingText = { Text(summary) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.time != null) onSave() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )

        if (composer.text.isBlank()) Section(stringResource(R.string.examples)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.composer_examples).forEach { ex -> SuggestionChip({ HapticUtil.performUIHaptic(view); onUpdate { copy(text = ex) } }, { Text(ex) }) }
            }
        }

        // Each chip is "Label, value" for TalkBack via the icon's description.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { HapticUtil.performUIHaptic(view); onPickTime() },
                label = { Text(draft.time?.format(timeFormat) ?: stringResource(R.string.pick_time)) },
                leadingIcon = { Icon(Icons.Rounded.Schedule, stringResource(R.string.time)) },
            )
            if (draft.days == 0) AssistChip(
                onClick = { HapticUtil.performUIHaptic(view); onPickDate() },
                label = { Text(describeSchedule(0, draft.date)) },
                leadingIcon = { Icon(Icons.Rounded.CalendarToday, stringResource(R.string.date)) },
            )
        }

        Section(stringResource(R.string.repeat_on)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                val days = DayOfWeek.entries
                days.forEachIndexed { i, day ->
                    val name = day.getDisplayName(TextStyle.FULL, locale)
                    ToggleButton(
                        checked = draft.days and day.bit() != 0,
                        onCheckedChange = { HapticUtil.performLightHaptic(view); onUpdate { copy(days = draft.days xor day.bit()) } },
                        // Narrow letters (T, S) are ambiguous when spoken; announce the full day.
                        modifier = Modifier.weight(1f).semantics { contentDescription = name },
                        shapes = connectedShapes(i, days.size),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(day.getDisplayName(TextStyle.NARROW, locale), Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }

        // One line: off = a plain notification. The whole row is the switch.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .toggleable(composer.ring, role = Role.Switch) { on ->
                    HapticUtil.performLightHaptic(view)
                    onUpdate { copy(ring = on) }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (composer.ring) Icons.Rounded.Alarm else Icons.Rounded.Notifications, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.kind_alarm), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = composer.ring, onCheckedChange = null)
        }

        // The attached note below covers the details; a free-text note only stays for reminders that already have one.
        if (composer.note.isNotBlank()) OutlinedTextField(
            value = composer.note,
            onValueChange = { n -> onUpdate { copy(note = n) } },
            label = { Text(stringResource(R.string.note_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Section(stringResource(R.string.attached_note)) {
            // Shown on the alarm screen, where its checklist can be ticked off.
            if (attachedTitle == null) AssistChip(
                onClick = { HapticUtil.performUIHaptic(view); onPickNote() },
                label = { Text(stringResource(R.string.attach_note)) },
                leadingIcon = { Icon(Icons.Rounded.AttachFile, contentDescription = null) },
            ) else InputChip(
                selected = true,
                onClick = { HapticUtil.performUIHaptic(view); onPickNote() },
                label = { Text(attachedTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            )
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            if (editing) TextButton(
                onClick = { HapticUtil.performUIHaptic(view); onDelete() },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { HapticUtil.performConfirmHaptic(view); onSave() }, shapes = ButtonDefaults.shapes(), enabled = draft.time != null) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/** Labelled group; the label is a heading so TalkBack users can jump between sections. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
    content()
}

@Composable
private fun connectedShapes(index: Int, count: Int) = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

/** Pick one note (radio list); "Remove note" detaches the current one. */
@Composable
private fun NoteDialog(notes: List<Note>, selected: Long?, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_note)) },
        text = {
            if (notes.isEmpty()) Text(stringResource(R.string.no_notes))
            else LazyColumn(Modifier.selectableGroup()) {
                items(notes, key = { it.id }) { n ->
                    ListItem(
                        headlineContent = { Text(noteTitle(n.body), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { RadioButton(selected = n.id == selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.selectable(n.id == selected, role = Role.RadioButton) {
                            HapticUtil.performLightHaptic(view)
                            onPick(n.id)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
        dismissButton = if (selected == null) null else ({
            TextButton(
                { onPick(null); onDismiss() },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.detach_note)) }
        }),
    )
}

@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    // Typing the time is the accessible alternative to dragging the dial.
    var typing by rememberSaveable { mutableStateOf(false) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_time)) },
        confirmButton = {
            TextButton({ onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
        modeToggleButton = {
            TooltipIconButton(
                if (typing) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                stringResource(if (typing) R.string.use_dial else R.string.use_keyboard),
            ) { typing = !typing }
        },
    ) {
        if (typing) TimeInput(state) else TimePicker(state)
    }
}

@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // DatePicker speaks UTC-midnight millis.
    val todayMs = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMs
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

@PreviewLightDark
@Composable
private fun ComposerNewPreview() = PomemoTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        val c = Composer()
        ReminderComposerContent(c, c.draft(), {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, locale = "es-rMX")
@Composable
private fun ComposerEditingPreview() = PomemoTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        val c = Composer(editingId = 1, text = "Water plants", note = "Balcony first", ring = false, time = LocalTime.of(20, 0), days = 0b10101)
        ReminderComposerContent(c, c.draft(), {}, {}, {}, {}, {})
    }
}
