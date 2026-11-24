package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PomodoroPhase
import com.example.data.PomodoroSlotState
import com.example.data.WorkItem
import com.example.data.WorkStatus
import com.example.ui.TimeFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TaskPulse", appName)
    }

    @Test
    fun `pomodoro customizable periods and break timeline`() {
        val startMillis = 1_000_000L
        val item = WorkItem(
            id = "item_pomo",
            title = "Pomodoro feature verification",
            projectedDurationMinutes = 50L,
            pomodoroWorkMinutes = 25L,
            pomodoroBreakMinutes = 5L,
            pomodoroWorkPeriods = 2L,
            pomodoroBreakCount = 1L,
            pomodoroPhase = PomodoroPhase.WORK.name,
            currentPeriodIndex = 1L,
            phaseElapsedSeconds = 300L, // 5m into Work 1
            elapsedSeconds = 300L,
            sessionStartedAtMillis = startMillis,
            status = WorkStatus.IN_PROGRESS.name
        )

        // 10m later -> 15m elapsed in Work 1, 10m left in Work 1
        val tenMinLater = startMillis + 600_000L
        val snap1 = item.computePomodoroSnapshot(tenMinLater)
        assertEquals(PomodoroPhase.WORK, snap1.currentPhase)
        assertEquals(1L, snap1.currentPeriodIndex)
        assertEquals(600L, snap1.phaseRemainingSeconds)
        assertEquals(0L, snap1.overtimeSeconds)
        assertEquals(3, snap1.slots.size) // [Work 1, Break 1, Work 2]
        assertEquals(PomodoroSlotState.ACTIVE, snap1.slots[0].state)
        assertEquals(true, snap1.slots[1].isBreak)
        assertEquals("10m", TimeFormatting.formatLiveChipText(600L, 0L, isPaused = false, isBreak = false))

        // During Break 1
        val breakItem = item.copy(
            pomodoroPhase = PomodoroPhase.BREAK.name,
            currentPeriodIndex = 1L,
            phaseElapsedSeconds = 60L,
            sessionStartedAtMillis = startMillis
        )
        val snapBreak = breakItem.computePomodoroSnapshot(startMillis + 60_000L)
        assertEquals(PomodoroPhase.BREAK, snapBreak.currentPhase)
        assertEquals(180L, snapBreak.phaseRemainingSeconds)
        assertEquals(PomodoroSlotState.COMPLETED, snapBreak.slots[0].state)
        assertEquals(PomodoroSlotState.ACTIVE, snapBreak.slots[1].state)
        assertEquals("Work 2", snapBreak.nextSlot?.label)
        assertEquals("R 3m", TimeFormatting.formatLiveChipText(180L, 0L, isPaused = false, isBreak = true))
    }

    @Test
    fun `legacy work item without explicit pomodoro fields gracefully defaults`() {
        val legacyItem = WorkItem(
            id = "legacy_1",
            title = "Legacy v1 task",
            projectedDurationMinutes = 45L,
            status = WorkStatus.TODO.name
        )
        assertEquals(1L, legacyItem.effectiveWorkPeriods)
        assertEquals(45L, legacyItem.effectiveWorkMinutes)
        assertEquals(0L, legacyItem.effectiveBreakCount)
        val snap = legacyItem.computePomodoroSnapshot(1_000_000L)
        assertEquals(1, snap.slots.size)
        assertNull(snap.nextSlot)
        assertEquals(2700L, snap.phaseRemainingSeconds)
    }
}
