package com.serranoie.app.pomemo.reminders

import androidx.compose.ui.platform.LocalView
import com.serranoie.app.pomemo.ui.HapticUtil
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.ui.theme.PomemoTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Route: wires [RemindersViewModel] into the stateless [RemindersContent] and the composer sheet. */
@Composable
fun RemindersScreen(vm: RemindersViewModel = hiltViewModel()) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val composer by vm.composer.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    RemindersContent(
        reminders = reminders,
        onEdit = vm::edit,
        onToggle = { r, on -> vm.setEnabled(r, on) },
        onSkipToday = { r, skip -> vm.skipToday(r, skip) },
        canUseFullScreen = vm::canUseFullScreen,
        canScheduleExact = vm::canScheduleExact,
    )
    composer?.let { c ->
        ReminderComposerSheet(
            composer = c,
            notes = notes,
            onUpdate = vm::updateComposer,
            onSave = vm::save,
            onDelete = { vm.delete(c.editingId) },
            onClose = vm::closeComposer,
        )
    }
}

@Composable
fun RemindersContent(
    reminders: List<Reminder>,
    onEdit: (Reminder) -> Unit,
    onToggle: (Reminder, Boolean) -> Unit,
    canUseFullScreen: () -> Boolean,
    canScheduleExact: () -> Boolean,
    onSkipToday: (Reminder, Boolean) -> Unit = { _, _ -> },
) {
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                subtitle = { Text(stringResource(R.string.reminders_active, reminders.count { it.enabled })) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val is24h = DateFormat.is24HourFormat(LocalContext.current)
        // Adaptive columns: two tiles on a phone, more on tablets/landscape.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { PermissionsBanner(canUseFullScreen, canScheduleExact) }
            if (reminders.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.reminders_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(reminders, key = { it.id }) { r ->
                ReminderTile(r, is24h, onClick = { onEdit(r) }, onToggle = { onToggle(r, it) }, onSkipToday = { onSkipToday(r, it) })
            }
        }
    }
}

/** Reminders silently degrade without these, so say so up front. Re-checked on every resume. */
@Composable
private fun PermissionsBanner(canUseFullScreen: () -> Boolean, canScheduleExact: () -> Boolean) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose {} }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val canNotify = remember(refresh) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val canFullScreen = remember(refresh) { canUseFullScreen() }
    val canExact = remember(refresh) { canScheduleExact() }
    if (canNotify && canFullScreen && canExact) return

    val buttonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
    val view = LocalView.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (!canNotify) TextButton({ HapticUtil.performUIHaptic(view); askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }, shapes = ButtonDefaults.shapes(), colors = buttonColors) {
                Text(stringResource(R.string.allow_notifications))
            }
            if (!canFullScreen && Build.VERSION.SDK_INT >= 34) TextButton({
                HapticUtil.performUIHaptic(view)
                context.openAppSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }, shapes = ButtonDefaults.shapes(), colors = buttonColors) { Text(stringResource(R.string.allow_full_screen)) }
            if (!canExact && Build.VERSION.SDK_INT >= 31) TextButton({
                HapticUtil.performUIHaptic(view)
                context.openAppSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }, shapes = ButtonDefaults.shapes(), colors = buttonColors) { Text(stringResource(R.string.allow_exact)) }
        }
    }
}

private fun Context.openAppSettings(action: String) =
    startActivity(Intent(action, "package:$packageName".toUri()))

@PreviewLightDark
@Composable
private fun RemindersPreview() = PomemoTheme {
    val t = { s: String -> LocalTime.parse(s) }
    RemindersContent(
        reminders = listOf(
            Reminder(1, "Feed the cat", time = t("07:30"), days = ALL_DAYS),
            Reminder(2, "Water plants", time = t("12:30"), days = WEEKDAYS, ring = false, enabled = false),
            Reminder(3, "Stretch", time = t("19:00"), days = WEEKENDS, enabled = false),
            Reminder(4, "Call mom", time = t("20:30"), date = LocalDate.now().plusDays(1)),
        ),
        onEdit = {}, onToggle = { _, _ -> },
        canUseFullScreen = { true }, canScheduleExact = { true },
    )
}

@Preview(showBackground = true, locale = "es")
@Composable
private fun RemindersEmptyPreview() = PomemoTheme {
    RemindersContent(emptyList(), {}, { _, _ -> }, { false }, { true })
}
