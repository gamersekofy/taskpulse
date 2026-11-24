package com.example.wear

import androidx.test.core.app.ApplicationProvider
import com.example.shared.SharedProject
import com.example.shared.SharedWorkItem
import com.example.shared.WearCommand
import com.example.shared.WearCommandType
import com.example.shared.WearSyncCodec
import com.example.shared.WearSyncSnapshot
import com.example.wear.data.WatchLocalStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearSyncCodecRobolectricTest {

    @Test
    fun snapshotAndCommandJsonRoundTrip() {
        val snapshot = WearSyncSnapshot(
            userId = "u123",
            userName = "Alex",
            userEmail = "alex@example.com",
            publishedAtMillis = 123456L,
            projects = listOf(
                SharedProject(
                    id = "p1",
                    userId = "u123",
                    name = "TaskPulse Wear",
                    description = "Watch companion",
                    colorKey = "INDIGO",
                    iconKey = "BOLT",
                    deadlineMillis = 999999L
                )
            ),
            workItems = listOf(
                SharedWorkItem(
                    id = "w1",
                    userId = "u123",
                    projectId = "p1",
                    title = "Pomodoro Sprint",
                    priority = "HIGH",
                    status = "IN_PROGRESS",
                    pomodoroWorkPeriods = 3L,
                    pomodoroWorkMinutes = 25L,
                    pomodoroBreakCount = 2L,
                    pomodoroBreakMinutes = 5L
                )
            )
        )

        val encoded = WearSyncCodec.encodeSnapshot(snapshot)
        val decoded = WearSyncCodec.decodeSnapshot(encoded)

        assertEquals("u123", decoded.userId)
        assertEquals(1, decoded.projects.size)
        assertEquals("TaskPulse Wear", decoded.projects[0].name)
        assertEquals(1, decoded.workItems.size)
        assertEquals(3L, decoded.workItems[0].effectiveWorkPeriods)

        val cmd = WearCommand(
            commandId = "cmd-1",
            type = WearCommandType.ADVANCE_PHASE,
            itemId = "w1",
            expectedPhase = "WORK",
            expectedPeriodIndex = 1L
        )
        val decodedCmd = WearSyncCodec.decodeCommand(WearSyncCodec.encodeCommand(cmd))
        assertNotNull(decodedCmd)
        assertEquals(WearCommandType.ADVANCE_PHASE, decodedCmd?.type)
        assertEquals("WORK", decodedCmd?.expectedPhase)
        assertEquals(1L, decodedCmd?.expectedPeriodIndex)
    }

    @Test
    fun localStorePersistsSnapshotAndCommandQueue() {
        val store = WatchLocalStore(ApplicationProvider.getApplicationContext())
        val cmd1 = WearCommand(commandId = "c1", type = WearCommandType.START_SESSION, itemId = "w1")
        val cmd2 = WearCommand(commandId = "c2", type = WearCommandType.PAUSE_SESSION, itemId = "w1")
        store.enqueueCommand(cmd1)
        store.enqueueCommand(cmd2)

        assertEquals(2, store.readPendingCommands().size)
        store.removeCommands(setOf("c1"))
        val remaining = store.readPendingCommands()
        assertEquals(1, remaining.size)
        assertEquals("c2", remaining[0].commandId)
    }
}
