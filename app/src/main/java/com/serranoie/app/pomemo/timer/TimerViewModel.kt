package com.serranoie.app.pomemo.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serranoie.app.pomemo.alarm.AlarmSettings
import com.serranoie.app.pomemo.alarm.Ringer
import dagger.hilt.android.lifecycle.HiltViewModel
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
class TimerViewModel @Inject constructor(private val timer: PomodoroTimer, private val ringer: Ringer) : ViewModel() {
    val state = timer.state
    val settings = timer.settings
    val alarmSettings = ringer.settings

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
}
