package com.serranoie.app.pomemo.tiles

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.reminders.Composer
import com.serranoie.app.pomemo.reminders.RemindersViewModel
import com.serranoie.app.pomemo.reminders.describeSchedule
import com.serranoie.app.pomemo.reminders.timeFormat
import com.serranoie.app.pomemo.ui.HapticUtil
import com.serranoie.app.pomemo.ui.theme.PomemoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * From the Reminder tile: one sheet, one field, over a transparent window. The text goes through the
 * same parser as the in-app composer; saving shows "Take pill - Every day 7:00 PM" and closes.
 */
@AndroidEntryPoint
class QuickReminderActivity : ComponentActivity() {
    private val vm: RemindersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.newReminder()
        setContent {
            PomemoTheme {
                val composer by vm.composer.collectAsStateWithLifecycle()
                composer?.let { c ->
                    QuickReminderSheet(
                        composer = c,
                        onTextChange = { t -> vm.updateComposer { copy(text = t) } },
                        // Close only once it's stored: finishing first would cancel the save with the ViewModel.
                        onSave = { vm.save(::onSaved) },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    private fun onSaved(r: Reminder) {
        val text = getString(R.string.reminder_saved_toast, r.title, describeSchedule(this, r.days, r.date), r.time.format(timeFormat))
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        finish()
    }
}

@Composable
private fun QuickReminderSheet(composer: Composer, onTextChange: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val draft = remember(composer) { composer.draft() }
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canSave = draft.time != null
    val save = { if (canSave) { HapticUtil.performConfirmHaptic(view); onSave() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.tile_reminder_subtitle),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            // Live readback of what the parser understood, like the in-app composer.
            val summary = draft.time?.let {
                stringResource(
                    R.string.reminder_saved_toast,
                    draft.title.ifBlank { stringResource(R.string.default_title) },
                    describeSchedule(draft.days, draft.date),
                    it.format(timeFormat),
                )
            } ?: stringResource(R.string.add_time_to_save)
            OutlinedTextField(
                value = composer.text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.composer_placeholder)) },
                supportingText = { Text(summary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                TextButton(onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = save, shapes = ButtonDefaults.shapes(), enabled = canSave) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
