package com.serranoie.app.pomemo.timer

import androidx.compose.material.icons.rounded.RestartAlt
import com.serranoie.app.pomemo.ui.HapticUtil
import androidx.compose.foundation.background
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
import com.serranoie.app.pomemo.ui.components.TooltipIconButton
import com.serranoie.app.pomemo.ui.theme.PomemoTheme

@Composable
fun TimerScreen(vm: TimerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val remaining by vm.remainingMs.collectAsStateWithLifecycle()
    TimerContent(state, settings, remaining, vm::toggle, vm::skip, vm::reset, vm::updateSettings)
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
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val total = settings.lengthMs(state.phase)
    val color = if (state.phase == Phase.FOCUS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val phase = stringResource(state.phase.label)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val started = state.running || state.remainingMs != null
                    if (started) Text(phase, color = color) else Text(stringResource(R.string.timer_title))
                },
                actions = { TooltipIconButton(Icons.Rounded.Settings, stringResource(R.string.timer_settings)) { showSettings = true } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        ) {
            val secs = (remaining + 999) / 1000
            val a11y = stringResource(R.string.timer_left_a11y, phase, (secs / 60).toInt(), (secs % 60).toInt())
            val stroke = with(LocalDensity.current) { Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round) }
            Box(
                Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clearAndSetSemantics { contentDescription = a11y },
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
                Text(
                    formatMs(remaining),
                    style = MaterialTheme.typography.displayLargeEmphasized.copy(fontFeatureSettings = "tnum"),
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
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            TimerSettingsContent(settings, onSettingsChange)
        }
    }
}

@Composable
fun TimerSettingsContent(settings: TimerSettings, onChange: (TimerSettings) -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.timer_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        Stepper(stringResource(R.string.phase_focus), settings.focusMin, stringResource(R.string.minutes_value, settings.focusMin), 1..120) { onChange(settings.copy(focusMin = it)) }
        Stepper(stringResource(R.string.phase_short_break), settings.shortMin, stringResource(R.string.minutes_value, settings.shortMin), 1..60) { onChange(settings.copy(shortMin = it)) }
        Stepper(stringResource(R.string.phase_long_break), settings.longMin, stringResource(R.string.minutes_value, settings.longMin), 1..90) { onChange(settings.copy(longMin = it)) }
        Stepper(stringResource(R.string.sessions_before_long), settings.sessions, "${settings.sessions}", 1..12) { onChange(settings.copy(sessions = it)) }
    }
}

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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { TimerSettingsContent(TimerSettings(), {}) }
}
