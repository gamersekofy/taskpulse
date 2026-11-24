package com.example.wear.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.StepperDefaults
import androidx.wear.compose.material3.StepperLevelIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.shared.SharedProject
import com.example.shared.SharedTimeFormatting
import com.example.shared.SharedWorkItem
import com.example.wear.data.WatchProjectColor
import com.example.wear.data.WatchProjectIcon
import com.example.wear.data.WatchWorkPriority
import com.example.wear.ui.components.rememberWatchTextInputLauncher
import com.example.wear.ui.theme.WatchHeroSerifStyle
import com.example.wear.ui.theme.rememberWatchHaptics

private enum class PomodoroTunerField(val title: String, val unit: String) {
    WORK_PERIODS("Work Periods", "sessions"),
    WORK_MINUTES("Work Length", "min"),
    BREAK_COUNT("Break Count", "breaks"),
    BREAK_MINUTES("Break Length", "min")
}

@Composable
fun WatchProjectEditorScreen(
    existingProject: SharedProject?,
    onSave: (
        name: String,
        description: String,
        colorKey: String,
        iconKey: String,
        deadlineMillis: Long
    ) -> Unit
) {
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var name by remember(existingProject?.id) { mutableStateOf(existingProject?.name.orEmpty()) }
    var description by remember(existingProject?.id) { mutableStateOf(existingProject?.description.orEmpty()) }
    var colorKey by remember(existingProject?.id) { mutableStateOf(existingProject?.colorKey ?: WatchProjectColor.CORAL.name) }
    var iconKey by remember(existingProject?.id) { mutableStateOf(existingProject?.iconKey ?: WatchProjectIcon.ROCKET.name) }
    var deadlineMillis by remember(existingProject?.id) { mutableLongStateOf(existingProject?.deadlineMillis ?: 0L) }

    val launchNameInput = rememberWatchTextInputLauncher(
        promptLabel = "Project Name",
        currentValue = name
    ) { name = it }
    val launchDescInput = rememberWatchTextInputLauncher(
        promptLabel = "Project Description",
        currentValue = description
    ) { description = it }

    val canSave = name.trim().isNotEmpty()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        haptics.confirm()
                        onSave(name.trim(), description.trim(), colorKey, iconKey, deadlineMillis)
                    }
                },
                modifier = Modifier
                    .testTag("watch_project_save_button")
                    .scrollable(
                        listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Save")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "pe_header") {
                Text(
                    text = if (existingProject == null) "New Project" else "Edit Project",
                    style = WatchHeroSerifStyle,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            item(key = "pe_name") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        launchNameInput()
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_project_name_input"),
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    label = {
                        Text(
                            text = name.ifBlank { "Tap to dictate name" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = { Text("Project name (voice/keyboard)") }
                )
            }

            if (name.isBlank()) {
                item(key = "pe_quick_names") {
                    val quick1 = remember { MutableInteractionSource() }
                    val quick2 = remember { MutableInteractionSource() }
                    ButtonGroup(
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                    ) {
                        Button(
                            onClick = { haptics.select(); name = "Deep Work" },
                            interactionSource = quick1,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f).animateWidth(quick1)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Deep Work", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(
                            onClick = { haptics.select(); name = "Sprint" },
                            interactionSource = quick2,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f).animateWidth(quick2)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Sprint", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item(key = "pe_desc") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        launchDescInput()
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_project_desc_input"),
                    icon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
                    label = {
                        Text(
                            text = description.ifBlank { "Add description" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            item(key = "pe_color_label") {
                Text(
                    text = "COLOR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "pe_colors") {
                Row(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WatchProjectColor.entries.forEach { entry ->
                        val selected = colorKey == entry.name
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(entry.accent)
                                .then(
                                    if (selected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
                                )
                                .clickable {
                                    haptics.select()
                                    colorKey = entry.name
                                }
                                .testTag("watch_color_${entry.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = entry.label,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "pe_icon_label") {
                Text(
                    text = "BADGE ICON",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "pe_icons") {
                Row(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WatchProjectIcon.entries.forEach { entry ->
                        val selected = iconKey == entry.name
                        val activeAccent = WatchProjectColor.safeValueOf(colorKey)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) activeAccent.accent else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable {
                                    haptics.select()
                                    iconKey = entry.name
                                }
                                .testTag("watch_icon_${entry.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = entry.vector,
                                contentDescription = entry.label,
                                tint = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            item(key = "pe_deadline_label") {
                Text(
                    text = "TARGET DEADLINE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "pe_deadline_row1") {
                val now = System.currentTimeMillis()
                val in3d = remember(now) { SharedTimeFormatting.endOfDayOffsetMillis(3, now) }
                val nextWk = remember(now) { SharedTimeFormatting.endOfDayOffsetMillis(7, now) }
                val in2Wk = remember(now) { SharedTimeFormatting.endOfDayOffsetMillis(14, now) }

                val presets = listOf(
                    "None" to 0L,
                    "+3d" to in3d,
                    "1 Wk" to nextWk,
                    "2 Wk" to in2Wk
                )
                Row(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { (label, target) ->
                        val selected = if (target == 0L) {
                            deadlineMillis <= 0L
                        } else {
                            deadlineMillis > 0L && SharedTimeFormatting.isSameDay(deadlineMillis, target)
                        }
                        Button(
                            onClick = {
                                haptics.select()
                                deadlineMillis = target
                            },
                            colors = if (selected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchWorkItemEditorScreen(
    existingItem: SharedWorkItem?,
    projects: List<SharedProject>,
    initialProjectId: String?,
    onSave: (
        projectId: String,
        title: String,
        notes: String,
        priorityKey: String,
        scheduledStartMillis: Long,
        pomodoroWorkMinutes: Long,
        pomodoroBreakMinutes: Long,
        pomodoroWorkPeriods: Long,
        pomodoroBreakCount: Long,
        deadlineMillis: Long
    ) -> Unit
) {
    val haptics = rememberWatchHaptics()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val defaultProjectId = remember(existingItem?.projectId, initialProjectId, projects) {
        existingItem?.projectId?.takeIf { id -> projects.any { it.id == id } }
            ?: initialProjectId?.takeIf { id -> projects.any { it.id == id } }
            ?: projects.firstOrNull()?.id.orEmpty()
    }

    var projectId by remember(existingItem?.id, defaultProjectId) { mutableStateOf(defaultProjectId) }
    var title by remember(existingItem?.id) { mutableStateOf(existingItem?.title.orEmpty()) }
    var notes by remember(existingItem?.id) { mutableStateOf(existingItem?.notes.orEmpty()) }
    var priorityKey by remember(existingItem?.id) { mutableStateOf(existingItem?.priority ?: WatchWorkPriority.MEDIUM.name) }
    var scheduledStartMillis by remember(existingItem?.id) { mutableLongStateOf(existingItem?.scheduledStartMillis ?: 0L) }
    var deadlineMillis by remember(existingItem?.id) { mutableLongStateOf(existingItem?.deadlineMillis ?: 0L) }

    var workPeriods by remember(existingItem?.id) {
        mutableIntStateOf((existingItem?.effectiveWorkPeriods ?: 2L).toInt().coerceIn(1, 12))
    }
    var workMinutes by remember(existingItem?.id) {
        mutableIntStateOf((existingItem?.effectiveWorkMinutes ?: 25L).toInt().coerceIn(5, 120))
    }
    var breakCount by remember(existingItem?.id) {
        mutableIntStateOf((existingItem?.effectiveBreakCount ?: 1L).toInt().coerceIn(0, 12))
    }
    var breakMinutes by remember(existingItem?.id) {
        mutableIntStateOf((existingItem?.effectiveBreakMinutes ?: 5L).toInt().coerceIn(1, 60))
    }

    var activeStepperField by remember { mutableStateOf<PomodoroTunerField?>(null) }

    if (activeStepperField != null) {
        BackHandler { activeStepperField = null }
        PomodoroStepperSubScreen(
            field = activeStepperField!!,
            workPeriods = workPeriods,
            workMinutes = workMinutes,
            breakCount = breakCount,
            breakMinutes = breakMinutes,
            onChange = { next ->
                haptics.select()
                when (activeStepperField!!) {
                    PomodoroTunerField.WORK_PERIODS -> {
                        workPeriods = next.coerceIn(1, 12)
                        if (breakCount > workPeriods) breakCount = workPeriods
                    }
                    PomodoroTunerField.WORK_MINUTES -> workMinutes = next.coerceIn(5, 120)
                    PomodoroTunerField.BREAK_COUNT -> breakCount = next.coerceIn(0, workPeriods)
                    PomodoroTunerField.BREAK_MINUTES -> breakMinutes = next.coerceIn(1, 60)
                }
            },
            onDone = {
                haptics.confirm()
                activeStepperField = null
            }
        )
        return
    }

    val launchTitleInput = rememberWatchTextInputLauncher(
        promptLabel = "Work Item Title",
        currentValue = title
    ) { title = it }
    val launchNotesInput = rememberWatchTextInputLauncher(
        promptLabel = "Notes",
        currentValue = notes
    ) { notes = it }

    val selectedProject = projects.firstOrNull { it.id == projectId }
    val canSave = title.trim().isNotEmpty()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        haptics.confirm()
                        onSave(
                            projectId,
                            title.trim(),
                            notes.trim(),
                            priorityKey,
                            scheduledStartMillis,
                            workMinutes.toLong(),
                            breakMinutes.toLong(),
                            workPeriods.toLong(),
                            breakCount.toLong(),
                            deadlineMillis
                        )
                    }
                },
                modifier = Modifier
                    .testTag("watch_item_save_button")
                    .scrollable(
                        listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Save")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "wi_header") {
                Text(
                    text = if (existingItem == null) "New Work Item" else "Edit Work Item",
                    style = WatchHeroSerifStyle,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            item(key = "wi_title") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        launchTitleInput()
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_item_title_input"),
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    label = {
                        Text(
                            text = title.ifBlank { "Tap to dictate title" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            if (title.isBlank()) {
                item(key = "wi_quick_titles") {
                    val q1 = remember { MutableInteractionSource() }
                    val q2 = remember { MutableInteractionSource() }
                    ButtonGroup(
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                    ) {
                        Button(
                            onClick = { haptics.select(); title = "Focus Sprint" },
                            interactionSource = q1,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f).animateWidth(q1)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Sprint", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(
                            onClick = { haptics.select(); title = "Review" },
                            interactionSource = q2,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f).animateWidth(q2)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Review", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (projects.isNotEmpty()) {
                item(key = "wi_project") {
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            val curIdx = projects.indexOfFirst { it.id == projectId }
                            val nextIdx = (curIdx + 1) % projects.size
                            projectId = projects[nextIdx].id
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_item_project_cycle"),
                        icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        label = { Text(selectedProject?.name ?: "Select project") },
                        secondaryLabel = { Text("Tap to switch project") }
                    )
                }
            }

            item(key = "wi_notes") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        launchNotesInput()
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_item_notes_input"),
                    icon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
                    label = { Text(notes.ifBlank { "Add notes (optional)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }

            item(key = "wi_priority_label") {
                Text(
                    text = "PRIORITY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "wi_priority") {
                ButtonGroup(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                ) {
                    WatchWorkPriority.entries.forEach { p ->
                        val interaction = remember(p) { MutableInteractionSource() }
                        val selected = priorityKey == p.name
                        Button(
                            onClick = {
                                haptics.select()
                                priorityKey = p.name
                            },
                            interactionSource = interaction,
                            colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(interaction)
                                .testTag("watch_priority_${p.name.lowercase()}")
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(p.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item(key = "wi_pomodoro_label") {
                Text(
                    text = "POMODORO PLAN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "wi_pomo_periods") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        activeStepperField = PomodoroTunerField.WORK_PERIODS
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_tune_work_periods"),
                    icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                    label = { Text("$workPeriods Work Period${if (workPeriods == 1) "" else "s"}") },
                    secondaryLabel = { Text("Tap to tune with Stepper") }
                )
            }

            item(key = "wi_pomo_work_len") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        activeStepperField = PomodoroTunerField.WORK_MINUTES
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_tune_work_minutes"),
                    label = { Text("Work Length: ${workMinutes}m") }
                )
            }

            item(key = "wi_pomo_breaks") {
                FilledTonalButton(
                    onClick = {
                        haptics.select()
                        activeStepperField = PomodoroTunerField.BREAK_COUNT
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth()
                        .testTag("watch_tune_break_count"),
                    label = { Text("Breaks: $breakCount × ${breakMinutes}m") },
                    secondaryLabel = { Text("Tap to adjust break count") }
                )
            }

            if (breakCount > 0) {
                item(key = "wi_pomo_break_len") {
                    FilledTonalButton(
                        onClick = {
                            haptics.select()
                            activeStepperField = PomodoroTunerField.BREAK_MINUTES
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth()
                            .testTag("watch_tune_break_minutes"),
                        label = { Text("Break Length: ${breakMinutes}m") }
                    )
                }
            }

            item(key = "wi_start_label") {
                Text(
                    text = "WHEN TO WORK",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "wi_start_presets") {
                val now = remember { System.currentTimeMillis() }
                val startOptions = listOf(
                    "None" to 0L,
                    "Now" to now,
                    "20:00" to SharedTimeFormatting.tonightAt20Millis(now),
                    "Tmrw" to SharedTimeFormatting.tomorrowAt9Millis(now)
                )
                Row(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    startOptions.forEach { (label, target) ->
                        val selected = when {
                            target == 0L -> scheduledStartMillis <= 0L
                            label == "Now" -> scheduledStartMillis > 0L && kotlin.math.abs(scheduledStartMillis - target) < 600_000L
                            else -> scheduledStartMillis > 0L && kotlin.math.abs(scheduledStartMillis - target) < 3_600_000L
                        }
                        Button(
                            onClick = {
                                haptics.select()
                                scheduledStartMillis = target
                            },
                            colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }

            item(key = "wi_deadline_label") {
                Text(
                    text = "DEADLINE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item(key = "wi_deadline_presets") {
                val now = remember { System.currentTimeMillis() }
                val todayEod = remember(now) { SharedTimeFormatting.endOfDayOffsetMillis(0, now) }
                val tomorrowEod = remember(now) { SharedTimeFormatting.endOfDayOffsetMillis(1, now) }
                val threeDaysEod = remember(now) { SharedTimeFormatting.endOfDayOffsetMillis(3, now) }
                val deadlineOptions = listOf(
                    "None" to 0L,
                    "Today" to todayEod,
                    "Tmrw" to tomorrowEod,
                    "+3d" to threeDaysEod
                )
                Row(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    deadlineOptions.forEach { (label, target) ->
                        val selected = if (target == 0L) {
                            deadlineMillis <= 0L
                        } else {
                            deadlineMillis > 0L && SharedTimeFormatting.isSameDay(deadlineMillis, target)
                        }
                        Button(
                            onClick = {
                                haptics.select()
                                deadlineMillis = target
                            },
                            colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PomodoroStepperSubScreen(
    field: PomodoroTunerField,
    workPeriods: Int,
    workMinutes: Int,
    breakCount: Int,
    breakMinutes: Int,
    onChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    val (currentVal, progression) = when (field) {
        PomodoroTunerField.WORK_PERIODS -> workPeriods to (1..12)
        PomodoroTunerField.WORK_MINUTES -> workMinutes to (5..120 step 5)
        PomodoroTunerField.BREAK_COUNT -> breakCount to (0..workPeriods.coerceAtLeast(1))
        PomodoroTunerField.BREAK_MINUTES -> breakMinutes to (1..60)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Stepper(
            value = currentVal,
            onValueChange = onChange,
            valueProgression = progression,
            decreaseIcon = {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(StepperDefaults.IconSize)
                )
            },
            increaseIcon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(StepperDefaults.IconSize)
                )
            }
        ) {
            Text(
                text = field.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$currentVal ${field.unit}",
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = onDone,
                modifier = Modifier.testTag("watch_stepper_done_button")
            ) {
                Text("Done")
            }
        }

        StepperLevelIndicator(
            value = { currentVal },
            valueProgression = progression,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
