package com.example.widget

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.ui.TimeFormatting
import com.example.ui.theme.ExpressiveDarkColorScheme
import com.example.ui.theme.ExpressiveLightColorScheme

private object TaskPulseWidgetColors {
    val fallbackColors = ColorProviders(
        light = ExpressiveLightColorScheme,
        dark = ExpressiveDarkColorScheme
    )
}

class TaskPulseGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_BUCKETS)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetSnapshotStore.readState(context)

        provideContent {
            val state by WidgetSnapshotStore.observeState(context).collectAsState(initial = initialState)
            val themeColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors
            } else {
                TaskPulseWidgetColors.fallbackColors
            }
            GlanceTheme(colors = themeColors) {
                TaskPulseWidgetRoot(state = state)
            }
        }
    }

    companion object {
        private val COMPACT_SIZE = DpSize(140.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 110.dp)
        private val LARGE_SIZE = DpSize(250.dp, 220.dp)

        private val RESPONSIVE_BUCKETS = setOf(
            COMPACT_SIZE,
            MEDIUM_SIZE,
            LARGE_SIZE
        )
    }
}

@Composable
private fun TaskPulseWidgetRoot(state: TaskPulseWidgetState) {
    val size = LocalSize.current
    val isCompact = size.width < 220.dp
    val isLarge = size.height >= 200.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(R.dimen.widget_corner_radius)
            .padding(if (isCompact) 12.dp else 14.dp)
    ) {
        when {
            !state.isSignedIn -> SignedOutWidgetContent(isCompact = isCompact)
            state.heroItem == null -> AllCaughtUpWidgetContent(
                doneCount = state.doneCount,
                isCompact = isCompact
            )
            else -> ActiveDashboardWidgetContent(
                state = state,
                hero = state.heroItem,
                isCompact = isCompact,
                isLarge = isLarge
            )
        }
    }
}

@Composable
private fun SignedOutWidgetContent(isCompact: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        WidgetBrandRow(trailingChip = null, showRefresh = false)
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = if (isCompact) "Sign in to Focus" else "Sign in to sync your Pomodoro timer & deadlines",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (isCompact) 14.sp else 15.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        WidgetPrimaryPillButton(
            label = "Open TaskPulse",
            iconRes = R.drawable.ic_widget_bolt,
            onClick = actionStartActivity<MainActivity>(),
            backgroundColor = GlanceTheme.colors.primary,
            contentColor = GlanceTheme.colors.onPrimary
        )
    }
}

@Composable
private fun AllCaughtUpWidgetContent(
    doneCount: Int,
    isCompact: Boolean
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetBrandRow(
            trailingChip = if (doneCount > 0) "$doneCount done" else null,
            showRefresh = true
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "All caught up!",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (isCompact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = if (isCompact) "Tap to add a task" else "Schedule a new work item with Pomodoro blocks",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            ),
            maxLines = 2
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        WidgetPrimaryPillButton(
            label = if (isCompact) "Add Task" else "Open TaskPulse",
            iconRes = R.drawable.ic_widget_play,
            onClick = actionStartActivity<MainActivity>(),
            backgroundColor = GlanceTheme.colors.primaryContainer,
            contentColor = GlanceTheme.colors.onPrimaryContainer
        )
    }
}

@Composable
private fun ActiveDashboardWidgetContent(
    state: TaskPulseWidgetState,
    hero: WidgetFocusItemSnapshot,
    isCompact: Boolean,
    isLarge: Boolean
) {
    val nowMillis = System.currentTimeMillis()
    val remainingSeconds = hero.livePhaseRemainingSeconds(nowMillis)
    val overtimeSeconds = hero.liveOvertimeSeconds(nowMillis)
    val phaseFraction = hero.livePhaseFraction(nowMillis)
    val deadlineInfo = TimeFormatting.formatDeadlineCountdown(hero.deadlineMillis, nowMillis, isDone = false)

    val phaseTag = when {
        hero.isRunning && hero.isBreak -> "BREAK ${hero.currentPeriodIndex}"
        hero.isRunning -> "WORK ${hero.currentPeriodIndex}/${hero.totalWorkPeriods}"
        hero.isPaused && hero.isBreak -> "PAUSED BREAK"
        hero.isPaused -> "PAUSED ${hero.currentPeriodIndex}/${hero.totalWorkPeriods}"
        else -> "UP NEXT • ${TimeFormatting.formatMinutesCompact(hero.totalMinutes)}"
    }

    val statusChip = when {
        state.doneCount > 0 -> "${state.doneCount} done"
        state.activeCount > 1 -> "${state.activeCount} active"
        else -> null
    }

    val accentTint = if (hero.isBreak) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary
    val accentBg = if (hero.isBreak) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.primaryContainer
    val accentOnBg = if (hero.isBreak) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onPrimaryContainer

    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (isCompact) {
            CompactWidgetContent(
                hero = hero,
                phaseTag = phaseTag,
                remainingSeconds = remainingSeconds,
                overtimeSeconds = overtimeSeconds,
                phaseFraction = phaseFraction,
                accentTint = accentTint,
                accentBg = accentBg,
                accentOnBg = accentOnBg
            )
        } else {
            WidgetBrandRow(
                trailingChip = statusChip,
                showRefresh = true
            )
            Spacer(modifier = GlanceModifier.height(8.dp))

            // Hero Pomodoro Card
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(18.dp)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "${hero.projectName} • $phaseTag",
                            style = TextStyle(
                                color = accentTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = hero.title,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSecondaryContainer,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = if (isLarge) 2 else 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PomodoroTimerDisplay(
                        hero = hero,
                        remainingSeconds = remainingSeconds,
                        overtimeSeconds = overtimeSeconds,
                        compact = false
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))
                LinearProgressIndicator(
                    progress = if (hero.isRunning || hero.isPaused) phaseFraction else hero.overallFraction,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .cornerRadius(100.dp),
                    color = accentTint,
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )

                Spacer(modifier = GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hero.deadlineMillis > 0L) deadlineInfo.label else "${hero.totalWorkPeriods}x Focus",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    WidgetPrimaryPillButton(
                        label = if (hero.isRunning) "Pause" else if (hero.isPaused) "Resume" else "Focus",
                        iconRes = if (hero.isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        onClick = actionRunCallback<StartOrPauseWidgetAction>(
                            actionParametersOf(KEY_WIDGET_ITEM_ID to hero.id)
                        ),
                        backgroundColor = accentBg,
                        contentColor = accentOnBg
                    )
                    if (hero.isRunning || hero.isPaused) {
                        if (hero.hasNextSlot) {
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            WidgetCircleIconButton(
                                iconRes = R.drawable.ic_widget_skip_next,
                                contentDescription = if (hero.isBreak) "Skip break" else "Next period",
                                onClick = actionRunCallback<AdvancePhaseWidgetAction>(
                                    actionParametersOf(KEY_WIDGET_ITEM_ID to hero.id)
                                ),
                                backgroundColor = GlanceTheme.colors.surface,
                                iconColor = GlanceTheme.colors.onSurface
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        WidgetCircleIconButton(
                            iconRes = R.drawable.ic_widget_check,
                            contentDescription = "Complete task",
                            onClick = actionRunCallback<CompleteItemWidgetAction>(
                                actionParametersOf(KEY_WIDGET_ITEM_ID to hero.id)
                            ),
                            backgroundColor = GlanceTheme.colors.tertiaryContainer,
                            iconColor = GlanceTheme.colors.onTertiaryContainer
                        )
                    }
                }
            }

            if (isLarge) {
                Spacer(modifier = GlanceModifier.height(10.dp))
                Text(
                    text = if (state.upNextItems.isEmpty()) "Queue Clear" else "Up Next",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                if (state.upNextItems.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(14.dp)
                            .clickable(actionStartActivity<MainActivity>())
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Tap to schedule your next Pomodoro work item",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp
                            ),
                            maxLines = 2
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    ) {
                        items(
                            items = state.upNextItems,
                            itemId = { item -> item.id.hashCode().toLong() }
                        ) { queueItem ->
                            UpNextQueueRow(item = queueItem, nowMillis = nowMillis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactWidgetContent(
    hero: WidgetFocusItemSnapshot,
    phaseTag: String,
    remainingSeconds: Long,
    overtimeSeconds: Long,
    phaseFraction: Float,
    accentTint: ColorProvider,
    accentBg: ColorProvider,
    accentOnBg: ColorProvider
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_bolt),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accentTint),
                modifier = GlanceModifier.size(16.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = phaseTag,
                style = TextStyle(
                    color = accentTint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = hero.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2,
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>())
        )

        Spacer(modifier = GlanceModifier.height(4.dp))
        PomodoroTimerDisplay(
            hero = hero,
            remainingSeconds = remainingSeconds,
            overtimeSeconds = overtimeSeconds,
            compact = true
        )

        Spacer(modifier = GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (hero.isRunning || hero.isPaused) phaseFraction else hero.overallFraction,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(5.dp)
                .cornerRadius(100.dp),
            color = accentTint,
            backgroundColor = GlanceTheme.colors.surfaceVariant
        )

        Spacer(modifier = GlanceModifier.height(6.dp))
        WidgetPrimaryPillButton(
            label = if (hero.isRunning) "Pause" else if (hero.isPaused) "Resume" else "Focus",
            iconRes = if (hero.isRunning) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            onClick = actionRunCallback<StartOrPauseWidgetAction>(
                actionParametersOf(KEY_WIDGET_ITEM_ID to hero.id)
            ),
            backgroundColor = accentBg,
            contentColor = accentOnBg,
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UpNextQueueRow(
    item: WidgetFocusItemSnapshot,
    nowMillis: Long
) {
    val deadlineLabel = if (item.deadlineMillis > 0L) {
        TimeFormatting.formatDeadlineCountdown(item.deadlineMillis, nowMillis, isDone = false).label
    } else {
        TimeFormatting.formatMinutesCompact(item.totalMinutes)
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(14.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${item.projectName} • $deadlineLabel",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            WidgetCircleIconButton(
                iconRes = R.drawable.ic_widget_play,
                contentDescription = "Start ${item.title}",
                onClick = actionRunCallback<StartOrPauseWidgetAction>(
                    actionParametersOf(KEY_WIDGET_ITEM_ID to item.id)
                ),
                backgroundColor = GlanceTheme.colors.primaryContainer,
                iconColor = GlanceTheme.colors.onPrimaryContainer
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
    }
}

@Composable
private fun PomodoroTimerDisplay(
    hero: WidgetFocusItemSnapshot,
    remainingSeconds: Long,
    overtimeSeconds: Long,
    compact: Boolean
) {
    val context = LocalContext.current
    if (hero.isRunning && remainingSeconds > 0L) {
        val baseElapsed = SystemClock.elapsedRealtime() + (remainingSeconds * 1000L)
        val layoutRes = if (compact) {
            R.layout.widget_pomodoro_chronometer_compact
        } else {
            R.layout.widget_pomodoro_chronometer
        }
        val remoteViews = RemoteViews(context.packageName, layoutRes).apply {
            setChronometer(R.id.widget_chronometer, baseElapsed, null, true)
            setChronometerCountDown(R.id.widget_chronometer, true)
        }
        AndroidRemoteViews(remoteViews = remoteViews)
    } else {
        val display = when {
            overtimeSeconds > 0L -> "+${TimeFormatting.formatDurationClock(overtimeSeconds)}"
            hero.isPaused -> TimeFormatting.formatDurationClock(remainingSeconds)
            else -> TimeFormatting.formatDurationClock(hero.phaseDurationSeconds)
        }
        Text(
            text = display,
            style = TextStyle(
                color = if (hero.isBreak) GlanceTheme.colors.tertiary else GlanceTheme.colors.onSurface,
                fontSize = if (compact) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun WidgetBrandRow(
    trailingChip: String?,
    showRefresh: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_bolt),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(15.dp)
                )
            }
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "TaskPulse",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (!trailingChip.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = trailingChip,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
        if (showRefresh) {
            WidgetCircleIconButton(
                iconRes = R.drawable.ic_widget_refresh,
                contentDescription = "Refresh widget",
                onClick = actionRunCallback<RefreshWidgetAction>(),
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                iconColor = GlanceTheme.colors.onSurfaceVariant,
                visualSize = 28.dp
            )
        }
    }
}

@Composable
private fun WidgetPrimaryPillButton(
    label: String,
    iconRes: Int,
    onClick: Action,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .background(backgroundColor)
            .cornerRadius(100.dp)
            .semantics { this.contentDescription = label }
            .clickable(onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = GlanceModifier.size(16.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WidgetCircleIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: Action,
    backgroundColor: ColorProvider,
    iconColor: ColorProvider,
    visualSize: androidx.compose.ui.unit.Dp = 34.dp
) {
    Box(
        modifier = GlanceModifier
            .size(visualSize)
            .background(backgroundColor)
            .cornerRadius(100.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = GlanceModifier.size(18.dp)
        )
    }
}
