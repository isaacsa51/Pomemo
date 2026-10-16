package com.serranoie.app.pomemo.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serranoie.app.pomemo.alarm.AlarmSettings
import com.serranoie.app.pomemo.alarm.Ringer
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Backup
import com.serranoie.app.pomemo.data.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import com.serranoie.app.pomemo.data.NoteDao
import com.serranoie.app.pomemo.notes.toggleCheckbox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timer: PomodoroTimer,
    private val ringer: Ringer,
    private val noteDao: NoteDao,
    private val backup: Backup,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state = timer.state
    val settings = timer.settings
    val alarmSettings = ringer.settings
    val notes = noteDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The focus checklist; null when none is attached or it was deleted. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val focusNote: StateFlow<Note?> = settings.map { it.noteId }.distinctUntilChanged()
        .flatMapLatest { id -> id?.let(noteDao::observe) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ticks only while the screen collects it; nothing runs in the background. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val remainingMs: StateFlow<Long> = combine(state, settings, ::Pair)
        .flatMapLatest { (s, set) ->
            val endAt = s.endAt ?: return@flatMapLatest flowOf(s.remainingMs ?: set.lengthMs(s.phase))
            flow {
                while (true) {
                    emit((endAt - System.currentTimeMillis()).coerceAtLeast(0))
                    delay(100)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggle() { if (state.value.running) timer.pause() else timer.start() }
    fun skip() { timer.skip() }
    fun reset() { timer.reset() }
    fun updateSettings(new: TimerSettings) { timer.updateSettings(new) }
    fun updateAlarmSettings(new: AlarmSettings) { ringer.updateSettings(new) }
    fun attachNote(id: Long?) { timer.updateSettings(settings.value.copy(noteId = id)) }
    fun check(note: Note, bracket: Int) = viewModelScope.launch { noteDao.upsert(note.copy(body = toggleCheckbox(note.body, bracket))) }
    fun canControlDnd() = timer.canControlDnd()

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val result = runCatching { context.contentResolver.openOutputStream(uri)!!.use { backup.export(it) } }
        toast(result.fold({ context.getString(R.string.backup_exported, it.notes, it.reminders) }, { context.getString(R.string.backup_failed) }))
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val result = runCatching { context.contentResolver.openInputStream(uri)!!.use { backup.import(it) } }
        toast(result.fold({ context.getString(R.string.backup_imported, it.notes, it.reminders) }, { context.getString(R.string.backup_failed) }))
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
