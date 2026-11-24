package com.example.wear.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.shared.SharedTimeFormatting
import com.example.shared.SharedWorkItem
import com.example.wear.data.WatchDashboardData
import com.example.wear.data.WatchProjectColor
import com.example.wear.data.WatchProjectIcon
import com.example.wear.data.WatchWorkStatus
import com.example.wear.data.accentColor
import com.example.wear.data.priorityEnum
import com.example.wear.data.snapshot
import com.example.wear.data.statusEnum
import com.example.wear.ui.components.WatchDeadlineChip
import com.example.wear.ui.components.WatchDeleteConfirmDialog
import com.example.wear.ui.components.WatchPomodoroSlotStrip
import com.example.wear.ui.components.WatchProjectBadge
import com.example.wear.ui.components.WatchWorkItemCard
import com.example.wear.ui.theme.WatchBreakMintColor
import com.example.wear.ui.theme.WatchHeroSerifStyle
import com.example.wear.ui.theme.rememberWatchHaptics

private enum class WatchProjectFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DONE("Done")
}

@Composable
fun WatchProjectDetailScreen(
    projectId: String,
    data: WatchDashboardData,
    nowMillis: Long,
    onEditProject: () -> Unit,
    onDeleteProject: (List<SharedWorkItem>) -> Unit,
    onAddWorkItem: () -> Unit,
    onInspectItem: (SharedWorkItem) -> Unit,
    onToggleFocus: (SharedWorkItem) -> Unit
) {
    val project = data.projectFor(projectId)
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var filter by remember { mutableStateOf(WatchProjectFilter.ALL) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val allItems = remember(data.workItems, projectId) {
        data.workItems.filter { it.projectId == projectId }
    }
    val filteredItems = remember(allItems, filter) {
        when (filter) {
            WatchProjectFilter.ALL -> allItems
            WatchProjectFilter.ACTIVE -> allItems.filter { it.statusEnum() != WatchWorkStatus.DONE }
            WatchProjectFilter.DONE -> allItems.filter { it.statusEnum() == WatchWorkStatus.DONE }
        }
    }

    val accent = WatchProjectColor.safeValueOf(project?.colorKey ?: "CORAL")
    val icon = WatchProjectIcon.safeValueOf(project?.iconKey ?: "ROCKET")

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    haptics.select()
                    onAddWorkItem()
                },
                modifier = Modifier
                    .testTag("watch_project_add_item_button")
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
            item(key = "proj_header") {
                Card(
                    onClick = onEditProject,
                    transformation = SurfaceTransformation(transformationSpec),
                    border = BorderStroke(1.dp, accent.accent.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WatchProjectBadge(color = accent, icon = icon, size = 30.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project?.name ?: "Project",
                                    style = WatchHeroSerifStyle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${allItems.count { it.statusEnum() == WatchWorkStatus.DONE }}/${allItems.size} completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!project?.description.isNullOrBlank()) {
                            Text(
                                text = project?.description.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (project != null && project.deadlineMillis > 0L) {
                            WatchDeadlineChip(project.deadlineMillis, nowMillis, isDone = false)
                        }
                    }
                }
            }

            item(key = "proj_actions") {
                val editInteraction = remember { MutableInteractionSource() }
                val deleteInteraction = remember { MutableInteractionSource() }
                ButtonGroup(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            onEditProject()
                        },
                        interactionSource = editInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(editInteraction)
                            .testTag("watch_project_edit_button"),
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Edit") }
                    )
                    IconButton(
                        onClick = {
                            haptics.select()
                            showDeleteDialog = true
                        },
                        interactionSource = deleteInteraction,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .animateWidth(deleteInteraction)
                            .testTag("watch_project_delete_button")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete project")
                    }
                }
            }

            item(key = "proj_filters") {
                ButtonGroup(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                ) {
                    WatchProjectFilter.entries.forEach { option ->
                        val interaction = remember(option) { MutableInteractionSource() }
                        val selected = filter == option
                        Button(
                            onClick = {
                                haptics.select()
                                filter = option
                            },
                            interactionSource = interaction,
                            colors = if (selected) {
                                ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = Color.Black)
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(interaction)
                                .testTag("watch_filter_${option.name.lowercase()}")
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item(key = "proj_empty") {
                    Card(
                        onClick = onAddWorkItem,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "No ${filter.label.lowercase()} work items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    WatchWorkItemCard(
                        item = item,
                        project = project,
                        nowMillis = nowMillis,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onInspect = {
                            haptics.select()
                            onInspectItem(item)
                        },
                        onToggleFocus = {
                            haptics.confirm()
                            onToggleFocus(item)
                        }
                    )
                }
            }
        }
    }

    WatchDeleteConfirmDialog(
        visible = showDeleteDialog,
        title = "Delete Project?",
        message = "Removes \"${project?.name ?: "project"}\" and its ${allItems.size} work item(s).",
        transformationSpec = transformationSpec,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            haptics.confirm()
            showDeleteDialog = false
            onDeleteProject(allItems)
        }
    )
}

@Composable
fun WatchWorkItemDetailScreen(
    itemId: String,
    data: WatchDashboardData,
    nowMillis: Long,
    onStartSession: (SharedWorkItem) -> Unit,
    onPauseSession: (SharedWorkItem) -> Unit,
    onAdvancePhase: (SharedWorkItem) -> Unit,
    onCompleteSession: (SharedWorkItem) -> Unit,
    onReopenItem: (SharedWorkItem) -> Unit,
    onPostponeItem: (SharedWorkItem) -> Unit,
    onDuplicateItem: (SharedWorkItem) -> Unit,
    onUpdateProgress: (SharedWorkItem, Long) -> Unit,
    onEditItem: (SharedWorkItem) -> Unit,
    onDeleteItem: (SharedWorkItem) -> Unit
) {
    val item = data.workItems.firstOrNull { it.id == itemId }
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (item == null) {
        ScreenScaffold { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Work item removed", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    val project = data.projectFor(item.projectId)
    val accent = item.accentColor(project)
    val status = item.statusEnum()
    val isRunning = status == WatchWorkStatus.IN_PROGRESS
    val isDone = status == WatchWorkStatus.DONE
    val snap = item.snapshot(nowMillis)
    val livePct = (item.liveProgressFraction(nowMillis) * 100f).toInt().coerceIn(0, 100)

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    haptics.confirm()
                    when {
                        isDone -> onReopenItem(item)
                        isRunning -> onPauseSession(item)
                        else -> onStartSession(item)
                    }
                },
                modifier = Modifier
                    .testTag("watch_item_primary_edge_button")
                    .scrollable(
                        listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
            ) {
                Icon(
                    imageVector = when {
                        isDone -> Icons.Rounded.Replay
                        isRunning -> Icons.Rounded.Pause
                        else -> Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    when {
                        isDone -> " Reopen"
                        isRunning -> " Pause"
                        else -> " Start Focus"
                    }
                )
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "item_summary") {
                Card(
                    onClick = { onEditItem(item) },
                    transformation = SurfaceTransformation(transformationSpec),
                    border = BorderStroke(1.dp, accent.accent.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = project?.name ?: "Project",
                            style = WatchHeroSerifStyle.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                            color = accent.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.notes.isNotBlank()) {
                            Text(
                                text = item.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.priorityEnum().label} • ${item.effectiveWorkPeriods}×${item.effectiveWorkMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.deadlineMillis > 0L || isDone) {
                                WatchDeadlineChip(item.deadlineMillis, nowMillis, isDone)
                            }
                        }
                        if (item.scheduledStartMillis > 0L) {
                            Text(
                                text = SharedTimeFormatting.formatScheduleWindow(item.scheduledStartMillis, item.totalPomodoroMinutes, nowMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        WatchPomodoroSlotStrip(slots = snap.slots, accentColor = accent.accent)
                    }
                }
            }

            item(key = "item_progress_tuner") {
                val minusInteraction = remember { MutableInteractionSource() }
                val plusInteraction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "PROGRESS • $livePct%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                haptics.select()
                                onUpdateProgress(item, (livePct - 10).coerceAtLeast(0).toLong())
                            },
                            interactionSource = minusInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(minusInteraction)
                                .testTag("watch_item_progress_minus")
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("-10%")
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                haptics.select()
                                onUpdateProgress(item, (livePct + 10).coerceAtMost(100).toLong())
                            },
                            interactionSource = plusInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(plusInteraction)
                                .testTag("watch_item_progress_plus")
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("+10%")
                            }
                        }
                    }
                }
            }

            if (!isDone) {
                item(key = "act_next_period") {
                    FilledTonalButton(
                        onClick = {
                            haptics.confirm()
                            onAdvancePhase(item)
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_item_next_period"),
                        icon = { Icon(Icons.Rounded.SkipNext, contentDescription = null) },
                        label = {
                            Text(snap.nextSlot?.let { "Next: ${it.label}" } ?: "Finish Last Period")
                        }
                    )
                }

                item(key = "act_complete") {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onCompleteSession(item)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WatchBreakMintColor,
                            contentColor = Color.Black
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_item_complete"),
                        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                        label = { Text("Mark Completed") }
                    )
                }

                item(key = "act_postpone") {
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            onPostponeItem(item)
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_item_postpone"),
                        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                        label = { Text("Postpone +1 Day") }
                    )
                }
            }

            item(key = "act_edit") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        onEditItem(item)
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_item_edit"),
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    label = { Text("Edit Work Item") }
                )
            }

            item(key = "act_duplicate") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        onDuplicateItem(item)
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_item_duplicate"),
                    icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    label = { Text("Duplicate") }
                )
            }

            item(key = "act_delete") {
                Button(
                    onClick = {
                        haptics.select()
                        showDeleteConfirm = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_item_delete"),
                    icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    label = { Text("Delete") }
                )
            }
        }
    }

    WatchDeleteConfirmDialog(
        visible = showDeleteConfirm,
        title = "Delete Work Item?",
        message = "Remove \"${item.title}\" permanently?",
        transformationSpec = transformationSpec,
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            haptics.confirm()
            showDeleteConfirm = false
            onDeleteItem(item)
        }
    )
}

@Composable
fun WatchAccountSyncScreen(
    data: WatchDashboardData,
    hasNotificationPermission: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onSyncFromPhone: () -> Unit,
    onOpenPhoneApp: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "acc_header") {
                Text(
                    text = "Account & Sync",
                    style = WatchHeroSerifStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            item(key = "acc_status") {
                Card(
                    onClick = onSyncFromPhone,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = data.syncMode.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = buildString {
                                append(if (data.userName.isNotBlank()) "${data.userName}\n" else "")
                                append(if (data.userEmail.isNotBlank()) data.userEmail else "Not signed in directly on watch")
                                if (data.pendingQueueCount > 0) append("\nPending offline edits: ${data.pendingQueueCount}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!data.isDirectCloudSignedIn) {
                item(key = "acc_signin") {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onSignInWithGoogle()
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_google_signin_button"),
                        icon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                        label = { Text("Sign in with Google") },
                        secondaryLabel = { Text("Direct cloud sync on LTE/Wi-Fi") }
                    )
                }
            }

            item(key = "acc_sync_phone") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        onSyncFromPhone()
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_sync_phone_button"),
                    icon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                    label = { Text("Sync from Phone") }
                )
            }

            item(key = "acc_open_phone") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        onOpenPhoneApp()
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_open_phone_button"),
                    icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                    label = { Text("Open Phone App") }
                )
            }

            if (!hasNotificationPermission) {
                item(key = "acc_notif") {
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            onRequestNotificationPermission()
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_grant_notif_button"),
                        icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                        label = { Text("Enable Live Alerts") }
                    )
                }
            }

            if (data.isDirectCloudSignedIn) {
                item(key = "acc_signout") {
                    FilledTonalButton(
                        onClick = {
                            haptics.confirm()
                            onSignOut()
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_signout_button"),
                        icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                        label = { Text("Sign Out (Watch)") }
                    )
                }
            }
        }
    }
}
