package com.serranoie.app.pomemo.alarm

import androidx.compose.material.icons.rounded.Stop
import android.view.View
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.notes.highlightMarkdown
import com.serranoie.app.pomemo.notes.rememberMarkdownColors
import com.serranoie.app.pomemo.timer.PomodoroTimer
import com.serranoie.app.pomemo.ui.HapticUtil
import com.serranoie.app.pomemo.ui.theme.PomemoTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var ringer: Ringer
    @Inject lateinit var timer: PomodoroTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val reminder by ringer.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(reminder) { if (reminder == null) finishAndRemoveTask() }
            PomemoTheme {
                reminder?.let { r ->
                    if (r.id == PomodoroTimer.RING_ID) {
                        // Phase end: go on to the next phase, or stop the pomodoro. Both stop the ring.
                        AlarmScreen(
                            reminder = r,
                            primary = AlarmAction(ringer.continueLabel, Icons.Rounded.PlayArrow, HapticUtil::performConfirmHaptic) { timer.start() },
                            secondary = AlarmAction(stringResource(R.string.end_period), Icons.Rounded.Stop, HapticUtil::performRejectHaptic) { timer.reset() },
                        )
                    } else {
                        AlarmScreen(
                            reminder = r,
                            primary = AlarmAction(stringResource(R.string.dismiss), Icons.Rounded.AlarmOff, HapticUtil::performConfirmHaptic) { ringer.stop(r.id) },
                            secondary = AlarmAction(stringResource(R.string.snooze), Icons.Rounded.Snooze, HapticUtil::performUIHaptic) { ringer.snooze(r.id) },
                        )
                    }
                }
            }
        }
    }
}

/** One full-width alarm button: its text, icon, the haptic it plays and what it does. */
data class AlarmAction(val label: String, val icon: ImageVector, val haptic: (View) -> Unit, val onClick: () -> Unit)

/**
 * One hero (the time) on a primaryContainer surface; actions are full-width, Large-size buttons at
 * the bottom so they stay in thumb reach and never truncate at big font scales.
 */
@Composable
fun AlarmScreen(reminder: Reminder, primary: AlarmAction, secondary: AlarmAction) {
    val pane = stringResource(R.string.alarm_pane)
    val view = LocalView.current
    Surface(
        Modifier.fillMaxSize().semantics { paneTitle = pane },
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                    style = MaterialTheme.typography.displayLargeEmphasized,
                )
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (reminder.note.isNotBlank()) {
                    // Same highlighter as Notes: markdown in a reminder note shows styled.
                    val colors = rememberMarkdownColors()
                    Text(highlightMarkdown(reminder.note, colors, rendered = true), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
            val height = ButtonDefaults.LargeContainerHeight
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { primary.haptic(view); primary.onClick() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                ) { ButtonContent(primary.icon, primary.label) }
                FilledTonalButton(
                    onClick = { secondary.haptic(view); secondary.onClick() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                ) { ButtonContent(secondary.icon, secondary.label) }
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String) {
    val height = ButtonDefaults.LargeContainerHeight
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    Text(label, style = ButtonDefaults.textStyleFor(height))
}

@PreviewLightDark
@Composable
private fun AlarmScreenPreview() = PomemoTheme {
    AlarmScreen(
        Reminder(title = "Stretch", note = "Neck, shoulders, wrists", time = LocalTime.NOON),
        AlarmAction("Dismiss", Icons.Rounded.AlarmOff, {}) {},
        AlarmAction("Snooze", Icons.Rounded.Snooze, {}) {},
    )
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun AlarmScreenTimerLargeFontPreview() = PomemoTheme {
    AlarmScreen(
        Reminder(title = "Focus done", note = "Up next: Short break · 5 min", time = LocalTime.NOON),
        AlarmAction("Continue to Short break", Icons.Rounded.PlayArrow, {}) {},
        AlarmAction("End period", Icons.Rounded.Stop, {}) {},
    )
}
