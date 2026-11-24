package com.example.shared

import org.json.JSONArray
import org.json.JSONObject

object WearSyncContract {
    const val CAPABILITY_PHONE_APP = "taskpulse_phone_app"
    const val CAPABILITY_WEAR_APP = "taskpulse_wear_app"

    const val PATH_SNAPSHOT = "/taskpulse/snapshot"
    const val PATH_COMMAND = "/taskpulse/command"
    const val PATH_REQUEST_SYNC = "/taskpulse/request_sync"

    const val KEY_SNAPSHOT_JSON = "snapshot_json"
    const val KEY_PUBLISHED_AT = "published_at"
}

enum class WearCommandType {
    START_SESSION,
    PAUSE_SESSION,
    ADVANCE_PHASE,
    COMPLETE_SESSION,
    REOPEN_ITEM,
    POSTPONE_ITEM,
    DUPLICATE_ITEM,
    DELETE_ITEM,
    UPDATE_PROGRESS,
    CREATE_PROJECT,
    UPDATE_PROJECT,
    DELETE_PROJECT,
    CREATE_WORK_ITEM,
    UPDATE_WORK_ITEM,
    REQUEST_SYNC
}

data class WearSyncSnapshot(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val publishedAtMillis: Long = 0L,
    val projects: List<SharedProject> = emptyList(),
    val workItems: List<SharedWorkItem> = emptyList()
)

data class WearCommand(
    val commandId: String,
    val type: WearCommandType,
    val issuedAtMillis: Long = System.currentTimeMillis(),
    val itemId: String = "",
    val projectId: String = "",
    val expectedPhase: String = "",
    val expectedPeriodIndex: Long = 0L,
    val progressPercent: Long = 0L,
    val name: String = "",
    val description: String = "",
    val colorKey: String = "CORAL",
    val iconKey: String = "ROCKET",
    val title: String = "",
    val notes: String = "",
    val priority: String = "MEDIUM",
    val scheduledStartMillis: Long = 0L,
    val deadlineMillis: Long = 0L,
    val pomodoroWorkMinutes: Long = 25L,
    val pomodoroBreakMinutes: Long = 5L,
    val pomodoroWorkPeriods: Long = 1L,
    val pomodoroBreakCount: Long = 0L
)

object WearSyncCodec {

    fun encodeSnapshot(snapshot: WearSyncSnapshot): String {
        val root = JSONObject().apply {
            put("userId", snapshot.userId)
            put("userName", snapshot.userName)
            put("userEmail", snapshot.userEmail)
            put("publishedAtMillis", snapshot.publishedAtMillis)
            put("projects", JSONArray().apply {
                snapshot.projects.forEach { p ->
                    put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("userId", p.userId)
                            put("name", p.name)
                            put("description", p.description)
                            put("colorKey", p.colorKey)
                            put("iconKey", p.iconKey)
                            put("deadlineMillis", p.deadlineMillis)
                            put("archived", p.archived)
                            put("createdAtMillis", p.createdAtMillis)
                            put("updatedAtMillis", p.updatedAtMillis)
                        }
                    )
                }
            })
            put("workItems", JSONArray().apply {
                snapshot.workItems.forEach { w ->
                    put(
                        JSONObject().apply {
                            put("id", w.id)
                            put("userId", w.userId)
                            put("projectId", w.projectId)
                            put("title", w.title)
                            put("notes", w.notes)
                            put("priority", w.priority)
                            put("status", w.status)
                            put("scheduledStartMillis", w.scheduledStartMillis)
                            put("projectedDurationMinutes", w.projectedDurationMinutes)
                            put("deadlineMillis", w.deadlineMillis)
                            put("progressPercent", w.progressPercent)
                            put("elapsedSeconds", w.elapsedSeconds)
                            put("sessionStartedAtMillis", w.sessionStartedAtMillis)
                            put("pomodoroWorkMinutes", w.pomodoroWorkMinutes)
                            put("pomodoroBreakMinutes", w.pomodoroBreakMinutes)
                            put("pomodoroWorkPeriods", w.pomodoroWorkPeriods)
                            put("pomodoroBreakCount", w.pomodoroBreakCount)
                            put("pomodoroPhase", w.pomodoroPhase)
                            put("currentPeriodIndex", w.currentPeriodIndex)
                            put("phaseElapsedSeconds", w.phaseElapsedSeconds)
                            put("createdAtMillis", w.createdAtMillis)
                            put("updatedAtMillis", w.updatedAtMillis)
                        }
                    )
                }
            })
        }
        return root.toString()
    }

    fun decodeSnapshot(rawJson: String?): WearSyncSnapshot {
        if (rawJson.isNullOrBlank()) return WearSyncSnapshot()
        return try {
            val root = JSONObject(rawJson)
            val projectsArr = root.optJSONArray("projects") ?: JSONArray()
            val projects = buildList {
                for (i in 0 until projectsArr.length()) {
                    val obj = projectsArr.optJSONObject(i) ?: continue
                    add(
                        SharedProject(
                            id = obj.optString("id"),
                            userId = obj.optString("userId"),
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            colorKey = obj.optString("colorKey", "CORAL"),
                            iconKey = obj.optString("iconKey", "ROCKET"),
                            deadlineMillis = obj.optLong("deadlineMillis", 0L),
                            archived = obj.optBoolean("archived", false),
                            createdAtMillis = obj.optLong("createdAtMillis", 0L),
                            updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                        )
                    )
                }
            }
            val itemsArr = root.optJSONArray("workItems") ?: JSONArray()
            val workItems = buildList {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.optJSONObject(i) ?: continue
                    add(
                        SharedWorkItem(
                            id = obj.optString("id"),
                            userId = obj.optString("userId"),
                            projectId = obj.optString("projectId"),
                            title = obj.optString("title"),
                            notes = obj.optString("notes"),
                            priority = obj.optString("priority", "MEDIUM"),
                            status = obj.optString("status", "TODO"),
                            scheduledStartMillis = obj.optLong("scheduledStartMillis", 0L),
                            projectedDurationMinutes = obj.optLong("projectedDurationMinutes", 50L),
                            deadlineMillis = obj.optLong("deadlineMillis", 0L),
                            progressPercent = obj.optLong("progressPercent", 0L),
                            elapsedSeconds = obj.optLong("elapsedSeconds", 0L),
                            sessionStartedAtMillis = obj.optLong("sessionStartedAtMillis", 0L),
                            pomodoroWorkMinutes = obj.optLong("pomodoroWorkMinutes", 0L),
                            pomodoroBreakMinutes = obj.optLong("pomodoroBreakMinutes", 5L),
                            pomodoroWorkPeriods = obj.optLong("pomodoroWorkPeriods", 0L),
                            pomodoroBreakCount = obj.optLong("pomodoroBreakCount", -1L),
                            pomodoroPhase = obj.optString("pomodoroPhase", "WORK"),
                            currentPeriodIndex = obj.optLong("currentPeriodIndex", 1L),
                            phaseElapsedSeconds = obj.optLong("phaseElapsedSeconds", 0L),
                            createdAtMillis = obj.optLong("createdAtMillis", 0L),
                            updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                        )
                    )
                }
            }
            WearSyncSnapshot(
                userId = root.optString("userId"),
                userName = root.optString("userName"),
                userEmail = root.optString("userEmail"),
                publishedAtMillis = root.optLong("publishedAtMillis", 0L),
                projects = projects,
                workItems = workItems
            )
        } catch (_: Exception) {
            WearSyncSnapshot()
        }
    }

    fun encodeCommand(cmd: WearCommand): String {
        return JSONObject().apply {
            put("commandId", cmd.commandId)
            put("type", cmd.type.name)
            put("issuedAtMillis", cmd.issuedAtMillis)
            put("itemId", cmd.itemId)
            put("projectId", cmd.projectId)
            put("expectedPhase", cmd.expectedPhase)
            put("expectedPeriodIndex", cmd.expectedPeriodIndex)
            put("progressPercent", cmd.progressPercent)
            put("name", cmd.name)
            put("description", cmd.description)
            put("colorKey", cmd.colorKey)
            put("iconKey", cmd.iconKey)
            put("title", cmd.title)
            put("notes", cmd.notes)
            put("priority", cmd.priority)
            put("scheduledStartMillis", cmd.scheduledStartMillis)
            put("deadlineMillis", cmd.deadlineMillis)
            put("pomodoroWorkMinutes", cmd.pomodoroWorkMinutes)
            put("pomodoroBreakMinutes", cmd.pomodoroBreakMinutes)
            put("pomodoroWorkPeriods", cmd.pomodoroWorkPeriods)
            put("pomodoroBreakCount", cmd.pomodoroBreakCount)
        }.toString()
    }

    fun decodeCommand(rawJson: String?): WearCommand? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(rawJson)
            val typeName = obj.optString("type")
            val type = WearCommandType.entries.firstOrNull { it.name == typeName } ?: return null
            WearCommand(
                commandId = obj.optString("commandId"),
                type = type,
                issuedAtMillis = obj.optLong("issuedAtMillis", System.currentTimeMillis()),
                itemId = obj.optString("itemId"),
                projectId = obj.optString("projectId"),
                expectedPhase = obj.optString("expectedPhase"),
                expectedPeriodIndex = obj.optLong("expectedPeriodIndex", 0L),
                progressPercent = obj.optLong("progressPercent", 0L),
                name = obj.optString("name"),
                description = obj.optString("description"),
                colorKey = obj.optString("colorKey", "CORAL"),
                iconKey = obj.optString("iconKey", "ROCKET"),
                title = obj.optString("title"),
                notes = obj.optString("notes"),
                priority = obj.optString("priority", "MEDIUM"),
                scheduledStartMillis = obj.optLong("scheduledStartMillis", 0L),
                deadlineMillis = obj.optLong("deadlineMillis", 0L),
                pomodoroWorkMinutes = obj.optLong("pomodoroWorkMinutes", 25L),
                pomodoroBreakMinutes = obj.optLong("pomodoroBreakMinutes", 5L),
                pomodoroWorkPeriods = obj.optLong("pomodoroWorkPeriods", 1L),
                pomodoroBreakCount = obj.optLong("pomodoroBreakCount", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}
