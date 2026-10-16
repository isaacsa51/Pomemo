package com.serranoie.app.pomemo.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serranoie.app.pomemo.R
import com.serranoie.app.pomemo.alarm.AlarmReceiver
import com.serranoie.app.pomemo.alarm.AlarmScheduler
import com.serranoie.app.pomemo.alarm.Ringer
import com.serranoie.app.pomemo.data.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

enum class Phase(@StringRes val label: Int) {
    FOCUS(R.string.phase_focus), SHORT_BREAK(R.string.phase_short_break), LONG_BREAK(R.string.phase_long_break)
}

data class TimerSettings(
    val focusMin: Int = 25,
    val shortMin: Int = 5,
    val longMin: Int = 15,
    val sessions: Int = 4,
    /** Start the next phase on its own after a short full-screen countdown, instead of waiting on "Continue". */
    val autoStart: Boolean = false,
    /** Turn on Do Not Disturb (priority only, alarms still ring) while a focus phase runs. */
    val dnd: Boolean = false,
    /** Checklist note shown under the timer, ticked off while focusing. */
    val noteId: Long? = null,
) {
    fun lengthMs(p: Phase) = 60_000L * when (p) {
        Phase.FOCUS -> focusMin
        Phase.SHORT_BREAK -> shortMin
        Phase.LONG_BREAK -> longMin
    }
}

data class TimerState(
    val phase: Phase = Phase.FOCUS,
    val done: Int = 0,
    val endAt: Long? = null,
    val remainingMs: Long? = null,
) {
    val running get() = endAt != null
}

fun nextPhase(phase: Phase, done: Int, sessions: Int): Pair<Phase, Int> = when (phase) {
    Phase.FOCUS -> (done + 1).let { (if (it >= sessions) Phase.LONG_BREAK else Phase.SHORT_BREAK) to it }
    Phase.SHORT_BREAK -> Phase.FOCUS to done
    Phase.LONG_BREAK -> Phase.FOCUS to 0
}

fun setSegments(set: TimerSettings): List<Phase> = buildList {
    repeat(set.sessions) { i ->
        add(Phase.FOCUS)
        add(if (i < set.sessions - 1) Phase.SHORT_BREAK else Phase.LONG_BREAK)
    }
}

fun setElapsedBefore(s: TimerState, set: TimerSettings): Long {
    val focus = set.lengthMs(Phase.FOCUS)
    val short = set.lengthMs(Phase.SHORT_BREAK)
    return when (s.phase) {
        Phase.FOCUS -> s.done * (focus + short)
        Phase.SHORT_BREAK -> s.done * focus + (s.done - 1).coerceAtLeast(0) * short
        Phase.LONG_BREAK -> set.sessions * focus + (set.sessions - 1) * short
    }
}

fun formatMs(ms: Long): String = ceil(ms / 1000.0).toLong().let { "%d:%02d".format(it / 60, it % 60) }

private val Context.dataStore by preferencesDataStore("timer")

@Singleton
class PomodoroTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
    private val ringer: Ringer,
) {
    private val store = context.dataStore
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    val state: StateFlow<TimerState> = store.data.map { it.state() }.stateIn(scope, SharingStarted.Eagerly, TimerState())
    val settings: StateFlow<TimerSettings> = store.data.map { it.settings() }.stateIn(scope, SharingStarted.Eagerly, TimerSettings())

    private fun op(block: suspend (TimerState, TimerSettings) -> Unit): Job = scope.launch {
        lock.withLock { store.data.first().let { block(it.state(), it.settings()) } }
    }

    fun start() = op { s, set ->
        ringer.stop(RING_ID)
        scheduler.cancelAutoStart()
        if (s.running) return@op
        val endAt = System.currentTimeMillis() + (s.remainingMs ?: set.lengthMs(s.phase))
        save(s.copy(endAt = endAt, remainingMs = null), set)
        scheduler.setTimer(endAt)
    }

    fun pause() = op { s, set ->
        val endAt = s.endAt ?: return@op
        scheduler.cancelTimer()
        save(s.copy(endAt = null, remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0)), set)
    }

    fun reset() = op { _, set ->
        ringer.stop(RING_ID)
        scheduler.cancelTimer()
        scheduler.cancelAutoStart()
        save(TimerState(), set)
    }

    fun skip() = op { s, set ->
        ringer.stop(RING_ID)
        scheduler.cancelTimer()
        scheduler.cancelAutoStart()
        val (phase, done) = nextPhase(s.phase, s.done, set.sessions)
        save(TimerState(phase, done), set)
    }

    fun onPhaseEnd() = op { s, set ->
        if (!s.running) return@op
        val (phase, done) = nextPhase(s.phase, s.done, set.sessions)
        val title = context.getString(R.string.phase_done, context.getString(s.phase.label))
        val next = context.getString(phase.label)
        val minutes = (set.lengthMs(phase) / 60_000).toInt()
        save(TimerState(phase, done), set)
        // Auto-start: a short full-screen countdown, then the alarm below starts the phase even if nobody
        // is looking. "Start now" and "End period" both cancel it.
        val autoStartAt = if (set.autoStart) System.currentTimeMillis() + AUTO_START_DELAY_MS else null
        autoStartAt?.let(scheduler::setAutoStart)
        ringer.ringTimer(
            Reminder(id = RING_ID, title = title, note = context.getString(R.string.up_next, next, minutes), time = LocalTime.now()),
            context.getString(R.string.continue_to, next),
            autoStartAt,
        )
    }

    fun updateSettings(new: TimerSettings) = op { s, _ ->
        store.edit {
            it[FOCUS_MIN] = new.focusMin; it[SHORT_MIN] = new.shortMin
            it[LONG_MIN] = new.longMin; it[SESSIONS] = new.sessions
            it[AUTO_START] = new.autoStart
            it[DND] = new.dnd
            if (new.noteId != null) it[FOCUS_NOTE] = new.noteId else it.remove(FOCUS_NOTE)
        }
        notify(s, new)
        applyDnd(s, new)
    }

    private suspend fun save(s: TimerState, set: TimerSettings) {
        store.edit {
            it[PHASE] = s.phase.name
            it[DONE] = s.done
            if (s.endAt != null) it[END_AT] = s.endAt else it.remove(END_AT)
            if (s.remainingMs != null) it[REMAINING] = s.remainingMs else it.remove(REMAINING)
        }
        notify(s, set)
        applyDnd(s, set)
    }

    /**
     * Do Not Disturb follows the focus phase: on while focus runs, off as soon as it stops, pauses or
     * ends (so the phase-end alarm and breaks are never muted). Only takes over when DND is off, and
     * only hands back what it changed itself: a DND mode the user set is never touched. [DND_OWNED]
     * survives process death so a restart still restores it.
     */
    private suspend fun applyDnd(s: TimerState, set: TimerSettings) {
        val owned = store.data.first()[DND_OWNED] ?: false
        val want = set.dnd && s.running && s.phase == Phase.FOCUS
        if (want == owned) return
        if (!nm.isNotificationPolicyAccessGranted) {
            if (owned) store.edit { it.remove(DND_OWNED) }
            return
        }
        if (want) {
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            store.edit { it[DND_OWNED] = true }
        } else {
            // The user may have changed DND themselves meanwhile; then it's theirs.
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            store.edit { it.remove(DND_OWNED) }
        }
    }

    fun canControlDnd(): Boolean = nm.isNotificationPolicyAccessGranted

    fun refresh() = op { s, set -> if (s.running) notify(s, set) }

    private fun notify(s: TimerState, set: TimerSettings) {
        if (!s.running && s.remainingMs == null) {
            scheduler.cancelTimerTick()
            return nm.cancel(NOTIFICATION_ID)
        }
        nm.deleteNotificationChannel(CH_TIMER_OLD)
        // Promoted notifications can't live in a MIN/LOW channel; silence comes from setSilent instead.
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_DEFAULT))

        val now = System.currentTimeMillis()
        val phaseLength = set.lengthMs(s.phase)
        val remaining = s.endAt?.let { (it - now).coerceAtLeast(0) } ?: s.remainingMs ?: phaseLength
        val (next, _) = nextPhase(s.phase, s.done, set.sessions)

        val b = NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(s.phase.label))
            .setContentText(context.getString(R.string.up_next, context.getString(next.label), (set.lengthMs(next) / 60_000).toInt()))
            .setOngoing(true)
            .setSilent(true)
            .setRequestPromotedOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setStyle(liveBar(set, setElapsedBefore(s, set) + phaseLength - remaining))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
        if (s.endAt != null) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(s.endAt).setShowWhen(true)
                .addAction(0, context.getString(R.string.pause), AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_TIMER_PAUSE, "pomemo://timer"))
            val tick = now + TICK_MS
            if (tick < s.endAt) scheduler.setTimerTick(tick) else scheduler.cancelTimerTick()
        } else {
            b.setSubText(context.getString(R.string.timer_paused, formatMs(remaining)))
                .setShortCriticalText(formatMs(remaining))
                .addAction(0, context.getString(R.string.resume), AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_TIMER_START, "pomemo://timer"))
            scheduler.cancelTimerTick()
        }
        b.addAction(0, context.getString(R.string.reset), AlarmReceiver.broadcast(context, AlarmReceiver.ACTION_TIMER_RESET, "pomemo://timer"))
        nm.notify(NOTIFICATION_ID, b.build())
    }

    private fun liveBar(set: TimerSettings, progressMs: Long): NotificationCompat.ProgressStyle {
        val (focusColor, breakColor) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_500) to context.getColor(android.R.color.system_accent2_500)
        } else Notification.COLOR_DEFAULT to Notification.COLOR_DEFAULT
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(
                setSegments(set).map { phase ->
                    NotificationCompat.ProgressStyle.Segment(set.lengthMs(phase).toInt())
                        .setColor(if (phase == Phase.FOCUS) focusColor else breakColor)
                }
            )
            .setStyledByProgress(true)
            .setProgress(progressMs.toInt())
    }

    private fun Preferences.state() = TimerState(
        phase = this[PHASE]?.let(Phase::valueOf) ?: Phase.FOCUS,
        done = this[DONE] ?: 0,
        endAt = this[END_AT],
        remainingMs = this[REMAINING],
    )

    private fun Preferences.settings() = TimerSettings(
        focusMin = this[FOCUS_MIN] ?: 25,
        shortMin = this[SHORT_MIN] ?: 5,
        longMin = this[LONG_MIN] ?: 15,
        sessions = this[SESSIONS] ?: 4,
        autoStart = this[AUTO_START] ?: false,
        dnd = this[DND] ?: false,
        noteId = this[FOCUS_NOTE],
    )

    companion object {
        const val RING_ID = -1L
        const val AUTO_START_DELAY_MS = 5_000L
        private const val NOTIFICATION_ID = -2
        private const val CH_TIMER = "timer_live"
        private const val CH_TIMER_OLD = "timer"
        private const val TICK_MS = 60_000L

        private val PHASE = stringPreferencesKey("phase")
        private val DONE = intPreferencesKey("done")
        private val END_AT = longPreferencesKey("end_at")
        private val REMAINING = longPreferencesKey("remaining")
        private val FOCUS_MIN = intPreferencesKey("focus_min")
        private val SHORT_MIN = intPreferencesKey("short_min")
        private val LONG_MIN = intPreferencesKey("long_min")
        private val SESSIONS = intPreferencesKey("sessions")
        private val AUTO_START = booleanPreferencesKey("auto_start")
        private val DND = booleanPreferencesKey("dnd")
        private val DND_OWNED = booleanPreferencesKey("dnd_owned")
        private val FOCUS_NOTE = longPreferencesKey("focus_note")
    }
}
