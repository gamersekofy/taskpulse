package com.example.widget

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import com.example.data.PomodoroPhase
import com.example.data.Project
import com.example.data.ProjectColor
import com.example.data.TaskPulseRepository
import com.example.data.WorkItem
import com.example.data.WorkStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.widgetSnapshotDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taskpulse_widget_snapshot"
)

data class WidgetFocusItemSnapshot(
    val id: String,
    val title: String,
    val projectName: String,
    val projectColorKey: String,
    val status: String,
    val phase: String,
    val currentPeriodIndex: Long,
    val totalWorkPeriods: Long,
    val totalBreakCount: Long,
    val phaseDurationSeconds: Long,
    val phaseElapsedSeconds: Long,
    val elapsedSeconds: Long,
    val sessionStartedAtMillis: Long,
    val deadlineMillis: Long,
    val overallFraction: Float,
    val totalMinutes: Long,
    val hasNextSlot: Boolean
) {
    val isRunning: Boolean
        get() = status == WorkStatus.IN_PROGRESS.name && sessionStartedAtMillis > 0L

    val isPaused: Boolean
        get() = status == WorkStatus.PAUSED.name

    val isBreak: Boolean
        get() = phase == PomodoroPhase.BREAK.name

    val projectColor: ProjectColor
        get() = ProjectColor.safeValueOf(projectColorKey)

    fun livePhaseRemainingSeconds(nowMillis: Long): Long {
        val delta = if (isRunning) ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L) else 0L
        val elapsed = (phaseElapsedSeconds.coerceAtLeast(0L) + delta).coerceAtMost(86400L)
        return (phaseDurationSeconds - elapsed).coerceAtLeast(0L)
    }

    fun liveOvertimeSeconds(nowMillis: Long): Long {
        val delta = if (isRunning) ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L) else 0L
        val elapsed = (phaseElapsedSeconds.coerceAtLeast(0L) + delta).coerceAtMost(86400L)
        return (elapsed - phaseDurationSeconds).coerceAtLeast(0L)
    }

    fun livePhaseFraction(nowMillis: Long): Float {
        if (phaseDurationSeconds <= 0L) return 0f
        val delta = if (isRunning) ((nowMillis - sessionStartedAtMillis) / 1000L).coerceAtLeast(0L) else 0L
        val elapsed = (phaseElapsedSeconds.coerceAtLeast(0L) + delta).coerceAtMost(86400L)
        return (elapsed.toFloat() / phaseDurationSeconds.toFloat()).coerceIn(0f, 1f)
    }
}

data class TaskPulseWidgetState(
    val isSignedIn: Boolean = false,
    val userName: String = "",
    val heroItem: WidgetFocusItemSnapshot? = null,
    val upNextItems: List<WidgetFocusItemSnapshot> = emptyList(),
    val activeCount: Int = 0,
    val doneCount: Int = 0,
    val updatedAtMillis: Long = 0L
)

object WidgetSnapshotCodec {

    fun buildState(
        isSignedIn: Boolean,
        userName: String,
        projects: List<Project>,
        workItems: List<WorkItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): TaskPulseWidgetState {
        if (!isSignedIn) {
            return TaskPulseWidgetState(isSignedIn = false, updatedAtMillis = nowMillis)
        }
        val projectMap = projects.associateBy { it.id }

        val active = workItems.firstOrNull { it.statusEnum == WorkStatus.IN_PROGRESS }
        val paused = if (active == null) {
            workItems.firstOrNull { it.statusEnum == WorkStatus.PAUSED }
        } else null
        val sortedPlanned = workItems
            .filter { it.statusEnum != WorkStatus.DONE && it.id != active?.id && it.id != paused?.id }
            .sortedWith(
                compareBy<WorkItem>(
                    { if (it.scheduledStartMillis > 0L) it.scheduledStartMillis else Long.MAX_VALUE },
                    { if (it.deadlineMillis > 0L) it.deadlineMillis else Long.MAX_VALUE }
                )
            )

        val heroSource = active ?: paused ?: sortedPlanned.firstOrNull()
        val heroSnapshot = heroSource?.let { toItemSnapshot(it, projectMap[it.projectId], nowMillis) }
        val remainingUpNext = sortedPlanned
            .filter { it.id != heroSource?.id }
            .take(4)
            .map { toItemSnapshot(it, projectMap[it.projectId], nowMillis) }

        val activeCount = workItems.count { it.statusEnum != WorkStatus.DONE }
        val doneCount = workItems.count { it.statusEnum == WorkStatus.DONE }

        return TaskPulseWidgetState(
            isSignedIn = true,
            userName = userName,
            heroItem = heroSnapshot,
            upNextItems = remainingUpNext,
            activeCount = activeCount,
            doneCount = doneCount,
            updatedAtMillis = nowMillis
        )
    }

    fun toItemSnapshot(
        item: WorkItem,
        project: Project?,
        nowMillis: Long
    ): WidgetFocusItemSnapshot {
        val snap = item.computePomodoroSnapshot(nowMillis)
        return WidgetFocusItemSnapshot(
            id = item.id,
            title = item.title,
            projectName = project?.name?.ifBlank { "TaskPulse" } ?: "TaskPulse",
            projectColorKey = project?.colorKey ?: ProjectColor.CORAL.name,
            status = item.statusEnum.name,
            phase = snap.currentPhase.name,
            currentPeriodIndex = snap.currentPeriodIndex,
            totalWorkPeriods = snap.totalWorkPeriods,
            totalBreakCount = snap.totalBreakCount,
            phaseDurationSeconds = snap.phaseDurationSeconds,
            phaseElapsedSeconds = item.phaseElapsedSeconds.coerceAtLeast(0L),
            elapsedSeconds = item.elapsedSeconds.coerceAtLeast(0L),
            sessionStartedAtMillis = item.sessionStartedAtMillis.coerceAtLeast(0L),
            deadlineMillis = item.deadlineMillis.coerceAtLeast(0L),
            overallFraction = item.liveProgressFraction(nowMillis),
            totalMinutes = item.totalPomodoroMinutes,
            hasNextSlot = snap.nextSlot != null
        )
    }

    fun encode(state: TaskPulseWidgetState): String {
        val root = JSONObject().apply {
            put("isSignedIn", state.isSignedIn)
            put("userName", state.userName)
            put("activeCount", state.activeCount)
            put("doneCount", state.doneCount)
            put("updatedAtMillis", state.updatedAtMillis)
            if (state.heroItem != null) {
                put("heroItem", encodeItem(state.heroItem))
            }
            val arr = JSONArray()
            state.upNextItems.forEach { arr.put(encodeItem(it)) }
            put("upNextItems", arr)
        }
        return root.toString()
    }

    fun decode(raw: String?): TaskPulseWidgetState {
        if (raw.isNullOrBlank()) return TaskPulseWidgetState()
        return try {
            val root = JSONObject(raw)
            val heroObj = root.optJSONObject("heroItem")
            val upNextArr = root.optJSONArray("upNextItems")
            val upNext = buildList {
                if (upNextArr != null) {
                    for (i in 0 until upNextArr.length()) {
                        upNextArr.optJSONObject(i)?.let { add(decodeItem(it)) }
                    }
                }
            }
            TaskPulseWidgetState(
                isSignedIn = root.optBoolean("isSignedIn", false),
                userName = root.optString("userName", ""),
                heroItem = heroObj?.let(::decodeItem),
                upNextItems = upNext,
                activeCount = root.optInt("activeCount", 0),
                doneCount = root.optInt("doneCount", 0),
                updatedAtMillis = root.optLong("updatedAtMillis", 0L)
            )
        } catch (_: Exception) {
            TaskPulseWidgetState()
        }
    }

    private fun encodeItem(item: WidgetFocusItemSnapshot): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("projectName", item.projectName)
        put("projectColorKey", item.projectColorKey)
        put("status", item.status)
        put("phase", item.phase)
        put("currentPeriodIndex", item.currentPeriodIndex)
        put("totalWorkPeriods", item.totalWorkPeriods)
        put("totalBreakCount", item.totalBreakCount)
        put("phaseDurationSeconds", item.phaseDurationSeconds)
        put("phaseElapsedSeconds", item.phaseElapsedSeconds)
        put("elapsedSeconds", item.elapsedSeconds)
        put("sessionStartedAtMillis", item.sessionStartedAtMillis)
        put("deadlineMillis", item.deadlineMillis)
        put("overallFraction", item.overallFraction.toDouble())
        put("totalMinutes", item.totalMinutes)
        put("hasNextSlot", item.hasNextSlot)
    }

    private fun decodeItem(obj: JSONObject): WidgetFocusItemSnapshot = WidgetFocusItemSnapshot(
        id = obj.optString("id", ""),
        title = obj.optString("title", ""),
        projectName = obj.optString("projectName", "TaskPulse"),
        projectColorKey = obj.optString("projectColorKey", ProjectColor.CORAL.name),
        status = obj.optString("status", WorkStatus.TODO.name),
        phase = obj.optString("phase", PomodoroPhase.WORK.name),
        currentPeriodIndex = obj.optLong("currentPeriodIndex", 1L),
        totalWorkPeriods = obj.optLong("totalWorkPeriods", 1L),
        totalBreakCount = obj.optLong("totalBreakCount", 0L),
        phaseDurationSeconds = obj.optLong("phaseDurationSeconds", 1500L),
        phaseElapsedSeconds = obj.optLong("phaseElapsedSeconds", 0L),
        elapsedSeconds = obj.optLong("elapsedSeconds", 0L),
        sessionStartedAtMillis = obj.optLong("sessionStartedAtMillis", 0L),
        deadlineMillis = obj.optLong("deadlineMillis", 0L),
        overallFraction = obj.optDouble("overallFraction", 0.0).toFloat().coerceIn(0f, 1f),
        totalMinutes = obj.optLong("totalMinutes", 25L),
        hasNextSlot = obj.optBoolean("hasNextSlot", false)
    )
}

object WidgetSnapshotStore {
    private const val TAG = "WidgetSnapshotStore"
    private val KEY_SNAPSHOT_JSON = stringPreferencesKey("snapshot_json")

    fun observeState(context: Context): Flow<TaskPulseWidgetState> =
        context.applicationContext.widgetSnapshotDataStore.data.map { prefs ->
            WidgetSnapshotCodec.decode(prefs[KEY_SNAPSHOT_JSON])
        }

    suspend fun readState(context: Context): TaskPulseWidgetState {
        val prefs = context.applicationContext.widgetSnapshotDataStore.data.first()
        return WidgetSnapshotCodec.decode(prefs[KEY_SNAPSHOT_JSON])
    }

    suspend fun syncFromDashboard(
        context: Context,
        userName: String,
        projects: List<Project>,
        workItems: List<WorkItem>
    ) {
        val appContext = context.applicationContext
        val state = WidgetSnapshotCodec.buildState(
            isSignedIn = true,
            userName = userName,
            projects = projects,
            workItems = workItems
        )
        writeAndRefreshIfChanged(appContext, state)
    }

    suspend fun syncFromActiveService(
        context: Context,
        activeItem: WorkItem,
        projectName: String
    ) {
        val appContext = context.applicationContext
        val existing = readState(appContext)
        val projectColorKey = existing.heroItem?.takeIf { it.id == activeItem.id }?.projectColorKey
            ?: existing.upNextItems.firstOrNull { it.id == activeItem.id }?.projectColorKey
            ?: ProjectColor.CORAL.name
        val now = System.currentTimeMillis()
        val snap = activeItem.computePomodoroSnapshot(now)
        val updatedHero = WidgetFocusItemSnapshot(
            id = activeItem.id,
            title = activeItem.title,
            projectName = projectName.ifBlank { "TaskPulse" },
            projectColorKey = projectColorKey,
            status = activeItem.statusEnum.name,
            phase = snap.currentPhase.name,
            currentPeriodIndex = snap.currentPeriodIndex,
            totalWorkPeriods = snap.totalWorkPeriods,
            totalBreakCount = snap.totalBreakCount,
            phaseDurationSeconds = snap.phaseDurationSeconds,
            phaseElapsedSeconds = activeItem.phaseElapsedSeconds.coerceAtLeast(0L),
            elapsedSeconds = activeItem.elapsedSeconds.coerceAtLeast(0L),
            sessionStartedAtMillis = activeItem.sessionStartedAtMillis.coerceAtLeast(0L),
            deadlineMillis = activeItem.deadlineMillis.coerceAtLeast(0L),
            overallFraction = activeItem.liveProgressFraction(now),
            totalMinutes = activeItem.totalPomodoroMinutes,
            hasNextSlot = snap.nextSlot != null
        )
        val updated = existing.copy(
            isSignedIn = true,
            heroItem = updatedHero,
            upNextItems = existing.upNextItems.filterNot { it.id == updatedHero.id },
            updatedAtMillis = now
        )
        writeAndRefreshIfChanged(appContext, updated)
    }

    suspend fun syncFromRepository(context: Context) {
        val appContext = context.applicationContext
        val user = Firebase.auth.currentUser
        if (user == null) {
            clearSignedOut(appContext)
            return
        }
        try {
            val repo = TaskPulseRepository(appContext)
            val projects = repo.fetchProjectsOnce(user.uid)
            val items = repo.fetchWorkItemsOnce(user.uid)
            val displayName = user.displayName ?: user.email ?: ""
            syncFromDashboard(appContext, displayName, projects, items)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to sync widget snapshot from Firestore: ${e.message}")
            TaskPulseGlanceWidget().updateAll(appContext)
        }
    }

    suspend fun clearSignedOut(context: Context) {
        val appContext = context.applicationContext
        writeAndRefreshIfChanged(appContext, TaskPulseWidgetState(isSignedIn = false))
    }

    private suspend fun writeAndRefreshIfChanged(appContext: Context, state: TaskPulseWidgetState) {
        val encoded = WidgetSnapshotCodec.encode(state)
        appContext.widgetSnapshotDataStore.edit { prefs ->
            prefs[KEY_SNAPSHOT_JSON] = encoded
        }
        try {
            TaskPulseGlanceWidget().updateAll(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh Glance widgets: ${e.message}")
        }
    }
}
