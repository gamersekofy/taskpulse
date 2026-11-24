package com.example.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Project
import com.example.data.ProjectWithStats
import com.example.data.WorkItem
import com.example.data.WorkStatus
import com.example.ui.DashboardData
import com.example.ui.TimeFormatting
import com.example.ui.components.DeadlineBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.WorkItemExpressiveCard
import com.example.ui.components.bouncyPressScale
import com.example.ui.components.rememberExpressiveHaptics
import kotlinx.coroutines.launch

private enum class ProjectItemFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DONE("Completed")
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ProjectsScreen(
    data: DashboardData,
    nowMillis: Long,
    onCreateProject: () -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project, List<WorkItem>) -> Unit,
    onAddWorkItemForProject: (String) -> Unit,
    onStartOrPauseItem: (WorkItem) -> Unit,
    onAdvancePhase: (WorkItem) -> Unit,
    onCompleteItem: (WorkItem) -> Unit,
    onReopenItem: (WorkItem) -> Unit,
    onInspectItem: (WorkItem) -> Unit,
    onEditItem: (WorkItem) -> Unit,
    onPostponeItem: (WorkItem) -> Unit,
    onDuplicateItem: (WorkItem) -> Unit,
    onDeleteItem: (WorkItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    val selectedId = navigator.currentDestination?.contentKey
        ?: data.projectsWithStats.firstOrNull()?.project?.id
    val selectedStats = data.projectsWithStats.firstOrNull { it.project.id == selectedId }
        ?: data.projectsWithStats.firstOrNull()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier.fillMaxSize(),
        listPane = {
            AnimatedPane(
                enterTransition = slideInHorizontally(animationSpec = tween(240)) { -it / 3 },
                exitTransition = slideOutHorizontally(animationSpec = tween(240)) { -it / 3 }
            ) {
                // Opaque Surface avoids any mid-transition ghosting/translucency over overlapping panes
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ProjectsListPane(
                        projectsWithStats = data.projectsWithStats,
                        selectedProjectId = selectedStats?.project?.id,
                        nowMillis = nowMillis,
                        onSelectProject = { projectId ->
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, projectId)
                            }
                        },
                        onEditProject = onEditProject,
                        onDeleteProject = onDeleteProject,
                        onAddWorkItemForProject = onAddWorkItemForProject,
                        onCreateProject = onCreateProject
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane(
                enterTransition = slideInHorizontally(animationSpec = tween(260)) { it },
                exitTransition = slideOutHorizontally(animationSpec = tween(260)) { it }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ProjectDetailPane(
                        stats = selectedStats,
                        nowMillis = nowMillis,
                        canNavigateBack = navigator.canNavigateBack(),
                        onBack = {
                            scope.launch {
                                navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                            }
                        },
                        onEditProject = onEditProject,
                        onDeleteProject = onDeleteProject,
                        onAddWorkItem = onAddWorkItemForProject,
                        onStartOrPauseItem = onStartOrPauseItem,
                        onAdvancePhase = onAdvancePhase,
                        onCompleteItem = onCompleteItem,
                        onReopenItem = onReopenItem,
                        onInspectItem = onInspectItem,
                        onEditItem = onEditItem,
                        onPostponeItem = onPostponeItem,
                        onDuplicateItem = onDuplicateItem,
                        onDeleteItem = onDeleteItem,
                        onCreateProject = onCreateProject
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProjectsListPane(
    projectsWithStats: List<ProjectWithStats>,
    selectedProjectId: String?,
    nowMillis: Long,
    onSelectProject: (String) -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project, List<WorkItem>) -> Unit,
    onAddWorkItemForProject: (String) -> Unit,
    onCreateProject: () -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    var pendingDeleteStats by remember { mutableStateOf<ProjectWithStats?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Projects", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${projectsWithStats.size} active workspaces",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val newProjInteraction = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        onCreateProject()
                    },
                    shapes = ButtonDefaults.shapes(),
                    interactionSource = newProjInteraction,
                    modifier = Modifier
                        .height(48.dp)
                        .bouncyPressScale(newProjInteraction)
                        .testTag("new_project_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Project")
                }
            }
        }

        if (projectsWithStats.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Create your first project",
                    subtitle = "Group related tasks, pick a project color, and track real-time Pomodoro progress & deadlines.",
                    actionLabel = "Launch Project",
                    onAction = onCreateProject
                )
            }
        } else {
            items(projectsWithStats, key = { it.project.id }) { stats ->
                val project = stats.project
                val isSelected = project.id == selectedProjectId
                val accent = project.accent
                var showProjectMenu by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                accent.container.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent.solid else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .combinedClickable(
                                onClick = {
                                    haptics.select()
                                    onSelectProject(project.id)
                                },
                                onLongClick = {
                                    haptics.thud()
                                    showProjectMenu = true
                                }
                            )
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                            haptics.thud()
                                            showProjectMenu = true
                                        }
                                    }
                                }
                            }
                            .testTag("project_card_${project.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accent.container,
                                        contentColor = accent.onContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(project.icon.vector, contentDescription = null)
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${stats.doneCount}/${stats.totalCount} items • ${TimeFormatting.formatMinutesCompact(stats.totalProjectedMinutes)} est.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "${(stats.completionFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = accent.solid
                                )
                            }

                            if (project.description.isNotBlank()) {
                                Text(
                                    text = project.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            LinearWavyProgressIndicator(
                                progress = { stats.completionFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp),
                                color = accent.solid,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                amplitude = { 0f }
                            )

                            if (project.deadlineMillis > 0L) {
                                DeadlineBadge(
                                    deadlineMillis = project.deadlineMillis,
                                    nowMillis = nowMillis,
                                    isDone = stats.totalCount > 0 && stats.doneCount == stats.totalCount
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Project") },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                            onClick = {
                                showProjectMenu = false
                                onSelectProject(project.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Work Item") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = {
                                showProjectMenu = false
                                onAddWorkItemForProject(project.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Project") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                showProjectMenu = false
                                onEditProject(project)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Project", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showProjectMenu = false
                                haptics.reject()
                                pendingDeleteStats = stats
                            }
                        )
                    }
                }
            }
        }
    }

    pendingDeleteStats?.let { statsToDelete ->
        DeleteProjectExpressiveDialog(
            stats = statsToDelete,
            onDismiss = { pendingDeleteStats = null },
            onConfirmDelete = {
                pendingDeleteStats = null
                onDeleteProject(statsToDelete.project, statsToDelete.items)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProjectDetailPane(
    stats: ProjectWithStats?,
    nowMillis: Long,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project, List<WorkItem>) -> Unit,
    onAddWorkItem: (String) -> Unit,
    onStartOrPauseItem: (WorkItem) -> Unit,
    onAdvancePhase: (WorkItem) -> Unit,
    onCompleteItem: (WorkItem) -> Unit,
    onReopenItem: (WorkItem) -> Unit,
    onInspectItem: (WorkItem) -> Unit,
    onEditItem: (WorkItem) -> Unit,
    onPostponeItem: (WorkItem) -> Unit,
    onDuplicateItem: (WorkItem) -> Unit,
    onDeleteItem: (WorkItem) -> Unit,
    onCreateProject: () -> Unit
) {
    val haptics = rememberExpressiveHaptics()

    if (stats == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                title = "Select or create a project",
                subtitle = "Your project work items, Pomodoro timelines, and deadlines will appear here.",
                actionLabel = "New Project",
                onAction = onCreateProject
            )
        }
        return
    }

    val project = stats.project
    var filter by remember(project.id) { mutableStateOf(ProjectItemFilter.ALL) }
    var confirmDelete by remember { mutableStateOf(false) }

    val filteredItems = stats.items
        .filter { item ->
            when (filter) {
                ProjectItemFilter.ALL -> true
                ProjectItemFilter.ACTIVE -> item.statusEnum != WorkStatus.DONE
                ProjectItemFilter.DONE -> item.statusEnum == WorkStatus.DONE
            }
        }
        .sortedWith(
            compareBy<WorkItem>(
                { it.statusEnum == WorkStatus.DONE },
                { if (it.scheduledStartMillis > 0L) it.scheduledStartMillis else Long.MAX_VALUE }
            )
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = project.accent.container.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (canNavigateBack) {
                                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to projects")
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = project.accent.solid,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = project.icon.vector,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    haptics.select()
                                    onEditProject(project)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit project")
                            }
                            IconButton(
                                onClick = {
                                    haptics.reject()
                                    confirmDelete = true
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete project")
                            }
                        }
                    }

                    if (project.description.isNotBlank()) {
                        Text(project.description, style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stats.doneCount} of ${stats.totalCount} work items done",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${(stats.completionFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = project.accent.solid
                        )
                    }

                    LinearWavyProgressIndicator(
                        progress = { stats.completionFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = project.accent.solid,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        amplitude = { 0f }
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (project.deadlineMillis > 0L) {
                            DeadlineBadge(
                                deadlineMillis = project.deadlineMillis,
                                nowMillis = nowMillis,
                                isDone = stats.totalCount > 0 && stats.doneCount == stats.totalCount
                            )
                        }
                        val addInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                haptics.select()
                                onAddWorkItem(project.id)
                            },
                            shapes = ButtonDefaults.shapes(),
                            interactionSource = addInteraction,
                            modifier = Modifier
                                .height(48.dp)
                                .bouncyPressScale(addInteraction)
                                .testTag("add_item_in_project_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Work Item")
                        }
                    }
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProjectItemFilter.entries.forEachIndexed { idx, option ->
                    SegmentedButton(
                        selected = filter == option,
                        onClick = {
                            haptics.select()
                            filter = option
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = ProjectItemFilter.entries.size
                        ),
                        modifier = Modifier.testTag("project_filter_${option.name.lowercase()}")
                    ) {
                        Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No work items here yet",
                    subtitle = "Schedule a work item under ${project.name} with Pomodoro focus periods and a deadline.",
                    actionLabel = "Add Work Item",
                    onAction = { onAddWorkItem(project.id) }
                )
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                WorkItemExpressiveCard(
                    item = item,
                    project = project,
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
    }

    if (confirmDelete) {
        DeleteProjectExpressiveDialog(
            stats = stats,
            onDismiss = { confirmDelete = false },
            onConfirmDelete = {
                confirmDelete = false
                onDeleteProject(project, stats.items)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteProjectExpressiveDialog(
    stats: ProjectWithStats,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    val project = stats.project
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Delete \"${project.name}\"?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This action cannot be undone. Everything inside this workspace will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (stats.items.isEmpty()) {
                                "0 linked work items will be removed."
                            } else if (stats.items.size == 1) {
                                "1 linked work item will also be permanently deleted."
                            } else {
                                "${stats.items.size} linked work items will also be permanently deleted."
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.reject()
                    onConfirmDelete()
                },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete Project")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Keep Project")
            }
        }
    )
}
