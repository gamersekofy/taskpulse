package com.example.wear.data

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.shared.SharedPomodoroSnapshot
import com.example.shared.SharedProject
import com.example.shared.SharedWorkItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WatchProjectColor(
    val label: String,
    val accent: Color,
    val badgeBg: Color,
    val badgeInk: Color
) {
    CORAL("Coral", Color(0xFFFF896F), Color(0xFF5D1D0E), Color(0xFFFFDAD2)),
    INDIGO("Indigo", Color(0xFFBEC2FF), Color(0xFF282A8E), Color(0xFFE0E0FF)),
    MINT("Mint", Color(0xFF6EE7B7), Color(0xFF064830), Color(0xFFBFF5DF)),
    AMBER("Amber", Color(0xFFFBBF24), Color(0xFF4A3100), Color(0xFFFFE08A)),
    BERRY("Berry", Color(0xFFF472B6), Color(0xFF5A1637), Color(0xFFFFD8E9)),
    SKY("Sky", Color(0xFF38BDF8), Color(0xFF0B3B59), Color(0xFFCAEAFF));

    companion object {
        fun safeValueOf(raw: String): WatchProjectColor =
            entries.firstOrNull { it.name == raw } ?: CORAL
    }
}

enum class WatchProjectIcon(val label: String, val vector: ImageVector) {
    ROCKET("Launch", Icons.Rounded.RocketLaunch),
    PALETTE("Design", Icons.Rounded.Palette),
    CODE("Build", Icons.Rounded.Code),
    BOOK("Study", Icons.AutoMirrored.Rounded.MenuBook),
    BOLT("Sprint", Icons.Rounded.Bolt),
    HEART("Life", Icons.Rounded.Favorite);

    companion object {
        fun safeValueOf(raw: String): WatchProjectIcon =
            entries.firstOrNull { it.name == raw } ?: ROCKET
    }
}

enum class WatchWorkPriority(val label: String) {
    LOW("Chill"),
    MEDIUM("Focus"),
    HIGH("Urgent");

    companion object {
        fun safeValueOf(raw: String): WatchWorkPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class WatchWorkStatus(val label: String) {
    TODO("Planned"),
    IN_PROGRESS("In Session"),
    PAUSED("Paused"),
    DONE("Completed");

    companion object {
        fun safeValueOf(raw: String): WatchWorkStatus =
            entries.firstOrNull { it.name == raw } ?: TODO
    }
}

enum class SyncConnectionMode(val label: String, val shortLabel: String) {
    HYBRID_CLOUD_AND_PHONE("Cloud + Phone Sync", "Hybrid"),
    DIRECT_CLOUD("Direct Cloud Sync", "Cloud"),
    PHONE_BRIDGED("Paired Phone Sync", "Phone"),
    OFFLINE_QUEUED("Offline Queue", "Offline"),
    NEEDS_SETUP("Sign In or Pair Phone", "Unlinked")
}

@Keep
@IgnoreExtraProperties
data class FirestoreProjectDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val colorKey: String = "CORAL",
    val iconKey: String = "ROCKET",
    val deadlineMillis: Long = 0L,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toShared(): SharedProject = SharedProject(
        id = id,
        userId = userId,
        name = name,
        description = description,
        colorKey = colorKey,
        iconKey = iconKey,
        deadlineMillis = deadlineMillis,
        archived = archived,
        createdAtMillis = createdAt?.toDate()?.time ?: 0L,
        updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreWorkItemDoc(
    @DocumentId val id: String = "",
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
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toShared(): SharedWorkItem = SharedWorkItem(
        id = id,
        userId = userId,
        projectId = projectId,
        title = title,
        notes = notes,
        priority = priority,
        status = status,
        scheduledStartMillis = scheduledStartMillis,
        projectedDurationMinutes = projectedDurationMinutes,
        deadlineMillis = deadlineMillis,
        progressPercent = progressPercent,
        elapsedSeconds = elapsedSeconds,
        sessionStartedAtMillis = sessionStartedAtMillis,
        pomodoroWorkMinutes = pomodoroWorkMinutes,
        pomodoroBreakMinutes = pomodoroBreakMinutes,
        pomodoroWorkPeriods = pomodoroWorkPeriods,
        pomodoroBreakCount = pomodoroBreakCount,
        pomodoroPhase = pomodoroPhase,
        currentPeriodIndex = currentPeriodIndex,
        phaseElapsedSeconds = phaseElapsedSeconds,
        createdAtMillis = createdAt?.toDate()?.time ?: 0L,
        updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
    )
}

data class WatchProjectStats(
    val project: SharedProject,
    val items: List<SharedWorkItem>,
    val doneCount: Int,
    val totalCount: Int,
    val totalProjectedMinutes: Long,
    val completionFraction: Float
) {
    val accent: WatchProjectColor get() = WatchProjectColor.safeValueOf(project.colorKey)
    val icon: WatchProjectIcon get() = WatchProjectIcon.safeValueOf(project.iconKey)
}

data class WatchDashboardData(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val projects: List<SharedProject> = emptyList(),
    val workItems: List<SharedWorkItem> = emptyList(),
    val projectsWithStats: List<WatchProjectStats> = emptyList(),
    val activeRunningItem: SharedWorkItem? = null,
    val pausedItem: SharedWorkItem? = null,
    val syncMode: SyncConnectionMode = SyncConnectionMode.NEEDS_SETUP,
    val pendingQueueCount: Int = 0,
    val isPhoneConnected: Boolean = false,
    val isDirectCloudSignedIn: Boolean = false,
    val isSyncing: Boolean = false
) {
    val isReady: Boolean
        get() = isDirectCloudSignedIn || userId.isNotBlank() || projects.isNotEmpty() || workItems.isNotEmpty()

    fun projectFor(projectId: String): SharedProject? =
        projects.firstOrNull { it.id == projectId }

    fun projectNameFor(projectId: String): String =
        projectFor(projectId)?.name ?: "Project"
}

fun SharedWorkItem.accentColor(project: SharedProject?): WatchProjectColor =
    WatchProjectColor.safeValueOf(project?.colorKey ?: "CORAL")

fun SharedWorkItem.statusEnum(): WatchWorkStatus = WatchWorkStatus.safeValueOf(status)
fun SharedWorkItem.priorityEnum(): WatchWorkPriority = WatchWorkPriority.safeValueOf(priority)
fun SharedWorkItem.snapshot(nowMillis: Long): SharedPomodoroSnapshot = computePomodoroSnapshot(nowMillis)
