package com.serranoie.app.pomemo.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.timer.PomodoroTimer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Focus · Start pomodoro": starts the timer with the user's own settings (lengths, sessions,
 * auto-start), same as Start in the app. While it runs the tile is on and its subtitle names the
 * phase and its full length ("Focus · 25 min"), so a glance at the shade says what's going on.
 */
@AndroidEntryPoint
class FocusTileService : TileService() {
    @Inject lateinit var timer: PomodoroTimer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            combine(timer.state, timer.settings, ::Pair).collect { (s, set) ->
                if (s.running) {
                    val minutes = (set.lengthMs(s.phase) / 60_000).toInt()
                    render(Tile.STATE_ACTIVE, getString(R.string.tile_focus_running, getString(s.phase.label), minutes))
                } else {
                    render(Tile.STATE_INACTIVE, R.string.tile_focus_subtitle)
                }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Already running: nothing to start, and the tile already says so.
        if (!timer.state.value.running) timer.start()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
