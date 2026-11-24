package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PomodoroPhase
import com.example.data.PomodoroSlot
import com.example.data.PomodoroSlotState
import com.example.data.Project
import com.example.data.WorkItem
import com.example.data.WorkPriority
import com.example.data.WorkStatus
import com.example.ui.DeadlineUrgency
import com.example.ui.TimeFormatting
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CountdownCondensedLargeStyle
import com.example.ui.theme.CountdownCondensedMediumStyle
import com.example.ui.theme.DarkAmberContainer
import com.example.ui.theme.DarkMintContainer
import com.example.ui.theme.DarkMintTertiary
import com.example.ui.theme.DarkOnAmberContainer
import com.example.ui.theme.DarkOnMintContainer
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnMintContainer

/**
 * Intercepts secondary (right-click) pointer events on [PointerEventPass.Initial] across Press, Move,
 * and Release so synthesized click/tap detectors (e.g., `combinedClickable`) do NOT also treat a
 * right-click (such as screen-cast Link to Windows mouse forwarding) as a primary card tap.
 */
fun Modifier.consumeSecondaryClickForContextMenu(
    onSecondaryClick: () -> Unit
): Modifier = composed {
    pointerInput(onSecondaryClick) {
        awaitPointerEventScope {
            var secondaryActive = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when {
                    event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                        secondaryActive = true
                        event.changes.forEach { it.consume() }
                        onSecondaryClick()
                    }
                    secondaryActive -> {
                        event.changes.forEach { it.consume() }
                        if (!event.changes.any { it.pressed }) {
                            secondaryActive = false
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveSessionHeroCard(
    item: WorkItem,
    project: Project?,
    nowMillis: Long,
    hasNotificationPermission: Boolean,
    canPostPromotedLiveUpdates: Boolean,
    isAndroid16OrAbove: Boolean,
    onTogglePauseResume: () -> Unit,
    onAdvancePhase: () -> Unit,
    onComplete: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onInspectItem: () -> Unit,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = rememberExpressiveHaptics()
    val darkTheme = isSystemInDarkTheme()
    val isRunning = item.statusEnum == WorkStatus.IN_PROGRESS
    val snap = item.computePomodoroSnapshot(nowMillis)
    val isBreak = snap.currentPhase == PomodoroPhase.BREAK
    val elapsedWork = item.liveElapsedSeconds(nowMillis)

    val phaseProgress by animateFloatAsState(
        targetValue = snap.phaseFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "live_phase_progress"
    )

    val mintTone = if (darkTheme) DarkMintTertiary else MintTertiary
    val workAccentColor = project?.accent?.vibrantColor(darkTheme) ?: MaterialTheme.colorScheme.primary
    val accentColor = if (isBreak) mintTone else workAccentColor
    val onAccentColor = if (isBreak) {
        if (darkTheme) Color(0xFF003823) else Color.White
    } else {
        project?.accent?.onVibrantColor(darkTheme) ?: MaterialTheme.colorScheme.onPrimary
    }

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(
            width = if (isRunning) 1.5.dp else 1.dp,
            color = if (isRunning) accentColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onInspectItem)
            .testTag("live_session_hero_card")
    ) {
        Column(
            modifier = Modifier.padding(if (compactMode) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val phaseHeader = when {
                    !isRunning && isBreak -> "BREAK ${snap.currentPeriodIndex} PAUSED"
                    !isRunning -> "WORK ${snap.currentPeriodIndex} OF ${snap.totalWorkPeriods} • PAUSED"
                    isBreak -> "BREAK ${snap.currentPeriodIndex} OF ${snap.totalBreakCount.coerceAtLeast(1L)}"
                    else -> "WORK ${snap.currentPeriodIndex} OF ${snap.totalWorkPeriods}"
                }
                Text(
                    text = phaseHeader,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRunning) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Only shown when notification alerts or Android 16 Live Updates promotion is turned off
                if (!hasNotificationPermission || (isAndroid16OrAbove && !canPostPromotedLiveUpdates)) {
                    LiveUpdateStatusChip(
                        hasPermission = hasNotificationPermission,
                        canPostPromoted = canPostPromotedLiveUpdates,
                        isAndroid16OrAbove = isAndroid16OrAbove,
                        onRequestPermission = onRequestNotificationPermission,
                        onOpenSettings = onOpenLiveUpdateSettings
                    )
                }
            }

            if (compactMode) {
                // Compact phone landscape header: Title on left, tall condensed countdown on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (project != null) {
                            ProjectAccentBadge(project = project)
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (snap.overtimeSeconds > 0L) "OVERTIME" else if (isBreak) "REST LEFT" else "PERIOD LEFT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snap.overtimeSeconds > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (snap.overtimeSeconds > 0L) {
                                "+${TimeFormatting.formatDurationClock(snap.overtimeSeconds)}"
                            } else {
                                TimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)
                            },
                            style = CountdownCondensedMediumStyle,
                            color = if (snap.overtimeSeconds > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (project != null) {
                        ProjectAccentBadge(project = project)
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = when {
                                snap.overtimeSeconds > 0L -> "OVERTIME"
                                isBreak -> "REST REMAINING"
                                else -> "WORK PERIOD REMAINING"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snap.overtimeSeconds > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (snap.overtimeSeconds > 0L) {
                                "+${TimeFormatting.formatDurationClock(snap.overtimeSeconds)}"
                            } else {
                                TimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)
                            },
                            style = CountdownCondensedLargeStyle,
                            color = if (snap.overtimeSeconds > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${TimeFormatting.formatDurationClock(elapsedWork)} focused",
                            style = MonoBadgeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${snap.totalWorkPeriods}×${item.effectiveWorkMinutes}m focus • ${snap.totalBreakCount}×${item.effectiveBreakMinutes}m rest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Pomodoro period timeline strip (Work blocks vs Rest blocks)
            PomodoroTimelineStrip(
                slots = snap.slots,
                workAccent = workAccentColor
            )

            LinearWavyProgressIndicator(
                progress = { phaseProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                color = if (snap.overtimeSeconds > 0L) MaterialTheme.colorScheme.error else accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                amplitude = { p -> if (isRunning && p > 0f && p < 1f) 1f else 0f }
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val toggleInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        haptics.confirm()
                        onTogglePauseResume()
                    },
                    shapes = ButtonDefaults.shapes(),
                    interactionSource = toggleInteraction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = onAccentColor
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .bouncyPressScale(toggleInteraction)
                        .testTag("hero_toggle_pause_button")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "Pause" else "Resume",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (snap.nextSlot != null) {
                    val nextInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            onAdvancePhase()
                        },
                        shapes = ButtonDefaults.shapes(),
                        interactionSource = nextInteraction,
                        modifier = Modifier
                            .height(48.dp)
                            .bouncyPressScale(nextInteraction)
                            .testTag("hero_next_phase_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isBreak) "Skip Break" else "Next Period",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                val completeInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        haptics.confirm()
                        onComplete()
                    },
                    shapes = ButtonDefaults.shapes(),
                    interactionSource = completeInteraction,
                    modifier = Modifier
                        .height(48.dp)
                        .bouncyPressScale(completeInteraction)
                        .testTag("hero_complete_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Complete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun PomodoroTimelineStrip(
    slots: List<PomodoroSlot>,
    workAccent: Color,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) return
    val darkTheme = isSystemInDarkTheme()
    val breakActive = if (darkTheme) DarkMintTertiary else MintTertiary
    val breakUpcoming = if (darkTheme) DarkMintContainer else MintContainer
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEach { slot ->
            val weightVal = slot.durationMinutes.toFloat().coerceAtLeast(3f)
            val color = when {
                slot.isBreak && slot.state == PomodoroSlotState.COMPLETED -> breakActive.copy(alpha = 0.5f)
                slot.isBreak && slot.state == PomodoroSlotState.ACTIVE -> breakActive
                slot.isBreak -> breakUpcoming
                slot.state == PomodoroSlotState.COMPLETED -> workAccent.copy(alpha = 0.55f)
                slot.state == PomodoroSlotState.ACTIVE -> workAccent
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            val height = if (slot.state == PomodoroSlotState.ACTIVE) 10.dp else 7.dp
            Box(
                modifier = Modifier
                    .weight(weightVal)
                    .height(height)
                    .clip(if (slot.isBreak) RoundedCornerShape(3.dp) else CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun LiveUpdateStatusChip(
    hasPermission: Boolean,
    canPostPromoted: Boolean,
    isAndroid16OrAbove: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val (bg, fg, label, icon) = when {
        !hasPermission -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Enable Live Alerts",
            Icons.Rounded.NotificationsOff
        )
        isAndroid16OrAbove && !canPostPromoted -> listOf(
            if (darkTheme) DarkAmberContainer else AmberContainer,
            if (darkTheme) DarkOnAmberContainer else OnAmberContainer,
            "Allow Live Updates",
            Icons.Rounded.Settings
        )
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bg as Color,
        contentColor = fg as Color,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable {
                if (!hasPermission) onRequestPermission()
                else if (isAndroid16OrAbove && !canPostPromoted) onOpenSettings()
            }
            .testTag("live_update_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = label as String,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun WorkItemExpressiveCard(
    item: WorkItem,
    project: Project?,
    nowMillis: Long,
    onStartOrPause: () -> Unit,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onAdvancePhase: (() -> Unit)? = null,
    onPostpone: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberExpressiveHaptics()
    val darkTheme = isSystemInDarkTheme()
    val isDone = item.statusEnum == WorkStatus.DONE
    val isRunning = item.statusEnum == WorkStatus.IN_PROGRESS
    val snap = item.computePomodoroSnapshot(nowMillis)
    val isBreak = snap.currentPhase == PomodoroPhase.BREAK
    val mintTone = if (darkTheme) DarkMintTertiary else MintTertiary
    val accent = if (isRunning && isBreak) {
        mintTone
    } else {
        project?.accent?.vibrantColor(darkTheme) ?: MaterialTheme.colorScheme.primary
    }

    val progress by animateFloatAsState(
        targetValue = item.liveProgressFraction(nowMillis),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "item_progress"
    )
    val cardColor by animateColorAsState(
        targetValue = when {
            isRunning -> MaterialTheme.colorScheme.surfaceContainer
            isDone -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "card_bg"
    )

    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(
            width = if (isRunning) 1.5.dp else 1.dp,
            color = if (isRunning) accent.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .consumeSecondaryClickForContextMenu {
                haptics.thud()
                showContextMenu = true
            }
            .combinedClickable(
                onClick = {
                    haptics.select()
                    onClick()
                },
                onLongClick = {
                    haptics.thud()
                    showContextMenu = true
                }
            )
            .testTag("work_item_card_${item.id}")
    ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.confirm()
                                onToggleComplete()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("complete_toggle_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = if (isDone) "Reopen task" else "Mark task completed",
                                tint = if (isDone) MintTertiary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (project != null) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!isDone) {
                            val actionInteraction = remember { MutableInteractionSource() }
                            FilledTonalButton(
                                onClick = {
                                    haptics.confirm()
                                    onStartOrPause()
                                },
                                shapes = ButtonDefaults.shapes(),
                                interactionSource = actionInteraction,
                                modifier = Modifier
                                    .height(48.dp)
                                    .bouncyPressScale(actionInteraction)
                                    .testTag("session_action_${item.id}")
                            ) {
                                Icon(
                                    imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isRunning) "Pause session" else "Start Pomodoro",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isRunning) "Pause" else "Focus",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    haptics.select()
                                    showContextMenu = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("work_item_menu_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More options for ${item.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showContextMenu,
                                onDismissRequest = { showContextMenu = false }
                            ) {
                                if (!isDone) {
                                    DropdownMenuItem(
                                        text = { Text(if (isRunning) "Pause Pomodoro" else "Start Work Period") },
                                        leadingIcon = {
                                            Icon(
                                                if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showContextMenu = false
                                            haptics.confirm()
                                            onStartOrPause()
                                        }
                                    )
                                    if (onAdvancePhase != null && snap.nextSlot != null) {
                                        DropdownMenuItem(
                                            text = { Text(if (isBreak) "Skip Break" else "Next Pomodoro Period") },
                                            leadingIcon = { Icon(Icons.Rounded.SkipNext, contentDescription = null) },
                                            onClick = {
                                                showContextMenu = false
                                                haptics.select()
                                                onAdvancePhase()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Mark Completed") },
                                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                                        onClick = {
                                            showContextMenu = false
                                            haptics.confirm()
                                            onToggleComplete()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Reopen Work Item") },
                                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                        onClick = {
                                            showContextMenu = false
                                            haptics.select()
                                            onToggleComplete()
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("View Details") },
                                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                    onClick = {
                                        showContextMenu = false
                                        onClick()
                                    }
                                )

                                if (onEdit != null && !isDone) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                        onClick = {
                                            showContextMenu = false
                                            onEdit()
                                        }
                                    )
                                }

                                if (onPostpone != null && !isDone) {
                                    DropdownMenuItem(
                                        text = { Text("Postpone +1 Day") },
                                        leadingIcon = { Icon(Icons.Rounded.EventRepeat, contentDescription = null) },
                                        onClick = {
                                            showContextMenu = false
                                            haptics.select()
                                            onPostpone()
                                        }
                                    )
                                }

                                if (onDuplicate != null) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            showContextMenu = false
                                            haptics.select()
                                            onDuplicate()
                                        }
                                    )
                                }

                                if (onDelete != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showContextMenu = false
                                            haptics.reject()
                                            onDelete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.scheduledStartMillis > 0L) {
                        MetaPill(
                            icon = Icons.Rounded.Schedule,
                            text = TimeFormatting.formatScheduleWindow(
                                item.scheduledStartMillis,
                                item.totalPomodoroMinutes,
                                nowMillis
                            )
                        )
                    }

                    MetaPill(
                        icon = if (isRunning && isBreak) Icons.Rounded.Coffee else Icons.Rounded.Timer,
                        text = when {
                            isRunning && isBreak ->
                                "Break ${snap.currentPeriodIndex} • ${TimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)} left"
                            isRunning || item.phaseElapsedSeconds > 0L || item.elapsedSeconds > 0L ->
                                "Work ${snap.currentPeriodIndex}/${snap.totalWorkPeriods} • ${TimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)} left"
                            else ->
                                "${snap.totalWorkPeriods}×${item.effectiveWorkMinutes}m" +
                                    if (snap.totalBreakCount > 0L) " + ${snap.totalBreakCount}×${item.effectiveBreakMinutes}m rest" else ""
                        }
                    )

                    DeadlineBadge(
                        deadlineMillis = item.deadlineMillis,
                        nowMillis = nowMillis,
                        isDone = isDone
                    )

                    if (item.priorityEnum == WorkPriority.HIGH && !isDone) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(
                                text = "Urgent",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = if (isDone) mintTone else accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    amplitude = { p -> if (isRunning && p > 0f && p < 1f) 1f else 0f }
                )
            }
        }
}

@Composable
fun ProjectAccentBadge(project: Project) {
    val darkTheme = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(50),
        color = project.accent.badgeContainerColor(darkTheme),
        contentColor = project.accent.badgeContentColor(darkTheme)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = project.icon.vector,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = project.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DeadlineBadge(
    deadlineMillis: Long,
    nowMillis: Long,
    isDone: Boolean
) {
    val display = TimeFormatting.formatDeadlineCountdown(deadlineMillis, nowMillis, isDone)
    if (display.urgency == DeadlineUrgency.NONE) return
    val darkTheme = isSystemInDarkTheme()

    val (bg, fg) = when (display.urgency) {
        DeadlineUrgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DeadlineUrgency.CRITICAL -> (if (darkTheme) DarkAmberContainer else AmberContainer) to (if (darkTheme) DarkOnAmberContainer else OnAmberContainer)
        DeadlineUrgency.SOON -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DeadlineUrgency.UPCOMING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DeadlineUrgency.COMFORTABLE -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        DeadlineUrgency.DONE -> (if (darkTheme) DarkMintContainer else MintContainer) to (if (darkTheme) DarkOnMintContainer else OnMintContainer)
        DeadlineUrgency.NONE -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = display.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MetaPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberExpressiveHaptics()
    val actionInteraction = remember { MutableInteractionSource() }
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_tasks),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(155.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    haptics.select()
                    onAction()
                },
                shapes = ButtonDefaults.shapes(),
                interactionSource = actionInteraction,
                modifier = Modifier
                    .height(48.dp)
                    .bouncyPressScale(actionInteraction)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
