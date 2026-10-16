package com.serranoie.app.pomemo.notes

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.TextRange
import com.serranoie.app.pomemo.R
import dagger.hilt.android.qualifiers.ApplicationContext
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
class NotesViewModel @Inject constructor(
    private val dao: NoteDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {
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

    /** Every keystroke; Enter on a list item continues the list. */
    fun update(value: TextFieldValue) = _editor.update { it?.copy(value = continueList(it.value, value) ?: value) }

    /** Copies the picked photo in and drops `![](…)` on its own line at the caret. */
    fun addImage(uri: Uri) = viewModelScope.launch {
        val source = NoteImages.import(context, uri)
        if (source == null) {
            Toast.makeText(context, R.string.note_image_failed, Toast.LENGTH_SHORT).show()
            return@launch
        }
        _editor.update { it?.copy(value = it.value.insertImage(source)) }
    }

    /** Images whose line was removed, or whose note is gone, are deleted after every save. */
    private suspend fun sweepImages() = NoteImages.sweep(context, dao.getAll().map { it.body })

    /**
     * Save and close. Leaving never loses text (back, ✕ and Save all come here). An empty new note is
     * dropped; an existing note emptied out is deleted, like clearing a note in Keep.
     */
    fun close() {
        val e = _editor.value ?: return
        _editor.value = null
        val body = e.value.text.trimEnd()
        if (body == e.original) {
            // Unchanged, but a picture may have been added and removed again.
            viewModelScope.launch { sweepImages() }
            return
        }
        viewModelScope.launch {
            when {
                body.isNotBlank() -> dao.upsert(Note(e.id, body, System.currentTimeMillis()))
                e.id != 0L -> dao.get(e.id)?.let { dao.delete(it) }
            }
            sweepImages()
        }
    }

    fun delete() {
        val e = _editor.value ?: return
        _editor.value = null
        viewModelScope.launch {
            if (e.id != 0L) dao.get(e.id)?.let { dao.delete(it) }
            sweepImages()
        }
    }
}
