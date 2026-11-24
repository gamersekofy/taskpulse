package com.example.liveupdates

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PomodoroPhase
import com.example.data.PomodoroSlot
import com.example.data.WorkItem
import com.example.ui.TimeFormatting

object WorkSessionNotificationHelper {

    const val CHANNEL_ID = "work_session_live"
    const val ALERT_CHANNEL_ID = "pomodoro_alerts"
    const val NOTIFICATION_ID = 4107
    const val ENDING_SOON_ALERT_ID = 4108
    const val PHASE_TRANSITION_ALERT_ID = 4109

    const val ACTION_PAUSE_SESSION = "com.example.liveupdates.ACTION_PAUSE"
    const val ACTION_NEXT_PHASE = "com.example.liveupdates.ACTION_NEXT_PHASE"
    const val ACTION_COMPLETE_SESSION = "com.example.liveupdates.ACTION_COMPLETE"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val liveExisting = manager.getNotificationChannel(CHANNEL_ID)
        if (liveExisting == null || liveExisting.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            val liveChannel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.live_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.live_update_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(liveChannel)
        }

        val alertExisting = manager.getNotificationChannel(ALERT_CHANNEL_ID)
        if (alertExisting == null || alertExisting.importance < NotificationManager.IMPORTANCE_HIGH) {
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.pomodoro_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.pomodoro_alert_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canPostPromotedLiveUpdates(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canPostPromotedNotifications()
    }

    fun createLiveUpdatesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    }

    fun buildSessionNotification(
        context: Context,
        item: WorkItem,
        projectName: String,
        nowMillis: Long
    ): Notification {
        ensureChannel(context)

        val snap = item.computePomodoroSnapshot(nowMillis)
        val isBreak = snap.currentPhase == PomodoroPhase.BREAK
        val remainingSeconds = snap.phaseRemainingSeconds
        val overtimeSeconds = snap.overtimeSeconds
        val deadlineInfo = TimeFormatting.formatDeadlineCountdown(item.deadlineMillis, nowMillis, isDone = false)
        val timerSummary = when {
            overtimeSeconds > 0L -> "+${TimeFormatting.formatDurationClock(overtimeSeconds)} overtime"
            else -> "${TimeFormatting.formatDurationClock(remainingSeconds)} left"
        }

        val subHeader = buildString {
            append(timerSummary)
            if (projectName.isNotBlank()) {
                append(" • ")
                append(projectName)
            }
        }

        val statusBody = buildString {
            if (isBreak) {
                append("Break ${snap.currentPeriodIndex} • Rest & recharge")
            } else {
                append("Work ${snap.currentPeriodIndex} of ${snap.totalWorkPeriods}")
                if (snap.totalBreakCount > 0L) {
                    append(" • Focus period")
                }
            }
            if (item.deadlineMillis > 0L) {
                append(" • ${deadlineInfo.label}")
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_item_id", item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pausePendingIntent = PendingIntent.getService(
            context,
            101,
            Intent(context, WorkSessionForegroundService::class.java).apply {
                action = ACTION_PAUSE_SESSION
                putExtra(WorkSessionForegroundService.EXTRA_ITEM_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            context,
            103,
            Intent(context, WorkSessionForegroundService::class.java).apply {
                action = ACTION_NEXT_PHASE
                putExtra(WorkSessionForegroundService.EXTRA_ITEM_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completePendingIntent = PendingIntent.getService(
            context,
            102,
            Intent(context, WorkSessionForegroundService::class.java).apply {
                action = ACTION_COMPLETE_SESSION
                putExtra(WorkSessionForegroundService.EXTRA_ITEM_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val overallThousand = (snap.overallFraction * 1000f).toInt().coerceIn(0, 1000)

        if (Build.VERSION.SDK_INT >= 36) {
            val segments = buildPomodoroSegments(snap.slots)
            val restPoints = buildRestBoundaryPoints(snap.slots)

            val progressStyle = Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(overallThousand)
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_tracker_pulse))
                .setProgressSegments(segments)
                .apply {
                    if (restPoints.isNotEmpty()) {
                        setProgressPoints(restPoints)
                    }
                }

            val shortChip = TimeFormatting.formatLiveChipText(
                remainingSeconds = remainingSeconds,
                overtimeSeconds = overtimeSeconds,
                isPaused = false,
                isBreak = isBreak
            )

            val pauseAction = Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_notification_pulse),
                context.getString(R.string.notif_action_pause),
                pausePendingIntent
            ).build()

            val nextLabel = if (isBreak) {
                "Skip Break"
            } else if (snap.nextSlot != null) {
                context.getString(R.string.notif_action_next)
            } else {
                context.getString(R.string.notif_action_done)
            }
            val nextTargetIntent = if (snap.nextSlot != null) nextPendingIntent else completePendingIntent
            val secondaryAction = Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_tracker_pulse),
                nextLabel,
                nextTargetIntent
            ).build()

            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_pulse)
                .setContentTitle(item.title)
                .setSubText(subHeader)
                .setContentText(statusBody)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setUsesChronometer(false)
                .setContentIntent(contentIntent)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(shortChip)
                .setStyle(progressStyle)
                .addAction(pauseAction)
                .addAction(secondaryAction)

            return builder.build()
        }

        val compatBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setContentTitle(item.title)
            .setSubText(subHeader)
            .setContentText(statusBody)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setContentIntent(contentIntent)
            .setProgress(1000, overallThousand, false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, context.getString(R.string.notif_action_pause), pausePendingIntent)
            .addAction(0, context.getString(R.string.notif_action_done), completePendingIntent)

        return compatBuilder.build()
    }

    /**
     * Maps each Pomodoro work period & break slot to proportional Segment boxes on Android 16 ProgressStyle.
     * Work periods are styled in warm Indigo/Coral depending on completion; Break slots appear as distinct Mint rest boxes.
     */
    private fun buildPomodoroSegments(slots: List<PomodoroSlot>): List<Notification.ProgressStyle.Segment> {
        if (slots.isEmpty()) {
            return listOf(
                Notification.ProgressStyle.Segment(1000).setColor(Color.parseColor("#FF5E3A"))
            )
        }
        val totalMinutes = slots.sumOf { it.durationMinutes }.coerceAtLeast(1L)
        val rawLengths = slots.map { slot ->
            ((slot.durationMinutes.toFloat() / totalMinutes.toFloat()) * 1000f).toInt().coerceAtLeast(12)
        }.toMutableList()
        val sum = rawLengths.sum()
        if (sum != 1000 && rawLengths.isNotEmpty()) {
            val diff = 1000 - sum
            rawLengths[rawLengths.lastIndex] = (rawLengths.last() + diff).coerceAtLeast(10)
        }

        return slots.mapIndexed { index, slot ->
            val segmentColor = if (slot.isBreak) {
                // Rest blocks visually stand out in cool Mint/Teal
                Color.parseColor("#0E9F6E")
            } else {
                // Work period blocks use Coral when active/done, Indigo when upcoming
                Color.parseColor("#4B4DED")
            }
            Notification.ProgressStyle.Segment(rawLengths[index]).setColor(segmentColor)
        }
    }

    /**
     * Places clear Point markers at the start of every Break (rest box) along the Pomodoro bar.
     */
    private fun buildRestBoundaryPoints(slots: List<PomodoroSlot>): List<Notification.ProgressStyle.Point> {
        if (slots.size <= 1) return emptyList()
        val totalMinutes = slots.sumOf { it.durationMinutes }.coerceAtLeast(1L)
        val points = mutableListOf<Notification.ProgressStyle.Point>()
        var accumulatedMinutes = 0L
        slots.forEach { slot ->
            if (slot.isBreak) {
                val pos = ((accumulatedMinutes.toFloat() / totalMinutes.toFloat()) * 1000f)
                    .toInt()
                    .coerceIn(1, 999)
                points += Notification.ProgressStyle.Point(pos).setColor(Color.parseColor("#0E9F6E"))
            }
            accumulatedMinutes += slot.durationMinutes
        }
        return points
    }

    fun sendEndingSoonAlert(
        context: Context,
        itemId: String,
        title: String,
        isBreak: Boolean,
        remainingSeconds: Long
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val contentIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_item_id", itemId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertTitle = if (isBreak) "Break ending soon" else "Work period almost done"
        val timeText = TimeFormatting.formatDurationClock(remainingSeconds)
        val body = "\"$title\" has $timeText left in this ${if (isBreak) "break" else "work period"}."

        val notif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setContentTitle(alertTitle)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(ENDING_SOON_ALERT_ID, notif)
    }

    fun sendPhaseTransitionAlert(
        context: Context,
        itemId: String,
        title: String,
        nextSlot: PomodoroSlot?
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val contentIntent = PendingIntent.getActivity(
            context,
            11,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_item_id", itemId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (alertTitle, body) = when {
            nextSlot == null -> "Pomodoro complete!" to "Finished all focus periods for \"$title\"."
            nextSlot.isBreak -> "Time for a ${nextSlot.durationMinutes}m break!" to "Great work on \"$title\". Rest before your next focus period."
            else -> "Break over — Back to work!" to "Work period ${nextSlot.index} (${nextSlot.durationMinutes}m) started for \"$title\"."
        }

        val notif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setContentTitle(alertTitle)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(PHASE_TRANSITION_ALERT_ID, notif)
    }
}
