package com.example.shared

data class SharedPomodoroSlot(
    val isBreak: Boolean,
    val index: Int,
    val durationMinutes: Long,
    val isCompleted: Boolean = false,
    val isActive: Boolean = false
) {
    val label: String
        get() = if (isBreak) "Break $index" else "Work $index"
}

data class SharedPomodoroSnapshot(
    val currentPhase: String,
    val currentPeriodIndex: Long,
    val totalWorkPeriods: Long,
    val totalBreakCount: Long,
    val phaseDurationSeconds: Long,
    val phaseElapsedSeconds: Long,
    val phaseRemainingSeconds: Long,
    val overtimeSeconds: Long,
    val phaseFraction: Float,
    val overallFraction: Float,
    val slots: List<SharedPomodoroSlot>,
    val activeSlotIndex: Int,
    val nextSlot: SharedPomodoroSlot?
)

data class SharedProject(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val colorKey: String = "CORAL",
    val iconKey: String = "ROCKET",
    val deadlineMillis: Long = 0L,
    val archived: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class SharedWorkItem(
    val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: String = "MEDIUM",
    val status: String = "TODO",
    val scheduledStartMillis: Long = 0L,
    val projectedDurationMinutes: Long = 50L,
    val deadlineMillis: Long = 0L,
    val progressPercent: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val sessionStartedAtMillis: Long = 0L,
    val pomodoroWorkMinutes: Long = 0L,
    val pomodoroBreakMinutes: Long = 5L,
    val pomodoroWorkPeriods: Long = 0L,
    val pomodoroBreakCount: Long = -1L,
    val pomodoroPhase: String = "WORK",
    val currentPeriodIndex: Long = 1L,
    val phaseElapsedSeconds: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val effectiveWorkPeriods: Long
        get() = if (pomodoroWorkPeriods > 0L) pomodoroWorkPeriods.coerceIn(1L, 12L) else 1L

    val effectiveWorkMinutes: Long
        get() = if (pomodoroWorkMinutes > 0L) {
            pomodoroWorkMinutes.coerceIn(1L, 120L)
        } else {
            projectedDurationMinutes.coerceIn(1L, 120L)
        }

    val effectiveBreakCount: Long
        get() = if (pomodoroBreakCount >= 0L) {
            pomodoroBreakCount.coerceIn(0L, effectiveWorkPeriods)
        } else {
            (effectiveWorkPeriods - 1L).coerceAtLeast(0L)
        }

    val effectiveBreakMinutes: Long
        get() = if (pomodoroBreakMinutes > 0L) pomodoroBreakMinutes.coerceIn(1L, 60L) else 5L

    val totalWorkMinutes: Long
        get() = (effectiveWorkPeriods * effectiveWorkMinutes).coerceIn(1L, 1440L)

    val totalBreakMinutes: Long
        get() = effectiveBreakCount * effectiveBreakMinutes

    val totalPomodoroMinutes: Long
        get() = totalWorkMinutes + totalBreakMinutes

    val projectedSeconds: Long
        get() = totalWorkMinutes * 60L

    fun computePomodoroSnapshot(nowMillis: Long): SharedPomodoroSnapshot =
        SharedPomodoroEngine.computeSnapshot(
            status = status,
            pomodoroPhase = pomodoroPhase,
            currentPeriodIndex = currentPeriodIndex,
            effectiveWorkPeriods = effectiveWorkPeriods,
            effectiveWorkMinutes = effectiveWorkMinutes,
            effectiveBreakCount = effectiveBreakCount,
            effectiveBreakMinutes = effectiveBreakMinutes,
            phaseElapsedSeconds = phaseElapsedSeconds,
            sessionStartedAtMillis = sessionStartedAtMillis,
            nowMillis = nowMillis
        )

    fun liveElapsedSeconds(nowMillis: Long): Long {
        if (status == "IN_PROGRESS" && sessionStartedAtMillis > 0L && pomodoroPhase == "WORK") {
            val delta = ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L)
            return (elapsedSeconds + delta).coerceAtMost(8_640_000L)
        }
        return elapsedSeconds.coerceAtLeast(0L)
    }

    fun liveProgressFraction(nowMillis: Long): Float {
        if (status == "DONE") return 1f
        val snap = computePomodoroSnapshot(nowMillis)
        val manualRatio = (progressPercent.coerceIn(0L, 100L) / 100f)
        return maxOf(snap.overallFraction, manualRatio).coerceIn(0f, 1f)
    }
}

object SharedPomodoroEngine {

    fun buildTimeline(
        effectiveWorkPeriods: Long,
        effectiveWorkMinutes: Long,
        effectiveBreakCount: Long,
        effectiveBreakMinutes: Long
    ): List<SharedPomodoroSlot> {
        val workCount = effectiveWorkPeriods.toInt().coerceIn(1, 12)
        val breakCount = effectiveBreakCount.toInt().coerceIn(0, workCount)
        val result = ArrayList<SharedPomodoroSlot>(workCount + breakCount)
        for (w in 1..workCount) {
            result += SharedPomodoroSlot(
                isBreak = false,
                index = w,
                durationMinutes = effectiveWorkMinutes.coerceIn(1L, 120L)
            )
            if (w <= breakCount) {
                result += SharedPomodoroSlot(
                    isBreak = true,
                    index = w,
                    durationMinutes = effectiveBreakMinutes.coerceIn(1L, 60L)
                )
            }
        }
        return result
    }

    fun computeSnapshot(
        status: String,
        pomodoroPhase: String,
        currentPeriodIndex: Long,
        effectiveWorkPeriods: Long,
        effectiveWorkMinutes: Long,
        effectiveBreakCount: Long,
        effectiveBreakMinutes: Long,
        phaseElapsedSeconds: Long,
        sessionStartedAtMillis: Long,
        nowMillis: Long
    ): SharedPomodoroSnapshot {
        val rawSlots = buildTimeline(
            effectiveWorkPeriods = effectiveWorkPeriods,
            effectiveWorkMinutes = effectiveWorkMinutes,
            effectiveBreakCount = effectiveBreakCount,
            effectiveBreakMinutes = effectiveBreakMinutes
        )
        val isDone = status == "DONE"
        val isRunning = status == "IN_PROGRESS" && sessionStartedAtMillis > 0L

        val desiredIsBreak = pomodoroPhase == "BREAK" && effectiveBreakCount > 0L
        val targetIndex = currentPeriodIndex.toInt().coerceAtLeast(1)
        var activeIdx = rawSlots.indexOfFirst { it.isBreak == desiredIsBreak && it.index == targetIndex }
        if (activeIdx < 0) {
            activeIdx = rawSlots.indexOfFirst { it.isBreak == desiredIsBreak }
        }
        if (activeIdx < 0) {
            activeIdx = 0
        }

        val activeSlot = rawSlots[activeIdx]
        val activePhase = if (activeSlot.isBreak) "BREAK" else "WORK"
        val phaseDurSec = (activeSlot.durationMinutes.coerceAtLeast(1L)) * 60L

        val deltaSec = if (isRunning) ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L) else 0L
        val curPhaseElapsed = (phaseElapsedSeconds.coerceAtLeast(0L) + deltaSec).coerceAtMost(86_400L)
        val curPhaseRemaining = (phaseDurSec - curPhaseElapsed).coerceAtLeast(0L)
        val overtime = (curPhaseElapsed - phaseDurSec).coerceAtLeast(0L)
        val curPhaseFraction = if (isDone) 1f else (curPhaseElapsed.toFloat() / phaseDurSec.toFloat()).coerceIn(0f, 1f)

        val statefulSlots = rawSlots.mapIndexed { idx, slot ->
            slot.copy(
                isCompleted = isDone || idx < activeIdx,
                isActive = !isDone && idx == activeIdx
            )
        }

        val totalTimelineSec = (rawSlots.sumOf { it.durationMinutes }.coerceAtLeast(1L)) * 60L
        val priorCompletedSec = rawSlots.take(activeIdx).sumOf { it.durationMinutes * 60L }
        val completedWithinActive = curPhaseElapsed.coerceAtMost(phaseDurSec)
        val timelineFraction = if (isDone) {
            1f
        } else {
            ((priorCompletedSec + completedWithinActive).toFloat() / totalTimelineSec.toFloat()).coerceIn(0f, 1f)
        }

        return SharedPomodoroSnapshot(
            currentPhase = activePhase,
            currentPeriodIndex = activeSlot.index.toLong(),
            totalWorkPeriods = effectiveWorkPeriods,
            totalBreakCount = effectiveBreakCount,
            phaseDurationSeconds = phaseDurSec,
            phaseElapsedSeconds = curPhaseElapsed,
            phaseRemainingSeconds = curPhaseRemaining,
            overtimeSeconds = overtime,
            phaseFraction = curPhaseFraction,
            overallFraction = timelineFraction,
            slots = statefulSlots,
            activeSlotIndex = activeIdx,
            nextSlot = rawSlots.getOrNull(activeIdx + 1)
        )
    }
}
