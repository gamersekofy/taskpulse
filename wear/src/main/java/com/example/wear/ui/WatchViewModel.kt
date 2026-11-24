package com.example.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.SharedWorkItem
import com.example.wear.data.WatchDashboardData
import com.example.wear.data.WatchSyncCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = WatchSyncCoordinator.getInstance(application)

    val dashboardData: StateFlow<WatchDashboardData> = coordinator.dashboardData
    val events: SharedFlow<String> = coordinator.events

    val tickerMillis: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = System.currentTimeMillis()
    )

    fun requestSyncFromPhone() = coordinator.requestSyncFromPhone()

    fun startSession(item: SharedWorkItem) = coordinator.startSession(item)
    fun pauseSession(item: SharedWorkItem) = coordinator.pauseSession(item)
    fun advancePhase(item: SharedWorkItem) {
        coordinator.advancePhase(item)
    }
    fun completeSession(item: SharedWorkItem) = coordinator.completeSession(item)
    fun reopenWorkItem(item: SharedWorkItem) = coordinator.reopenWorkItem(item)
    fun postponeWorkItem(item: SharedWorkItem) = coordinator.postponeWorkItem(item)
    fun duplicateWorkItem(item: SharedWorkItem) = coordinator.duplicateWorkItem(item)
    fun updateProgress(item: SharedWorkItem, percent: Long) = coordinator.updateProgress(item, percent)
    fun deleteWorkItem(item: SharedWorkItem) = coordinator.deleteWorkItem(item)

    fun saveProject(
        existingProjectId: String?,
        name: String,
        description: String,
        colorKey: String,
        iconKey: String,
        deadlineMillis: Long
    ) = coordinator.saveProject(existingProjectId, name, description, colorKey, iconKey, deadlineMillis)

    fun deleteProject(projectId: String, childItems: List<SharedWorkItem>) {
        val project = dashboardData.value.projectFor(projectId) ?: return
        coordinator.deleteProject(project, childItems)
    }

    fun saveWorkItem(
        existingItem: SharedWorkItem?,
        projectId: String,
        title: String,
        notes: String,
        priorityKey: String,
        scheduledStartMillis: Long,
        pomodoroWorkMinutes: Long,
        pomodoroBreakMinutes: Long,
        pomodoroWorkPeriods: Long,
        pomodoroBreakCount: Long,
        deadlineMillis: Long
    ) = coordinator.saveWorkItem(
        existingItem = existingItem,
        projectId = projectId,
        title = title,
        notes = notes,
        priorityKey = priorityKey,
        scheduledStartMillis = scheduledStartMillis,
        pomodoroWorkMinutes = pomodoroWorkMinutes,
        pomodoroBreakMinutes = pomodoroBreakMinutes,
        pomodoroWorkPeriods = pomodoroWorkPeriods,
        pomodoroBreakCount = pomodoroBreakCount,
        deadlineMillis = deadlineMillis
    )

    fun onSignedOut() = coordinator.onDirectSignOut()
}
