package com.example.ui.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PomodoroPhase
import com.example.data.PomodoroSlot
import com.example.data.PomodoroSlotState
import com.example.data.Project
import com.example.data.ProjectColor
import com.example.data.ProjectIcon
import com.example.data.WorkItem
import com.example.data.WorkPriority
import com.example.data.WorkStatus
import com.example.ui.TimeFormatting
import com.example.ui.components.DeadlineBadge
import com.example.ui.components.PomodoroTimelineStrip
import com.example.ui.components.ProjectAccentBadge
import com.example.ui.components.bouncyPressScale
import com.example.ui.components.rememberExpressiveHaptics
import com.example.ui.theme.CountdownCondensedLargeStyle
import com.example.ui.theme.DarkMintTertiary
import com.example.ui.theme.MintTertiary
import java.util.Calendar
import kotlin.math.roundToInt

private enum class ProjectDeadlinePreset(val label: String) {
    NONE("None"),
    THREE_DAYS("In 3d"),
    NEXT_WEEK("Next Wk"),
    CUSTOM("Custom")
}

private enum class SchedulePreset(val label: String) {
    RIGHT_NOW("Now"),
    IN_1_HOUR("In 1h"),
    TOMORROW_9AM("Tmrw 9a"),
    CUSTOM("Custom")
}

private enum class TaskDeadlinePreset(val label: String) {
    NONE("None"),
    TODAY_6PM("Today"),
    TOMORROW_6PM("Tmrw"),
    CUSTOM("Custom")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun ProjectEditorSheet(
    existingProject: Project?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, color: ProjectColor, icon: ProjectIcon, deadlineMillis: Long) -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(existingProject) { mutableStateOf(existingProject?.name.orEmpty()) }
    var description by remember(existingProject) { mutableStateOf(existingProject?.description.orEmpty()) }
    var selectedColor by remember(existingProject) { mutableStateOf(existingProject?.accent ?: ProjectColor.CORAL) }
    var selectedIcon by remember(existingProject) { mutableStateOf(existingProject?.icon ?: ProjectIcon.ROCKET) }
    var deadlineMillis by remember(existingProject) { mutableLongStateOf(existingProject?.deadlineMillis ?: 0L) }
    var deadlinePreset by remember(existingProject) {
        mutableStateOf(
            if ((existingProject?.deadlineMillis ?: 0L) <= 0L) ProjectDeadlinePreset.NONE
            else ProjectDeadlinePreset.CUSTOM
        )
    }
    var showDateTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !isImeVisible),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existingProject == null) "Create Project" else "Edit Project",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("Project name") },
                placeholder = { Text("e.g. Mobile App Launch") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_name_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 300) description = it },
                label = { Text("Goal or notes (optional)") },
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Color", style = MaterialTheme.typography.titleSmall)
            val darkTheme = isSystemInDarkTheme()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProjectColor.entries.forEach { colorOption ->
                    val selected = colorOption == selectedColor
                    val swatchColor = colorOption.vibrantColor(darkTheme)
                    val swatchCheckTint = colorOption.onVibrantColor(darkTheme)
                    val interaction = remember(colorOption) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .bouncyPressScale(interaction, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(interactionSource = interaction, indication = null) {
                                haptics.select()
                                selectedColor = colorOption
                            }
                            .testTag("color_swatch_${colorOption.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected ${colorOption.label}",
                                tint = swatchCheckTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Text("Badge Icon", style = MaterialTheme.typography.titleSmall)
            val iconRows = remember { ProjectIcon.entries.chunked(3) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                iconRows.forEach { rowIcons ->
                    ButtonGroup(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowIcons.forEachIndexed { idx, iconOption ->
                            val interaction = remember(iconOption) { MutableInteractionSource() }
                            val shapes = when (idx) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                rowIcons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            TonalToggleButton(
                                checked = iconOption == selectedIcon,
                                onCheckedChange = {
                                    haptics.select()
                                    selectedIcon = iconOption
                                },
                                shapes = shapes,
                                interactionSource = interaction,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interaction)
                            ) {
                                Icon(
                                    iconOption.vector,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    iconOption.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            Text("Target Project Deadline", style = MaterialTheme.typography.titleSmall)
            if (deadlineMillis > 0L) {
                Text(
                    text = TimeFormatting.formatDateTime(deadlineMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ButtonGroup(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProjectDeadlinePreset.entries.forEachIndexed { idx, preset ->
                    val interaction = remember(preset) { MutableInteractionSource() }
                    val shapes = when (idx) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ProjectDeadlinePreset.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    TonalToggleButton(
                        checked = deadlinePreset == preset,
                        onCheckedChange = {
                            haptics.select()
                            when (preset) {
                                ProjectDeadlinePreset.NONE -> {
                                    deadlinePreset = preset
                                    deadlineMillis = 0L
                                }
                                ProjectDeadlinePreset.THREE_DAYS -> {
                                    deadlinePreset = preset
                                    deadlineMillis = endOfDayPlusDays(3)
                                }
                                ProjectDeadlinePreset.NEXT_WEEK -> {
                                    deadlinePreset = preset
                                    deadlineMillis = endOfDayPlusDays(7)
                                }
                                ProjectDeadlinePreset.CUSTOM -> {
                                    showDateTimePicker = true
                                }
                            }
                        },
                        shapes = shapes,
                        interactionSource = interaction,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interaction)
                    ) {
                        Text(
                            text = preset.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            val saveInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        focusManager.clearFocus()
                        haptics.confirm()
                        onSave(name.trim(), description.trim(), selectedColor, selectedIcon, deadlineMillis)
                    }
                },
                enabled = name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                interactionSource = saveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .bouncyPressScale(saveInteraction)
                    .testTag("save_project_button")
            ) {
                Text(
                    text = if (existingProject == null) "Launch Project" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showDateTimePicker) {
        DateTimePickerModal(
            initialMillis = if (deadlineMillis > 0L) deadlineMillis else endOfDayPlusDays(2),
            onDismiss = { showDateTimePicker = false },
            onConfirm = { picked ->
                haptics.confirm()
                deadlineMillis = picked
                deadlinePreset = ProjectDeadlinePreset.CUSTOM
                showDateTimePicker = false
            }
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun WorkItemEditorSheet(
    projects: List<Project>,
    defaultProjectId: String?,
    existingItem: WorkItem?,
    onDismiss: () -> Unit,
    onSave: (
        projectId: String,
        title: String,
        notes: String,
        priority: WorkPriority,
        scheduledStartMillis: Long,
        pomodoroWorkMinutes: Long,
        pomodoroBreakMinutes: Long,
        pomodoroWorkPeriods: Long,
        pomodoroBreakCount: Long,
        deadlineMillis: Long
    ) -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedProjectId by remember(existingItem, defaultProjectId, projects) {
        mutableStateOf(
            existingItem?.projectId
                ?: defaultProjectId?.takeIf { id -> projects.any { it.id == id } }
                ?: projects.firstOrNull()?.id.orEmpty()
        )
    }
    var title by remember(existingItem) { mutableStateOf(existingItem?.title.orEmpty()) }
    var notes by remember(existingItem) { mutableStateOf(existingItem?.notes.orEmpty()) }
    var priority by remember(existingItem) { mutableStateOf(existingItem?.priorityEnum ?: WorkPriority.MEDIUM) }

    var scheduledStartMillis by remember(existingItem) {
        mutableLongStateOf(
            existingItem?.scheduledStartMillis?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
        )
    }
    var schedulePreset by remember(existingItem) {
        mutableStateOf(
            if (existingItem != null && existingItem.scheduledStartMillis > 0L) SchedulePreset.CUSTOM
            else SchedulePreset.RIGHT_NOW
        )
    }

    // Customizable Pomodoro state
    var workMinutes by remember(existingItem) {
        mutableIntStateOf((existingItem?.effectiveWorkMinutes ?: 25L).toInt().coerceIn(5, 90))
    }
    var workPeriods by remember(existingItem) {
        mutableIntStateOf((existingItem?.effectiveWorkPeriods ?: 2L).toInt().coerceIn(1, 12))
    }
    var breakCount by remember(existingItem) {
        mutableIntStateOf((existingItem?.effectiveBreakCount ?: 1L).toInt().coerceIn(0, 12))
    }
    var breakMinutes by remember(existingItem) {
        mutableIntStateOf((existingItem?.effectiveBreakMinutes ?: 5L).toInt().coerceIn(1, 30))
    }

    var deadlineMillis by remember(existingItem) {
        mutableLongStateOf(existingItem?.deadlineMillis ?: endOfDayPlusDays(0))
    }
    var deadlinePreset by remember(existingItem) {
        mutableStateOf(
            when {
                existingItem == null -> TaskDeadlinePreset.TODAY_6PM
                existingItem.deadlineMillis <= 0L -> TaskDeadlinePreset.NONE
                else -> TaskDeadlinePreset.CUSTOM
            }
        )
    }

    var pickingSchedule by remember { mutableStateOf(false) }
    var pickingDeadline by remember { mutableStateOf(false) }

    // Ensure breakCount never exceeds workPeriods
    LaunchedEffect(workPeriods) {
        if (breakCount > workPeriods) {
            breakCount = workPeriods
        }
    }

    val previewSlots = remember(workMinutes, workPeriods, breakCount, breakMinutes) {
        buildList {
            for (w in 1..workPeriods) {
                add(PomodoroSlot(isBreak = false, index = w, durationMinutes = workMinutes.toLong(), state = PomodoroSlotState.UPCOMING))
                if (w <= breakCount) {
                    add(PomodoroSlot(isBreak = true, index = w, durationMinutes = breakMinutes.toLong(), state = PomodoroSlotState.ACTIVE))
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !isImeVisible),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existingItem == null) "New Work Item" else "Edit Work Item",
                style = MaterialTheme.typography.headlineMedium
            )

            Text("Project", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                projects.forEach { proj ->
                    FilterChip(
                        selected = proj.id == selectedProjectId,
                        onClick = {
                            haptics.select()
                            selectedProjectId = proj.id
                        },
                        leadingIcon = {
                            Icon(proj.icon.vector, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(proj.name) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                label = { Text("What are you working on?") },
                placeholder = { Text("e.g. Finalize sprint wireframes") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("work_item_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 500) notes = it },
                label = { Text("Notes or milestones (optional)") },
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Priority", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WorkPriority.entries.forEachIndexed { idx, p ->
                    SegmentedButton(
                        selected = p == priority,
                        onClick = {
                            haptics.select()
                            priority = p
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = WorkPriority.entries.size
                        ),
                        modifier = Modifier.testTag("priority_seg_${p.name}")
                    ) {
                        Text(p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // When I want to work on it (Button Group with highlighted selected preset)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("When I Want to Work", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (scheduledStartMillis > 0L) TimeFormatting.formatDateTime(scheduledStartMillis) else "Unscheduled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ButtonGroup(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SchedulePreset.entries.forEachIndexed { idx, preset ->
                            val interaction = remember(preset) { MutableInteractionSource() }
                            val shapes = when (idx) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                SchedulePreset.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            TonalToggleButton(
                                checked = schedulePreset == preset,
                                onCheckedChange = {
                                    haptics.select()
                                    when (preset) {
                                        SchedulePreset.RIGHT_NOW -> {
                                            schedulePreset = preset
                                            scheduledStartMillis = System.currentTimeMillis()
                                        }
                                        SchedulePreset.IN_1_HOUR -> {
                                            schedulePreset = preset
                                            scheduledStartMillis = System.currentTimeMillis() + 3600_000L
                                        }
                                        SchedulePreset.TOMORROW_9AM -> {
                                            schedulePreset = preset
                                            scheduledStartMillis = tomorrowAt9Am()
                                        }
                                        SchedulePreset.CUSTOM -> {
                                            pickingSchedule = true
                                        }
                                    }
                                },
                                shapes = shapes,
                                interactionSource = interaction,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interaction)
                            ) {
                                Text(
                                    preset.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // Customizable Pomodoro Timer Section
            val totalFocusMin = workPeriods * workMinutes
            val totalRestMin = breakCount * breakMinutes
            val totalPlanMin = totalFocusMin + totalRestMin

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pomodoro Plan", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${workPeriods}×${workMinutes}m focus" +
                                    if (breakCount > 0) " • ${breakCount}×${breakMinutes}m break" else " • No breaks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "${TimeFormatting.formatMinutesCompact(totalPlanMin.toLong())} total",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    PomodoroTimelineStrip(
                        slots = previewSlots,
                        workAccent = MaterialTheme.colorScheme.primary
                    )

                    // Work period length presets + haptic slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Work Period Length", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${workMinutes}m",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        ButtonGroup(
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val workPresets = listOf(15, 25, 45, 60)
                            workPresets.forEachIndexed { idx, mins ->
                                val interaction = remember(mins) { MutableInteractionSource() }
                                val shapes = when (idx) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    workPresets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                                TonalToggleButton(
                                    checked = workMinutes == mins,
                                    onCheckedChange = {
                                        haptics.select()
                                        workMinutes = mins
                                    },
                                    shapes = shapes,
                                    interactionSource = interaction,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(interaction)
                                ) {
                                    Text("${mins}m", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Slider(
                            value = workMinutes.toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(5, 90)
                                if (snapped != workMinutes) {
                                    haptics.sliderStep()
                                    workMinutes = snapped
                                }
                            },
                            valueRange = 5f..90f,
                            steps = 16
                        )
                    }

                    // Work periods & Breaks steppers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PomodoroCountStepper(
                            label = "Work Periods",
                            value = workPeriods,
                            min = 1,
                            max = 12,
                            onChange = { next ->
                                haptics.sliderStep()
                                workPeriods = next
                                if (breakCount == 0 && next > 1) {
                                    breakCount = (next - 1).coerceAtLeast(0)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PomodoroCountStepper(
                            label = "Breaks",
                            value = breakCount,
                            min = 0,
                            max = workPeriods,
                            onChange = { next ->
                                haptics.sliderStep()
                                breakCount = next
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (breakCount > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Break Length", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${breakMinutes}m",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MintTertiary
                                )
                            }
                            Slider(
                                value = breakMinutes.toFloat(),
                                onValueChange = { raw ->
                                    val snapped = raw.roundToInt().coerceIn(1, 30)
                                    if (snapped != breakMinutes) {
                                        haptics.sliderStep()
                                        breakMinutes = snapped
                                    }
                                },
                                valueRange = 1f..30f,
                                steps = 28
                            )
                        }
                    }
                }
            }

            // Deadline with highlighted ButtonGroup selection
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Deadline", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (deadlineMillis > 0L) TimeFormatting.formatDateTime(deadlineMillis) else "No deadline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    ButtonGroup(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TaskDeadlinePreset.entries.forEachIndexed { idx, preset ->
                            val interaction = remember(preset) { MutableInteractionSource() }
                            val shapes = when (idx) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                TaskDeadlinePreset.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            TonalToggleButton(
                                checked = deadlinePreset == preset,
                                onCheckedChange = {
                                    haptics.select()
                                    when (preset) {
                                        TaskDeadlinePreset.NONE -> {
                                            deadlinePreset = preset
                                            deadlineMillis = 0L
                                        }
                                        TaskDeadlinePreset.TODAY_6PM -> {
                                            deadlinePreset = preset
                                            deadlineMillis = endOfDayPlusDays(0)
                                        }
                                        TaskDeadlinePreset.TOMORROW_6PM -> {
                                            deadlinePreset = preset
                                            deadlineMillis = endOfDayPlusDays(1)
                                        }
                                        TaskDeadlinePreset.CUSTOM -> {
                                            pickingDeadline = true
                                        }
                                    }
                                },
                                shapes = shapes,
                                interactionSource = interaction,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interaction)
                            ) {
                                Text(
                                    preset.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            val saveInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (title.isNotBlank() && selectedProjectId.isNotBlank()) {
                        focusManager.clearFocus()
                        haptics.confirm()
                        onSave(
                            selectedProjectId,
                            title.trim(),
                            notes.trim(),
                            priority,
                            scheduledStartMillis,
                            workMinutes.toLong(),
                            breakMinutes.toLong(),
                            workPeriods.toLong(),
                            breakCount.toLong(),
                            deadlineMillis
                        )
                    }
                },
                enabled = title.isNotBlank() && selectedProjectId.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                interactionSource = saveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .bouncyPressScale(saveInteraction)
                    .testTag("save_work_item_button")
            ) {
                Text(
                    text = if (existingItem == null) "Schedule Work Item" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (pickingSchedule) {
        DateTimePickerModal(
            initialMillis = if (scheduledStartMillis > 0L) scheduledStartMillis else System.currentTimeMillis(),
            onDismiss = { pickingSchedule = false },
            onConfirm = { picked ->
                haptics.confirm()
                scheduledStartMillis = picked
                schedulePreset = SchedulePreset.CUSTOM
                pickingSchedule = false
            }
        )
    }

    if (pickingDeadline) {
        DateTimePickerModal(
            initialMillis = if (deadlineMillis > 0L) deadlineMillis else endOfDayPlusDays(1),
            onDismiss = { pickingDeadline = false },
            onConfirm = { picked ->
                haptics.confirm()
                deadlineMillis = picked
                deadlinePreset = TaskDeadlinePreset.CUSTOM
                pickingDeadline = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PomodoroCountStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { if (value > min) onChange(value - 1) },
                    enabled = value > min,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                FilledIconButton(
                    onClick = { if (value < max) onChange(value + 1) },
                    enabled = value < max,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Increase $label", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun WorkItemDetailSheet(
    item: WorkItem,
    project: Project?,
    nowMillis: Long,
    onDismiss: () -> Unit,
    onStartOrPause: () -> Unit,
    onAdvancePhase: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onUpdateProgress: (Long) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = rememberExpressiveHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDone = item.statusEnum == WorkStatus.DONE
    val isRunning = item.statusEnum == WorkStatus.IN_PROGRESS
    val snap = item.computePomodoroSnapshot(nowMillis)
    val isBreak = snap.currentPhase == PomodoroPhase.BREAK

    var sliderProgress by remember(item.id, item.progressPercent) {
        mutableFloatStateOf(item.progressPercent.toFloat())
    }
    var lastHapticPercent by remember(item.id) { mutableIntStateOf(item.progressPercent.toInt()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (project != null) {
                ProjectAccentBadge(project = project)
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineMedium
            )

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val darkTheme = isSystemInDarkTheme()
                    val mintTone = if (darkTheme) DarkMintTertiary else MintTertiary
                    val workAccent = project?.accent?.vibrantColor(darkTheme) ?: MaterialTheme.colorScheme.primary
                    val labelText = when {
                        isDone -> "Pomodoro Complete"
                        snap.overtimeSeconds > 0L -> "Period Overtime"
                        isBreak -> "Break ${snap.currentPeriodIndex} • Rest Remaining"
                        else -> "Work ${snap.currentPeriodIndex} of ${snap.totalWorkPeriods} • Remaining"
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isBreak) mintTone else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            isDone -> "Completed"
                            snap.overtimeSeconds > 0L -> "+${TimeFormatting.formatDurationClock(snap.overtimeSeconds)}"
                            else -> TimeFormatting.formatDurationClock(snap.phaseRemainingSeconds)
                        },
                        style = CountdownCondensedLargeStyle
                    )
                    PomodoroTimelineStrip(
                        slots = snap.slots,
                        workAccent = workAccent
                    )
                    LinearWavyProgressIndicator(
                        progress = { snap.phaseFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = if (isBreak) mintTone else workAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        amplitude = { p -> if (isRunning && p > 0f && p < 1f) 1f else 0f }
                    )
                    Text(
                        text = "Scheduled: ${TimeFormatting.formatScheduleWindow(item.scheduledStartMillis, item.totalPomodoroMinutes, nowMillis)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    DeadlineBadge(item.deadlineMillis, nowMillis, isDone)
                }
            }

            if (!isDone) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Task Progress", style = MaterialTheme.typography.titleSmall)
                        Text("${sliderProgress.roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = sliderProgress,
                        onValueChange = { raw ->
                            sliderProgress = raw
                            val rounded = (raw / 5f).roundToInt() * 5
                            if (rounded != lastHapticPercent) {
                                lastHapticPercent = rounded
                                haptics.sliderStep()
                            }
                        },
                        onValueChangeFinished = {
                            onUpdateProgress(sliderProgress.roundToInt().toLong())
                        },
                        valueRange = 0f..100f
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isDone) {
                    val startInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            haptics.confirm()
                            onStartOrPause()
                        },
                        shapes = ButtonDefaults.shapes(),
                        interactionSource = startInteraction,
                        modifier = Modifier
                            .height(48.dp)
                            .bouncyPressScale(startInteraction)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRunning) "Pause" else "Start Work")
                    }

                    if (snap.nextSlot != null) {
                        FilledTonalButton(
                            onClick = {
                                haptics.select()
                                onAdvancePhase()
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBreak) "Skip Break" else "Next Period")
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.confirm()
                            onComplete()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Complete")
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.select()
                            onEdit()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            onReopen()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reopen Task")
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptics.reject()
                        onDelete()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerModal(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val initialCal = remember(initialMillis) {
        Calendar.getInstance().apply { timeInMillis = initialMillis }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = false
    )
    var step by remember { mutableIntStateOf(0) }

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { step = 1 },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Next: Time")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Time", style = MaterialTheme.typography.titleLarge) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDateUtc = datePickerState.selectedDateMillis ?: initialMillis
                        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = selectedDateUtc
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onConfirm(localCal.timeInMillis)
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun endOfDayPlusDays(daysToAdd: Int): Long {
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysToAdd)
        set(Calendar.HOUR_OF_DAY, 18)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun tomorrowAt9Am(): Long {
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
