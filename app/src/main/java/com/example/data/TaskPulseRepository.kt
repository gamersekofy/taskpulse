package com.example.data

import android.content.Context
import com.example.R
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

class TaskPulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProjects(userId: String): Flow<List<Project>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Project::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.archived }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorkItems(userId: String): Flow<List<WorkItem>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/workItems"
        emitAll(
            db.collection("users").document(userId).collection("workItems")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WorkItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(
        name: String,
        description: String,
        color: ProjectColor,
        icon: ProjectIcon,
        deadlineMillis: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val trimmedName = name.trim().take(60)
            require(trimmedName.isNotEmpty()) { "Project name cannot be empty." }
            val trimmedDesc = description.trim().take(300)
            val safeDeadline = deadlineMillis.coerceAtLeast(0L)

            val docRef = db.collection("users").document(uid).collection("projects").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to trimmedName,
                "description" to trimmedDesc,
                "colorKey" to color.name,
                "iconKey" to icon.name,
                "deadlineMillis" to safeDeadline,
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProject(
        projectId: String,
        name: String,
        description: String,
        color: ProjectColor,
        icon: ProjectIcon,
        deadlineMillis: Long
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Project ID is required." }
            val trimmedName = name.trim().take(60)
            require(trimmedName.isNotEmpty()) { "Project name cannot be empty." }
            val trimmedDesc = description.trim().take(300)

            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to trimmedName,
                    "description" to trimmedDesc,
                    "colorKey" to color.name,
                    "iconKey" to icon.name,
                    "deadlineMillis" to deadlineMillis.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectAndWorkItems(projectId: String, childItemIds: List<String>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Project ID is required." }
            val userRef = db.collection("users").document(uid)
            val batch = db.batch()
            childItemIds.filter { it.isNotBlank() }.forEach { itemId ->
                batch.delete(userRef.collection("workItems").document(itemId))
            }
            val projRef = userRef.collection("projects").document(projectId)
            docPath = projRef.path
            batch.delete(projRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createWorkItem(
        projectId: String,
        title: String,
        notes: String,
        priority: WorkPriority,
        scheduledStartMillis: Long,
        pomodoroWorkMinutes: Long,
        pomodoroBreakMinutes: Long,
        pomodoroWorkPeriods: Long,
        pomodoroBreakCount: Long,
        deadlineMillis: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Please select a project." }
            val trimmedTitle = title.trim().take(100)
            require(trimmedTitle.isNotEmpty()) { "Work item title cannot be empty." }
            val trimmedNotes = notes.trim().take(500)

            val safePeriods = pomodoroWorkPeriods.coerceIn(1L, 12L)
            val safeWorkMins = pomodoroWorkMinutes.coerceIn(1L, 120L)
            val safeBreakMins = pomodoroBreakMinutes.coerceIn(1L, 60L)
            val safeBreaks = pomodoroBreakCount.coerceIn(0L, safePeriods)
            val safeDuration = (safePeriods * safeWorkMins).coerceIn(1L, 1440L)

            val docRef = db.collection("users").document(uid).collection("workItems").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "projectId" to projectId,
                "title" to trimmedTitle,
                "notes" to trimmedNotes,
                "priority" to priority.name,
                "status" to WorkStatus.TODO.name,
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
                "pomodoroPhase" to PomodoroPhase.WORK.name,
                "currentPeriodIndex" to 1L,
                "phaseElapsedSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateWorkItemDetails(
        existingItem: WorkItem,
        projectId: String,
        title: String,
        notes: String,
        priority: WorkPriority,
        scheduledStartMillis: Long,
        pomodoroWorkMinutes: Long,
        pomodoroBreakMinutes: Long,
        pomodoroWorkPeriods: Long,
        pomodoroBreakCount: Long,
        deadlineMillis: Long
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${existingItem.id}"
        return try {
            val uid = requireUserId()
            require(existingItem.id.isNotBlank()) { "Work item ID is required." }
            require(projectId.isNotBlank()) { "Project ID is required." }
            val trimmedTitle = title.trim().take(100)
            require(trimmedTitle.isNotEmpty()) { "Work item title cannot be empty." }

            val safePeriods = pomodoroWorkPeriods.coerceIn(1L, 12L)
            val safeWorkMins = pomodoroWorkMinutes.coerceIn(1L, 120L)
            val safeBreakMins = pomodoroBreakMinutes.coerceIn(1L, 60L)
            val safeBreaks = pomodoroBreakCount.coerceIn(0L, safePeriods)
            val safeDuration = (safePeriods * safeWorkMins).coerceIn(1L, 1440L)

            val clampedIndex = existingItem.currentPeriodIndex.coerceIn(1L, safePeriods)
            val clampedPhase = if (safeBreaks == 0L && existingItem.phaseEnum == PomodoroPhase.BREAK) {
                PomodoroPhase.WORK.name
            } else {
                existingItem.phaseEnum.name
            }

            val docRef = db.collection("users").document(uid).collection("workItems").document(existingItem.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "projectId" to projectId,
                    "title" to trimmedTitle,
                    "notes" to notes.trim().take(500),
                    "priority" to priority.name,
                    "scheduledStartMillis" to scheduledStartMillis.coerceAtLeast(0L),
                    "projectedDurationMinutes" to safeDuration,
                    "deadlineMillis" to deadlineMillis.coerceAtLeast(0L),
                    "pomodoroWorkMinutes" to safeWorkMins,
                    "pomodoroBreakMinutes" to safeBreakMins,
                    "pomodoroWorkPeriods" to safePeriods,
                    "pomodoroBreakCount" to safeBreaks,
                    "pomodoroPhase" to clampedPhase,
                    "currentPeriodIndex" to clampedIndex,
                    "phaseElapsedSeconds" to existingItem.phaseElapsedSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun postponeWorkItem(item: WorkItem, plusMillis: Long = 86_400_000L): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Work item ID is required." }
            val baseStart = if (item.scheduledStartMillis > 0L) item.scheduledStartMillis else System.currentTimeMillis()
            val newStart = baseStart + plusMillis
            val newDeadline = if (item.deadlineMillis > 0L) item.deadlineMillis + plusMillis else 0L

            val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "scheduledStartMillis" to newStart.coerceAtLeast(0L),
                    "deadlineMillis" to newDeadline.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun duplicateWorkItem(item: WorkItem): Result<String> {
        val copyTitle = if (item.title.length + 7 <= 100) "${item.title} (Copy)" else item.title.take(93) + " (Copy)"
        return createWorkItem(
            projectId = item.projectId,
            title = copyTitle,
            notes = item.notes,
            priority = item.priorityEnum,
            scheduledStartMillis = item.scheduledStartMillis,
            pomodoroWorkMinutes = item.effectiveWorkMinutes,
            pomodoroBreakMinutes = item.effectiveBreakMinutes,
            pomodoroWorkPeriods = item.effectiveWorkPeriods,
            pomodoroBreakCount = item.effectiveBreakCount,
            deadlineMillis = item.deadlineMillis
        )
    }

    suspend fun startWorkSession(
        targetItem: WorkItem,
        currentlyRunningItem: WorkItem?,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${targetItem.id}"
        return try {
            val uid = requireUserId()
            require(targetItem.id.isNotBlank()) { "Work item ID is required." }
            val collection = db.collection("users").document(uid).collection("workItems")
            val batch = db.batch()

            if (currentlyRunningItem != null && currentlyRunningItem.id != targetItem.id && currentlyRunningItem.id.isNotBlank()) {
                val runningSnap = currentlyRunningItem.computePomodoroSnapshot(nowMillis)
                val pausedWorkElapsed = currentlyRunningItem.liveElapsedSeconds(nowMillis).coerceIn(0L, 8640000L)
                val pausedPct = (runningSnap.overallFraction * 100f)
                    .toLong()
                    .coerceIn(currentlyRunningItem.progressPercent, 99L)
                batch.update(
                    collection.document(currentlyRunningItem.id),
                    mapOf(
                        "status" to WorkStatus.PAUSED.name,
                        "elapsedSeconds" to pausedWorkElapsed,
                        "phaseElapsedSeconds" to runningSnap.phaseElapsedSeconds.coerceIn(0L, 86400L),
                        "pomodoroPhase" to runningSnap.currentPhase.name,
                        "currentPeriodIndex" to runningSnap.currentPeriodIndex.coerceIn(1L, 12L),
                        "sessionStartedAtMillis" to 0L,
                        "progressPercent" to pausedPct,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val freshTodo = targetItem.statusEnum == WorkStatus.TODO
            val phaseName = if (freshTodo) PomodoroPhase.WORK.name else targetItem.phaseEnum.name
            val periodIdx = if (freshTodo) 1L else targetItem.currentPeriodIndex.coerceIn(1L, 12L)
            val phaseElapsed = if (freshTodo) 0L else targetItem.phaseElapsedSeconds.coerceIn(0L, 86400L)

            val targetRef = collection.document(targetItem.id)
            docPath = targetRef.path
            batch.update(
                targetRef,
                mapOf(
                    "status" to WorkStatus.IN_PROGRESS.name,
                    "pomodoroPhase" to phaseName,
                    "currentPeriodIndex" to periodIdx,
                    "phaseElapsedSeconds" to phaseElapsed,
                    "sessionStartedAtMillis" to nowMillis.coerceAtLeast(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun pauseWorkSession(
        item: WorkItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Work item ID is required." }
            val snap = item.computePomodoroSnapshot(nowMillis)
            val newElapsed = item.liveElapsedSeconds(nowMillis).coerceIn(0L, 8640000L)
            val computedPct = (snap.overallFraction * 100f)
                .toLong()
                .coerceIn(item.progressPercent, 99L)

            val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to WorkStatus.PAUSED.name,
                    "elapsedSeconds" to newElapsed,
                    "phaseElapsedSeconds" to snap.phaseElapsedSeconds.coerceIn(0L, 86400L),
                    "pomodoroPhase" to snap.currentPhase.name,
                    "currentPeriodIndex" to snap.currentPeriodIndex.coerceIn(1L, 12L),
                    "sessionStartedAtMillis" to 0L,
                    "progressPercent" to computedPct,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Advances the Pomodoro session to the next Work or Break slot in the timeline,
     * or marks the item DONE when the final slot finishes.
     */
    suspend fun advancePomodoroPhase(
        item: WorkItem,
        autoContinue: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<PomodoroSlot?> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Work item ID is required." }
            val snap = item.computePomodoroSnapshot(nowMillis)
            val newElapsed = item.liveElapsedSeconds(nowMillis).coerceIn(0L, 8640000L)
            val nextSlot = snap.nextSlot
            val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)
            docPath = docRef.path

            if (nextSlot == null) {
                docRef.update(
                    mapOf(
                        "status" to WorkStatus.DONE.name,
                        "progressPercent" to 100L,
                        "elapsedSeconds" to newElapsed,
                        "phaseElapsedSeconds" to 0L,
                        "sessionStartedAtMillis" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(null)
            } else {
                val keepRunning = autoContinue || item.statusEnum == WorkStatus.IN_PROGRESS
                val computedPct = ((snap.activeSlotIndex + 1).toFloat() / snap.slots.size.toFloat() * 100f)
                    .toLong()
                    .coerceIn(item.progressPercent, 99L)
                docRef.update(
                    mapOf(
                        "status" to if (keepRunning) WorkStatus.IN_PROGRESS.name else WorkStatus.PAUSED.name,
                        "elapsedSeconds" to newElapsed,
                        "progressPercent" to computedPct,
                        "pomodoroPhase" to if (nextSlot.isBreak) PomodoroPhase.BREAK.name else PomodoroPhase.WORK.name,
                        "currentPeriodIndex" to nextSlot.index.toLong().coerceIn(1L, 12L),
                        "phaseElapsedSeconds" to 0L,
                        "sessionStartedAtMillis" to if (keepRunning) nowMillis.coerceAtLeast(1L) else 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(nextSlot)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeWorkSession(
        item: WorkItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Work item ID is required." }
            val finalElapsed = item.liveElapsedSeconds(nowMillis).coerceIn(0L, 8640000L)
            val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to WorkStatus.DONE.name,
                    "progressPercent" to 100L,
                    "elapsedSeconds" to finalElapsed,
                    "phaseElapsedSeconds" to 0L,
                    "sessionStartedAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reopenWorkItem(item: WorkItem): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Work item ID is required." }
            val restoredPct = ((item.elapsedSeconds.toFloat() / item.projectedSeconds) * 100f)
                .toLong()
                .coerceIn(0L, 95L)
            val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to WorkStatus.TODO.name,
                    "progressPercent" to restoredPct,
                    "pomodoroPhase" to PomodoroPhase.WORK.name,
                    "currentPeriodIndex" to 1L,
                    "phaseElapsedSeconds" to 0L,
                    "sessionStartedAtMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProgressPercent(item: WorkItem, newPercent: Long): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Work item ID is required." }
            val clamped = newPercent.coerceIn(0L, 100L)
            val docRef = db.collection("users").document(uid).collection("workItems").document(item.id)
            docPath = docRef.path
            val targetStatus = if (clamped >= 100L) WorkStatus.DONE.name else item.status
            val targetSessionStart = if (clamped >= 100L) 0L else item.sessionStartedAtMillis
            val targetElapsed = if (clamped >= 100L) item.liveElapsedSeconds(System.currentTimeMillis()) else item.elapsedSeconds
            docRef.update(
                mapOf(
                    "status" to targetStatus,
                    "progressPercent" to clamped,
                    "elapsedSeconds" to targetElapsed.coerceIn(0L, 8640000L),
                    "sessionStartedAtMillis" to targetSessionStart.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkItem(workItemId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workItems/$workItemId"
        return try {
            val uid = requireUserId()
            require(workItemId.isNotBlank()) { "Work item ID is required." }
            val docRef = db.collection("users").document(uid).collection("workItems").document(workItemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
