package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PomodoroPhase
import com.example.data.Project
import com.example.data.ProjectColor
import com.example.data.ProjectIcon
import com.example.data.ProjectWithStats
import com.example.data.TaskPulseRepository
import com.example.data.UiState
import com.example.data.WorkItem
import com.example.data.WorkPriority
import com.example.data.WorkStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardData(
    val projects: List<Project>,
    val workItems: List<WorkItem>,
    val projectsWithStats: List<ProjectWithStats>,
    val activeRunningItem: WorkItem?,
    val pausedItem: WorkItem?
) {
    fun projectNameFor(projectId: String): String =
        projects.firstOrNull { it.id == projectId }?.name ?: "Project"

    fun projectFor(projectId: String): Project? =
        projects.firstOrNull { it.id == projectId }
}

class TaskPulseViewModel(
    private val repository: TaskPulseRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

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

    val dashboardState: StateFlow<UiState<DashboardData>> = combine(
        repository.observeProjects(currentUserId),
        repository.observeWorkItems(currentUserId)
    ) { projects, items ->
        val stats = projects.map { project ->
            val projectItems = items.filter { it.projectId == project.id }
            val doneCount = projectItems.count { it.statusEnum == WorkStatus.DONE }
            val totalCount = projectItems.size
            val totalProjected = projectItems.sumOf { it.totalWorkMinutes }
            val totalElapsed = projectItems.sumOf { it.elapsedSeconds }
            val completionFraction = if (totalCount == 0) {
                0f
            } else {
                projectItems.sumOf {
                    if (it.statusEnum == WorkStatus.DONE) 100L else it.progressPercent.coerceIn(0L, 100L)
                }.toFloat() / (totalCount * 100f)
            }
            ProjectWithStats(
                project = project,
                items = projectItems,
                doneCount = doneCount,
                totalCount = totalCount,
                totalProjectedMinutes = totalProjected,
                totalElapsedSeconds = totalElapsed,
                completionFraction = completionFraction.coerceIn(0f, 1f)
            )
        }

        val active = items.firstOrNull { it.statusEnum == WorkStatus.IN_PROGRESS }
        val paused = if (active == null) {
            items.firstOrNull { it.statusEnum == WorkStatus.PAUSED }
        } else null

        UiState.Success(
            DashboardData(
                projects = projects,
                workItems = items,
                projectsWithStats = stats,
                activeRunningItem = active,
                pausedItem = paused
            )
        ) as UiState<DashboardData>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing TaskPulse data", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load projects and tasks."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun saveProject(
        existingProjectId: String?,
        name: String,
        description: String,
        color: ProjectColor,
        icon: ProjectIcon,
        deadlineMillis: Long,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (existingProjectId.isNullOrBlank()) {
                repository.createProject(name, description, color, icon, deadlineMillis)
                    .onSuccess { newId ->
                        _events.tryEmit("Project \"$name\" created")
                        onComplete(newId)
                    }
                    .onFailure { e ->
                        _events.tryEmit(e.message ?: "Could not create project")
                        onComplete(null)
                    }
            } else {
                repository.updateProject(existingProjectId, name, description, color, icon, deadlineMillis)
                    .onSuccess {
                        _events.tryEmit("Project updated")
                        onComplete(existingProjectId)
                    }
                    .onFailure { e ->
                        _events.tryEmit(e.message ?: "Could not update project")
                        onComplete(null)
                    }
            }
        }
    }

    fun deleteProject(project: Project, childItems: List<WorkItem>) {
        viewModelScope.launch {
            repository.deleteProjectAndWorkItems(project.id, childItems.map { it.id })
                .onSuccess { _events.tryEmit("Deleted \"${project.name}\"") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Failed to delete project") }
        }
    }

    fun saveWorkItem(
        existingItem: WorkItem?,
        projectId: String,
        title: String,
        notes: String,
        priority: WorkPriority,
        scheduledStartMillis: Long,
        pomodoroWorkMinutes: Long,
        pomodoroBreakMinutes: Long,
        pomodoroWorkPeriods: Long,
        pomodoroBreakCount: Long,
        deadlineMillis: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (existingItem == null) {
                repository.createWorkItem(
                    projectId = projectId,
                    title = title,
                    notes = notes,
                    priority = priority,
                    scheduledStartMillis = scheduledStartMillis,
                    pomodoroWorkMinutes = pomodoroWorkMinutes,
                    pomodoroBreakMinutes = pomodoroBreakMinutes,
                    pomodoroWorkPeriods = pomodoroWorkPeriods,
                    pomodoroBreakCount = pomodoroBreakCount,
                    deadlineMillis = deadlineMillis
                ).onSuccess {
                    _events.tryEmit("Work item scheduled")
                    onComplete(true)
                }.onFailure { e ->
                    _events.tryEmit(e.message ?: "Could not add work item")
                    onComplete(false)
                }
            } else {
                repository.updateWorkItemDetails(
                    existingItem = existingItem,
                    projectId = projectId,
                    title = title,
                    notes = notes,
                    priority = priority,
                    scheduledStartMillis = scheduledStartMillis,
                    pomodoroWorkMinutes = pomodoroWorkMinutes,
                    pomodoroBreakMinutes = pomodoroBreakMinutes,
                    pomodoroWorkPeriods = pomodoroWorkPeriods,
                    pomodoroBreakCount = pomodoroBreakCount,
                    deadlineMillis = deadlineMillis
                ).onSuccess {
                    _events.tryEmit("Work item updated")
                    onComplete(true)
                }.onFailure { e ->
                    _events.tryEmit(e.message ?: "Could not update work item")
                    onComplete(false)
                }
            }
        }
    }

    fun startSession(targetItem: WorkItem, currentlyRunning: WorkItem?) {
        viewModelScope.launch {
            repository.startWorkSession(targetItem, currentlyRunning)
                .onSuccess {
                    val label = if (targetItem.phaseEnum == PomodoroPhase.BREAK && targetItem.statusEnum == WorkStatus.PAUSED) {
                        "Resumed break on \"${targetItem.title}\""
                    } else {
                        "Pomodoro started for \"${targetItem.title}\""
                    }
                    _events.tryEmit(label)
                }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not start session") }
        }
    }

    fun pauseSession(item: WorkItem) {
        viewModelScope.launch {
            repository.pauseWorkSession(item)
                .onSuccess { _events.tryEmit("Pomodoro paused") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not pause session") }
        }
    }

    fun advancePhase(item: WorkItem) {
        viewModelScope.launch {
            repository.advancePomodoroPhase(item, autoContinue = item.statusEnum == WorkStatus.IN_PROGRESS)
                .onSuccess { next ->
                    val msg = if (next == null) {
                        "Completed \"${item.title}\"!"
                    } else {
                        "Moved to ${next.label} (${next.durationMinutes}m)"
                    }
                    _events.tryEmit(msg)
                }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not switch period") }
        }
    }

    fun completeSession(item: WorkItem) {
        viewModelScope.launch {
            repository.completeWorkSession(item)
                .onSuccess { _events.tryEmit("Completed \"${item.title}\"!") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not complete work item") }
        }
    }

    fun reopenWorkItem(item: WorkItem) {
        viewModelScope.launch {
            repository.reopenWorkItem(item)
                .onSuccess { _events.tryEmit("Reopened \"${item.title}\"") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not reopen work item") }
        }
    }

    fun updateProgress(item: WorkItem, newPercent: Long) {
        viewModelScope.launch {
            repository.updateProgressPercent(item, newPercent)
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not update progress") }
        }
    }

    fun postponeWorkItem(item: WorkItem) {
        viewModelScope.launch {
            repository.postponeWorkItem(item)
                .onSuccess { _events.tryEmit("Moved \"${item.title}\" forward 1 day") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not postpone work item") }
        }
    }

    fun duplicateWorkItem(item: WorkItem) {
        viewModelScope.launch {
            repository.duplicateWorkItem(item)
                .onSuccess { _events.tryEmit("Duplicated \"${item.title}\"") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not duplicate work item") }
        }
    }

    fun deleteWorkItem(item: WorkItem) {
        viewModelScope.launch {
            repository.deleteWorkItem(item.id)
                .onSuccess { _events.tryEmit("Removed \"${item.title}\"") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Could not delete work item") }
        }
    }

    private companion object {
        const val TAG = "TaskPulseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
