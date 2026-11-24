package com.example.ui

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.window.core.layout.WindowSizeClass
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.signOutUser
import com.example.data.Project
import com.example.data.TaskPulseRepository
import com.example.data.UiState
import com.example.data.WorkItem
import com.example.data.WorkStatus
import com.example.liveupdates.WorkSessionForegroundService
import com.example.liveupdates.WorkSessionNotificationHelper
import com.example.ui.components.bouncyPressScale
import com.example.ui.components.rememberExpressiveHaptics
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.sheets.ProjectEditorSheet
import com.example.ui.sheets.WorkItemDetailSheet
import com.example.ui.sheets.WorkItemEditorSheet
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class PulseDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FOCUS(R.string.nav_focus, Icons.Rounded.Timer, Icons.Outlined.Timer),
    PROJECTS(R.string.nav_projects, Icons.Rounded.Folder, Icons.Outlined.Folder),
    SCHEDULE(R.string.nav_schedule, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth)
}

@Composable
fun TaskPulseRootApp(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    val context = LocalContext.current

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        LaunchedEffect(Unit) {
            WorkSessionForegroundService.stop(context)
        }
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedTaskPulse(user = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthenticatedTaskPulse(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberExpressiveHaptics()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TaskPulseViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TaskPulseViewModel(
                    repository = TaskPulseRepository(db),
                    currentUserId = user.uid
                )
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val nowMillis by viewModel.tickerMillis.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var currentDestination by rememberSaveable { mutableStateOf(PulseDestination.FOCUS) }
    var showProjectEditor by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }

    var showWorkItemEditor by remember { mutableStateOf(false) }
    var editingWorkItem by remember { mutableStateOf<WorkItem?>(null) }
    var defaultProjectIdForNewItem by remember { mutableStateOf<String?>(null) }

    var inspectingWorkItemId by remember { mutableStateOf<String?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(WorkSessionNotificationHelper.hasNotificationPermission(context))
    }
    var canPostPromoted by remember {
        mutableStateOf(WorkSessionNotificationHelper.canPostPromotedLiveUpdates(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = WorkSessionNotificationHelper.hasNotificationPermission(context)
                canPostPromoted = WorkSessionNotificationHelper.canPostPromotedLiveUpdates(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        canPostPromoted = WorkSessionNotificationHelper.canPostPromotedLiveUpdates(context)
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission needed to display Live Updates.")
            }
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val successData = (dashboardState as? UiState.Success<DashboardData>)?.data
    val runningItem = successData?.activeRunningItem
    LaunchedEffect(
        runningItem?.id,
        runningItem?.title,
        runningItem?.elapsedSeconds,
        runningItem?.phaseElapsedSeconds,
        runningItem?.pomodoroPhase,
        runningItem?.currentPeriodIndex,
        runningItem?.pomodoroWorkMinutes,
        runningItem?.pomodoroBreakMinutes,
        runningItem?.pomodoroWorkPeriods,
        runningItem?.pomodoroBreakCount,
        runningItem?.sessionStartedAtMillis,
        runningItem?.deadlineMillis,
        hasNotificationPermission
    ) {
        if (runningItem != null && runningItem.statusEnum == WorkStatus.IN_PROGRESS) {
            if (hasNotificationPermission) {
                val projName = successData.projectNameFor(runningItem.projectId)
                WorkSessionForegroundService.syncActiveSession(context, runningItem, projName)
            }
        } else {
            WorkSessionForegroundService.stop(context)
        }
    }

    // Todoist-style responsive navigation:
    // Phone Portrait -> Bottom NavigationBar
    // Phone Landscape OR Tablet/Foldable -> Left NavigationRail sidebar
    val configuration = LocalConfiguration.current
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isMediumOrExpanded = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )
    val useNavRail = isLandscape || isMediumOrExpanded
    val layoutType = if (useNavRail) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }

    val startOrPauseHandler: (WorkItem) -> Unit = { item ->
        if (item.statusEnum == WorkStatus.IN_PROGRESS) {
            viewModel.pauseSession(item)
        } else {
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.startSession(item, runningItem)
        }
    }

    val openEditWorkItem: (WorkItem) -> Unit = { item ->
        editingWorkItem = item
        defaultProjectIdForNewItem = item.projectId
        showWorkItemEditor = true
    }

    val contentWindowInsets = if (useNavRail) {
        WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
        )
    } else {
        WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    }

    NavigationSuiteScaffoldLayout(
        layoutType = layoutType,
        navigationSuite = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = if (useNavRail) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                    )
                } else {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                }
            ) {
                NavigationSuite(
                    layoutType = layoutType
                ) {
                    PulseDestination.entries.forEach { dest ->
                        val selected = currentDestination == dest
                        val label = context.getString(dest.labelRes)
                        item(
                            selected = selected,
                            onClick = {
                                if (currentDestination != dest) {
                                    haptics.select()
                                    currentDestination = dest
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("nav_item_${dest.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = contentWindowInsets,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        val initials = (user.displayName?.firstOrNull()
                            ?: user.email?.firstOrNull()
                            ?: 'T').uppercaseChar().toString()
                        IconButton(
                            onClick = {
                                haptics.select()
                                showAccountDialog = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("account_menu_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(initials, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (successData != null) {
                    val hasProjects = successData.projects.isNotEmpty()
                    val fabInteraction = remember { MutableInteractionSource() }
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptics.select()
                            if (!hasProjects) {
                                editingProject = null
                                showProjectEditor = true
                            } else {
                                editingWorkItem = null
                                defaultProjectIdForNewItem = successData.projects.firstOrNull()?.id
                                showWorkItemEditor = true
                            }
                        },
                        interactionSource = fabInteraction,
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = {
                            Text(
                                text = if (hasProjects) "Work Item" else "New Project",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier
                            .bouncyPressScale(fabInteraction)
                            .testTag("main_fab")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (val state = dashboardState) {
                    UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator(modifier = Modifier.size(64.dp))
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val data = state.data
                        when (currentDestination) {
                            PulseDestination.FOCUS -> FocusScreen(
                                data = data,
                                nowMillis = nowMillis,
                                hasNotificationPermission = hasNotificationPermission,
                                canPostPromotedLiveUpdates = canPostPromoted,
                                isAndroid16OrAbove = Build.VERSION.SDK_INT >= 36,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onOpenLiveUpdateSettings = {
                                    context.startActivity(
                                        WorkSessionNotificationHelper.createLiveUpdatesSettingsIntent(context)
                                    )
                                },
                                onStartOrPauseItem = startOrPauseHandler,
                                onAdvancePhase = { viewModel.advancePhase(it) },
                                onCompleteItem = { viewModel.completeSession(it) },
                                onReopenItem = { viewModel.reopenWorkItem(it) },
                                onInspectItem = { inspectingWorkItemId = it.id },
                                onEditItem = openEditWorkItem,
                                onPostponeItem = { viewModel.postponeWorkItem(it) },
                                onDuplicateItem = { viewModel.duplicateWorkItem(it) },
                                onDeleteItem = { viewModel.deleteWorkItem(it) },
                                onCreateWorkItem = {
                                    editingWorkItem = null
                                    defaultProjectIdForNewItem = data.projects.firstOrNull()?.id
                                    showWorkItemEditor = true
                                },
                                onCreateProject = {
                                    editingProject = null
                                    showProjectEditor = true
                                }
                            )

                            PulseDestination.PROJECTS -> ProjectsScreen(
                                data = data,
                                nowMillis = nowMillis,
                                onCreateProject = {
                                    editingProject = null
                                    showProjectEditor = true
                                },
                                onEditProject = { proj ->
                                    editingProject = proj
                                    showProjectEditor = true
                                },
                                onDeleteProject = { proj, items ->
                                    viewModel.deleteProject(proj, items)
                                },
                                onAddWorkItemForProject = { projId ->
                                    editingWorkItem = null
                                    defaultProjectIdForNewItem = projId
                                    showWorkItemEditor = true
                                },
                                onStartOrPauseItem = startOrPauseHandler,
                                onAdvancePhase = { viewModel.advancePhase(it) },
                                onCompleteItem = { viewModel.completeSession(it) },
                                onReopenItem = { viewModel.reopenWorkItem(it) },
                                onInspectItem = { inspectingWorkItemId = it.id },
                                onEditItem = openEditWorkItem,
                                onPostponeItem = { viewModel.postponeWorkItem(it) },
                                onDuplicateItem = { viewModel.duplicateWorkItem(it) },
                                onDeleteItem = { viewModel.deleteWorkItem(it) }
                            )

                            PulseDestination.SCHEDULE -> ScheduleScreen(
                                data = data,
                                nowMillis = nowMillis,
                                onStartOrPauseItem = startOrPauseHandler,
                                onAdvancePhase = { viewModel.advancePhase(it) },
                                onCompleteItem = { viewModel.completeSession(it) },
                                onReopenItem = { viewModel.reopenWorkItem(it) },
                                onInspectItem = { inspectingWorkItemId = it.id },
                                onEditItem = openEditWorkItem,
                                onPostponeItem = { viewModel.postponeWorkItem(it) },
                                onDuplicateItem = { viewModel.duplicateWorkItem(it) },
                                onDeleteItem = { viewModel.deleteWorkItem(it) },
                                onAddWorkItem = {
                                    editingWorkItem = null
                                    defaultProjectIdForNewItem = data.projects.firstOrNull()?.id
                                    showWorkItemEditor = true
                                },
                                onCreateProject = {
                                    editingProject = null
                                    showProjectEditor = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProjectEditor) {
        ProjectEditorSheet(
            existingProject = editingProject,
            onDismiss = { showProjectEditor = false },
            onSave = { name, desc, color, icon, deadline ->
                viewModel.saveProject(
                    existingProjectId = editingProject?.id,
                    name = name,
                    description = desc,
                    color = color,
                    icon = icon,
                    deadlineMillis = deadline
                ) {
                    showProjectEditor = false
                }
            }
        )
    }

    if (showWorkItemEditor && successData != null) {
        WorkItemEditorSheet(
            projects = successData.projects,
            defaultProjectId = defaultProjectIdForNewItem,
            existingItem = editingWorkItem,
            onDismiss = { showWorkItemEditor = false },
            onSave = { projId, title, notes, priority, startMillis, workMins, breakMins, workPeriods, breakCount, deadline ->
                viewModel.saveWorkItem(
                    existingItem = editingWorkItem,
                    projectId = projId,
                    title = title,
                    notes = notes,
                    priority = priority,
                    scheduledStartMillis = startMillis,
                    pomodoroWorkMinutes = workMins,
                    pomodoroBreakMinutes = breakMins,
                    pomodoroWorkPeriods = workPeriods,
                    pomodoroBreakCount = breakCount,
                    deadlineMillis = deadline
                ) { ok ->
                    if (ok) showWorkItemEditor = false
                }
            }
        )
    }

    val inspectedItem = inspectingWorkItemId?.let { id ->
        successData?.workItems?.firstOrNull { it.id == id }
    }
    if (inspectedItem != null) {
        WorkItemDetailSheet(
            item = inspectedItem,
            project = successData?.projectFor(inspectedItem.projectId),
            nowMillis = nowMillis,
            onDismiss = { inspectingWorkItemId = null },
            onStartOrPause = { startOrPauseHandler(inspectedItem) },
            onAdvancePhase = { viewModel.advancePhase(inspectedItem) },
            onComplete = {
                viewModel.completeSession(inspectedItem)
                inspectingWorkItemId = null
            },
            onReopen = { viewModel.reopenWorkItem(inspectedItem) },
            onUpdateProgress = { pct -> viewModel.updateProgress(inspectedItem, pct) },
            onEdit = {
                inspectingWorkItemId = null
                editingWorkItem = inspectedItem
                defaultProjectIdForNewItem = inspectedItem.projectId
                showWorkItemEditor = true
            },
            onDelete = {
                viewModel.deleteWorkItem(inspectedItem)
                inspectingWorkItemId = null
            }
        )
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("TaskPulse Account & Live Updates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = user.displayName ?: user.email ?: "Signed-in User",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!user.email.isNullOrBlank()) {
                        Text(
                            text = user.email!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val liveLabel = when {
                        !hasNotificationPermission -> "Notification permission is off. Tap below to enable Pomodoro alerts."
                        Build.VERSION.SDK_INT >= 36 && !canPostPromoted -> "Android Live Updates promotion can be toggled in system settings."
                        Build.VERSION.SDK_INT >= 36 -> "Android 16 Live Updates are active and promoted."
                        else -> "Live ongoing Pomodoro notifications are enabled."
                    }
                    Text(text = liveLabel, style = MaterialTheme.typography.bodyMedium)

                    FilledTonalButton(
                        onClick = {
                            if (!hasNotificationPermission) {
                                requestNotificationPermission()
                            } else {
                                context.startActivity(
                                    WorkSessionNotificationHelper.createLiveUpdatesSettingsIntent(context)
                                )
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (!hasNotificationPermission) "Enable Notifications" else "Live Update Settings")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccountDialog = false
                        WorkSessionForegroundService.stop(context)
                        signOutUser(credentialManager, scope)
                    },
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
