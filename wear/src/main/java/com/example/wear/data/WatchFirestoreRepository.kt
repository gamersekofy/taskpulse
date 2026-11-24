package com.example.wear.data

import android.content.Context
import android.util.Log
import com.example.shared.SharedPomodoroSlot
import com.example.shared.SharedProject
import com.example.shared.SharedWorkItem
import com.example.wear.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WatchFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required for direct cloud sync.")

    fun observeProjects(userId: String): Flow<List<SharedProject>> = flow {
        require(userId.isNotBlank())
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        FirestoreProjectDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { !it.archived }
                        .map { it.toShared() }
                        .sortedByDescending { it.createdAtMillis }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "observeProjects error: ${error.message}")
                    throw error
                }
        )
    }

    fun observeWorkItems(userId: String): Flow<List<SharedWorkItem>> = flow {
        require(userId.isNotBlank())
        emitAll(
            db.collection("users").document(userId).collection("workItems")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        FirestoreWorkItemDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toShared() }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "observeWorkItems error: ${error.message}")
                    throw error
                }
        )
    }

    suspend fun fetchProjectsOnce(userId: String): List<SharedProject> {
        val snap = db.collection("users").document(userId).collection("projects").get().await()
        return snap.toObjects(FirestoreProjectDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { !it.archived }
            .map { it.toShared() }
            .sortedByDescending { it.createdAtMillis }
    }

    suspend fun fetchWorkItemsOnce(userId: String): List<SharedWorkItem> {
        val snap = db.collection("users").document(userId).collection("workItems").get().await()
        return snap.toObjects(FirestoreWorkItemDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .map { it.toShared() }
    }

    suspend fun createProject(
        name: String,
        description: String,
        colorKey: String,
        iconKey: String,
        deadlineMillis: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val trimmedName = name.trim().take(60)
        require(trimmedName.isNotEmpty()) { "Project name cannot be empty." }
        val docRef = db.collection("users").document(uid).collection("projects").document()
        docRef.set(
            mapOf(
                "userId" to uid,
                "name" to trimmedName,
                "description" to description.trim().take(300),
                "colorKey" to WatchProjectColor.safeValueOf(colorKey).name,
                "iconKey" to WatchProjectIcon.safeValueOf(iconKey).name,
                "deadlineMillis" to deadlineMillis.coerceAtLeast(0L),
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        docRef.id
    }

    suspend fun updateProject(
        projectId: String,
        name: String,
        description: String,
        colorKey: String,
        iconKey: String,
        deadlineMillis: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val trimmedName = name.trim().take(60)
        require(projectId.isNotBlank() && trimmedName.isNotEmpty())
        db.collection("users").document(uid).collection("projects").document(projectId)
            .update(
                mapOf(
                    "name" to trimmedName,
                    "description" to description.trim().take(300),
                    "colorKey" to WatchProjectColor.safeValueOf(colorKey).name,
                    "iconKey" to WatchProjectIcon.safeValueOf(iconKey).name,
                    "deadlineMillis" to deadlineMillis.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun deleteProjectAndWorkItems(projectId: String, childItemIds: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(projectId.isNotBlank())
        val userRef = db.collection("users").document(uid)
        val batch = db.batch()
        childItemIds.filter { it.isNotBlank() }.forEach { childId ->
            batch.delete(userRef.collection("workItems").document(childId))
        }
        batch.delete(userRef.collection("projects").document(projectId))
        batch.commit().await()
        Unit
    }

    suspend fun createWorkItem(
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
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val trimmedTitle = title.trim().take(100)
        require(projectId.isNotBlank() && trimmedTitle.isNotEmpty())

        val safePeriods = pomodoroWorkPeriods.coerceIn(1L, 12L)
        val safeWorkMins = pomodoroWorkMinutes.coerceIn(1L, 120L)
        val safeBreakMins = pomodoroBreakMinutes.coerceIn(1L, 60L)
        val safeBreaks = pomodoroBreakCount.coerceIn(0L, safePeriods)
        val safeDuration = (safePeriods * safeWorkMins).coerceIn(1L, 1440L)

        val docRef = db.collection("users").document(uid).collection("workItems").document()
        docRef.set(
            mapOf(
                "userId" to uid,
                "projectId" to projectId,
                "title" to trimmedTitle,
                "notes" to notes.trim().take(500),
                "priority" to WatchWorkPriority.safeValueOf(priorityKey).name,
                "status" to WatchWorkStatus.TODO.name,
                "scheduledStartMillis" to scheduledStartMillis.coerceAtLeast(0L),
                "projectedDurationMinutes" to safeDuration,
                "deadlineMillis" to deadlineMillis.coerceAtLeast(0L),
                "progressPercent" to 0L,
                "elapsedSeconds" to 0L,
                "sessionStartedAtMillis" to 0L,
                "pomodoroWorkMinutes" to safeWorkMins,
                "pomodoroBreakMinutes" to safeBreakMins,
                "pomodoroWorkPeriods" to safePeriods,
                "pomodoroBreakCount" to safeBreaks,
                "pomodoroPhase" to "WORK",
                "currentPeriodIndex" to 1L,
                "phaseElapsedSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        docRef.id
    }

    suspend fun updateWorkItemDetails(
        existingItem: SharedWorkItem,
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
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val trimmedTitle = title.trim().take(100)
        require(existingItem.id.isNotBlank() && projectId.isNotBlank() && trimmedTitle.isNotEmpty())

        val safePeriods = pomodoroWorkPeriods.coerceIn(1L, 12L)
        val safeWorkMins = pomodoroWorkMinutes.coerceIn(1L, 120L)
        val safeBreakMins = pomodoroBreakMinutes.coerceIn(1L, 60L)
        val safeBreaks = pomodoroBreakCount.coerceIn(0L, safePeriods)
        val safeDuration = (safePeriods * safeWorkMins).coerceIn(1L, 1440L)

        val clampedIndex = existingItem.currentPeriodIndex.coerceIn(1L, safePeriods)
        val clampedPhase = if (safeBreaks == 0L && existingItem.pomodoroPhase == "BREAK") "WORK" else existingItem.pomodoroPhase

        db.collection("users").document(uid).collection("workItems").document(existingItem.id)
            .update(
                mapOf(
                    "projectId" to projectId,
                    "title" to trimmedTitle,
                    "notes" to notes.trim().take(500),
                    "priority" to WatchWorkPriority.safeValueOf(priorityKey).name,
                    "scheduledStartMillis" to scheduledStartMillis.coerceAtLeast(0L),
                    "projectedDurationMinutes" to safeDuration,
                    "deadlineMillis" to deadlineMillis.coerceAtLeast(0L),
                    "pomodoroWorkMinutes" to safeWorkMins,
                    "pomodoroBreakMinutes" to safeBreakMins,
                    "pomodoroWorkPeriods" to safePeriods,
                    "pomodoroBreakCount" to safeBreaks,
                    "pomodoroPhase" to clampedPhase,
                    "currentPeriodIndex" to clampedIndex,
                    "phaseElapsedSeconds" to existingItem.phaseElapsedSeconds.coerceIn(0L, 86_400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun startWorkSession(
        targetItem: SharedWorkItem,
        currentlyRunningItem: SharedWorkItem?,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(targetItem.id.isNotBlank())
        val collection = db.collection("users").document(uid).collection("workItems")
        val batch = db.batch()

        if (currentlyRunningItem != null && currentlyRunningItem.id != targetItem.id && currentlyRunningItem.id.isNotBlank()) {
            val runningSnap = currentlyRunningItem.computePomodoroSnapshot(nowMillis)
            val pausedWorkElapsed = currentlyRunningItem.liveElapsedSeconds(nowMillis).coerceIn(0L, 8_640_000L)
            val pausedPct = (runningSnap.overallFraction * 100f)
                .toLong()
                .coerceIn(currentlyRunningItem.progressPercent, 99L)
            batch.update(
                collection.document(currentlyRunningItem.id),
                mapOf(
                    "status" to WatchWorkStatus.PAUSED.name,
                    "elapsedSeconds" to pausedWorkElapsed,
                    "phaseElapsedSeconds" to runningSnap.phaseElapsedSeconds.coerceIn(0L, 86_400L),
                    "pomodoroPhase" to runningSnap.currentPhase,
                    "currentPeriodIndex" to runningSnap.currentPeriodIndex.coerceIn(1L, 12L),
                    "sessionStartedAtMillis" to 0L,
                    "progressPercent" to pausedPct,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val freshTodo = targetItem.status == WatchWorkStatus.TODO.name
        val phaseName = if (freshTodo) "WORK" else targetItem.pomodoroPhase
        val periodIdx = if (freshTodo) 1L else targetItem.currentPeriodIndex.coerceIn(1L, 12L)
        val phaseElapsed = if (freshTodo) 0L else targetItem.phaseElapsedSeconds.coerceIn(0L, 86_400L)

        batch.update(
            collection.document(targetItem.id),
            mapOf(
                "status" to WatchWorkStatus.IN_PROGRESS.name,
                "pomodoroPhase" to phaseName,
                "currentPeriodIndex" to periodIdx,
                "phaseElapsedSeconds" to phaseElapsed,
                "sessionStartedAtMillis" to nowMillis.coerceAtLeast(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }

    suspend fun pauseWorkSession(
        item: SharedWorkItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val snap = item.computePomodoroSnapshot(nowMillis)
        val newElapsed = item.liveElapsedSeconds(nowMillis).coerceIn(0L, 8_640_000L)
        val computedPct = (snap.overallFraction * 100f).toLong().coerceIn(item.progressPercent, 99L)
        db.collection("users").document(uid).collection("workItems").document(item.id)
            .update(
                mapOf(
                    "status" to WatchWorkStatus.PAUSED.name,
                    "elapsedSeconds" to newElapsed,
                    "phaseElapsedSeconds" to snap.phaseElapsedSeconds.coerceIn(0L, 86_400L),
                    "pomodoroPhase" to snap.currentPhase,
                    "currentPeriodIndex" to snap.currentPeriodIndex.coerceIn(1L, 12L),
                    "sessionStartedAtMillis" to 0L,
                    "progressPercent" to computedPct,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun advancePomodoroPhase(
        item: SharedWorkItem,
        autoContinue: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<SharedPomodoroSlot?> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)

        db.runTransaction { tx ->
            val currentDoc = tx.get(docRef).toObject(FirestoreWorkItemDoc::class.java)?.toShared() ?: item
            if (currentDoc.pomodoroPhase != item.pomodoroPhase ||
                currentDoc.currentPeriodIndex != item.currentPeriodIndex ||
                currentDoc.status == WatchWorkStatus.DONE.name
            ) {
                return@runTransaction null
            }

            val snap = currentDoc.computePomodoroSnapshot(nowMillis)
            val newElapsed = currentDoc.liveElapsedSeconds(nowMillis).coerceIn(0L, 8_640_000L)
            val nextSlot = snap.nextSlot

            if (nextSlot == null) {
                tx.update(
                    docRef,
                    mapOf(
                        "status" to WatchWorkStatus.DONE.name,
                        "progressPercent" to 100L,
                        "elapsedSeconds" to newElapsed,
                        "phaseElapsedSeconds" to 0L,
                        "sessionStartedAtMillis" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            } else {
                val keepRunning = autoContinue || currentDoc.status == WatchWorkStatus.IN_PROGRESS.name
                val computedPct = ((snap.activeSlotIndex + 1).toFloat() / snap.slots.size.toFloat() * 100f)
                    .toLong()
                    .coerceIn(currentDoc.progressPercent, 99L)
                tx.update(
                    docRef,
                    mapOf(
                        "status" to if (keepRunning) WatchWorkStatus.IN_PROGRESS.name else WatchWorkStatus.PAUSED.name,
                        "elapsedSeconds" to newElapsed,
                        "progressPercent" to computedPct,
                        "pomodoroPhase" to if (nextSlot.isBreak) "BREAK" else "WORK",
                        "currentPeriodIndex" to nextSlot.index.toLong().coerceIn(1L, 12L),
                        "phaseElapsedSeconds" to 0L,
                        "sessionStartedAtMillis" to if (keepRunning) nowMillis.coerceAtLeast(1L) else 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextSlot
            }
        }.await()
    }

    suspend fun completeWorkSession(
        item: SharedWorkItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val finalElapsed = item.liveElapsedSeconds(nowMillis).coerceIn(0L, 8_640_000L)
        db.collection("users").document(uid).collection("workItems").document(item.id)
            .update(
                mapOf(
                    "status" to WatchWorkStatus.DONE.name,
                    "progressPercent" to 100L,
                    "elapsedSeconds" to finalElapsed,
                    "phaseElapsedSeconds" to 0L,
                    "sessionStartedAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun reopenWorkItem(item: SharedWorkItem): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val restoredPct = ((item.elapsedSeconds.toFloat() / item.projectedSeconds) * 100f)
            .toLong()
            .coerceIn(0L, 95L)
        db.collection("users").document(uid).collection("workItems").document(item.id)
            .update(
                mapOf(
                    "status" to WatchWorkStatus.TODO.name,
                    "progressPercent" to restoredPct,
                    "pomodoroPhase" to "WORK",
                    "currentPeriodIndex" to 1L,
                    "phaseElapsedSeconds" to 0L,
                    "sessionStartedAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun postponeWorkItem(item: SharedWorkItem, plusMillis: Long = 86_400_000L): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val baseStart = if (item.scheduledStartMillis > 0L) item.scheduledStartMillis else System.currentTimeMillis()
        val newStart = baseStart + plusMillis
        val newDeadline = if (item.deadlineMillis > 0L) item.deadlineMillis + plusMillis else 0L
        db.collection("users").document(uid).collection("workItems").document(item.id)
            .update(
                mapOf(
                    "scheduledStartMillis" to newStart.coerceAtLeast(0L),
                    "deadlineMillis" to newDeadline.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun duplicateWorkItem(item: SharedWorkItem): Result<String> {
        val copyTitle = if (item.title.length + 7 <= 100) "${item.title} (Copy)" else item.title.take(93) + " (Copy)"
        return createWorkItem(
            projectId = item.projectId,
            title = copyTitle,
            notes = item.notes,
            priorityKey = item.priority,
            scheduledStartMillis = item.scheduledStartMillis,
            pomodoroWorkMinutes = item.effectiveWorkMinutes,
            pomodoroBreakMinutes = item.effectiveBreakMinutes,
            pomodoroWorkPeriods = item.effectiveWorkPeriods,
            pomodoroBreakCount = item.effectiveBreakCount,
            deadlineMillis = item.deadlineMillis
        )
    }

    suspend fun updateProgressPercent(item: SharedWorkItem, newPercent: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank())
        val clamped = newPercent.coerceIn(0L, 100L)
        val targetStatus = if (clamped >= 100L) WatchWorkStatus.DONE.name else item.status
        val targetSessionStart = if (clamped >= 100L) 0L else item.sessionStartedAtMillis
        val targetElapsed = if (clamped >= 100L) item.liveElapsedSeconds(System.currentTimeMillis()) else item.elapsedSeconds
        db.collection("users").document(uid).collection("workItems").document(item.id)
            .update(
                mapOf(
                    "status" to targetStatus,
                    "progressPercent" to clamped,
                    "elapsedSeconds" to targetElapsed.coerceIn(0L, 8_640_000L),
                    "sessionStartedAtMillis" to targetSessionStart.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }

    suspend fun deleteWorkItem(workItemId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(workItemId.isNotBlank())
        db.collection("users").document(uid).collection("workItems").document(workItemId)
            .delete().await()
        Unit
    }

    private companion object {
        const val TAG = "WatchFirestoreRepo"
    }
}
