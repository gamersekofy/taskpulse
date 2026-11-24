package com.example.wearsync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.Project
import com.example.data.ProjectColor
import com.example.data.ProjectIcon
import com.example.data.TaskPulseRepository
import com.example.data.WorkItem
import com.example.data.WorkPriority
import com.example.data.WorkStatus
import com.example.shared.SharedProject
import com.example.shared.SharedWorkItem
import com.example.shared.WearCommand
import com.example.shared.WearCommandType
import com.example.shared.WearSyncCodec
import com.example.shared.WearSyncContract
import com.example.shared.WearSyncSnapshot
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object PhoneWearSyncManager {
    private const val TAG = "PhoneWearSync"

    private fun isWearableApiAvailable(context: Context): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return status == ConnectionResult.SUCCESS
    }

    suspend fun publishSnapshot(
        context: Context,
        userId: String,
        userName: String,
        userEmail: String,
        projects: List<Project>,
        workItems: List<WorkItem>
    ) {
        if (!isWearableApiAvailable(context)) return
        try {
            val now = System.currentTimeMillis()
            val snapshot = WearSyncSnapshot(
                userId = userId,
                userName = userName,
                userEmail = userEmail,
                publishedAtMillis = now,
                projects = projects.map { it.toShared() },
                workItems = workItems.map { it.toShared() }
            )
            val json = WearSyncCodec.encodeSnapshot(snapshot)
            val request = PutDataMapRequest.create(WearSyncContract.PATH_SNAPSHOT).apply {
                dataMap.putString(WearSyncContract.KEY_SNAPSHOT_JSON, json)
                dataMap.putLong(WearSyncContract.KEY_PUBLISHED_AT, now)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context.applicationContext).putDataItem(request).await()
        } catch (e: Exception) {
            Log.d(TAG, "Wear snapshot publish skipped/failed: ${e.message}")
        }
    }

    suspend fun clearSignedOut(context: Context) {
        publishSnapshot(
            context = context,
            userId = "",
            userName = "",
            userEmail = "",
            projects = emptyList(),
            workItems = emptyList()
        )
    }

    suspend fun publishFromRepository(context: Context) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            clearSignedOut(context)
            return
        }
        try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            val repo = TaskPulseRepository(FirebaseFirestore.getInstance(dbId))
            val projects = repo.fetchProjectsOnce(user.uid)
            val workItems = repo.fetchWorkItemsOnce(user.uid)
            publishSnapshot(
                context = context,
                userId = user.uid,
                userName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty(),
                projects = projects,
                workItems = workItems
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish Wear snapshot from repository: ${e.message}")
        }
    }

    suspend fun handleIncomingCommand(context: Context, command: WearCommand) {
        val user = Firebase.auth.currentUser ?: return
        val appContext = context.applicationContext
        val dbId = appContext.getString(R.string.firestore_database_id)
        val repo = TaskPulseRepository(FirebaseFirestore.getInstance(dbId))

        if (command.type == WearCommandType.REQUEST_SYNC) {
            publishFromRepository(appContext)
            return
        }

        val items = runCatching { repo.fetchWorkItemsOnce(user.uid) }.getOrDefault(emptyList())
        val projects = runCatching { repo.fetchProjectsOnce(user.uid) }.getOrDefault(emptyList())
        val runningItem = items.firstOrNull { it.statusEnum == WorkStatus.IN_PROGRESS }
        val targetItem = items.firstOrNull { it.id == command.itemId }

        when (command.type) {
            WearCommandType.START_SESSION -> {
                if (targetItem != null) {
                    repo.startWorkSession(targetItem, runningItem)
                }
            }
            WearCommandType.PAUSE_SESSION -> {
                if (targetItem != null) {
                    repo.pauseWorkSession(targetItem)
                }
            }
            WearCommandType.ADVANCE_PHASE -> {
                if (targetItem != null) {
                    val phaseMatches = command.expectedPhase.isBlank() ||
                        (targetItem.phaseEnum.name == command.expectedPhase &&
                            (command.expectedPeriodIndex <= 0L || targetItem.currentPeriodIndex == command.expectedPeriodIndex))
                    if (phaseMatches) {
                        repo.advancePomodoroPhase(
                            item = targetItem,
                            autoContinue = targetItem.statusEnum == WorkStatus.IN_PROGRESS
                        )
                    }
                }
            }
            WearCommandType.COMPLETE_SESSION -> {
                if (targetItem != null) {
                    repo.completeWorkSession(targetItem)
                }
            }
            WearCommandType.REOPEN_ITEM -> {
                if (targetItem != null) {
                    repo.reopenWorkItem(targetItem)
                }
            }
            WearCommandType.POSTPONE_ITEM -> {
                if (targetItem != null) {
                    repo.postponeWorkItem(targetItem)
                }
            }
            WearCommandType.DUPLICATE_ITEM -> {
                if (targetItem != null) {
                    repo.duplicateWorkItem(targetItem)
                }
            }
            WearCommandType.DELETE_ITEM -> {
                if (command.itemId.isNotBlank()) {
                    repo.deleteWorkItem(command.itemId)
                }
            }
            WearCommandType.UPDATE_PROGRESS -> {
                if (targetItem != null) {
                    repo.updateProgressPercent(targetItem, command.progressPercent)
                }
            }
            WearCommandType.CREATE_PROJECT -> {
                repo.createProject(
                    name = command.name,
                    description = command.description,
                    color = ProjectColor.safeValueOf(command.colorKey),
                    icon = ProjectIcon.safeValueOf(command.iconKey),
                    deadlineMillis = command.deadlineMillis
                )
            }
            WearCommandType.UPDATE_PROJECT -> {
                if (command.projectId.isNotBlank()) {
                    repo.updateProject(
                        projectId = command.projectId,
                        name = command.name,
                        description = command.description,
                        color = ProjectColor.safeValueOf(command.colorKey),
                        icon = ProjectIcon.safeValueOf(command.iconKey),
                        deadlineMillis = command.deadlineMillis
                    )
                }
            }
            WearCommandType.DELETE_PROJECT -> {
                if (command.projectId.isNotBlank()) {
                    val childIds = items.filter { it.projectId == command.projectId }.map { it.id }
                    repo.deleteProjectAndWorkItems(command.projectId, childIds)
                }
            }
            WearCommandType.CREATE_WORK_ITEM -> {
                val resolvedProjectId = command.projectId.ifBlank { projects.firstOrNull()?.id.orEmpty() }
                if (resolvedProjectId.isNotBlank()) {
                    repo.createWorkItem(
                        projectId = resolvedProjectId,
                        title = command.title,
                        notes = command.notes,
                        priority = WorkPriority.safeValueOf(command.priority),
                        scheduledStartMillis = command.scheduledStartMillis,
                        pomodoroWorkMinutes = command.pomodoroWorkMinutes,
                        pomodoroBreakMinutes = command.pomodoroBreakMinutes,
                        pomodoroWorkPeriods = command.pomodoroWorkPeriods,
                        pomodoroBreakCount = command.pomodoroBreakCount,
                        deadlineMillis = command.deadlineMillis
                    )
                }
            }
            WearCommandType.UPDATE_WORK_ITEM -> {
                if (targetItem != null) {
                    repo.updateWorkItemDetails(
                        existingItem = targetItem,
                        projectId = command.projectId.ifBlank { targetItem.projectId },
                        title = command.title,
                        notes = command.notes,
                        priority = WorkPriority.safeValueOf(command.priority),
                        scheduledStartMillis = command.scheduledStartMillis,
                        pomodoroWorkMinutes = command.pomodoroWorkMinutes,
                        pomodoroBreakMinutes = command.pomodoroBreakMinutes,
                        pomodoroWorkPeriods = command.pomodoroWorkPeriods,
                        pomodoroBreakCount = command.pomodoroBreakCount,
                        deadlineMillis = command.deadlineMillis
                    )
                }
            }
            WearCommandType.REQUEST_SYNC -> Unit
        }

        publishFromRepository(appContext)
    }

    private fun Project.toShared(): SharedProject = SharedProject(
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

    private fun WorkItem.toShared(): SharedWorkItem = SharedWorkItem(
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
