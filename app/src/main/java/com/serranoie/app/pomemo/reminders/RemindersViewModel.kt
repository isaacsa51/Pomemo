package com.serranoie.app.pomemo.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.alarm.AlarmScheduler
import com.serranoie.app.pomemo.alarm.Ringer
import com.serranoie.app.pomemo.data.Reminder
import com.serranoie.app.pomemo.data.NoteDao
import com.serranoie.app.pomemo.data.ReminderDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Composer = free text plus manual overrides from the chips. Overrides win over what the parser
 * found, so typing more never undoes a time the user picked by hand.
 */
data class Composer(
    val editingId: Long = 0,
    val text: String = "",
    val note: String = "",
    val ring: Boolean = true,
    val time: LocalTime? = null,
    val days: Int? = null,
    val date: LocalDate? = null,
    val noteId: Long? = null,
    /** Carried through edits so saving doesn't undo a "skip today". */
    val skipDate: LocalDate? = null,
) {
    fun draft(): ReminderDraft {
        val parsed = ReminderParser.parse(text)
        val days = days ?: parsed.days
        return ReminderDraft(
            title = parsed.title,
            time = time ?: parsed.time,
            days = days,
            date = if (days != 0) null else date ?: parsed.date,
        )
    }
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderDao,
    private val scheduler: AlarmScheduler,
    private val ringer: Ringer,
    noteDao: NoteDao,
) : ViewModel() {
    val reminders = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = noteDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null = composer sheet closed. */
    private val _composer = MutableStateFlow<Composer?>(null)
    val composer = _composer.asStateFlow()

    fun newReminder(text: String = "") { _composer.value = Composer(text = text) }

    fun edit(r: Reminder) {
        _composer.value = Composer(r.id, r.title, r.note, r.ring, r.time, r.days, r.date, r.noteId, r.skipDate)
    }

    fun closeComposer() { _composer.value = null }

    fun updateComposer(change: Composer.() -> Composer) = _composer.update { it?.change() }

    fun save() {
        val c = _composer.value ?: return
        val d = c.draft()
        val time = d.time ?: return
        val date = if (d.days != 0) null else upcomingDate(d.date, time)
        val reminder = Reminder(
            id = c.editingId,
            title = d.title.ifBlank { context.getString(R.string.default_title) },
            note = c.note.trim(),
            time = time,
            days = d.days,
            date = date,
            ring = c.ring,
            skipDate = c.skipDate,
            noteId = c.noteId,
        )
        _composer.value = null
        viewModelScope.launch {
            val id = dao.upsert(reminder)
            // Upsert returns -1 when it updated an existing row.
            scheduler.schedule(reminder.copy(id = if (id == -1L) reminder.id else id))
        }
    }

    fun setEnabled(r: Reminder, enabled: Boolean) = viewModelScope.launch {
        val updated = r.copy(enabled = enabled, date = if (enabled && r.days == 0) upcomingDate(r.date, r.time) else r.date)
        dao.update(updated)
        scheduler.schedule(updated)
    }

    /** Quiet for today only; the reminder stays on and rings again on its next day. */
    fun skipToday(r: Reminder, skip: Boolean) = viewModelScope.launch {
        val updated = r.copy(skipDate = if (skip) LocalDate.now() else null)
        dao.update(updated)
        scheduler.schedule(updated)
    }

    fun delete(id: Long) = viewModelScope.launch {
        scheduler.cancel(id)
        ringer.stop(id)
        dao.get(id)?.let { dao.delete(it) }
        _composer.value = null
    }

    fun canScheduleExact() = scheduler.canScheduleExact()
    fun canUseFullScreen() = ringer.canUseFullScreen()
}
