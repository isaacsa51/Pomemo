package com.serranoie.app.pomemo.notes

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.data.Note
import com.serranoie.app.pomemo.ui.HapticUtil
import com.serranoie.app.pomemo.ui.theme.PomemoTheme

@Composable
fun NotesScreen(vm: NotesViewModel = hiltViewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    NotesContent(notes, onOpen = vm::open, onCheck = { n, i -> vm.check(n, i) })
    editor?.let { NoteEditor(it, onChange = vm::update, onSave = vm::close, onDelete = vm::delete) }
}

@Composable
fun NotesContent(notes: List<Note>, onOpen: (Note) -> Unit, onCheck: (Note, Int) -> Unit = { _, _ -> }) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = rememberMarkdownColors()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(title = { Text(stringResource(R.string.notes_title)) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(top= 8.dp),
        ) {
            if (notes.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.notes_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(notes, key = { it.id }) { note -> NoteCard(note, colors, onCheck = { onCheck(note, it) }) { onOpen(note) } }
        }
    }
}

@Composable
private fun NoteCard(note: Note, colors: MarkdownColors, onCheck: (Int) -> Unit, onClick: () -> Unit) {
    val view = LocalView.current
    // ☐/☑ are links: tapping one ticks it in place, tapping anywhere else opens the editor.
    val body = remember(note.body, colors) { highlightMarkdown(note.body, colors, rendered = true, onCheck = onCheck) }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.edit)) { HapticUtil.performUIHaptic(view); onClick() }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(body, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
            Text(
                DateUtils.getRelativeTimeSpanString(note.updatedAt).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NotesPreview() = PomemoTheme {
    val now = System.currentTimeMillis()
    NotesContent(
        listOf(
            Note(1, "# Markdown guide\nPress understands **bold**, _italic_, ~~strike~~ and `code`.", now),
            Note(2, "### Groceries\n- [ ] milk\n- [x] bread\n> remember the coupons", now - 3_600_000),
            Note(3, "Ana!!\nThis place looks absolutely incredible! Can't wait to catch up.", now - 86_400_000),
        ),
        onOpen = {},
    )
}
