package com.example.wear.liveupdates

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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.shared.SharedPomodoroSlot
import com.example.shared.SharedTimeFormatting
import com.example.shared.SharedWorkItem
import com.example.wear.R
import com.example.wear.WatchMainActivity

object WatchPomodoroNotificationHelper {

    const val CHANNEL_ID = "wear_pomodoro_live"
    const val ALERT_CHANNEL_ID = "wear_pomodoro_alerts"
    const val NOTIFICATION_ID = 5207
    const val ENDING_SOON_ALERT_ID = 5208
    const val PHASE_TRANSITION_ALERT_ID = 5209

    const val ACTION_PAUSE_SESSION = "com.example.wear.liveupdates.ACTION_PAUSE"
    const val ACTION_NEXT_PHASE = "com.example.wear.liveupdates.ACTION_NEXT"
    const val ACTION_COMPLETE_SESSION = "com.example.wear.liveupdates.ACTION_COMPLETE"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val liveExisting = manager.getNotificationChannel(CHANNEL_ID)
        if (liveExisting == null || liveExisting.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.live_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.live_update_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
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

    fun buildSessionNotification(
        context: Context,
        item: SharedWorkItem,
        projectName: String,
        nowMillis: Long
    ): Notification {
        ensureChannels(context)

        val snap = item.computePomodoroSnapshot(nowMillis)
        val isBreak = snap.currentPhase == "BREAK"
        val remainingSeconds = snap.phaseRemainingSeconds
        val overtimeSeconds = snap.overtimeSeconds
        val phaseShort = if (isBreak) "Break ${snap.currentPeriodIndex}" else "Work ${snap.currentPeriodIndex}/${snap.totalWorkPeriods}"
        val timerSummary = if (overtimeSeconds > 0L) {
            "+${SharedTimeFormatting.formatDurationClock(overtimeSeconds)} overtime"
        } else {
            "${SharedTimeFormatting.formatDurationClock(remainingSeconds)} left"
        }

        val subHeader = buildString {
            append(timerSummary)
            if (projectName.isNotBlank()) {
                append(" • ")
                append(projectName)
            }
        }

        val deadlineInfo = SharedTimeFormatting.formatDeadlineCountdown(item.deadlineMillis, nowMillis, isDone = false)
        val statusBody = buildString {
            append(phaseShort)
            if (item.deadlineMillis > 0L) {
                append(" • ")
                append(deadlineInfo.label)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            200,
            Intent(context, WatchMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_item_id", item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            context,
            201,
            Intent(context, WatchPomodoroForegroundService::class.java).apply {
                action = ACTION_PAUSE_SESSION
                putExtra(WatchPomodoroForegroundService.EXTRA_ITEM_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            context,
            202,
            Intent(context, WatchPomodoroForegroundService::class.java).apply {
                action = ACTION_NEXT_PHASE
                putExtra(WatchPomodoroForegroundService.EXTRA_ITEM_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = PendingIntent.getService(
            context,
            203,
            Intent(context, WatchPomodoroForegroundService::class.java).apply {
                action = ACTION_COMPLETE_SESSION
                putExtra(WatchPomodoroForegroundService.EXTRA_ITEM_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val overallThousand = (snap.overallFraction * 1000f).toInt().coerceIn(0, 1000)

        // Wear OS 7+ (API 36+) native local Live Updates with ProgressStyle + Status Chip
        if (Build.VERSION.SDK_INT >= 36) {
            val segments = buildProgressSegments(snap.slots)
            val restPoints = buildRestPoints(snap.slots)
            val progressStyle = Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(overallThousand)
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_tracker_pulse))
                .setProgressSegments(segments)
                .apply {
                    if (restPoints.isNotEmpty()) setProgressPoints(restPoints)
                }

            val shortChip = SharedTimeFormatting.formatLiveChipText(
                remainingSeconds = remainingSeconds,
                overtimeSeconds = overtimeSeconds,
                isPaused = false,
                isBreak = isBreak
            )

            val targetEpochWhen = if (remainingSeconds > 0L) {
                nowMillis + remainingSeconds * 1000L
            } else {
                nowMillis - overtimeSeconds * 1000L
            }

            val nextLabel = if (isBreak) {
                "Skip Break"
            } else if (snap.nextSlot != null) {
                context.getString(R.string.notif_action_next)
            } else {
                context.getString(R.string.notif_action_done)
            }
            val secondaryIntent = if (snap.nextSlot != null) nextIntent else completeIntent

            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_pulse)
                .setContentTitle(item.title)
                .setSubText(subHeader)
                .setContentText(statusBody)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(remainingSeconds > 0L)
                .setWhen(targetEpochWhen)
                .setShowWhen(true)
                .setContentIntent(contentIntent)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(shortChip)
                .setStyle(progressStyle)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notification_pulse),
                        context.getString(R.string.notif_action_pause),
                        pauseIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_tracker_pulse),
                        nextLabel,
                        secondaryIntent
                    ).build()
                )
                .build()
        }

        // Wear OS 4-6 (including Samsung One UI Watch): Ongoing Activity + Live TimerPart
        val targetEpochWhen = if (remainingSeconds > 0L) {
            nowMillis + remainingSeconds * 1000L
        } else {
            nowMillis - overtimeSeconds * 1000L
        }
        val compatBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setContentTitle(item.title)
            .setSubText(subHeader)
            .setContentText(statusBody)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(remainingSeconds > 0L)
            .setWhen(targetEpochWhen)
            .setContentIntent(contentIntent)
            .setProgress(1000, overallThousand, false)
            .addAction(0, context.getString(R.string.notif_action_pause), pauseIntent)
            .addAction(
                0,
                if (snap.nextSlot != null) context.getString(R.string.notif_action_next) else context.getString(R.string.notif_action_done),
                if (snap.nextSlot != null) nextIntent else completeIntent
            )

        val elapsedNow = SystemClock.elapsedRealtime()
        val statusPart = if (overtimeSeconds > 0L) {
            Status.StopwatchPart(elapsedNow - overtimeSeconds * 1000L)
        } else {
            Status.TimerPart(elapsedNow + remainingSeconds * 1000L)
        }
        val ongoingStatus = Status.Builder()
            .addTemplate("#phase# • #time#")
            .addPart("phase", Status.TextPart(if (isBreak) "Break" else "Focus"))
            .addPart("time", statusPart)
            .build()

        val ongoingActivity = OngoingActivity.Builder(context.applicationContext, NOTIFICATION_ID, compatBuilder)
            .setStaticIcon(R.drawable.ic_notification_pulse)
            .setAnimatedIcon(R.drawable.ic_notification_pulse)
            .setTouchIntent(contentIntent)
            .setTitle(item.title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setStatus(ongoingStatus)
            .build()

        ongoingActivity.apply(context.applicationContext)
        return compatBuilder.build()
    }

    private fun buildProgressSegments(slots: List<SharedPomodoroSlot>): List<Notification.ProgressStyle.Segment> {
        if (slots.isEmpty()) {
            return listOf(Notification.ProgressStyle.Segment(1000).setColor(Color.parseColor("#FF5E3A")))
        }
        val totalMinutes = slots.sumOf { it.durationMinutes }.coerceAtLeast(1L)
        val rawLengths = slots.map { slot ->
            ((slot.durationMinutes.toFloat() / totalMinutes.toFloat()) * 1000f).toInt().coerceAtLeast(12)
        }.toMutableList()
        val sum = rawLengths.sum()
        if (sum != 1000 && rawLengths.isNotEmpty()) {
            rawLengths[rawLengths.lastIndex] = (rawLengths.last() + (1000 - sum)).coerceAtLeast(10)
        }
        return slots.mapIndexed { index, slot ->
            val color = if (slot.isBreak) Color.parseColor("#0E9F6E") else Color.parseColor("#4B4DED")
            Notification.ProgressStyle.Segment(rawLengths[index]).setColor(color)
        }
    }

    private fun buildRestPoints(slots: List<SharedPomodoroSlot>): List<Notification.ProgressStyle.Point> {
        if (slots.size <= 1) return emptyList()
        val totalMinutes = slots.sumOf { it.durationMinutes }.coerceAtLeast(1L)
        val points = mutableListOf<Notification.ProgressStyle.Point>()
        var acc = 0L
        slots.forEach { slot ->
            if (slot.isBreak) {
                val pos = ((acc.toFloat() / totalMinutes.toFloat()) * 1000f).toInt().coerceIn(1, 999)
                points += Notification.ProgressStyle.Point(pos).setColor(Color.parseColor("#0E9F6E"))
            }
            acc += slot.durationMinutes
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
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            210,
            Intent(context, WatchMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_item_id", itemId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = SharedTimeFormatting.formatDurationClock(remainingSeconds)
        val notif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setContentTitle(if (isBreak) "Break ending soon" else "Focus period ending")
            .setContentText("\"$title\" • $timeText left")
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
        nextSlot: SharedPomodoroSlot?
    ) {
        pulsePhaseTransitionHaptic(context)
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            211,
            Intent(context, WatchMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("focus_item_id", itemId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (alertTitle, body) = when {
            nextSlot == null -> "Pomodoro complete!" to "Finished \"$title\"."
            nextSlot.isBreak -> "Break ${nextSlot.index} (${nextSlot.durationMinutes}m)" to "Rest & recharge after \"$title\"."
            else -> "Work ${nextSlot.index} (${nextSlot.durationMinutes}m)" to "Back in focus on \"$title\"."
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

    fun pulsePhaseTransitionHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 120L, 80L, 180L), -1)
            )
        } catch (_: Exception) {
        }
    }
}
