package com.example.wear.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SegmentedCircularProgressIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.shared.SharedProject
import com.example.shared.SharedTimeFormatting
import com.example.shared.SharedWorkItem
import com.example.wear.data.SyncConnectionMode
import com.example.wear.data.WatchDashboardData
import com.example.wear.data.WatchProjectIcon
import com.example.wear.data.WatchProjectStats
import com.example.wear.data.WatchWorkPriority
import com.example.wear.data.WatchWorkStatus
import com.example.wear.data.accentColor
import com.example.wear.data.priorityEnum
import com.example.wear.data.snapshot
import com.example.wear.data.statusEnum
import com.example.wear.ui.components.WatchDeadlineChip
import com.example.wear.ui.components.WatchPomodoroSlotStrip
import com.example.wear.ui.components.WatchProjectBadge
import com.example.wear.ui.components.WatchWorkItemCard
import com.example.wear.ui.theme.WatchBreakMintColor
import com.example.wear.ui.theme.WatchCountdownLargeStyle
import com.example.wear.ui.theme.WatchHeroSerifStyle
import com.example.wear.ui.theme.rememberWatchHaptics

@Composable
fun WatchFocusPage(
    data: WatchDashboardData,
    nowMillis: Long,
    isAmbient: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onStartSession: (SharedWorkItem) -> Unit,
    onPauseSession: (SharedWorkItem) -> Unit,
    onAdvancePhase: (SharedWorkItem) -> Unit,
    onCompleteSession: (SharedWorkItem) -> Unit,
    onInspectItem: (SharedWorkItem) -> Unit,
    onAddWorkItem: () -> Unit,
    onOpenAccount: () -> Unit
) {
    val activeItem = data.activeRunningItem
    if (isAmbient && activeItem != null) {
        WatchAmbientFocusOverlay(
            item = activeItem,
            project = data.projectFor(activeItem.projectId),
            nowMillis = nowMillis
        )
        return
    }

    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val openItems = remember(data.workItems) {
        data.workItems.filter { it.statusEnum() != WatchWorkStatus.DONE }
    }
    val completedItems = remember(data.workItems) {
        data.workItems.filter { it.statusEnum() == WatchWorkStatus.DONE }
            .sortedByDescending { it.updatedAtMillis }
            .take(5)
    }
    val deadlineRadar = remember(openItems) {
        openItems.filter { it.deadlineMillis > 0L }
            .sortedBy { it.deadlineMillis }
            .take(4)
    }
    val heroCandidate = remember(data.pausedItem, openItems) {
        data.pausedItem ?: openItems.sortedWith(
            compareBy<SharedWorkItem> {
                when (it.priorityEnum()) {
                    WatchWorkPriority.HIGH -> 0
                    WatchWorkPriority.MEDIUM -> 1
                    WatchWorkPriority.LOW -> 2
                }
            }.thenBy { if (it.deadlineMillis > 0L) it.deadlineMillis else Long.MAX_VALUE }
        ).firstOrNull()
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (activeItem == null) {
                EdgeButton(
                    onClick = {
                        haptics.select()
                        onAddWorkItem()
                    },
                    modifier = Modifier
                        .testTag("watch_focus_add_item_button")
                        .scrollable(
                            listState,
                            orientation = Orientation.Vertical,
                            reverseDirection = true,
                            overscrollEffect = rememberOverscrollEffect()
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(" Item")
                }
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = if (activeItem != null) PaddingValues(horizontal = 10.dp, vertical = 14.dp) else contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            if (activeItem != null) {
                item(key = "active_ring_hero") {
                    ActivePomodoroHeroRing(
                        item = activeItem,
                        project = data.projectFor(activeItem.projectId),
                        nowMillis = nowMillis,
                        onPause = {
                            haptics.confirm()
                            onPauseSession(activeItem)
                        },
                        onNext = {
                            haptics.confirm()
                            onAdvancePhase(activeItem)
                        },
                        onComplete = {
                            haptics.confirm()
                            onCompleteSession(activeItem)
                        },
                        onInspect = {
                            haptics.select()
                            onInspectItem(activeItem)
                        }
                    )
                }

                if (!hasNotificationPermission) {
                    item(key = "notif_warning") {
                        WatchNotificationAlertCard(
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            onEnable = onRequestNotificationPermission
                        )
                    }
                }
            } else {
                if (data.syncMode == SyncConnectionMode.NEEDS_SETUP && !data.isReady) {
                    item(key = "setup_banner") {
                        WatchUnlinkedBannerCard(
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            onOpenAccount = onOpenAccount
                        )
                    }
                }

                if (!hasNotificationPermission) {
                    item(key = "notif_warning_idle") {
                        WatchNotificationAlertCard(
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            onEnable = onRequestNotificationPermission
                        )
                    }
                }

                if (heroCandidate != null) {
                    item(key = "idle_hero_${heroCandidate.id}") {
                        IdleHeroFocusCard(
                            item = heroCandidate,
                            project = data.projectFor(heroCandidate.projectId),
                            nowMillis = nowMillis,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            onStart = {
                                haptics.confirm()
                                onStartSession(heroCandidate)
                            },
                            onInspect = {
                                haptics.select()
                                onInspectItem(heroCandidate)
                            }
                        )
                    }
                } else {
                    item(key = "empty_focus") {
                        Card(
                            onClick = {
                                haptics.select()
                                onAddWorkItem()
                            },
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .transformedHeight(this, transformationSpec)
                                .fillMaxWidth()
                                .testTag("watch_focus_empty_card")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Ready to Focus",
                                    style = WatchHeroSerifStyle,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Tap + Item to plan your next Pomodoro sprint.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            if (deadlineRadar.isNotEmpty()) {
                item(key = "radar_header") {
                    WatchSectionLabel("Deadline Radar")
                }
                items(deadlineRadar, key = { "radar_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = {
                            haptics.select()
                            onInspectItem(item)
                        },
                        onToggleFocus = {
                            haptics.confirm()
                            if (item.statusEnum() == WatchWorkStatus.IN_PROGRESS) {
                                onPauseSession(item)
                            } else {
                                onStartSession(item)
                            }
                        }
                    )
                }
            }

            if (completedItems.isNotEmpty()) {
                item(key = "completed_header") {
                    WatchSectionLabel("Recently Completed")
                }
                items(completedItems, key = { "done_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = {
                            haptics.select()
                            onInspectItem(item)
                        },
                        onToggleFocus = {}
                    )
                }
            }

            item(key = "account_footer") {
                WatchSyncStatusPillCard(
                    data = data,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    onClick = {
                        haptics.select()
                        onOpenAccount()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActivePomodoroHeroRing(
    item: SharedWorkItem,
    project: SharedProject?,
    nowMillis: Long,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
    onInspect: () -> Unit
) {
    val snap = item.snapshot(nowMillis)
    val isBreak = snap.currentPhase == "BREAK"
    val accent = item.accentColor(project)
    val ringColor = if (isBreak) WatchBreakMintColor else accent.accent
    val clockText = if (snap.overtimeSeconds > 0L) {
        "+${SharedTimeFormatting.formatDurationClock(snap.overtimeSeconds)}"
    } else {
        SharedTimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)
    }
    val phaseLabel = if (isBreak) {
        "BREAK ${snap.currentPeriodIndex}"
    } else {
        "WORK ${snap.currentPeriodIndex} OF ${snap.totalWorkPeriods}"
    }

    val pauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val doneInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .testTag("watch_active_pomodoro_ring"),
        contentAlignment = Alignment.Center
    ) {
        SegmentedCircularProgressIndicator(
            segmentCount = snap.slots.size.coerceAtLeast(1),
            progress = { snap.overallFraction },
            strokeWidth = 7.dp,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = ringColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(CircularProgressIndicatorDefaults.FullScreenPadding)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (project != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.badgeBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = project.name,
                        style = WatchHeroSerifStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                        color = accent.badgeInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ringColor,
                maxLines = 1
            )

            Text(
                text = clockText,
                style = WatchCountdownLargeStyle,
                color = if (snap.overtimeSeconds > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onPause,
                    interactionSource = pauseInteraction,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ringColor,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .animateWidth(pauseInteraction)
                        .testTag("watch_active_pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause session"
                    )
                }

                IconButton(
                    onClick = onNext,
                    interactionSource = nextInteraction,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier
                        .animateWidth(nextInteraction)
                        .testTag("watch_active_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Pomodoro period"
                    )
                }

                IconButton(
                    onClick = onComplete,
                    interactionSource = doneInteraction,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = WatchBreakMintColor.copy(alpha = 0.25f),
                        contentColor = WatchBreakMintColor
                    ),
                    modifier = Modifier
                        .animateWidth(doneInteraction)
                        .testTag("watch_active_complete_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Complete item"
                    )
                }
            }

            Button(
                onClick = onInspect,
                colors = ButtonDefaults.childButtonColors(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("watch_active_details_button")
            ) {
                Text(
                    text = "Options & Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WatchAmbientFocusOverlay(
    item: SharedWorkItem,
    project: SharedProject?,
    nowMillis: Long
) {
    val snap = item.snapshot(nowMillis)
    val isBreak = snap.currentPhase == "BREAK"
    val ringColor = Color.White.copy(alpha = 0.65f)
    val clockText = if (snap.overtimeSeconds > 0L) {
        "+${SharedTimeFormatting.formatDurationClock(snap.overtimeSeconds)}"
    } else {
        SharedTimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        SegmentedCircularProgressIndicator(
            segmentCount = snap.slots.size.coerceAtLeast(1),
            progress = { snap.overallFraction },
            strokeWidth = 3.dp,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = ringColor,
                trackColor = Color.DarkGray.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(CircularProgressIndicatorDefaults.FullScreenPadding)
        )

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (project != null) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isBreak) "BREAK ${snap.currentPeriodIndex}" else "WORK ${snap.currentPeriodIndex}/${snap.totalWorkPeriods}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.LightGray
            )
            Text(
                text = clockText,
                style = WatchCountdownLargeStyle,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IdleHeroFocusCard(
    item: SharedWorkItem,
    project: SharedProject?,
    nowMillis: Long,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onInspect: () -> Unit
) {
    val accent = item.accentColor(project)
    val snap = item.snapshot(nowMillis)
    val isPaused = item.statusEnum() == WatchWorkStatus.PAUSED

    Card(
        onClick = onInspect,
        transformation = transformation,
        border = BorderStroke(1.2.dp, accent.accent.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("watch_idle_hero_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPaused) "PAUSED SPRINT" else "UP NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.accent
                )
                if (item.deadlineMillis > 0L) {
                    WatchDeadlineChip(item.deadlineMillis, nowMillis, isDone = false)
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildString {
                    if (project != null) append("${project.name} • ")
                    append("${item.effectiveWorkPeriods}×${item.effectiveWorkMinutes}m")
                    if (item.effectiveBreakCount > 0L) append(" + ${item.effectiveBreakCount}×${item.effectiveBreakMinutes}m rest")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            WatchPomodoroSlotStrip(slots = snap.slots, accentColor = accent.accent)

            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.accent,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watch_hero_start_button"),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(if (isPaused) "Resume Focus" else "Start Focus")
                }
            )
        }
    }
}

@Composable
fun WatchProjectsPage(
    data: WatchDashboardData,
    nowMillis: Long,
    onOpenProject: (String) -> Unit,
    onAddProject: () -> Unit,
    onOpenAccount: () -> Unit
) {
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    haptics.select()
                    onAddProject()
                },
                modifier = Modifier
                    .testTag("watch_projects_add_button")
                    .scrollable(
                        listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(" Project")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "projects_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Projects",
                        style = WatchHeroSerifStyle,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = onOpenAccount,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("watch_projects_sync_chip")
                    ) {
                        Text(
                            text = data.syncMode.shortLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (data.projectsWithStats.isEmpty()) {
                item(key = "projects_empty") {
                    Card(
                        onClick = {
                            haptics.select()
                            onAddProject()
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_projects_empty_card")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No Projects Yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Create a project on watch or sync from phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(data.projectsWithStats, key = { it.project.id }) { stats ->
                    WatchProjectCard(
                        stats = stats,
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onClick = {
                            haptics.select()
                            onOpenProject(stats.project.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchProjectCard(
    stats: WatchProjectStats,
    nowMillis: Long,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = stats.accent
    val pct = (stats.completionFraction * 100f).toInt()
    Card(
        onClick = onClick,
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("watch_project_card_${stats.project.id}")
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
                WatchProjectBadge(color = accent, icon = stats.icon, size = 28.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stats.project.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stats.doneCount}/${stats.totalCount} items • ${SharedTimeFormatting.formatMinutesCompact(stats.totalProjectedMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent.accent
                )
            }

            if (stats.project.deadlineMillis > 0L) {
                WatchDeadlineChip(stats.project.deadlineMillis, nowMillis, isDone = pct == 100 && stats.totalCount > 0)
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
                        .fillMaxWidth(stats.completionFraction.coerceIn(0.03f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(accent.accent)
                )
            }
        }
    }
}

@Composable
fun WatchSchedulePage(
    data: WatchDashboardData,
    nowMillis: Long,
    onInspectItem: (SharedWorkItem) -> Unit,
    onToggleFocus: (SharedWorkItem) -> Unit,
    onAddWorkItem: () -> Unit
) {
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val openItems = remember(data.workItems) {
        data.workItems.filter { it.statusEnum() != WatchWorkStatus.DONE }
    }
    val activeItems = remember(openItems) {
        openItems.filter { it.statusEnum() == WatchWorkStatus.IN_PROGRESS || it.statusEnum() == WatchWorkStatus.PAUSED }
    }
    val todayItems = remember(openItems, nowMillis) {
        openItems.filter {
            it.statusEnum() == WatchWorkStatus.TODO &&
                ((it.scheduledStartMillis > 0L && SharedTimeFormatting.isSameDay(it.scheduledStartMillis, nowMillis)) ||
                    (it.deadlineMillis > 0L && SharedTimeFormatting.isSameDay(it.deadlineMillis, nowMillis)))
        }.sortedBy { if (it.scheduledStartMillis > 0L) it.scheduledStartMillis else it.deadlineMillis }
    }
    val tomorrowMillis = remember(nowMillis) { nowMillis + 86_400_000L }
    val tomorrowItems = remember(openItems, tomorrowMillis) {
        openItems.filter {
            it.statusEnum() == WatchWorkStatus.TODO &&
                it !in todayItems &&
                ((it.scheduledStartMillis > 0L && SharedTimeFormatting.isSameDay(it.scheduledStartMillis, tomorrowMillis)) ||
                    (it.deadlineMillis > 0L && SharedTimeFormatting.isSameDay(it.deadlineMillis, tomorrowMillis)))
        }.sortedBy { if (it.scheduledStartMillis > 0L) it.scheduledStartMillis else it.deadlineMillis }
    }
    val upcomingItems = remember(openItems, todayItems, tomorrowItems) {
        openItems.filter {
            it.statusEnum() == WatchWorkStatus.TODO &&
                it !in todayItems &&
                it !in tomorrowItems &&
                (it.scheduledStartMillis > 0L || it.deadlineMillis > 0L)
        }.sortedBy { if (it.scheduledStartMillis > 0L) it.scheduledStartMillis else it.deadlineMillis }
    }
    val unscheduledItems = remember(openItems, todayItems, tomorrowItems, upcomingItems) {
        openItems.filter {
            it.statusEnum() == WatchWorkStatus.TODO &&
                it !in todayItems &&
                it !in tomorrowItems &&
                it !in upcomingItems
        }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    haptics.select()
                    onAddWorkItem()
                },
                modifier = Modifier
                    .testTag("watch_schedule_add_button")
                    .scrollable(
                        listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(" Work Item")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "schedule_title") {
                Text(
                    text = "Schedule",
                    style = WatchHeroSerifStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (openItems.isEmpty()) {
                item(key = "schedule_empty") {
                    Card(
                        onClick = {
                            haptics.select()
                            onAddWorkItem()
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("All Clear", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Schedule your next work period here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (activeItems.isNotEmpty()) {
                item(key = "sec_active") { WatchSectionLabel("Active") }
                items(activeItems, key = { "act_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = { onInspectItem(item) },
                        onToggleFocus = { onToggleFocus(item) }
                    )
                }
            }

            if (todayItems.isNotEmpty()) {
                item(key = "sec_today") { WatchSectionLabel("Due / Planned Today") }
                items(todayItems, key = { "td_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = { onInspectItem(item) },
                        onToggleFocus = { onToggleFocus(item) }
                    )
                }
            }

            if (tomorrowItems.isNotEmpty()) {
                item(key = "sec_tomorrow") { WatchSectionLabel("Tomorrow") }
                items(tomorrowItems, key = { "tm_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = { onInspectItem(item) },
                        onToggleFocus = { onToggleFocus(item) }
                    )
                }
            }

            if (upcomingItems.isNotEmpty()) {
                item(key = "sec_upcoming") { WatchSectionLabel("Upcoming") }
                items(upcomingItems, key = { "up_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = { onInspectItem(item) },
                        onToggleFocus = { onToggleFocus(item) }
                    )
                }
            }

            if (unscheduledItems.isNotEmpty()) {
                item(key = "sec_unscheduled") { WatchSectionLabel("Unscheduled") }
                items(unscheduledItems, key = { "un_${it.id}" }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = data.projectFor(item.projectId),
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = { onInspectItem(item) },
                        onToggleFocus = { onToggleFocus(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun WatchNotificationAlertCard(
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onEnable: () -> Unit
) {
    Card(
        onClick = onEnable,
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("watch_enable_notifications_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Live Timer Alerts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Tap to allow watch-face countdowns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun WatchUnlinkedBannerCard(
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onOpenAccount: () -> Unit
) {
    Card(
        onClick = onOpenAccount,
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
            .fillMaxWidth()
            .testTag("watch_unlinked_banner")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Connect TaskPulse",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Sign in with Google on watch or sync from your paired phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun WatchSyncStatusPillCard(
    data: WatchDashboardData,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val icon = when (data.syncMode) {
        SyncConnectionMode.HYBRID_CLOUD_AND_PHONE, SyncConnectionMode.DIRECT_CLOUD -> Icons.Rounded.CloudDone
        SyncConnectionMode.PHONE_BRIDGED -> Icons.Rounded.Sync
        else -> Icons.Rounded.CloudOff
    }
    Card(
        onClick = onClick,
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .testTag("watch_sync_status_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.syncMode.label,
                    style = MaterialTheme.typography.labelMedium
                )
                val subtitle = buildString {
                    if (data.userEmail.isNotBlank()) append(data.userEmail)
                    else append("Account & Sync settings")
                    if (data.pendingQueueCount > 0) append(" • ${data.pendingQueueCount} queued")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
