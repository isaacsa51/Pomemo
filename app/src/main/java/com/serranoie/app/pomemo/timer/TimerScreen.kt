package com.serranoie.app.pomemo.timer

import androidx.compose.material.icons.rounded.RestartAlt
import com.serranoie.app.pomemo.ui.HapticUtil
import androidx.compose.animation.core.animateIntAsState
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.serranoie.app.pomemo.data.Note
import com.serranoie.app.pomemo.notes.highlightMarkdown
import com.serranoie.app.pomemo.notes.rememberMarkdownColors
import com.serranoie.app.pomemo.reminders.NoteDialog
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.serranoie.app.pomemo.ui.LocalNavBarPadding
import com.serranoie.app.pomemo.ui.components.ExpressiveHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.alarm.AlarmSettings
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import com.serranoie.app.pomemo.ui.components.TooltipIconButton
import com.serranoie.app.pomemo.ui.theme.PomemoTheme

@Composable
fun TimerScreen(vm: TimerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val remaining by vm.remainingMs.collectAsStateWithLifecycle()
    val alarm by vm.alarmSettings.collectAsStateWithLifecycle()
    val focusNote by vm.focusNote.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    // Backups go through the system file picker: the user chooses where the file lives.
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportTo) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFrom) }
    TimerContent(
        state, settings, remaining, vm::toggle, vm::skip, vm::reset, vm::updateSettings, alarm, vm::updateAlarmSettings,
        focusNote = focusNote,
        notes = notes,
        onAttachNote = vm::attachNote,
        onCheck = { n, i -> vm.check(n, i) },
        canControlDnd = vm::canControlDnd,
        onExport = { export.launch("pomemo-backup-${LocalDate.now()}.json") },
        onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )
}

@Composable
fun TimerContent(
    state: TimerState,
    settings: TimerSettings,
    remaining: Long,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onSettingsChange: (TimerSettings) -> Unit,
    alarm: AlarmSettings = AlarmSettings(),
    onAlarmChange: (AlarmSettings) -> Unit = {},
    focusNote: Note? = null,
    notes: List<Note> = emptyList(),
    onAttachNote: (Long?) -> Unit = {},
    onCheck: (Note, Int) -> Unit = { _, _ -> },
    canControlDnd: () -> Boolean = { true },
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pickNote by rememberSaveable { mutableStateOf(false) }
    val total = settings.lengthMs(state.phase)
    val color = if (state.phase == Phase.FOCUS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val phase = stringResource(state.phase.label)

    Scaffold(
        topBar = {
            ExpressiveHeader {
                CenterAlignedTopAppBar(
                    title = {
                        val started = state.running || state.remainingMs != null
                        if (started) Text(phase, color = color) else Text(stringResource(R.string.timer_title))
                    },
                    actions = { TooltipIconButton(Icons.Rounded.Settings, stringResource(R.string.timer_settings)) { showSettings = true } },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(bottom = LocalNavBarPadding.current).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        ) {
            val secs = (remaining + 999) / 1000
            val a11y = stringResource(R.string.timer_left_a11y, phase, (secs / 60).toInt(), (secs % 60).toInt())
            val stroke = with(LocalDensity.current) { Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round) }
            val view = LocalView.current
            val settingsLabel = stringResource(R.string.timer_settings)
            Box(
                Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    // Tap the time to adjust it; locked while it runs so a stray tap can't open settings mid-focus.
                    .clickable(enabled = !state.running, onClickLabel = settingsLabel, role = Role.Button) {
                        HapticUtil.performUIHaptic(view)
                        showSettings = true
                    }
                    .clearAndSetSemantics { contentDescription = a11y },
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    progress = { (1f - remaining.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    stroke = stroke,
                    trackStroke = stroke,
                    amplitude = { if (state.running) WavyProgressIndicatorDefaults.indicatorAmplitude(it) * 0.85f else 0f },
                    wavelength = 56.dp,
                )
                // Heavy while it runs, light while idle, like the reminder tiles' on/off digits.
                val weight by animateIntAsState(if (state.running) 800 else 400, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "timeWeight")
                Text(
                    formatMs(remaining),
                    style = MaterialTheme.typography.displayLargeEmphasized.copy(
                        fontSize = 80.sp,
                        lineHeight = 80.sp,
                        fontWeight = FontWeight(weight),
                        fontFeatureSettings = "tnum",
                    ),
                )
            }

            val sessions = pluralStringResource(R.plurals.sessions_done_a11y, settings.sessions, state.done, settings.sessions)
            Row(
                Modifier.clearAndSetSemantics { contentDescription = sessions },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(settings.sessions) { i ->
                    Box(
                        Modifier.size(12.dp).clip(CircleShape).background(
                            if (i < state.done) color else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val medium = IconButtonDefaults.mediumContainerSize()
                TooltipIconButton(Icons.Rounded.RestartAlt, stringResource(R.string.reset), medium, IconButtonDefaults.mediumIconSize, tonal = true, haptic = HapticUtil::performRejectHaptic, onClick = onReset)
                TooltipIconButton(
                    if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (state.running) R.string.pause else R.string.start),
                    IconButtonDefaults.extraLargeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide),
                    IconButtonDefaults.extraLargeIconSize,
                    filled = true,
                    haptic = if (state.running) HapticUtil::performUIHaptic else HapticUtil::performConfirmHaptic,
                    onClick = onToggle,
                )
                TooltipIconButton(Icons.Rounded.SkipNext, stringResource(R.string.skip), medium, IconButtonDefaults.mediumIconSize, tonal = true, onClick = onSkip)
            }

            FocusNote(focusNote, onPick = { pickNote = true }, onCheck = { i -> focusNote?.let { onCheck(it, i) } })
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            TimerSettingsContent(settings, onSettingsChange, alarm, onAlarmChange, canControlDnd, onExport, onImport)
        }
    }
    if (pickNote) NoteDialog(notes, focusNote?.id, { pickNote = false }, onAttachNote)
}

/** The checklist for this focus: tick items right on the timer screen. Empty: a chip to pick one. */
@Composable
private fun FocusNote(note: Note?, onPick: () -> Unit, onCheck: (Int) -> Unit) {
    val view = LocalView.current
    if (note == null) {
        AssistChip(
            onClick = { HapticUtil.performUIHaptic(view); onPick() },
            label = { Text(stringResource(R.string.focus_note_attach)) },
            leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
        )
        return
    }
    val colors = rememberMarkdownColors()
    val body = remember(note.body, colors) { highlightMarkdown(note.body, colors, rendered = true, onCheck = onCheck) }
    Surface(
        Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.focus_note),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TooltipIconButton(Icons.Rounded.SwapHoriz, stringResource(R.string.focus_note_change), onClick = onPick)
            }
            Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
fun TimerSettingsContent(
    settings: TimerSettings,
    onChange: (TimerSettings) -> Unit,
    alarm: AlarmSettings,
    onAlarmChange: (AlarmSettings) -> Unit,
    canControlDnd: () -> Boolean = { true },
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val view = LocalView.current
    val context = LocalContext.current
    // DND needs a system grant: send the user there, and switch on once they come back with it.
    var waitingForDnd by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(waitingForDnd, settings) {
        if (waitingForDnd && canControlDnd()) {
            waitingForDnd = false
            onChange(settings.copy(dnd = true))
        }
        onPauseOrDispose {}
    }
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.timer_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        SectionHeader(stringResource(R.string.timer_title))
        Stepper(stringResource(R.string.phase_focus), settings.focusMin, stringResource(R.string.minutes_value, settings.focusMin), 1..120) { onChange(settings.copy(focusMin = it)) }
        Stepper(stringResource(R.string.phase_short_break), settings.shortMin, stringResource(R.string.minutes_value, settings.shortMin), 1..60) { onChange(settings.copy(shortMin = it)) }
        Stepper(stringResource(R.string.phase_long_break), settings.longMin, stringResource(R.string.minutes_value, settings.longMin), 1..90) { onChange(settings.copy(longMin = it)) }
        Stepper(stringResource(R.string.sessions_before_long), settings.sessions, "${settings.sessions}", 1..12) { onChange(settings.copy(sessions = it)) }
        ListItem(
            headlineContent = { Text(stringResource(R.string.auto_start)) },
            supportingContent = { Text(stringResource(R.string.auto_start_summary)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            trailingContent = { Switch(checked = settings.autoStart, onCheckedChange = null) },
            // The whole row is the switch: one target for touch and TalkBack.
            modifier = Modifier.toggleable(settings.autoStart, role = Role.Switch) {
                HapticUtil.performLightHaptic(view)
                onChange(settings.copy(autoStart = it))
            },
        )
        val dndBlocked = settings.dnd && !canControlDnd()
        ListItem(
            headlineContent = { Text(stringResource(R.string.dnd_focus)) },
            supportingContent = { Text(stringResource(if (dndBlocked) R.string.dnd_focus_needs_access else R.string.dnd_focus_summary)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            trailingContent = { Switch(checked = settings.dnd, onCheckedChange = null) },
            modifier = Modifier.toggleable(settings.dnd, role = Role.Switch) { on ->
                HapticUtil.performLightHaptic(view)
                if (on && !canControlDnd()) {
                    waitingForDnd = true
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } else {
                    onChange(settings.copy(dnd = on))
                }
            },
        )
        SectionHeader(stringResource(R.string.channel_alarms))
        Stepper(stringResource(R.string.snooze_length), alarm.snoozeMin, stringResource(R.string.minutes_value, alarm.snoozeMin), 1..60) { onAlarmChange(alarm.copy(snoozeMin = it)) }
        Stepper(stringResource(R.string.ring_length), alarm.ringMin, stringResource(R.string.minutes_value, alarm.ringMin), 1..30) { onAlarmChange(alarm.copy(ringMin = it)) }
        SectionHeader(stringResource(R.string.backup))
        ActionRow(Icons.Rounded.Upload, stringResource(R.string.backup_export), stringResource(R.string.backup_export_summary)) { onExport() }
        ActionRow(Icons.Rounded.Download, stringResource(R.string.backup_import), stringResource(R.string.backup_import_summary)) { onImport() }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, summary: String, onClick: () -> Unit) {
    val view = LocalView.current
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable { HapticUtil.performUIHaptic(view); onClick() },
    )
}

@Composable
private fun SectionHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
)

@Composable
private fun Stepper(label: String, value: Int, valueText: String, range: IntRange, onChange: (Int) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(Icons.Rounded.Remove, stringResource(R.string.decrease, label), enabled = value > range.first, haptic = HapticUtil::performLightHaptic) { onChange(value - 1) }
                Text(
                    valueText,
                    style = MaterialTheme.typography.titleMedium,
                    // Announce the new value after each tap.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TooltipIconButton(Icons.Rounded.Add, stringResource(R.string.increase, label), enabled = value < range.last, haptic = HapticUtil::performLightHaptic) { onChange(value + 1) }
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun TimerFocusRunningPreview() = PomemoTheme {
    TimerContent(TimerState(Phase.FOCUS, done = 1, endAt = 1L), TimerSettings(), 17 * 60_000L + 42_000, {}, {}, {}, {})
}

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun TimerBreakPausedPreview() = PomemoTheme {
    TimerContent(TimerState(Phase.SHORT_BREAK, done = 3, remainingMs = 2 * 60_000L), TimerSettings(), 2 * 60_000L, {}, {}, {}, {})
}

@PreviewLightDark
@Composable
private fun TimerSettingsPreview() = PomemoTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { TimerSettingsContent(TimerSettings(), {}, AlarmSettings(), {}) }
}
