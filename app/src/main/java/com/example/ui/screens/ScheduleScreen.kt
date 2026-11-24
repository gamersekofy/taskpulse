package com.example.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.WorkItem
import com.example.data.WorkStatus
import com.example.ui.DashboardData
import com.example.ui.TimeFormatting
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.WorkItemExpressiveCard
import com.example.ui.components.bouncyPressScale
import com.example.ui.components.rememberExpressiveHaptics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScheduleScreen(
    data: DashboardData,
    nowMillis: Long,
    onStartOrPauseItem: (WorkItem) -> Unit,
    onAdvancePhase: (WorkItem) -> Unit,
    onCompleteItem: (WorkItem) -> Unit,
    onReopenItem: (WorkItem) -> Unit,
    onInspectItem: (WorkItem) -> Unit,
    onEditItem: (WorkItem) -> Unit,
    onPostponeItem: (WorkItem) -> Unit,
    onDuplicateItem: (WorkItem) -> Unit,
    onDeleteItem: (WorkItem) -> Unit,
    onAddWorkItem: () -> Unit,
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberExpressiveHaptics()
    val activeItems = data.workItems.filter { it.statusEnum != WorkStatus.DONE }
    val overdueItems = activeItems
        .filter { it.deadlineMillis in 1 until nowMillis }
        .sortedBy { it.deadlineMillis }

    val overdueIds = overdueItems.map { it.id }.toSet()

    val scheduledGroups = activeItems
        .filter { it.id !in overdueIds && it.scheduledStartMillis > 0L }
        .sortedBy { it.scheduledStartMillis }
        .groupBy { TimeFormatting.formatRelativeDay(it.scheduledStartMillis, nowMillis) }

    val unscheduledItems = activeItems
        .filter { it.id !in overdueIds && it.scheduledStartMillis <= 0L }
        .sortedBy { if (it.deadlineMillis > 0L) it.deadlineMillis else Long.MAX_VALUE }

    val totalPlannedMinutes = activeItems.sumOf { it.totalPomodoroMinutes }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 330.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Work Timeline", style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(
                            text = "${activeItems.size} open items • ${TimeFormatting.formatMinutesCompact(totalPlannedMinutes)} with breaks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val addInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            if (data.projects.isNotEmpty()) onAddWorkItem() else onCreateProject()
                        },
                        shapes = ButtonDefaults.shapes(),
                        interactionSource = addInteraction,
                        modifier = Modifier
                            .height(48.dp)
                            .bouncyPressScale(addInteraction)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (data.projects.isNotEmpty()) "Schedule" else "Project")
                    }
                }
            }
        }

        if (activeItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStateCard(
                    title = "Nothing scheduled yet",
                    subtitle = "Plan when you want to tackle each work item with custom Pomodoro focus and break blocks.",
                    actionLabel = if (data.projects.isEmpty()) "Create Project" else "Schedule Work Item",
                    onAction = if (data.projects.isEmpty()) onCreateProject else onAddWorkItem
                )
            }
        }

        if (overdueItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Overdue Deadlines (${overdueItems.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(overdueItems, key = { "overdue_${it.id}" }) { item ->
                WorkItemExpressiveCard(
                    item = item,
                    project = data.projectFor(item.projectId),
                    nowMillis = nowMillis,
                    onStartOrPause = { onStartOrPauseItem(item) },
                    onToggleComplete = { onCompleteItem(item) },
                    onClick = { onInspectItem(item) },
                    onEdit = { onEditItem(item) },
                    onAdvancePhase = { onAdvancePhase(item) },
                    onPostpone = { onPostponeItem(item) },
                    onDuplicate = { onDuplicateItem(item) },
                    onDelete = { onDeleteItem(item) }
                )
            }
        }

        scheduledGroups.forEach { (dayLabel, dayItems) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                val mins = dayItems.sumOf { it.totalPomodoroMinutes }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dayLabel, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = TimeFormatting.formatMinutesCompact(mins),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            items(dayItems, key = { "sched_${it.id}" }) { item ->
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

        if (unscheduledItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Unscheduled Backlog",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(unscheduledItems, key = { "unsched_${it.id}" }) { item ->
                WorkItemExpressiveCard(
                    item = item,
                    project = data.projectFor(item.projectId),
                    nowMillis = nowMillis,
                    onStartOrPause = { onStartOrPauseItem(item) },
                    onToggleComplete = { onCompleteItem(item) },
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
}
