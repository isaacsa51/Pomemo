package com.serranoie.app.pomemo.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroTimerTest {
    @Test fun fullSetOfFour() {
        var phase = Phase.FOCUS
        var done = 0
        val seen = mutableListOf<Phase>()
        repeat(9) {
            nextPhase(phase, done, 4).let { phase = it.first; done = it.second }
            seen += phase
        }
        val (F, S, L) = Triple(Phase.FOCUS, Phase.SHORT_BREAK, Phase.LONG_BREAK)
        assertEquals(listOf(S, F, S, F, S, F, L, F, S), seen)
    }

    @Test fun singleSessionAlwaysLongBreak() =
        assertEquals(Phase.LONG_BREAK to 1, nextPhase(Phase.FOCUS, 0, 1))

    @Test fun format() {
        assertEquals("25:00", formatMs(25 * 60_000L))
        assertEquals("25:00", formatMs(25 * 60_000L - 300)) // rounds up, so a fresh second never shows 24:59
        assertEquals("0:00", formatMs(0))
        assertEquals("0:01", formatMs(1))
    }
}

class SetProgressTest {
    private val set = TimerSettings() // 25 / 5 / 15, 4 sessions
    private val min = 60_000L

    @Test fun segmentsOfASet() = assertEquals(
        listOf(Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS, Phase.LONG_BREAK),
        setSegments(set),
    )

    @Test fun elapsedBeforeEachPhase() {
        assertEquals(0L, setElapsedBefore(TimerState(Phase.FOCUS, done = 0), set))
        assertEquals(25 * min, setElapsedBefore(TimerState(Phase.SHORT_BREAK, done = 1), set))
        assertEquals(30 * min, setElapsedBefore(TimerState(Phase.FOCUS, done = 1), set))
        assertEquals(115 * min, setElapsedBefore(TimerState(Phase.LONG_BREAK, done = 4), set))
    }

    @Test fun barEndsExactlyAtSetLength() =
        assertEquals(setSegments(set).sumOf { set.lengthMs(it) }, setElapsedBefore(TimerState(Phase.LONG_BREAK, done = 4), set) + set.lengthMs(Phase.LONG_BREAK))
}
