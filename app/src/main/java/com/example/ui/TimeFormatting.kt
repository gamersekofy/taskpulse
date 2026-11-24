package com.example.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatting {

    fun formatDurationClock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatMinutesCompact(minutes: Long): String {
        val safe = minutes.coerceAtLeast(0L)
        val h = safe / 60L
        val m = safe % 60L
        return when {
            h > 0L && m > 0L -> "${h}h ${m}m"
            h > 0L -> "${h}h"
            else -> "${m}m"
        }
    }

    /**
     * Short critical text for Android 16 Live Update status chip (<= 6 characters).
     */
    fun formatLiveChipText(
        remainingSeconds: Long,
        overtimeSeconds: Long,
        isPaused: Boolean,
        isBreak: Boolean = false
    ): String {
        if (isPaused) return "Paused"
        if (overtimeSeconds > 0L) {
            val mins = (overtimeSeconds / 60L).coerceAtLeast(1L)
            return if (mins >= 60L) "+${mins / 60L}h" else "+${mins}m"
        }
        val prefix = if (isBreak) "R " else ""
        val core = when {
            remainingSeconds >= 3600L -> "${remainingSeconds / 3600L}h${(remainingSeconds % 3600L) / 60L}m"
            remainingSeconds >= 60L -> "${(remainingSeconds + 59L) / 60L}m"
            else -> "${remainingSeconds}s"
        }
        return "$prefix$core".take(6)
    }

    fun formatDeadlineCountdown(deadlineMillis: Long, nowMillis: Long, isDone: Boolean): DeadlineDisplay {
        if (deadlineMillis <= 0L) {
            return DeadlineDisplay("No deadline", DeadlineUrgency.NONE)
        }
        if (isDone) {
            return DeadlineDisplay("Completed", DeadlineUrgency.DONE)
        }
        val diffMillis = deadlineMillis - nowMillis
        if (diffMillis <= 0L) {
            val overdueSec = (-diffMillis) / 1000L
            val label = when {
                overdueSec < 60L -> "Overdue just now"
                overdueSec < 3600L -> "Overdue ${overdueSec / 60L}m"
                overdueSec < 86400L -> "Overdue ${overdueSec / 3600L}h ${(overdueSec % 3600L) / 60L}m"
                else -> "Overdue ${overdueSec / 86400L}d"
            }
            return DeadlineDisplay(label, DeadlineUrgency.OVERDUE)
        }

        val sec = diffMillis / 1000L
        return when {
            sec < 3600L -> {
                val mins = (sec / 60L).coerceAtLeast(1L)
                DeadlineDisplay("Due in ${mins}m", DeadlineUrgency.CRITICAL)
            }
            sec < 6 * 3600L -> {
                val hrs = sec / 3600L
                val mins = (sec % 3600L) / 60L
                DeadlineDisplay("Due in ${hrs}h ${mins}m", DeadlineUrgency.SOON)
            }
            sec < 86400L -> {
                val hrs = sec / 3600L
                DeadlineDisplay("Due in ${hrs}h", DeadlineUrgency.SOON)
            }
            else -> {
                val days = sec / 86400L
                val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                val urgency = if (days <= 2L) DeadlineUrgency.UPCOMING else DeadlineUrgency.COMFORTABLE
                DeadlineDisplay("Due ${dateFmt.format(Date(deadlineMillis))}", urgency)
            }
        }
    }

    fun formatScheduleWindow(startMillis: Long, durationMinutes: Long, nowMillis: Long): String {
        if (startMillis <= 0L) return "Unscheduled • ${formatMinutesCompact(durationMinutes)}"
        val endMillis = startMillis + durationMinutes * 60_000L
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dayLabel = formatRelativeDay(startMillis, nowMillis)
        return "$dayLabel, ${timeFmt.format(Date(startMillis))} – ${timeFmt.format(Date(endMillis))}"
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "Not set"
        val fmt = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatRelativeDay(targetMillis: Long, nowMillis: Long): String {
        val targetCal = Calendar.getInstance().apply { timeInMillis = targetMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        val targetDay = targetCal.get(Calendar.DAY_OF_YEAR)
        val targetYear = targetCal.get(Calendar.YEAR)
        val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)
        val nowYear = nowCal.get(Calendar.YEAR)

        if (targetYear == nowYear && targetDay == nowDay) return "Today"

        val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        if (targetYear == tomorrowCal.get(Calendar.YEAR) && targetDay == tomorrowCal.get(Calendar.DAY_OF_YEAR)) {
            return "Tomorrow"
        }

        val yesterdayCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (targetYear == yesterdayCal.get(Calendar.YEAR) && targetDay == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday"
        }

        return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(targetMillis))
    }

    fun isSameDay(aMillis: Long, bMillis: Long): Boolean {
        if (aMillis <= 0L || bMillis <= 0L) return false
        val calA = Calendar.getInstance().apply { timeInMillis = aMillis }
        val calB = Calendar.getInstance().apply { timeInMillis = bMillis }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }
}

enum class DeadlineUrgency {
    NONE,
    DONE,
    COMFORTABLE,
    UPCOMING,
    SOON,
    CRITICAL,
    OVERDUE
}

data class DeadlineDisplay(
    val label: String,
    val urgency: DeadlineUrgency
)
