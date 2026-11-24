package com.example.data

import androidx.annotation.Keep
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberUrgent
import com.example.ui.theme.BerryAccent
import com.example.ui.theme.BerryContainer
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkAmberContainer
import com.example.ui.theme.DarkAmberUrgent
import com.example.ui.theme.DarkBerryAccent
import com.example.ui.theme.DarkBerryContainer
import com.example.ui.theme.DarkCoralContainer
import com.example.ui.theme.DarkCoralPrimary
import com.example.ui.theme.DarkIndigoContainer
import com.example.ui.theme.DarkIndigoSecondary
import com.example.ui.theme.DarkMintContainer
import com.example.ui.theme.DarkMintTertiary
import com.example.ui.theme.DarkOnAmberContainer
import com.example.ui.theme.DarkOnBerryContainer
import com.example.ui.theme.DarkOnCoralContainer
import com.example.ui.theme.DarkOnIndigoContainer
import com.example.ui.theme.DarkOnMintContainer
import com.example.ui.theme.DarkOnSkyContainer
import com.example.ui.theme.DarkSkyAccent
import com.example.ui.theme.DarkSkyContainer
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoSecondary
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnBerryContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnIndigoContainer
import com.example.ui.theme.OnMintContainer
import com.example.ui.theme.OnSkyContainer
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkyContainer
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ProjectColor(
    val label: String,
    val solid: Color,
    val darkAccent: Color,
    val container: Color,
    val onContainer: Color,
    val darkContainer: Color,
    val darkOnContainer: Color
) {
    CORAL(
        label = "Coral",
        solid = CoralPrimary,
        darkAccent = DarkCoralPrimary,
        container = CoralContainer,
        onContainer = OnCoralContainer,
        darkContainer = DarkCoralContainer,
        darkOnContainer = DarkOnCoralContainer
    ),
    INDIGO(
        label = "Indigo",
        solid = IndigoSecondary,
        darkAccent = DarkIndigoSecondary,
        container = IndigoContainer,
        onContainer = OnIndigoContainer,
        darkContainer = DarkIndigoContainer,
        darkOnContainer = DarkOnIndigoContainer
    ),
    MINT(
        label = "Mint",
        solid = MintTertiary,
        darkAccent = DarkMintTertiary,
        container = MintContainer,
        onContainer = OnMintContainer,
        darkContainer = DarkMintContainer,
        darkOnContainer = DarkOnMintContainer
    ),
    AMBER(
        label = "Amber",
        solid = AmberUrgent,
        darkAccent = DarkAmberUrgent,
        container = AmberContainer,
        onContainer = OnAmberContainer,
        darkContainer = DarkAmberContainer,
        darkOnContainer = DarkOnAmberContainer
    ),
    BERRY(
        label = "Berry",
        solid = BerryAccent,
        darkAccent = DarkBerryAccent,
        container = BerryContainer,
        onContainer = OnBerryContainer,
        darkContainer = DarkBerryContainer,
        darkOnContainer = DarkOnBerryContainer
    ),
    SKY(
        label = "Sky",
        solid = SkyAccent,
        darkAccent = DarkSkyAccent,
        container = SkyContainer,
        onContainer = OnSkyContainer,
        darkContainer = DarkSkyContainer,
        darkOnContainer = DarkOnSkyContainer
    );

    @Composable
    fun vibrantColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
        if (darkTheme) darkAccent else solid

    @Composable
    fun onVibrantColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
        if (darkTheme) Color(0xFF13111C) else Color.White

    @Composable
    fun badgeContainerColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
        if (darkTheme) darkContainer else container

    @Composable
    fun badgeContentColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
        if (darkTheme) darkOnContainer else onContainer

    companion object {
        fun safeValueOf(raw: String): ProjectColor =
            entries.firstOrNull { it.name == raw } ?: CORAL
    }
}

enum class ProjectIcon(val label: String, val vector: ImageVector) {
    ROCKET("Launch", Icons.Rounded.RocketLaunch),
    PALETTE("Design", Icons.Rounded.Palette),
    CODE("Build", Icons.Rounded.Code),
    BOOK("Study", Icons.AutoMirrored.Rounded.MenuBook),
    BOLT("Sprint", Icons.Rounded.Bolt),
    HEART("Life", Icons.Rounded.Favorite);

    companion object {
        fun safeValueOf(raw: String): ProjectIcon =
            entries.firstOrNull { it.name == raw } ?: ROCKET
    }
}

enum class WorkPriority(val label: String) {
    LOW("Chill"),
    MEDIUM("Focus"),
    HIGH("Urgent");

    companion object {
        fun safeValueOf(raw: String): WorkPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class WorkStatus(val label: String) {
    TODO("Planned"),
    IN_PROGRESS("In Session"),
    PAUSED("Paused"),
    DONE("Completed");

    companion object {
        fun safeValueOf(raw: String): WorkStatus =
            entries.firstOrNull { it.name == raw } ?: TODO
    }
}

enum class PomodoroPhase(val label: String) {
    WORK("Work"),
    BREAK("Break");

    companion object {
        fun safeValueOf(raw: String): PomodoroPhase =
            entries.firstOrNull { it.name == raw } ?: WORK
    }
}

enum class PomodoroSlotState {
    COMPLETED,
    ACTIVE,
    UPCOMING
}

data class PomodoroSlot(
    val isBreak: Boolean,
    val index: Int,
    val durationMinutes: Long,
    val state: PomodoroSlotState
) {
    val label: String
        get() = if (isBreak) "Break $index" else "Work $index"
}

data class PomodoroSnapshot(
    val currentPhase: PomodoroPhase,
    val currentPeriodIndex: Long,
    val totalWorkPeriods: Long,
    val totalBreakCount: Long,
    val phaseDurationSeconds: Long,
    val phaseElapsedSeconds: Long,
    val phaseRemainingSeconds: Long,
    val overtimeSeconds: Long,
    val phaseFraction: Float,
    val overallFraction: Float,
    val slots: List<PomodoroSlot>,
    val activeSlotIndex: Int,
    val nextSlot: PomodoroSlot?
)

@Keep
@IgnoreExtraProperties
data class Project(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val colorKey: String = ProjectColor.CORAL.name,
    val iconKey: String = ProjectIcon.ROCKET.name,
    val deadlineMillis: Long = 0L,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val accent: ProjectColor get() = ProjectColor.safeValueOf(colorKey)
    val icon: ProjectIcon get() = ProjectIcon.safeValueOf(iconKey)
}

@Keep
@IgnoreExtraProperties
data class WorkItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: String = WorkPriority.MEDIUM.name,
    val status: String = WorkStatus.TODO.name,
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
    val pomodoroPhase: String = PomodoroPhase.WORK.name,
    val currentPeriodIndex: Long = 1L,
    val phaseElapsedSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val priorityEnum: WorkPriority get() = WorkPriority.safeValueOf(priority)
    val statusEnum: WorkStatus get() = WorkStatus.safeValueOf(status)
    val phaseEnum: PomodoroPhase get() = PomodoroPhase.safeValueOf(pomodoroPhase)

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

    fun buildPomodoroTimeline(): List<PomodoroSlot> {
        val workCount = effectiveWorkPeriods.toInt()
        val breakCount = effectiveBreakCount.toInt()
        val result = ArrayList<PomodoroSlot>(workCount + breakCount)
        for (w in 1..workCount) {
            result += PomodoroSlot(
                isBreak = false,
                index = w,
                durationMinutes = effectiveWorkMinutes,
                state = PomodoroSlotState.UPCOMING
            )
            if (w <= breakCount) {
                result += PomodoroSlot(
                    isBreak = true,
                    index = w,
                    durationMinutes = effectiveBreakMinutes,
                    state = PomodoroSlotState.UPCOMING
                )
            }
        }
        return result
    }

    fun computePomodoroSnapshot(nowMillis: Long): PomodoroSnapshot {
        val rawSlots = buildPomodoroTimeline()
        val isDone = statusEnum == WorkStatus.DONE
        val isRunning = statusEnum == WorkStatus.IN_PROGRESS && sessionStartedAtMillis > 0L

        val desiredIsBreak = phaseEnum == PomodoroPhase.BREAK && effectiveBreakCount > 0L
        val targetIndex = currentPeriodIndex.toInt().coerceAtLeast(1)
        var activeIdx = rawSlots.indexOfFirst { it.isBreak == desiredIsBreak && it.index == targetIndex }
        if (activeIdx < 0) {
            activeIdx = rawSlots.indexOfFirst { it.isBreak == desiredIsBreak }
        }
        if (activeIdx < 0) {
            activeIdx = 0
        }

        val activeSlot = rawSlots[activeIdx]
        val activePhase = if (activeSlot.isBreak) PomodoroPhase.BREAK else PomodoroPhase.WORK
        val phaseDurSec = (activeSlot.durationMinutes.coerceAtLeast(1L)) * 60L

        val deltaSec = if (isRunning) ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L) else 0L
        val curPhaseElapsed = (phaseElapsedSeconds.coerceAtLeast(0L) + deltaSec).coerceAtMost(86400L)
        val curPhaseRemaining = (phaseDurSec - curPhaseElapsed).coerceAtLeast(0L)
        val overtime = (curPhaseElapsed - phaseDurSec).coerceAtLeast(0L)
        val curPhaseFraction = if (isDone) 1f else (curPhaseElapsed.toFloat() / phaseDurSec.toFloat()).coerceIn(0f, 1f)

        val statefulSlots = rawSlots.mapIndexed { idx, slot ->
            val st = when {
                isDone -> PomodoroSlotState.COMPLETED
                idx < activeIdx -> PomodoroSlotState.COMPLETED
                idx == activeIdx -> PomodoroSlotState.ACTIVE
                else -> PomodoroSlotState.UPCOMING
            }
            slot.copy(state = st)
        }

        val totalTimelineSec = (rawSlots.sumOf { it.durationMinutes }.coerceAtLeast(1L)) * 60L
        val priorCompletedSec = rawSlots.take(activeIdx).sumOf { it.durationMinutes * 60L }
        val completedWithinActive = curPhaseElapsed.coerceAtMost(phaseDurSec)
        val timelineFraction = if (isDone) {
            1f
        } else {
            ((priorCompletedSec + completedWithinActive).toFloat() / totalTimelineSec.toFloat()).coerceIn(0f, 1f)
        }

        return PomodoroSnapshot(
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

    fun livePhaseElapsedSeconds(nowMillis: Long): Long =
        computePomodoroSnapshot(nowMillis).phaseElapsedSeconds

    fun liveElapsedSeconds(nowMillis: Long): Long {
        if (statusEnum == WorkStatus.IN_PROGRESS && sessionStartedAtMillis > 0L && phaseEnum == PomodoroPhase.WORK) {
            val delta = ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L)
            return (elapsedSeconds + delta).coerceAtMost(8640000L)
        }
        return elapsedSeconds.coerceAtLeast(0L)
    }

    fun remainingSessionSeconds(nowMillis: Long): Long =
        computePomodoroSnapshot(nowMillis).phaseRemainingSeconds

    fun overtimeSeconds(nowMillis: Long): Long =
        computePomodoroSnapshot(nowMillis).overtimeSeconds

    fun liveProgressFraction(nowMillis: Long): Float {
        if (statusEnum == WorkStatus.DONE) return 1f
        val snap = computePomodoroSnapshot(nowMillis)
        val manualRatio = (progressPercent.coerceIn(0L, 100L) / 100f)
        return maxOf(snap.overallFraction, manualRatio).coerceIn(0f, 1f)
    }
}

data class ProjectWithStats(
    val project: Project,
    val items: List<WorkItem>,
    val doneCount: Int,
    val totalCount: Int,
    val totalProjectedMinutes: Long,
    val totalElapsedSeconds: Long,
    val completionFraction: Float
)
