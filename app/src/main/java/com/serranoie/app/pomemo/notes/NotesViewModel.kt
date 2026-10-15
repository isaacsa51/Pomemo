package com.serranoie.app.pomemo.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serranoie.app.pomemo.data.Note
import com.serranoie.app.pomemo.data.NoteDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The note open in the editor. [original] tells whether saving would change anything. */
data class NoteEditorState(val id: Long = 0, val value: TextFieldValue = TextFieldValue(), val original: String = "")

@HiltViewModel
class NotesViewModel @Inject constructor(private val dao: NoteDao) : ViewModel() {
    val notes = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null = editor closed. */
    private val _editor = MutableStateFlow<NoteEditorState?>(null)
    val editor = _editor.asStateFlow()

    fun newNote() { _editor.value = NoteEditorState() }

    fun open(note: Note) {
        _editor.value = NoteEditorState(note.id, TextFieldValue(note.body, TextRange(note.body.length)), note.body)
    }

    /** Ticks a checklist box straight from the card; the note keeps its place in the list. */
    fun check(note: Note, bracket: Int) = viewModelScope.launch {
        dao.upsert(note.copy(body = toggleCheckbox(note.body, bracket)))
    }

    fun update(value: TextFieldValue) = _editor.update { it?.copy(value = value) }

    /**
     * Save and close. Leaving never loses text (back, ✕ and Save all come here). An empty new note is
     * dropped; an existing note emptied out is deleted, like clearing a note in Keep.
     */
    fun close() {
        val e = _editor.value ?: return
        _editor.value = null
        val body = e.value.text.trimEnd()
        if (body == e.original) return
        viewModelScope.launch {
            when {
                body.isNotBlank() -> dao.upsert(Note(e.id, body, System.currentTimeMillis()))
                e.id != 0L -> dao.get(e.id)?.let { dao.delete(it) }
            }
        }
    }

    fun delete() {
        val e = _editor.value ?: return
        _editor.value = null
        if (e.id != 0L) viewModelScope.launch { dao.get(e.id)?.let { dao.delete(it) } }
    }
}
