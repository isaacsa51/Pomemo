package com.serranoie.app.pomemo.reminders

import java.time.LocalDateTime
import java.time.LocalDate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalView
import com.serranoie.app.pomemo.ui.HapticUtil
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.ui.theme.CondensedFlex
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Description
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Alarm-clock tile: tall condensed hour, minutes stacked over AM/PM beside it. Heavy and on
 * primaryContainer when on, thin on a neutral surface when off; the shape rounds further when on.
 */
@Composable
fun ReminderTile(r: Reminder, is24h: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onSkipToday: (Boolean) -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val container by animateColorAsState(
        if (r.enabled) cs.primaryContainer else cs.surfaceContainerHigh,
        label = "tileContainer"
    )
    val content by animateColorAsState(
        if (r.enabled) cs.onPrimaryContainer else cs.onSurfaceVariant,
        label = "tileContent"
    )
    val corner by animateDpAsState(if (r.enabled) 32.dp else 20.dp, spatial, label = "tileCorner")

    val locale = LocalConfiguration.current.locales[0]
    val (hh, mm, amPm) = remember(r.time, is24h, locale) {
        val f = { p: String -> r.time.format(DateTimeFormatter.ofPattern(p, locale)) }
        Triple(f(if (is24h) "HH" else "hh"), f("mm"), if (is24h) null else f("a"))
    }
    val kind = stringResource(if (r.ring) R.string.kind_alarm else R.string.kind_notification)
    val view = LocalView.current
    val weight = if (r.enabled) FontWeight.ExtraBold else FontWeight.ExtraLight
    fun digits(size: TextUnit) = TextStyle(
        fontFamily = CondensedFlex,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size,
        lineHeightStyle = LineHeightStyle(
            LineHeightStyle.Alignment.Center,
            LineHeightStyle.Trim.Both
        ),
    )

    val hourSize = 116.sp

    // Long-press offers "Skip today" while a recurring reminder still has a ring left today, and the undo once skipped.
    val today = LocalDate.now()
    val skipped = r.skipDate == today
    val canSkip = remember(r, today) {
        r.enabled && r.days != 0 && (skipped || nextTrigger(r, LocalDateTime.now())?.toLocalDate() == today)
    }
    val skipLabel = stringResource(if (skipped) R.string.unskip_today else R.string.skip_today)
    var menu by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides content) {
        Column(
            Modifier
                .clip(RoundedCornerShape(corner))
                .background(container)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.edit),
                    onLongClickLabel = skipLabel.takeIf { canSkip },
                    onLongClick = if (canSkip) ({ menu = true }) else null,
                ) { HapticUtil.performUIHaptic(view); onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fitWidth()
                    .height(IntrinsicSize.Min)
                    .clearAndSetSemantics { contentDescription = r.time.format(timeFormat) },
            ) {
                Text(
                    hh,
                    style = digits(hourSize),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.cropToBaseline()
                )
                Spacer(Modifier.width(6.dp))
                Column(
                    Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val minutes = if (amPm == null) hourSize else hourSize * 0.80f
                    Text(
                        mm,
                        style = digits(minutes),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.cropToBaseline()
                    )
                    if (amPm != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            amPm.uppercase(locale),
                            style = digits(hourSize * 0.24f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.cropToBaseline()
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                r.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val schedule = describeSchedule(r.days, r.date).let { if (skipped) stringResource(R.string.schedule_skipped, it) else it }
            if (r.days != 0) DayLetters(r.days, skipToday = if (skipped) today.dayOfWeek else null, description = schedule)
            else Text(
                schedule,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small tonal badges: how it rings, and whether a note opens with it.
                Badge(if (r.ring) Icons.Rounded.Alarm else Icons.Rounded.Notifications, kind, content)
                if (r.noteId != null) {
                    Spacer(Modifier.width(4.dp))
                    Badge(Icons.Rounded.Description, stringResource(R.string.attached_note), content)
                }
                Spacer(Modifier.weight(1f))
                Switch(
                    r.enabled,
                    { on ->
                        if (on) HapticUtil.performConfirmHaptic(view) else HapticUtil.performLightHaptic(view)
                        onToggle(on)
                    },
                    Modifier.semantics { contentDescription = r.title },
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(skipLabel) },
                    leadingIcon = { Icon(if (skipped) Icons.Rounded.Alarm else Icons.Rounded.EventBusy, contentDescription = null) },
                    onClick = {
                        menu = false
                        HapticUtil.performConfirmHaptic(view)
                        onSkipToday(!skipped)
                    },
                )
            }
        }
    }
}

/** M T W T F S S, the repeat days full strength and the rest faded; today's letter struck through when skipped. */
@Composable
private fun DayLetters(days: Int, skipToday: DayOfWeek?, description: String) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val on = days and day.bit() != 0
            Text(
                day.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal,
                    textDecoration = if (day == skipToday) TextDecoration.LineThrough else null,
                ),
                modifier = Modifier.alpha(if (on) 1f else 0.4f),
            )
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, label: String, tint: Color) = Box(
    Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
    contentAlignment = Alignment.Center,
) { Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp)) }

private fun Modifier.cropToBaseline() = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val baseline = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
    layout(p.width, baseline) { p.place(0, 0) }
}

private fun Modifier.fitWidth() = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val scale =
        if (constraints.hasBoundedWidth) min(1f, constraints.maxWidth.toFloat() / p.width) else 1f
    val w = (p.width * scale).roundToInt()
    val h = (p.height * scale).roundToInt()
    layout(w, h) {
        p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) { scaleX = scale; scaleY = scale }
    }
}

@PreviewLightDark
@Composable
private fun ReminderTilePreview() = com.serranoie.app.pomemo.ui.theme.PomemoTheme {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val t = { s: String -> java.time.LocalTime.parse(s) }
        Column(Modifier.weight(1f)) {
            ReminderTile(
                Reminder(
                    title = "Feed the cat",
                    time = t("07:30"),
                    days = ALL_DAYS
                ), false, {}, {})
        }
        Column(Modifier.weight(1f)) {
            ReminderTile(
                Reminder(
                    title = "Water plants",
                    time = t("12:30"),
                    days = WEEKDAYS,
                    ring = false,
                    enabled = false
                ), false, {}, {})
        }
    }
}
