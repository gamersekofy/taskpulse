package com.example.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPomodoroEngineTest {

    @Test
    fun timelineInterleavesWorkAndBreaksRespectingBreakCount() {
        val slots = SharedPomodoroEngine.buildTimeline(
            effectiveWorkPeriods = 3L,
            effectiveWorkMinutes = 25L,
            effectiveBreakCount = 2L,
            effectiveBreakMinutes = 5L
        )
        assertEquals(5, slots.size)
        assertEquals(listOf("Work 1", "Break 1", "Work 2", "Break 2", "Work 3"), slots.map { it.label })
        assertTrue(slots[1].isBreak)
        assertTrue(!slots[4].isBreak)
    }

    @Test
    fun computeSnapshotTracksRemainingAndOvertime() {
        val start = 1_000_000L
        val item = SharedWorkItem(
            id = "w1",
            title = "Write Wear UI",
            status = "IN_PROGRESS",
            pomodoroWorkPeriods = 2L,
            pomodoroWorkMinutes = 25L,
            pomodoroBreakCount = 1L,
            pomodoroBreakMinutes = 5L,
            pomodoroPhase = "WORK",
            currentPeriodIndex = 1L,
            phaseElapsedSeconds = 0L,
            sessionStartedAtMillis = start
        )

        val midSnap = item.computePomodoroSnapshot(start + 10 * 60_000L)
        assertEquals("WORK", midSnap.currentPhase)
        assertEquals(1L, midSnap.currentPeriodIndex)
        assertEquals(900L, midSnap.phaseRemainingSeconds)
        assertEquals(0L, midSnap.overtimeSeconds)
        assertNotNull(midSnap.nextSlot)
        assertEquals("Break 1", midSnap.nextSlot?.label)

        val overSnap = item.computePomodoroSnapshot(start + 27 * 60_000L)
        assertEquals(0L, overSnap.phaseRemainingSeconds)
        assertEquals(120L, overSnap.overtimeSeconds)
    }

    @Test
    fun lastSlotHasNullNextSlot() {
        val snap = SharedPomodoroEngine.computeSnapshot(
            status = "IN_PROGRESS",
            pomodoroPhase = "WORK",
            currentPeriodIndex = 2L,
            effectiveWorkPeriods = 2L,
            effectiveWorkMinutes = 25L,
            effectiveBreakCount = 1L,
            effectiveBreakMinutes = 5L,
            phaseElapsedSeconds = 0L,
            sessionStartedAtMillis = 10_000L,
            nowMillis = 20_000L
        )
        assertNull(snap.nextSlot)
    }

    @Test
    fun liveChipFormattingFitsWearBadgeLimits() {
        assertEquals("25m", SharedTimeFormatting.formatLiveChipText(1500L, 0L, isPaused = false, isBreak = false))
        assertEquals("R 5m", SharedTimeFormatting.formatLiveChipText(300L, 0L, isPaused = false, isBreak = true))
        assertEquals("+3m", SharedTimeFormatting.formatLiveChipText(0L, 180L, isPaused = false, isBreak = false))
        assertEquals("Paused", SharedTimeFormatting.formatLiveChipText(600L, 0L, isPaused = true))
    }
}
