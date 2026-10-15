package com.serranoie.app.pomemo.ui

import com.serranoie.app.pomemo.notes.NotesViewModel
import com.serranoie.app.pomemo.notes.NotesScreen
import com.serranoie.app.pomemo.ui.navigation.NavFab
import com.serranoie.app.pomemo.reminders.RemindersViewModel
import com.serranoie.app.pomemo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serranoie.app.pomemo.reminders.RemindersScreen
import com.serranoie.app.pomemo.timer.Phase
import com.serranoie.app.pomemo.timer.TimerContent
import com.serranoie.app.pomemo.timer.TimerScreen
import com.serranoie.app.pomemo.timer.TimerSettings
import com.serranoie.app.pomemo.timer.TimerState
import com.serranoie.app.pomemo.timer.TimerViewModel
import com.serranoie.app.pomemo.ui.navigation.FloatingNavBar
import com.serranoie.app.pomemo.ui.theme.PomemoTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * App root. Like Essentials, the tabs are pages: swipe between them, or tap a tab and the pager
 * animates there. The nav highlight follows the target page so it moves as soon as a swipe commits.
 */
@Composable
fun AppScreen(
    timer: TimerViewModel = hiltViewModel(),
    reminders: RemindersViewModel = hiltViewModel(),
    notes: NotesViewModel = hiltViewModel(),
) {
    val state by timer.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // The tab tap already ticked; only a swipe that lands on a new page gets the settle tick.
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect {
            if (tapped) tapped = false else HapticUtil.performLightHaptic(view)
        }
    }

    // The toolbar's "+" follows the page; the timer keeps its controls on screen instead.
    val fab = when (pager.targetPage) {
        1 -> NavFab(Icons.Rounded.Add, stringResource(R.string.new_reminder), HapticUtil::performVirtualKeyHaptic) { reminders.newReminder() }
        2 -> NavFab(Icons.Rounded.Add, stringResource(R.string.new_note), HapticUtil::performVirtualKeyHaptic) { notes.newNote() }
        else -> null
    }

    AppScaffold(
        selected = pager.targetPage,
        phase = state.phase,
        fab = fab,
        onSelect = { page ->
            if (page != pager.currentPage) {
                tapped = true
                scope.launch { pager.animateScrollToPage(page) }
            }
        },
    ) {
        HorizontalPager(pager, beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> TimerScreen()
                1 -> RemindersScreen()
                else -> NotesScreen()
            }
        }
    }
}

private const val PAGES = 3

/** Stateless shell: floating nav bar + content. */
@Composable
fun AppScaffold(selected: Int, phase: Phase, fab: NavFab?, onSelect: (Int) -> Unit, content: @Composable () -> Unit) = Scaffold(
    // Screens draw their own top bars, so only the floating bar's height is padded here.
    contentWindowInsets = WindowInsets(0),
    bottomBar = { FloatingNavBar(selected, phase, fab, onSelect) },
) { padding ->
    Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
}

@Preview(showSystemUi = true)
@Composable
private fun AppScaffoldPreview() = PomemoTheme {
    AppScaffold(selected = 0, phase = Phase.FOCUS, fab = null, onSelect = {}) {
        TimerContent(TimerState(), TimerSettings(), TimerSettings().focusMin * 60_000L, {}, {}, {}, {})
    }
}
