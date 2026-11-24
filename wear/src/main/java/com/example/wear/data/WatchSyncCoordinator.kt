package com.example.wear.data

import android.content.Context
import android.util.Log
import com.example.shared.SharedPomodoroSlot
import com.example.shared.SharedProject
import com.example.shared.SharedWorkItem
import com.example.shared.WearCommand
import com.example.shared.WearCommandType
import com.example.shared.WearSyncCodec
import com.example.shared.WearSyncContract
import com.example.shared.WearSyncSnapshot
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WatchSyncCoordinator private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localStore = WatchLocalStore(appContext)
    private val firestoreRepo = WatchFirestoreRepository(appContext)
    private val auth: FirebaseAuth = Firebase.auth

    private val _dashboardData = MutableStateFlow(buildDashboardFromSnapshot(localStore.readSnapshot()))
    val dashboardData: StateFlow<WatchDashboardData> = _dashboardData.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var directFirestoreJob: Job? = null
    private var phoneNodeIds: Set<String> = emptySet()
    private var isDirectCloudActive: Boolean = false
    private var lastCloudSnapshot: WearSyncSnapshot? = null
    private var lastPhoneSnapshot: WearSyncSnapshot = localStore.readSnapshot()
    private var dataClientListener: DataClient.OnDataChangedListener? = null
    private var capabilityListener: CapabilityClient.OnCapabilityChangedListener? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            startDirectFirestore(user.uid, user.displayName.orEmpty(), user.email.orEmpty())
        } else {
            stopDirectFirestore()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        auth.currentUser?.let { u ->
            startDirectFirestore(u.uid, u.displayName.orEmpty(), u.email.orEmpty())
        }
        initializeWearableListeners()
    }

    private fun isWearApiAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    private fun initializeWearableListeners() {
        if (!isWearApiAvailable()) return
        scope.launch {
            refreshPhoneNodes()
            fetchInitialPhoneDataItem()
            flushPendingQueue()
        }

        val dataClient = Wearable.getDataClient(appContext)
        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearSyncContract.PATH_SNAPSHOT) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val rawJson = dataMap.getString(WearSyncContract.KEY_SNAPSHOT_JSON)
                    onPhoneSnapshotReceived(WearSyncCodec.decodeSnapshot(rawJson))
                }
            }
        }
        dataClient.addListener(listener)
        dataClientListener = listener

        val capClient = Wearable.getCapabilityClient(appContext)
        val capListener = CapabilityClient.OnCapabilityChangedListener { info ->
            phoneNodeIds = info.nodes.filter { it.isNearby }.map { it.id }.ifEmpty {
                info.nodes.map { it.id }
            }.toSet()
            recomputeDashboard()
            if (phoneNodeIds.isNotEmpty()) {
                scope.launch { flushPendingQueue() }
            }
        }
        capClient.addListener(capListener, WearSyncContract.CAPABILITY_PHONE_APP)
        capabilityListener = capListener
    }

    private suspend fun refreshPhoneNodes() {
        if (!isWearApiAvailable()) return
        try {
            val info = Wearable.getCapabilityClient(appContext)
                .getCapability(WearSyncContract.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                .await()
            val reachable = info.nodes.map { it.id }.toSet()
            if (reachable.isNotEmpty()) {
                phoneNodeIds = reachable
            } else {
                val connected = Wearable.getNodeClient(appContext).connectedNodes.await()
                phoneNodeIds = connected.map { it.id }.toSet()
            }
            recomputeDashboard()
        } catch (e: Exception) {
            Log.d(TAG, "refreshPhoneNodes skipped: ${e.message}")
        }
    }

    private suspend fun fetchInitialPhoneDataItem() {
        if (!isWearApiAvailable()) return
        try {
            val buffer = Wearable.getDataClient(appContext).dataItems.await()
            try {
                buffer.forEach { item ->
                    if (item.uri.path == WearSyncContract.PATH_SNAPSHOT) {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        val raw = map.getString(WearSyncContract.KEY_SNAPSHOT_JSON)
                        onPhoneSnapshotReceived(WearSyncCodec.decodeSnapshot(raw))
                    }
                }
            } finally {
                buffer.release()
            }
        } catch (e: Exception) {
            Log.d(TAG, "fetchInitialPhoneDataItem failed: ${e.message}")
        }
    }

    fun onPhoneSnapshotReceived(snapshot: WearSyncSnapshot) {
        lastPhoneSnapshot = snapshot
        localStore.saveSnapshot(snapshot)
        recomputeDashboard()
    }

    fun requestSyncFromPhone() {
        scope.launch {
            refreshPhoneNodes()
            fetchInitialPhoneDataItem()
            flushPendingQueue()
            sendMessageToPhone(WearSyncContract.PATH_REQUEST_SYNC, ByteArray(0))
        }
    }

    private fun startDirectFirestore(uid: String, displayName: String, email: String) {
        directFirestoreJob?.cancel()
        isDirectCloudActive = true
        recomputeDashboard()
        directFirestoreJob = scope.launch {
            combine(
                firestoreRepo.observeProjects(uid),
                firestoreRepo.observeWorkItems(uid)
            ) { projects, items ->
                WearSyncSnapshot(
                    userId = uid,
                    userName = displayName,
                    userEmail = email,
                    publishedAtMillis = System.currentTimeMillis(),
                    projects = projects,
                    workItems = items
                )
            }.catch { error ->
                Log.w(TAG, "Direct Firestore stream error: ${error.message}")
                isDirectCloudActive = false
                recomputeDashboard()
            }.collect { cloudSnap ->
                isDirectCloudActive = true
                lastCloudSnapshot = cloudSnap
                localStore.saveSnapshot(cloudSnap)
                recomputeDashboard()
            }
        }
    }

    private fun stopDirectFirestore() {
        directFirestoreJob?.cancel()
        directFirestoreJob = null
        isDirectCloudActive = false
        lastCloudSnapshot = null
        recomputeDashboard()
    }

    fun onDirectSignOut() {
        auth.signOut()
        stopDirectFirestore()
    }

    private fun recomputeDashboard() {
        val activeSnapshot = when {
            isDirectCloudActive && lastCloudSnapshot != null -> lastCloudSnapshot!!
            else -> lastPhoneSnapshot
        }
        _dashboardData.value = buildDashboardFromSnapshot(activeSnapshot)
    }

    private fun buildDashboardFromSnapshot(snap: WearSyncSnapshot): WatchDashboardData {
        val projects = snap.projects.filter { !it.archived }
        val items = snap.workItems
        val stats = projects.map { proj ->
            val projItems = items.filter { it.projectId == proj.id }
            val doneCount = projItems.count { it.status == WatchWorkStatus.DONE.name }
            val totalCount = projItems.size
            val totalProjected = projItems.sumOf { it.totalWorkMinutes }
            val completionFraction = if (totalCount == 0) {
                0f
            } else {
                projItems.sumOf {
                    if (it.status == WatchWorkStatus.DONE.name) 100L else it.progressPercent.coerceIn(0L, 100L)
                }.toFloat() / (totalCount * 100f)
            }
            WatchProjectStats(
                project = proj,
                items = projItems,
                doneCount = doneCount,
                totalCount = totalCount,
                totalProjectedMinutes = totalProjected,
                completionFraction = completionFraction.coerceIn(0f, 1f)
            )
        }

        val running = items.firstOrNull { it.status == WatchWorkStatus.IN_PROGRESS.name }
        val paused = if (running == null) items.firstOrNull { it.status == WatchWorkStatus.PAUSED.name } else null
        val queueCount = localStore.readPendingCommands().size
        val phoneConnected = phoneNodeIds.isNotEmpty()
        val directSignedIn = auth.currentUser != null

        val mode = when {
            directSignedIn && phoneConnected -> SyncConnectionMode.HYBRID_CLOUD_AND_PHONE
            directSignedIn -> SyncConnectionMode.DIRECT_CLOUD
            queueCount > 0 && !phoneConnected -> SyncConnectionMode.OFFLINE_QUEUED
            phoneConnected || snap.userId.isNotBlank() -> SyncConnectionMode.PHONE_BRIDGED
            else -> SyncConnectionMode.NEEDS_SETUP
        }

        return WatchDashboardData(
            userId = auth.currentUser?.uid ?: snap.userId,
            userName = auth.currentUser?.displayName ?: snap.userName,
            userEmail = auth.currentUser?.email ?: snap.userEmail,
            projects = projects,
            workItems = items,
            projectsWithStats = stats,
            activeRunningItem = running,
            pausedItem = paused,
            syncMode = mode,
            pendingQueueCount = queueCount,
            isPhoneConnected = phoneConnected,
            isDirectCloudSignedIn = directSignedIn,
            isSyncing = false
        )
    }

    // ---- Hybrid Mutations (Direct Firestore when signed in on Watch, else Phone Data Layer + Optimistic Mirror) ----

    fun startSession(target: SharedWorkItem) {
        val currentRunning = _dashboardData.value.activeRunningItem
        val now = System.currentTimeMillis()
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.startWorkSession(target, currentRunning, now)
                    .onSuccess { _events.tryEmit("Focus started") }
                    .onFailure { enqueueOrWarn(makeStartCommand(target.id), it.message) }
            } else {
                applyOptimisticStart(target.id, now)
                sendOrQueueCommand(makeStartCommand(target.id))
                _events.tryEmit("Focus started")
            }
        }
    }

    fun pauseSession(item: SharedWorkItem) {
        val now = System.currentTimeMillis()
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.pauseWorkSession(item, now)
                    .onSuccess { _events.tryEmit("Session paused") }
                    .onFailure { enqueueOrWarn(makePauseCommand(item.id), it.message) }
            } else {
                applyOptimisticPause(item.id, now)
                sendOrQueueCommand(makePauseCommand(item.id))
                _events.tryEmit("Session paused")
            }
        }
    }

    fun advancePhase(item: SharedWorkItem): Job = scope.launch {
        val now = System.currentTimeMillis()
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.ADVANCE_PHASE,
            itemId = item.id,
            expectedPhase = item.pomodoroPhase,
            expectedPeriodIndex = item.currentPeriodIndex
        )
        if (auth.currentUser != null) {
            firestoreRepo.advancePomodoroPhase(item, autoContinue = item.status == WatchWorkStatus.IN_PROGRESS.name, nowMillis = now)
                .onSuccess { next ->
                    _events.tryEmit(next?.let { "Moved to ${it.label}" } ?: "Work item completed!")
                }
                .onFailure { enqueueOrWarn(cmd, it.message) }
        } else {
            val next = applyOptimisticAdvance(item.id, now)
            sendOrQueueCommand(cmd)
            _events.tryEmit(next?.let { "Moved to ${it.label}" } ?: "Work item completed!")
        }
    }

    fun completeSession(item: SharedWorkItem) {
        val now = System.currentTimeMillis()
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.COMPLETE_SESSION,
            itemId = item.id
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.completeWorkSession(item, now)
                    .onSuccess { _events.tryEmit("Completed \"${item.title}\"") }
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                applyOptimisticComplete(item.id, now)
                sendOrQueueCommand(cmd)
                _events.tryEmit("Completed \"${item.title}\"")
            }
        }
    }

    fun reopenWorkItem(item: SharedWorkItem) {
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.REOPEN_ITEM,
            itemId = item.id
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.reopenWorkItem(item)
                    .onSuccess { _events.tryEmit("Reopened") }
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                updateLocalSnapshot { snap ->
                    snap.copy(workItems = snap.workItems.map {
                        if (it.id == item.id) it.copy(status = WatchWorkStatus.TODO.name, sessionStartedAtMillis = 0L, phaseElapsedSeconds = 0L, currentPeriodIndex = 1L, pomodoroPhase = "WORK") else it
                    })
                }
                sendOrQueueCommand(cmd)
                _events.tryEmit("Reopened")
            }
        }
    }

    fun postponeWorkItem(item: SharedWorkItem) {
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.POSTPONE_ITEM,
            itemId = item.id
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.postponeWorkItem(item)
                    .onSuccess { _events.tryEmit("Moved +1 day") }
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                val baseStart = if (item.scheduledStartMillis > 0L) item.scheduledStartMillis else System.currentTimeMillis()
                val newStart = baseStart + 86_400_000L
                val newDeadline = if (item.deadlineMillis > 0L) item.deadlineMillis + 86_400_000L else 0L
                updateLocalSnapshot { snap ->
                    snap.copy(workItems = snap.workItems.map {
                        if (it.id == item.id) it.copy(scheduledStartMillis = newStart, deadlineMillis = newDeadline) else it
                    })
                }
                sendOrQueueCommand(cmd)
                _events.tryEmit("Moved +1 day")
            }
        }
    }

    fun duplicateWorkItem(item: SharedWorkItem) {
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.DUPLICATE_ITEM,
            itemId = item.id
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.duplicateWorkItem(item)
                    .onSuccess { _events.tryEmit("Duplicated") }
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                sendOrQueueCommand(cmd)
                _events.tryEmit("Duplicating on phone…")
            }
        }
    }

    fun updateProgress(item: SharedWorkItem, newPercent: Long) {
        val clamped = newPercent.coerceIn(0L, 100L)
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.UPDATE_PROGRESS,
            itemId = item.id,
            progressPercent = clamped
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.updateProgressPercent(item, clamped)
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                updateLocalSnapshot { snap ->
                    snap.copy(workItems = snap.workItems.map {
                        if (it.id == item.id) {
                            it.copy(
                                progressPercent = clamped,
                                status = if (clamped >= 100L) WatchWorkStatus.DONE.name else it.status,
                                sessionStartedAtMillis = if (clamped >= 100L) 0L else it.sessionStartedAtMillis
                            )
                        } else it
                    })
                }
                sendOrQueueCommand(cmd)
            }
        }
    }

    fun deleteWorkItem(item: SharedWorkItem) {
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.DELETE_ITEM,
            itemId = item.id
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.deleteWorkItem(item.id)
                    .onSuccess { _events.tryEmit("Deleted") }
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                updateLocalSnapshot { snap ->
                    snap.copy(workItems = snap.workItems.filterNot { it.id == item.id })
                }
                sendOrQueueCommand(cmd)
                _events.tryEmit("Deleted")
            }
        }
    }

    fun saveProject(
        existingProjectId: String?,
        name: String,
        description: String,
        colorKey: String,
        iconKey: String,
        deadlineMillis: Long
    ) {
        scope.launch {
            if (auth.currentUser != null) {
                val res = if (existingProjectId.isNullOrBlank()) {
                    firestoreRepo.createProject(name, description, colorKey, iconKey, deadlineMillis).map { }
                } else {
                    firestoreRepo.updateProject(existingProjectId, name, description, colorKey, iconKey, deadlineMillis)
                }
                res.onSuccess { _events.tryEmit(if (existingProjectId.isNullOrBlank()) "Project created" else "Project updated") }
                    .onFailure { _events.tryEmit(it.message ?: "Save failed") }
            } else {
                val cmd = WearCommand(
                    commandId = UUID.randomUUID().toString(),
                    type = if (existingProjectId.isNullOrBlank()) WearCommandType.CREATE_PROJECT else WearCommandType.UPDATE_PROJECT,
                    projectId = existingProjectId.orEmpty(),
                    name = name,
                    description = description,
                    colorKey = colorKey,
                    iconKey = iconKey,
                    deadlineMillis = deadlineMillis
                )
                if (existingProjectId.isNullOrBlank()) {
                    val tempProj = SharedProject(
                        id = "pending_${UUID.randomUUID()}",
                        userId = _dashboardData.value.userId,
                        name = name,
                        description = description,
                        colorKey = colorKey,
                        iconKey = iconKey,
                        deadlineMillis = deadlineMillis,
                        createdAtMillis = System.currentTimeMillis()
                    )
                    updateLocalSnapshot { it.copy(projects = listOf(tempProj) + it.projects) }
                } else {
                    updateLocalSnapshot { snap ->
                        snap.copy(projects = snap.projects.map {
                            if (it.id == existingProjectId) it.copy(name = name, description = description, colorKey = colorKey, iconKey = iconKey, deadlineMillis = deadlineMillis) else it
                        })
                    }
                }
                sendOrQueueCommand(cmd)
                _events.tryEmit(if (existingProjectId.isNullOrBlank()) "Project created" else "Project updated")
            }
        }
    }

    fun deleteProject(project: SharedProject, childItems: List<SharedWorkItem>) {
        val cmd = WearCommand(
            commandId = UUID.randomUUID().toString(),
            type = WearCommandType.DELETE_PROJECT,
            projectId = project.id
        )
        scope.launch {
            if (auth.currentUser != null) {
                firestoreRepo.deleteProjectAndWorkItems(project.id, childItems.map { it.id })
                    .onSuccess { _events.tryEmit("Project deleted") }
                    .onFailure { enqueueOrWarn(cmd, it.message) }
            } else {
                updateLocalSnapshot { snap ->
                    snap.copy(
                        projects = snap.projects.filterNot { it.id == project.id },
                        workItems = snap.workItems.filterNot { it.projectId == project.id }
                    )
                }
                sendOrQueueCommand(cmd)
                _events.tryEmit("Project deleted")
            }
        }
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
    ) {
        scope.launch {
            if (auth.currentUser != null) {
                val res = if (existingItem == null) {
                    firestoreRepo.createWorkItem(
                        projectId, title, notes, priorityKey, scheduledStartMillis,
                        pomodoroWorkMinutes, pomodoroBreakMinutes, pomodoroWorkPeriods, pomodoroBreakCount, deadlineMillis
                    ).map { }
                } else {
                    firestoreRepo.updateWorkItemDetails(
                        existingItem, projectId, title, notes, priorityKey, scheduledStartMillis,
                        pomodoroWorkMinutes, pomodoroBreakMinutes, pomodoroWorkPeriods, pomodoroBreakCount, deadlineMillis
                    )
                }
                res.onSuccess { _events.tryEmit(if (existingItem == null) "Work item added" else "Work item updated") }
                    .onFailure { _events.tryEmit(it.message ?: "Save failed") }
            } else {
                val cmd = WearCommand(
                    commandId = UUID.randomUUID().toString(),
                    type = if (existingItem == null) WearCommandType.CREATE_WORK_ITEM else WearCommandType.UPDATE_WORK_ITEM,
                    itemId = existingItem?.id.orEmpty(),
                    projectId = projectId,
                    title = title,
                    notes = notes,
                    priority = priorityKey,
                    scheduledStartMillis = scheduledStartMillis,
                    deadlineMillis = deadlineMillis,
                    pomodoroWorkMinutes = pomodoroWorkMinutes,
                    pomodoroBreakMinutes = pomodoroBreakMinutes,
                    pomodoroWorkPeriods = pomodoroWorkPeriods,
                    pomodoroBreakCount = pomodoroBreakCount
                )
                val safeDuration = (pomodoroWorkPeriods.coerceIn(1L, 12L) * pomodoroWorkMinutes.coerceIn(1L, 120L)).coerceIn(1L, 1440L)
                if (existingItem == null) {
                    val tempItem = SharedWorkItem(
                        id = "pending_${UUID.randomUUID()}",
                        userId = _dashboardData.value.userId,
                        projectId = projectId,
                        title = title,
                        notes = notes,
                        priority = priorityKey,
                        status = WatchWorkStatus.TODO.name,
                        scheduledStartMillis = scheduledStartMillis,
                        projectedDurationMinutes = safeDuration,
                        deadlineMillis = deadlineMillis,
                        pomodoroWorkMinutes = pomodoroWorkMinutes,
                        pomodoroBreakMinutes = pomodoroBreakMinutes,
                        pomodoroWorkPeriods = pomodoroWorkPeriods,
                        pomodoroBreakCount = pomodoroBreakCount
                    )
                    updateLocalSnapshot { it.copy(workItems = it.workItems + tempItem) }
                } else {
                    updateLocalSnapshot { snap ->
                        snap.copy(workItems = snap.workItems.map {
                            if (it.id == existingItem.id) {
                                it.copy(
                                    projectId = projectId,
                                    title = title,
                                    notes = notes,
                                    priority = priorityKey,
                                    scheduledStartMillis = scheduledStartMillis,
                                    projectedDurationMinutes = safeDuration,
                                    deadlineMillis = deadlineMillis,
                                    pomodoroWorkMinutes = pomodoroWorkMinutes,
                                    pomodoroBreakMinutes = pomodoroBreakMinutes,
                                    pomodoroWorkPeriods = pomodoroWorkPeriods,
                                    pomodoroBreakCount = pomodoroBreakCount
                                )
                            } else it
                        })
                    }
                }
                sendOrQueueCommand(cmd)
                _events.tryEmit(if (existingItem == null) "Work item added" else "Work item updated")
            }
        }
    }

    private fun makeStartCommand(itemId: String) = WearCommand(
        commandId = UUID.randomUUID().toString(),
        type = WearCommandType.START_SESSION,
        itemId = itemId
    )

    private fun makePauseCommand(itemId: String) = WearCommand(
        commandId = UUID.randomUUID().toString(),
        type = WearCommandType.PAUSE_SESSION,
        itemId = itemId
    )

    private fun updateLocalSnapshot(transform: (WearSyncSnapshot) -> WearSyncSnapshot) {
        val updated = transform(lastPhoneSnapshot)
        lastPhoneSnapshot = updated
        localStore.saveSnapshot(updated)
        recomputeDashboard()
    }

    private fun applyOptimisticStart(itemId: String, now: Long) {
        updateLocalSnapshot { snap ->
            snap.copy(
                workItems = snap.workItems.map { item ->
                    when {
                        item.id == itemId -> {
                            val fresh = item.status == WatchWorkStatus.TODO.name
                            item.copy(
                                status = WatchWorkStatus.IN_PROGRESS.name,
                                pomodoroPhase = if (fresh) "WORK" else item.pomodoroPhase,
                                currentPeriodIndex = if (fresh) 1L else item.currentPeriodIndex,
                                phaseElapsedSeconds = if (fresh) 0L else item.phaseElapsedSeconds,
                                sessionStartedAtMillis = now
                            )
                        }
                        item.status == WatchWorkStatus.IN_PROGRESS.name -> {
                            val itemSnap = item.computePomodoroSnapshot(now)
                            item.copy(
                                status = WatchWorkStatus.PAUSED.name,
                                elapsedSeconds = item.liveElapsedSeconds(now),
                                phaseElapsedSeconds = itemSnap.phaseElapsedSeconds,
                                pomodoroPhase = itemSnap.currentPhase,
                                currentPeriodIndex = itemSnap.currentPeriodIndex,
                                sessionStartedAtMillis = 0L,
                                progressPercent = (itemSnap.overallFraction * 100f).toLong().coerceIn(item.progressPercent, 99L)
                            )
                        }
                        else -> item
                    }
                }
            )
        }
    }

    private fun applyOptimisticPause(itemId: String, now: Long) {
        updateLocalSnapshot { snap ->
            snap.copy(
                workItems = snap.workItems.map { item ->
                    if (item.id == itemId) {
                        val s = item.computePomodoroSnapshot(now)
                        item.copy(
                            status = WatchWorkStatus.PAUSED.name,
                            elapsedSeconds = item.liveElapsedSeconds(now),
                            phaseElapsedSeconds = s.phaseElapsedSeconds,
                            pomodoroPhase = s.currentPhase,
                            currentPeriodIndex = s.currentPeriodIndex,
                            sessionStartedAtMillis = 0L,
                            progressPercent = (s.overallFraction * 100f).toLong().coerceIn(item.progressPercent, 99L)
                        )
                    } else item
                }
            )
        }
    }

    private fun applyOptimisticAdvance(itemId: String, now: Long): SharedPomodoroSlot? {
        var nextResult: SharedPomodoroSlot? = null
        updateLocalSnapshot { snap ->
            snap.copy(
                workItems = snap.workItems.map { item ->
                    if (item.id == itemId) {
                        val pSnap = item.computePomodoroSnapshot(now)
                        val nextSlot = pSnap.nextSlot
                        nextResult = nextSlot
                        if (nextSlot == null) {
                            item.copy(
                                status = WatchWorkStatus.DONE.name,
                                progressPercent = 100L,
                                elapsedSeconds = item.liveElapsedSeconds(now),
                                phaseElapsedSeconds = 0L,
                                sessionStartedAtMillis = 0L
                            )
                        } else {
                            val keepRunning = item.status == WatchWorkStatus.IN_PROGRESS.name
                            item.copy(
                                status = if (keepRunning) WatchWorkStatus.IN_PROGRESS.name else WatchWorkStatus.PAUSED.name,
                                elapsedSeconds = item.liveElapsedSeconds(now),
                                progressPercent = ((pSnap.activeSlotIndex + 1).toFloat() / pSnap.slots.size * 100f).toLong().coerceIn(item.progressPercent, 99L),
                                pomodoroPhase = if (nextSlot.isBreak) "BREAK" else "WORK",
                                currentPeriodIndex = nextSlot.index.toLong(),
                                phaseElapsedSeconds = 0L,
                                sessionStartedAtMillis = if (keepRunning) now else 0L
                            )
                        }
                    } else item
                }
            )
        }
        return nextResult
    }

    private fun applyOptimisticComplete(itemId: String, now: Long) {
        updateLocalSnapshot { snap ->
            snap.copy(
                workItems = snap.workItems.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            status = WatchWorkStatus.DONE.name,
                            progressPercent = 100L,
                            elapsedSeconds = item.liveElapsedSeconds(now),
                            phaseElapsedSeconds = 0L,
                            sessionStartedAtMillis = 0L
                        )
                    } else item
                }
            )
        }
    }

    private suspend fun enqueueOrWarn(cmd: WearCommand, message: String?) {
        localStore.enqueueCommand(cmd)
        recomputeDashboard()
        _events.tryEmit(message ?: "Queued for sync")
    }

    private suspend fun sendOrQueueCommand(cmd: WearCommand) {
        val bytes = WearSyncCodec.encodeCommand(cmd).toByteArray(Charsets.UTF_8)
        val sent = sendMessageToPhone(WearSyncContract.PATH_COMMAND, bytes)
        if (!sent) {
            localStore.enqueueCommand(cmd)
            recomputeDashboard()
        }
    }

    private suspend fun flushPendingQueue() {
        val pending = localStore.readPendingCommands()
        if (pending.isEmpty()) return
        val succeeded = mutableSetOf<String>()
        for (cmd in pending) {
            val ok = sendMessageToPhone(
                WearSyncContract.PATH_COMMAND,
                WearSyncCodec.encodeCommand(cmd).toByteArray(Charsets.UTF_8)
            )
            if (ok) {
                succeeded += cmd.commandId
            } else {
                break
            }
        }
        if (succeeded.isNotEmpty()) {
            localStore.removeCommands(succeeded)
            recomputeDashboard()
        }
    }

    private suspend fun sendMessageToPhone(path: String, data: ByteArray): Boolean {
        if (!isWearApiAvailable()) return false
        refreshPhoneNodes()
        val nodes = phoneNodeIds
        if (nodes.isEmpty()) return false
        return try {
            val client = Wearable.getMessageClient(appContext)
            var anyOk = false
            for (nodeId in nodes) {
                runCatching { client.sendMessage(nodeId, path, data).await() }
                    .onSuccess { anyOk = true }
            }
            anyOk
        } catch (e: Exception) {
            Log.d(TAG, "sendMessageToPhone($path) failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "WatchSyncCoordinator"

        @Volatile
        private var instance: WatchSyncCoordinator? = null

        fun getInstance(context: Context): WatchSyncCoordinator {
            return instance ?: synchronized(this) {
                instance ?: WatchSyncCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }
}
