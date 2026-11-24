package com.example.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.example.data.TaskPulseRepository
import com.example.data.WorkStatus
import com.example.liveupdates.WorkSessionForegroundService
import com.example.liveupdates.WorkSessionNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

val KEY_WIDGET_ITEM_ID = ActionParameters.Key<String>("widget_item_id")

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetSnapshotStore.syncFromRepository(context)
    }
}

class StartOrPauseWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetId = parameters[KEY_WIDGET_ITEM_ID].orEmpty()
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank() || targetId.isBlank()) {
            WidgetSnapshotStore.syncFromRepository(context)
            return
        }

        val repo = TaskPulseRepository(context)
        try {
            val items = repo.fetchWorkItemsOnce(uid)
            val projects = repo.fetchProjectsOnce(uid)
            val targetItem = items.firstOrNull { it.id == targetId }
            val currentlyRunning = items.firstOrNull { it.statusEnum == WorkStatus.IN_PROGRESS }

            if (targetItem != null) {
                if (targetItem.statusEnum == WorkStatus.IN_PROGRESS) {
                    repo.pauseWorkSession(targetItem)
                    WorkSessionForegroundService.stop(context)
                } else {
                    repo.startWorkSession(targetItem, currentlyRunning)
                    if (WorkSessionNotificationHelper.hasNotificationPermission(context)) {
                        val refreshed = repo.fetchWorkItemsOnce(uid).firstOrNull { it.id == targetId }
                        if (refreshed != null) {
                            val projName = projects.firstOrNull { it.id == refreshed.projectId }?.name.orEmpty()
                            try {
                                WorkSessionForegroundService.syncActiveSession(context, refreshed, projName)
                            } catch (e: Exception) {
                                Log.w("TaskPulseWidget", "FGS start deferred from background widget: ${e.message}")
                            }
                        }
                    }
                }
            }
            WidgetSnapshotStore.syncFromRepository(context)
        } catch (e: Exception) {
            Log.w("TaskPulseWidget", "Failed start/pause from widget: ${e.message}")
            TaskPulseGlanceWidget().updateAll(context)
        }
    }
}

class AdvancePhaseWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetId = parameters[KEY_WIDGET_ITEM_ID].orEmpty()
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank() || targetId.isBlank()) {
            WidgetSnapshotStore.syncFromRepository(context)
            return
        }

        val repo = TaskPulseRepository(context)
        try {
            val items = repo.fetchWorkItemsOnce(uid)
            val projects = repo.fetchProjectsOnce(uid)
            val targetItem = items.firstOrNull { it.id == targetId }
            if (targetItem != null) {
                val res = repo.advancePomodoroPhase(
                    item = targetItem,
                    autoContinue = targetItem.statusEnum == WorkStatus.IN_PROGRESS
                )
                val nextSlot = res.getOrNull()
                if (nextSlot == null || targetItem.statusEnum != WorkStatus.IN_PROGRESS) {
                    if (items.none { it.statusEnum == WorkStatus.IN_PROGRESS && it.id != targetId }) {
                        WorkSessionForegroundService.stop(context)
                    }
                } else if (WorkSessionNotificationHelper.hasNotificationPermission(context)) {
                    val refreshed = repo.fetchWorkItemsOnce(uid).firstOrNull { it.id == targetId }
                    if (refreshed != null) {
                        val projName = projects.firstOrNull { it.id == refreshed.projectId }?.name.orEmpty()
                        try {
                            WorkSessionForegroundService.syncActiveSession(context, refreshed, projName)
                        } catch (e: Exception) {
                            Log.w("TaskPulseWidget", "FGS sync deferred after phase advance: ${e.message}")
                        }
                    }
                }
            }
            WidgetSnapshotStore.syncFromRepository(context)
        } catch (e: Exception) {
            Log.w("TaskPulseWidget", "Failed advancing phase from widget: ${e.message}")
            TaskPulseGlanceWidget().updateAll(context)
        }
    }
}

class CompleteItemWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val targetId = parameters[KEY_WIDGET_ITEM_ID].orEmpty()
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank() || targetId.isBlank()) {
            WidgetSnapshotStore.syncFromRepository(context)
            return
        }

        val repo = TaskPulseRepository(context)
        try {
            val items = repo.fetchWorkItemsOnce(uid)
            val targetItem = items.firstOrNull { it.id == targetId }
            if (targetItem != null) {
                repo.completeWorkSession(targetItem)
                if (targetItem.statusEnum == WorkStatus.IN_PROGRESS) {
                    WorkSessionForegroundService.stop(context)
                }
            }
            WidgetSnapshotStore.syncFromRepository(context)
        } catch (e: Exception) {
            Log.w("TaskPulseWidget", "Failed completing item from widget: ${e.message}")
            TaskPulseGlanceWidget().updateAll(context)
        }
    }
}
