package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.WorkItem
import com.example.data.WorkStatus
import com.example.ui.DashboardData
import com.example.ui.TimeFormatting
import com.example.ui.components.DeadlineBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LiveSessionHeroCard
import com.example.ui.components.WorkItemExpressiveCard
import com.example.ui.components.bouncyPressScale
import com.example.ui.components.rememberExpressiveHaptics
import com.example.ui.theme.CondensedStatStyle
import com.example.ui.theme.DarkMintTertiary
import com.example.ui.theme.MintTertiary

@Composable
fun FocusScreen(
    data: DashboardData,
    nowMillis: Long,
    hasNotificationPermission: Boolean,
    canPostPromotedLiveUpdates: Boolean,
    isAndroid16OrAbove: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onStartOrPauseItem: (WorkItem) -> Unit,
    onAdvancePhase: (WorkItem) -> Unit,
    onCompleteItem: (WorkItem) -> Unit,
    onReopenItem: (WorkItem) -> Unit,
    onInspectItem: (WorkItem) -> Unit,
    onEditItem: (WorkItem) -> Unit,
    onPostponeItem: (WorkItem) -> Unit,
    onDuplicateItem: (WorkItem) -> Unit,
    onDeleteItem: (WorkItem) -> Unit,
    onCreateWorkItem: () -> Unit,
    onCreateProject: () -> Unit,
    onFabExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeOrPaused = data.activeRunningItem ?: data.pausedItem

    val queueItems = data.workItems
        .filter { it.id != activeOrPaused?.id && it.statusEnum != WorkStatus.DONE }
        .sortedWith(
            compareBy<WorkItem>(
                { if (it.scheduledStartMillis > 0L) it.scheduledStartMillis else Long.MAX_VALUE },
                { if (it.deadlineMillis > 0L) it.deadlineMillis else Long.MAX_VALUE }
            )
        )

    val completedToday = data.workItems
        .filter { it.statusEnum == WorkStatus.DONE }
        .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        .take(5)

    val urgentDeadlines = data.workItems
        .filter { it.statusEnum != WorkStatus.DONE && it.deadlineMillis > 0L }
        .sortedBy { it.deadlineMillis }
        .take(4)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Only split into 2 columns on Tablets / Unfolded Foldables with ample vertical & horizontal space.
        // Phone landscape stays in a smart single scrollable flow so the hero & timers never clip.
        val isTabletSplit = maxWidth >= 720.dp && maxHeight >= 520.dp
        val isPhoneLandscape = maxWidth > maxHeight && maxHeight < 520.dp

        val tabletPrimaryListState = rememberLazyListState()
        val tabletSecondaryListState = rememberLazyListState()
        val phoneListState = rememberLazyListState()

        val isAtTop by remember(isTabletSplit) {
            derivedStateOf {
                if (isTabletSplit) {
                    (tabletPrimaryListState.firstVisibleItemIndex == 0 && tabletPrimaryListState.firstVisibleItemScrollOffset < 120) &&
                        (tabletSecondaryListState.firstVisibleItemIndex == 0 && tabletSecondaryListState.firstVisibleItemScrollOffset < 120)
                } else {
                    phoneListState.firstVisibleItemIndex == 0 && phoneListState.firstVisibleItemScrollOffset < 120
                }
            }
        }
        LaunchedEffect(isAtTop) {
            onFabExpandedChange(isAtTop)
        }

        if (isTabletSplit) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(
                    state = tabletPrimaryListState,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        FocusHeroSection(
                            activeOrPaused = activeOrPaused,
                            topQueueItem = queueItems.firstOrNull(),
                            data = data,
                            nowMillis = nowMillis,
                            hasNotificationPermission = hasNotificationPermission,
                            canPostPromotedLiveUpdates = canPostPromotedLiveUpdates,
                            isAndroid16OrAbove = isAndroid16OrAbove,
                            compactMode = false,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
                            onStartOrPauseItem = onStartOrPauseItem,
                            onAdvancePhase = onAdvancePhase,
                            onCompleteItem = onCompleteItem,
                            onInspectItem = onInspectItem,
                            onCreateProject = onCreateProject,
                            onCreateWorkItem = onCreateWorkItem
                        )
                    }

                    item {
                        SectionHeaderRow(
                            title = "Scheduled & Ready",
                            countLabel = "${queueItems.size} active",
                            onAddClick = if (data.projects.isNotEmpty()) onCreateWorkItem else onCreateProject
                        )
                    }

                    if (queueItems.isEmpty() && activeOrPaused == null) {
                        item {
                            EmptyStateCard(
                                title = if (data.projects.isEmpty()) "Start your first project" else "Your focus queue is clear",
                                subtitle = if (data.projects.isEmpty()) {
                                    "Create a project, plan custom Pomodoro focus & rest periods, and stay ahead of every deadline."
                                } else {
                                    "Add a work item with your preferred start time, Pomodoro sessions, and deadline."
                                },
                                actionLabel = if (data.projects.isEmpty()) "Create Project" else "Add Work Item",
                                onAction = if (data.projects.isEmpty()) onCreateProject else onCreateWorkItem
                            )
                        }
                    } else {
                        items(queueItems, key = { it.id }) { item ->
                            WorkItemExpressiveCard(
                                item = item,
                                project = data.projectFor(item.projectId),
                                nowMillis = nowMillis,
                                onStartOrPause = { onStartOrPauseItem(item) },
                                onToggleComplete = {
                                    if (item.statusEnum == WorkStatus.DONE) onReopenItem(item)
                                    else onCompleteItem(item)
                                },
                                onClick = { onInspectItem(item) },
                                onEdit = { onEditItem(item) },
                                onAdvancePhase = { onAdvancePhase(item) },
                                onPostpone = { onPostponeItem(item) },
                                onDuplicate = { onDuplicateItem(item) },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }

                    if (completedToday.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recently Completed",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(completedToday, key = { "done_${it.id}" }) { item ->
                            WorkItemExpressiveCard(
                                item = item,
                                project = data.projectFor(item.projectId),
                                nowMillis = nowMillis,
                                onStartOrPause = {},
                                onToggleComplete = { onReopenItem(item) },
                                onClick = { onInspectItem(item) },
                                onDuplicate = { onDuplicateItem(item) },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }
                }

                LazyColumn(
                    state = tabletSecondaryListState,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        TodayProgressCard(data = data, nowMillis = nowMillis)
                    }
                    item {
                        DeadlineRadarCard(
                            urgentDeadlines = urgentDeadlines,
                            data = data,
                            nowMillis = nowMillis,
                            onInspectItem = onInspectItem
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = phoneListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    FocusHeroSection(
                        activeOrPaused = activeOrPaused,
                        topQueueItem = queueItems.firstOrNull(),
                        data = data,
                        nowMillis = nowMillis,
                        hasNotificationPermission = hasNotificationPermission,
                        canPostPromotedLiveUpdates = canPostPromotedLiveUpdates,
                        isAndroid16OrAbove = isAndroid16OrAbove,
                        compactMode = isPhoneLandscape,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
                        onStartOrPauseItem = onStartOrPauseItem,
                        onAdvancePhase = onAdvancePhase,
                        onCompleteItem = onCompleteItem,
                        onInspectItem = onInspectItem,
                        onCreateProject = onCreateProject,
                        onCreateWorkItem = onCreateWorkItem
                    )
                }

                if (isPhoneLandscape) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TodayProgressCard(
                                data = data,
                                nowMillis = nowMillis,
                                modifier = Modifier.weight(1f)
                            )
                            if (urgentDeadlines.isNotEmpty()) {
                                DeadlineRadarCard(
                                    urgentDeadlines = urgentDeadlines,
                                    data = data,
                                    nowMillis = nowMillis,
                                    onInspectItem = onInspectItem,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        TodayProgressCard(data = data, nowMillis = nowMillis)
                    }

                    if (urgentDeadlines.isNotEmpty()) {
                        item {
                            DeadlineRadarCard(
                                urgentDeadlines = urgentDeadlines,
                                data = data,
                                nowMillis = nowMillis,
                                onInspectItem = onInspectItem
                            )
                        }
                    }
                }

                item {
                    SectionHeaderRow(
                        title = "Scheduled & Ready",
                        countLabel = "${queueItems.size} active",
                        onAddClick = if (data.projects.isNotEmpty()) onCreateWorkItem else onCreateProject
                    )
                }

                if (queueItems.isEmpty() && activeOrPaused == null) {
                    item {
                        EmptyStateCard(
                            title = if (data.projects.isEmpty()) "Start your first project" else "Your focus queue is clear",
                            subtitle = if (data.projects.isEmpty()) {
                                "Create a project, plan custom Pomodoro focus & rest periods, and stay ahead of every deadline."
                            } else {
                                "Add a work item with your preferred start time, Pomodoro sessions, and deadline."
                            },
                            actionLabel = if (data.projects.isEmpty()) "Create Project" else "Add Work Item",
                            onAction = if (data.projects.isEmpty()) onCreateProject else onCreateWorkItem
                        )
                    }
                } else {
                    items(queueItems, key = { it.id }) { item ->
                        WorkItemExpressiveCard(
                            item = item,
                            project = data.projectFor(item.projectId),
                            nowMillis = nowMillis,
                            onStartOrPause = { onStartOrPauseItem(item) },
                            onToggleComplete = {
                                if (item.statusEnum == WorkStatus.DONE) onReopenItem(item)
                                else onCompleteItem(item)
                            },
                            onClick = { onInspectItem(item) },
                            onEdit = { onEditItem(item) },
                            onAdvancePhase = { onAdvancePhase(item) },
                            onPostpone = { onPostponeItem(item) },
                            onDuplicate = { onDuplicateItem(item) },
                            onDelete = { onDeleteItem(item) }
                        )
                    }
                }

                if (completedToday.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recently Completed",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(completedToday, key = { "done_${it.id}" }) { item ->
                        WorkItemExpressiveCard(
                            item = item,
                            project = data.projectFor(item.projectId),
                            nowMillis = nowMillis,
                            onStartOrPause = {},
                            onToggleComplete = { onReopenItem(item) },
                            onClick = { onInspectItem(item) },
                            onDuplicate = { onDuplicateItem(item) },
                            onDelete = { onDeleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusHeroSection(
    activeOrPaused: WorkItem?,
    topQueueItem: WorkItem?,
    data: DashboardData,
    nowMillis: Long,
    hasNotificationPermission: Boolean,
    canPostPromotedLiveUpdates: Boolean,
    isAndroid16OrAbove: Boolean,
    compactMode: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenLiveUpdateSettings: () -> Unit,
    onStartOrPauseItem: (WorkItem) -> Unit,
    onAdvancePhase: (WorkItem) -> Unit,
    onCompleteItem: (WorkItem) -> Unit,
    onInspectItem: (WorkItem) -> Unit,
    onCreateProject: () -> Unit,
    onCreateWorkItem: () -> Unit
) {
    val haptics = rememberExpressiveHaptics()

    if (activeOrPaused != null) {
        LiveSessionHeroCard(
            item = activeOrPaused,
            project = data.projectFor(activeOrPaused.projectId),
            nowMillis = nowMillis,
            hasNotificationPermission = hasNotificationPermission,
            canPostPromotedLiveUpdates = canPostPromotedLiveUpdates,
            isAndroid16OrAbove = isAndroid16OrAbove,
            onTogglePauseResume = { onStartOrPauseItem(activeOrPaused) },
            onAdvancePhase = { onAdvancePhase(activeOrPaused) },
            onComplete = { onCompleteItem(activeOrPaused) },
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenLiveUpdateSettings = onOpenLiveUpdateSettings,
            onInspectItem = { onInspectItem(activeOrPaused) },
            compactMode = compactMode
        )
    } else {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compactMode) 16.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (topQueueItem != null) "Ready for a focus session?" else "Plan your next sprint",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (topQueueItem != null) {
                        "Up next: \"${topQueueItem.title}\" (${topQueueItem.effectiveWorkPeriods}×${topQueueItem.effectiveWorkMinutes}m Pomodoro)"
                    } else {
                        "Schedule a work item with custom Pomodoro focus and break blocks to get started."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primaryInteraction = remember { MutableInteractionSource() }
                    when {
                        topQueueItem != null -> {
                            Button(
                                onClick = {
                                    haptics.confirm()
                                    onStartOrPauseItem(topQueueItem)
                                },
                                shapes = ButtonDefaults.shapes(),
                                interactionSource = primaryInteraction,
                                modifier = Modifier
                                    .height(48.dp)
                                    .bouncyPressScale(primaryInteraction)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start Focus")
                            }
                        }
                        data.projects.isEmpty() -> {
                            Button(
                                onClick = {
                                    haptics.select()
                                    onCreateProject()
                                },
                                shapes = ButtonDefaults.shapes(),
                                interactionSource = primaryInteraction,
                                modifier = Modifier
                                    .height(48.dp)
                                    .bouncyPressScale(primaryInteraction)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("New Project")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    haptics.select()
                                    onCreateWorkItem()
                                },
                                shapes = ButtonDefaults.shapes(),
                                interactionSource = primaryInteraction,
                                modifier = Modifier
                                    .height(48.dp)
                                    .bouncyPressScale(primaryInteraction)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Schedule Work Item")
                            }
                        }
                    }

                    if (!hasNotificationPermission) {
                        FilledTonalButton(
                            onClick = onRequestNotificationPermission,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Enable Alerts")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodayProgressCard(
    data: DashboardData,
    nowMillis: Long,
    modifier: Modifier = Modifier
) {
    val totalItems = data.workItems.size
    val doneItems = data.workItems.count { it.statusEnum == WorkStatus.DONE }
    val completionRatio = if (totalItems == 0) 0f else doneItems.toFloat() / totalItems.toFloat()
    val todayProjectedMinutes = data.workItems
        .filter { it.statusEnum != WorkStatus.DONE && (it.scheduledStartMillis == 0L || TimeFormatting.isSameDay(it.scheduledStartMillis, nowMillis)) }
        .sumOf { it.totalWorkMinutes }
    val totalLoggedMinutes = data.workItems.sumOf { it.liveElapsedSeconds(nowMillis) } / 60L

    val darkTheme = isSystemInDarkTheme()
    val mintTone = if (darkTheme) DarkMintTertiary else MintTertiary
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { completionRatio },
                    modifier = Modifier.size(72.dp),
                    color = mintTone,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                Text(
                    text = "${(completionRatio * 100).toInt()}%",
                    style = CondensedStatStyle
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Today's Progress", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMiniPill(Icons.Rounded.CheckCircle, "$doneItems/$totalItems done")
                    StatMiniPill(Icons.Rounded.Timer, "${TimeFormatting.formatMinutesCompact(todayProjectedMinutes)} planned")
                }
                Text(
                    text = "${TimeFormatting.formatMinutesCompact(totalLoggedMinutes)} focused across ${data.projects.size} projects",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatMiniPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DeadlineRadarCard(
    urgentDeadlines: List<WorkItem>,
    data: DashboardData,
    nowMillis: Long,
    onInspectItem: (WorkItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Upcoming Deadlines", style = MaterialTheme.typography.titleMedium)
            }

            if (urgentDeadlines.isEmpty()) {
                Text(
                    text = "All upcoming deadlines are clear!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                urgentDeadlines.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onInspectItem(item) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = data.projectNameFor(item.projectId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        DeadlineBadge(
                            deadlineMillis = item.deadlineMillis,
                            nowMillis = nowMillis,
                            isDone = false
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeaderRow(
    title: String,
    countLabel: String,
    onAddClick: () -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    val addInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(countLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(
            onClick = {
                haptics.select()
                onAddClick()
            },
            shapes = ButtonDefaults.shapes(),
            interactionSource = addInteraction,
            modifier = Modifier
                .height(48.dp)
                .bouncyPressScale(addInteraction)
                .testTag("section_add_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add", style = MaterialTheme.typography.labelLarge)
        }
    }
}
