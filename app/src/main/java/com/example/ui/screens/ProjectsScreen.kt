package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.consumeSecondaryClickForContextMenu
import com.example.ui.components.rememberExpressiveHaptics
import com.example.ui.theme.CondensedStatStyle

private enum class ProjectItemFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DONE("Completed")
}

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
    onFabExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var isDetailOpenOnPhone by rememberSaveable { mutableStateOf(false) }

    val selectedStats = data.projectsWithStats.firstOrNull { it.project.id == selectedProjectId }
        ?: data.projectsWithStats.firstOrNull()

    // Close detail pane automatically if the inspected project was deleted
    LaunchedEffect(data.projectsWithStats, selectedProjectId) {
        if (selectedProjectId != null && data.projectsWithStats.none { it.project.id == selectedProjectId }) {
            selectedProjectId = data.projectsWithStats.firstOrNull()?.project?.id
            if (data.projectsWithStats.isEmpty()) {
                isDetailOpenOnPhone = false
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 720.dp && maxHeight >= 520.dp

        if (isTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                ) {
                    ProjectsListPane(
                        projectsWithStats = data.projectsWithStats,
                        selectedProjectId = selectedStats?.project?.id,
                        highlightSelection = true,
                        nowMillis = nowMillis,
                        onSelectProject = { projectId ->
                            selectedProjectId = projectId
                        },
                        onEditProject = onEditProject,
                        onDeleteProject = onDeleteProject,
                        onAddWorkItemForProject = onAddWorkItemForProject,
                        onCreateProject = onCreateProject,
                        onFabExpandedChange = onFabExpandedChange
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                ) {
                    ProjectDetailPane(
                        stats = selectedStats,
                        nowMillis = nowMillis,
                        canNavigateBack = false,
                        onBack = {},
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
        } else {
            val showingDetail = isDetailOpenOnPhone && selectedStats != null

            BackHandler(enabled = showingDetail) {
                isDetailOpenOnPhone = false
            }

            LaunchedEffect(showingDetail) {
                onFabExpandedChange(true)
            }

            AnimatedContent(
                targetState = showingDetail,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(animationSpec = tween(250)) { it })
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { -it / 4 })
                            .apply { targetContentZIndex = 1f }
                    } else {
                        (slideInHorizontally(animationSpec = tween(250)) { -it / 4 })
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { it })
                            .apply { targetContentZIndex = 0f }
                    }
                },
                label = "projects_single_pane_transition",
                modifier = Modifier.fillMaxSize()
            ) { showDetail ->
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (showDetail) {
                        ProjectDetailPane(
                            stats = selectedStats,
                            nowMillis = nowMillis,
                            canNavigateBack = true,
                            onBack = { isDetailOpenOnPhone = false },
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
                            onCreateProject = onCreateProject,
                            onFabExpandedChange = onFabExpandedChange
                        )
                    } else {
                        ProjectsListPane(
                            projectsWithStats = data.projectsWithStats,
                            selectedProjectId = selectedStats?.project?.id,
                            highlightSelection = false,
                            nowMillis = nowMillis,
                            onSelectProject = { projectId ->
                                selectedProjectId = projectId
                                isDetailOpenOnPhone = true
                            },
                            onEditProject = onEditProject,
                            onDeleteProject = onDeleteProject,
                            onAddWorkItemForProject = onAddWorkItemForProject,
                            onCreateProject = onCreateProject,
                            onFabExpandedChange = onFabExpandedChange
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProjectsListPane(
    projectsWithStats: List<ProjectWithStats>,
    selectedProjectId: String?,
    highlightSelection: Boolean,
    nowMillis: Long,
    onSelectProject: (String) -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project, List<WorkItem>) -> Unit,
    onAddWorkItemForProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    onFabExpandedChange: (Boolean) -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    val darkTheme = isSystemInDarkTheme()
    val listState = rememberLazyListState()
    var pendingDeleteStats by remember { mutableStateOf<ProjectWithStats?>(null) }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 120
        }
    }
    LaunchedEffect(isAtTop) {
        onFabExpandedChange(isAtTop)
    }

    LazyColumn(
        state = listState,
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
                val isSelected = highlightSelection && project.id == selectedProjectId
                val accent = project.accent
                val accentVibrant = accent.vibrantColor(darkTheme)
                val accentOnVibrant = accent.onVibrantColor(darkTheme)
                var showProjectMenu by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accentVibrant else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .consumeSecondaryClickForContextMenu {
                            haptics.thud()
                            showProjectMenu = true
                        }
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
                                    color = accentVibrant,
                                    contentColor = accentOnVibrant,
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
                                        color = MaterialTheme.colorScheme.onSurface,
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${(stats.completionFraction * 100).toInt()}%",
                                    style = CondensedStatStyle,
                                    color = accentVibrant
                                )

                                Box {
                                    IconButton(
                                        onClick = {
                                            haptics.select()
                                            showProjectMenu = true
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("project_menu_${project.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "More options for ${project.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
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
                            color = accentVibrant,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
    onCreateProject: () -> Unit,
    onFabExpandedChange: ((Boolean) -> Unit)? = null
) {
    val haptics = rememberExpressiveHaptics()
    val darkTheme = isSystemInDarkTheme()

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
    val accentVibrant = project.accent.vibrantColor(darkTheme)
    val accentOnVibrant = project.accent.onVibrantColor(darkTheme)
    var filter by remember(project.id) { mutableStateOf(ProjectItemFilter.ALL) }
    var confirmDelete by remember { mutableStateOf(false) }

    val detailListState = rememberLazyListState()
    if (onFabExpandedChange != null) {
        val isAtTop by remember {
            derivedStateOf {
                detailListState.firstVisibleItemIndex == 0 && detailListState.firstVisibleItemScrollOffset < 120
            }
        }
        LaunchedEffect(isAtTop) {
            onFabExpandedChange(isAtTop)
        }
    }

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
        state = detailListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                color = accentVibrant,
                                contentColor = accentOnVibrant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = project.icon.vector,
                                        contentDescription = null
                                    )
                                }
                            }
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
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
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stats.doneCount} of ${stats.totalCount} work items done",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(stats.completionFraction * 100).toInt()}%",
                            style = CondensedStatStyle,
                            color = accentVibrant
                        )
                    }

                    LinearWavyProgressIndicator(
                        progress = { stats.completionFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = accentVibrant,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
