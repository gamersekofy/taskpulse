package com.example.wear.ui.components

import android.app.RemoteInput
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.input.RemoteInputIntentHelper
import com.example.shared.SharedDeadlineUrgency
import com.example.shared.SharedPomodoroSlot
import com.example.shared.SharedProject
import com.example.shared.SharedTimeFormatting
import com.example.shared.SharedWorkItem
import com.example.wear.data.WatchProjectColor
import com.example.wear.data.WatchProjectIcon
import com.example.wear.data.WatchWorkStatus
import com.example.wear.data.accentColor
import com.example.wear.data.snapshot
import com.example.wear.data.statusEnum
import com.example.wear.ui.theme.WatchBreakMintColor
import com.example.wear.ui.theme.WatchUrgentCriticalColor
import com.example.wear.ui.theme.WatchUrgentSoonColor

private const val REMOTE_INPUT_KEY = "wear_text_input"

@Composable
fun rememberWatchTextInputLauncher(
    promptLabel: String,
    currentValue: String = "",
    onResult: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val remoteText = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(REMOTE_INPUT_KEY)
            ?.toString()
            ?.trim()
        if (!remoteText.isNullOrBlank()) {
            onResult(remoteText)
            return@rememberLauncherForActivityResult
        }
        val speechText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!speechText.isNullOrBlank()) {
            onResult(speechText)
        }
    }

    return {
        try {
            val remoteInputs = listOf(
                RemoteInput.Builder(REMOTE_INPUT_KEY)
                    .setLabel(promptLabel)
                    .build()
            )
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putTitleExtra(intent, promptLabel)
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
            launcher.launch(intent)
        } catch (_: Exception) {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (currentValue.isBlank()) promptLabel else "$promptLabel ($currentValue)")
            }
            launcher.launch(speechIntent)
        }
    }
}

@Composable
fun WatchProjectBadge(
    color: WatchProjectColor,
    icon: WatchProjectIcon,
    size: Dp = 28.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.badgeBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon.vector,
            contentDescription = null,
            tint = color.badgeInk,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun WatchDeadlineChip(
    deadlineMillis: Long,
    nowMillis: Long,
    isDone: Boolean
) {
    if (deadlineMillis <= 0L && !isDone) return
    val info = SharedTimeFormatting.formatDeadlineCountdown(deadlineMillis, nowMillis, isDone)
    val (bg, fg) = when (info.urgency) {
        SharedDeadlineUrgency.OVERDUE, SharedDeadlineUrgency.CRITICAL ->
            WatchUrgentCriticalColor.copy(alpha = 0.22f) to WatchUrgentCriticalColor
        SharedDeadlineUrgency.SOON ->
            WatchUrgentSoonColor.copy(alpha = 0.2f) to WatchUrgentSoonColor
        SharedDeadlineUrgency.DONE ->
            WatchBreakMintColor.copy(alpha = 0.18f) to WatchBreakMintColor
        else ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = info.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WatchPomodoroSlotStrip(
    slots: List<SharedPomodoroSlot>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEach { slot ->
            val pillColor = when {
                slot.isBreak && slot.isActive -> WatchBreakMintColor
                slot.isBreak && slot.isCompleted -> WatchBreakMintColor.copy(alpha = 0.72f)
                slot.isBreak -> WatchBreakMintColor.copy(alpha = 0.28f)
                slot.isActive -> accentColor
                slot.isCompleted -> accentColor.copy(alpha = 0.72f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .weight(slot.durationMinutes.toFloat().coerceAtLeast(1f))
                    .height(if (slot.isActive) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(pillColor)
            )
        }
    }
}

@Composable
fun WatchWorkItemCard(
    item: SharedWorkItem,
    project: SharedProject?,
    nowMillis: Long,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onInspect: () -> Unit,
    onToggleFocus: () -> Unit
) {
    val accent = item.accentColor(project)
    val icon = WatchProjectIcon.safeValueOf(project?.iconKey ?: "ROCKET")
    val status = item.statusEnum()
    val isRunning = status == WatchWorkStatus.IN_PROGRESS
    val isDone = status == WatchWorkStatus.DONE
    val pSnap = item.snapshot(nowMillis)
    val progress = item.liveProgressFraction(nowMillis)

    Card(
        onClick = onInspect,
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                accent.badgeBg.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (isRunning) BorderStroke(1.5.dp, accent.accent) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Completed",
                        tint = WatchBreakMintColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    WatchProjectBadge(color = accent, icon = icon, size = 24.dp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (project != null) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = accent.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isDone) {
                    IconButton(
                        onClick = onToggleFocus,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRunning) accent.accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isRunning) Color.Black else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isRunning) "Pause" else "Start focus",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val periodText = if (isRunning || status == WatchWorkStatus.PAUSED) {
                    val phaseTag = if (pSnap.currentPhase == "BREAK") "Break" else "Work ${pSnap.currentPeriodIndex}/${pSnap.totalWorkPeriods}"
                    "$phaseTag • ${SharedTimeFormatting.formatDurationClock(pSnap.phaseRemainingSeconds)}"
                } else {
                    "${item.effectiveWorkPeriods}×${item.effectiveWorkMinutes}m"
                }
                Text(
                    text = periodText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isRunning) accent.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                if (item.deadlineMillis > 0L || isDone) {
                    WatchDeadlineChip(item.deadlineMillis, nowMillis, isDone)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (isDone) WatchBreakMintColor else accent.accent)
                )
            }
        }
    }
}

@Composable
fun WatchDeleteConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    transformationSpec: TransformationSpec,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Text(message, style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = onConfirm)
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = onDismiss)
        }
    )
}
