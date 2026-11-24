package com.example.wear.liveupdates

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.shared.SharedWorkItem
import com.example.wear.data.WatchSyncCoordinator
import com.example.wear.data.WatchWorkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchPomodoroForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private lateinit var coordinator: WatchSyncCoordinator

    private var activeItem: SharedWorkItem? = null
    private var activeProjectName: String = ""
    private var lastEndingSoonAlertKey: String? = null
    private var phaseTransitionInFlight: Boolean = false
    private var lastNotifUpdateMillis: Long = 0L

    override fun onCreate() {
        super.onCreate()
        coordinator = WatchSyncCoordinator.getInstance(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WatchPomodoroNotificationHelper.ACTION_PAUSE_SESSION -> {
                val targetId = intent.getStringExtra(EXTRA_ITEM_ID) ?: activeItem?.id.orEmpty()
                val item = activeItem?.takeIf { it.id == targetId }
                if (item != null) {
                    coordinator.pauseSession(item)
                }
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }

            WatchPomodoroNotificationHelper.ACTION_NEXT_PHASE -> {
                val targetId = intent.getStringExtra(EXTRA_ITEM_ID) ?: activeItem?.id.orEmpty()
                val item = activeItem?.takeIf { it.id == targetId }
                if (item != null && !phaseTransitionInFlight) {
                    phaseTransitionInFlight = true
                    serviceScope.launch {
                        coordinator.advancePhase(item).join()
                        phaseTransitionInFlight = false
                    }
                }
                return START_NOT_STICKY
            }

            WatchPomodoroNotificationHelper.ACTION_COMPLETE_SESSION -> {
                val targetId = intent.getStringExtra(EXTRA_ITEM_ID) ?: activeItem?.id.orEmpty()
                val item = activeItem?.takeIf { it.id == targetId }
                if (item != null) {
                    coordinator.completeSession(item)
                }
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_SERVICE -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }

            ACTION_SYNC_SESSION -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
                val sessionStartedAt = intent.getLongExtra(EXTRA_SESSION_STARTED_AT, 0L)
                if (itemId.isBlank() || sessionStartedAt <= 0L) {
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }

                activeProjectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty()
                val syncedItem = SharedWorkItem(
                    id = itemId,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    status = WatchWorkStatus.IN_PROGRESS.name,
                    projectedDurationMinutes = intent.getLongExtra(EXTRA_PROJECTED_MINUTES, 25L),
                    deadlineMillis = intent.getLongExtra(EXTRA_DEADLINE_MILLIS, 0L),
                    progressPercent = intent.getLongExtra(EXTRA_PROGRESS_PERCENT, 0L),
                    elapsedSeconds = intent.getLongExtra(EXTRA_ELAPSED_SECONDS, 0L),
                    sessionStartedAtMillis = sessionStartedAt,
                    pomodoroWorkMinutes = intent.getLongExtra(EXTRA_WORK_MINUTES, 25L),
                    pomodoroBreakMinutes = intent.getLongExtra(EXTRA_BREAK_MINUTES, 5L),
                    pomodoroWorkPeriods = intent.getLongExtra(EXTRA_WORK_PERIODS, 1L),
                    pomodoroBreakCount = intent.getLongExtra(EXTRA_BREAK_COUNT, 0L),
                    pomodoroPhase = intent.getStringExtra(EXTRA_POMODORO_PHASE) ?: "WORK",
                    currentPeriodIndex = intent.getLongExtra(EXTRA_PERIOD_INDEX, 1L),
                    phaseElapsedSeconds = intent.getLongExtra(EXTRA_PHASE_ELAPSED_SECONDS, 0L)
                )

                activeItem = syncedItem
                phaseTransitionInFlight = false
                startForegroundWithLiveSurface(syncedItem)
                startTicker()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithLiveSurface(item: SharedWorkItem) {
        val now = System.currentTimeMillis()
        lastNotifUpdateMillis = now
        val notification = WatchPomodoroNotificationHelper.buildSessionNotification(
            context = this,
            item = item,
            projectName = activeProjectName,
            nowMillis = now
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    WatchPomodoroNotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(WatchPomodoroNotificationHelper.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start Watch Foreground Service: ${e.message}")
            stopSelf()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            while (isActive) {
                delay(1000L)
                val item = activeItem ?: break
                if (item.id.isBlank() || item.sessionStartedAtMillis <= 0L) break

                val now = System.currentTimeMillis()
                val snap = item.computePomodoroSnapshot(now)

                val warningWindowSec = if (snap.phaseDurationSeconds >= 180L) 60L else 15L
                val phaseKey = "${item.id}:${snap.currentPhase}:${snap.currentPeriodIndex}"
                if (snap.phaseRemainingSeconds in 1L..warningWindowSec && lastEndingSoonAlertKey != phaseKey) {
                    lastEndingSoonAlertKey = phaseKey
                    WatchPomodoroNotificationHelper.sendEndingSoonAlert(
                        context = this@WatchPomodoroForegroundService,
                        itemId = item.id,
                        title = item.title,
                        isBreak = snap.currentPhase == "BREAK",
                        remainingSeconds = snap.phaseRemainingSeconds
                    )
                }

                if (snap.phaseRemainingSeconds == 0L && !phaseTransitionInFlight) {
                    phaseTransitionInFlight = true
                    val nextSlot = snap.nextSlot
                    coordinator.advancePhase(item).invokeOnCompletion {
                        WatchPomodoroNotificationHelper.sendPhaseTransitionAlert(
                            context = this@WatchPomodoroForegroundService,
                            itemId = item.id,
                            title = item.title,
                            nextSlot = nextSlot
                        )
                        if (nextSlot == null) {
                            stopForegroundAndSelf()
                        } else {
                            val nextItem = item.copy(
                                elapsedSeconds = item.liveElapsedSeconds(now),
                                pomodoroPhase = if (nextSlot.isBreak) "BREAK" else "WORK",
                                currentPeriodIndex = nextSlot.index.toLong(),
                                phaseElapsedSeconds = 0L,
                                sessionStartedAtMillis = now
                            )
                            activeItem = nextItem
                            phaseTransitionInFlight = false
                            lastNotifUpdateMillis = 0L
                        }
                    }
                    continue
                }

                // Battery-conscious updates: Status.TimerPart & Chronometer tick natively on watch face/Recents;
                // refresh notification progress bar/Live Update segments every 15s (or immediately on phase reset).
                if (now - lastNotifUpdateMillis >= 15_000L) {
                    lastNotifUpdateMillis = now
                    val updatedNotif = WatchPomodoroNotificationHelper.buildSessionNotification(
                        context = this@WatchPomodoroForegroundService,
                        item = item,
                        projectName = activeProjectName,
                        nowMillis = now
                    )
                    manager?.notify(WatchPomodoroNotificationHelper.NOTIFICATION_ID, updatedNotif)
                }
            }
        }
    }

    private fun stopForegroundAndSelf() {
        tickerJob?.cancel()
        activeItem = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchPomodoroFGS"
        const val ACTION_SYNC_SESSION = "com.example.wear.liveupdates.ACTION_SYNC"
        const val ACTION_STOP_SERVICE = "com.example.wear.liveupdates.ACTION_STOP"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_ELAPSED_SECONDS = "extra_elapsed_seconds"
        const val EXTRA_SESSION_STARTED_AT = "extra_session_started_at"
        const val EXTRA_PROJECTED_MINUTES = "extra_projected_minutes"
        const val EXTRA_DEADLINE_MILLIS = "extra_deadline_millis"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
        const val EXTRA_WORK_MINUTES = "extra_work_minutes"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"
        const val EXTRA_WORK_PERIODS = "extra_work_periods"
        const val EXTRA_BREAK_COUNT = "extra_break_count"
        const val EXTRA_POMODORO_PHASE = "extra_pomodoro_phase"
        const val EXTRA_PERIOD_INDEX = "extra_period_index"
        const val EXTRA_PHASE_ELAPSED_SECONDS = "extra_phase_elapsed_seconds"

        fun syncActiveSession(context: Context, item: SharedWorkItem, projectName: String) {
            if (!WatchPomodoroNotificationHelper.hasNotificationPermission(context)) return
            val intent = Intent(context, WatchPomodoroForegroundService::class.java).apply {
                action = ACTION_SYNC_SESSION
                putExtra(EXTRA_ITEM_ID, item.id)
                putExtra(EXTRA_TITLE, item.title)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_ELAPSED_SECONDS, item.elapsedSeconds)
                putExtra(EXTRA_SESSION_STARTED_AT, item.sessionStartedAtMillis)
                putExtra(EXTRA_PROJECTED_MINUTES, item.projectedDurationMinutes)
                putExtra(EXTRA_DEADLINE_MILLIS, item.deadlineMillis)
                putExtra(EXTRA_PROGRESS_PERCENT, item.progressPercent)
                putExtra(EXTRA_WORK_MINUTES, item.effectiveWorkMinutes)
                putExtra(EXTRA_BREAK_MINUTES, item.effectiveBreakMinutes)
                putExtra(EXTRA_WORK_PERIODS, item.effectiveWorkPeriods)
                putExtra(EXTRA_BREAK_COUNT, item.effectiveBreakCount)
                putExtra(EXTRA_POMODORO_PHASE, item.pomodoroPhase)
                putExtra(EXTRA_PERIOD_INDEX, item.currentPeriodIndex)
                putExtra(EXTRA_PHASE_ELAPSED_SECONDS, item.phaseElapsedSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchPomodoroForegroundService::class.java))
        }
    }
}
