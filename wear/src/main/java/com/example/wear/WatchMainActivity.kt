package com.example.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerDefaults
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.shared.SharedWorkItem
import com.example.wear.auth.WatchAuthHelper
import com.example.wear.data.WatchWorkStatus
import com.example.wear.data.statusEnum
import com.example.wear.liveupdates.WatchPomodoroForegroundService
import com.example.wear.liveupdates.WatchPomodoroNotificationHelper
import com.example.wear.ui.WatchViewModel
import com.example.wear.ui.screens.WatchAccountSyncScreen
import com.example.wear.ui.screens.WatchFocusPage
import com.example.wear.ui.screens.WatchProjectDetailScreen
import com.example.wear.ui.screens.WatchProjectEditorScreen
import com.example.wear.ui.screens.WatchProjectsPage
import com.example.wear.ui.screens.WatchSchedulePage
import com.example.wear.ui.screens.WatchWorkItemDetailScreen
import com.example.wear.ui.screens.WatchWorkItemEditorScreen
import com.example.wear.ui.theme.TaskPulseWearTheme
import kotlinx.coroutines.delay

private object WatchRoutes {
    const val HOME = "home"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val ITEM_DETAIL = "item/{itemId}"
    const val EDIT_PROJECT = "edit_project?projectId={projectId}"
    const val EDIT_ITEM = "edit_item?itemId={itemId}&projectId={projectId}"
    const val ACCOUNT = "account"

    fun projectDetail(projectId: String) = "project/$projectId"
    fun itemDetail(itemId: String) = "item/$itemId"
    fun editProject(projectId: String? = null) =
        if (projectId.isNullOrBlank()) "edit_project" else "edit_project?projectId=$projectId"
    fun editItem(itemId: String? = null, projectId: String? = null): String {
        val params = buildList {
            if (!itemId.isNullOrBlank()) add("itemId=$itemId")
            if (!projectId.isNullOrBlank()) add("projectId=$projectId")
        }
        return if (params.isEmpty()) "edit_item" else "edit_item?${params.joinToString("&")}"
    }
}

class WatchMainActivity : ComponentActivity() {

    private var isAmbientState by mutableStateOf(false)
    private var ambientTickMillisState by mutableLongStateOf(System.currentTimeMillis())

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbientState = true
            ambientTickMillisState = System.currentTimeMillis()
        }

        override fun onExitAmbient() {
            isAmbientState = false
        }

        override fun onUpdateAmbient() {
            ambientTickMillisState = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WatchPomodoroNotificationHelper.ensureChannels(this)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        setContent {
            TaskPulseWearTheme {
                WatchAppRoot(
                    isAmbient = isAmbientState,
                    ambientTickMillis = ambientTickMillisState
                )
            }
        }
    }
}

@Composable
private fun WatchAppRoot(
    isAmbient: Boolean,
    ambientTickMillis: Long,
    watchViewModel: WatchViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val dashboardData by watchViewModel.dashboardData.collectAsStateWithLifecycle()
    val liveTick by watchViewModel.tickerMillis.collectAsStateWithLifecycle()
    val effectiveNowMillis = if (isAmbient) ambientTickMillis else liveTick

    var hasNotificationPermission by remember {
        mutableStateOf(WatchPomodoroNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            dashboardData.activeRunningItem?.let { active ->
                WatchPomodoroForegroundService.syncActiveSession(
                    context = context,
                    item = active,
                    projectName = dashboardData.projectNameFor(active.projectId)
                )
            }
        }
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var bannerMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(watchViewModel) {
        watchViewModel.events.collect { msg ->
            bannerMessage = msg
            delay(2400L)
            if (bannerMessage == msg) bannerMessage = null
        }
    }

    val activeItem = dashboardData.activeRunningItem
    LaunchedEffect(
        activeItem?.id,
        activeItem?.status,
        activeItem?.sessionStartedAtMillis,
        activeItem?.pomodoroPhase,
        activeItem?.currentPeriodIndex,
        activeItem?.phaseElapsedSeconds,
        activeItem?.deadlineMillis,
        hasNotificationPermission
    ) {
        if (activeItem != null && activeItem.statusEnum() == WatchWorkStatus.IN_PROGRESS) {
            WatchPomodoroForegroundService.syncActiveSession(
                context = context,
                item = activeItem,
                projectName = dashboardData.projectNameFor(activeItem.projectId)
            )
        } else {
            WatchPomodoroForegroundService.stop(context)
        }
    }

    val startFocusWithPermission: (SharedWorkItem) -> Unit = { item ->
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
        watchViewModel.startSession(item)
    }

    val toggleFocusWithPermission: (SharedWorkItem) -> Unit = { item ->
        if (item.statusEnum() == WatchWorkStatus.IN_PROGRESS) {
            watchViewModel.pauseSession(item)
        } else {
            startFocusWithPermission(item)
        }
    }

    val navController = rememberSwipeDismissableNavController()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    val hideTimeText = isAmbient && activeItem != null

    AppScaffold(
        timeText = {
            if (!hideTimeText) {
                TimeText()
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = WatchRoutes.HOME
            ) {
                composable(WatchRoutes.HOME) {
                    HorizontalPagerScaffold(pagerState = pagerState) {
                        HorizontalPager(
                            state = pagerState,
                            flingBehavior = PagerDefaults.snapFlingBehavior(
                                state = pagerState,
                                maxFlingPages = 1,
                                snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                            ),
                            rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(pagerState)
                        ) { page ->
                            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                when (page) {
                                    0 -> WatchFocusPage(
                                        data = dashboardData,
                                        nowMillis = effectiveNowMillis,
                                        isAmbient = isAmbient,
                                        hasNotificationPermission = hasNotificationPermission,
                                        onRequestNotificationPermission = requestNotificationPermission,
                                        onStartSession = startFocusWithPermission,
                                        onPauseSession = { watchViewModel.pauseSession(it) },
                                        onAdvancePhase = { watchViewModel.advancePhase(it) },
                                        onCompleteSession = { watchViewModel.completeSession(it) },
                                        onInspectItem = { navController.navigate(WatchRoutes.itemDetail(it.id)) },
                                        onAddWorkItem = { navController.navigate(WatchRoutes.editItem()) },
                                        onOpenAccount = { navController.navigate(WatchRoutes.ACCOUNT) }
                                    )

                                    1 -> WatchProjectsPage(
                                        data = dashboardData,
                                        nowMillis = effectiveNowMillis,
                                        onOpenProject = { navController.navigate(WatchRoutes.projectDetail(it)) },
                                        onAddProject = { navController.navigate(WatchRoutes.editProject()) },
                                        onOpenAccount = { navController.navigate(WatchRoutes.ACCOUNT) }
                                    )

                                    else -> WatchSchedulePage(
                                        data = dashboardData,
                                        nowMillis = effectiveNowMillis,
                                        onInspectItem = { navController.navigate(WatchRoutes.itemDetail(it.id)) },
                                        onToggleFocus = toggleFocusWithPermission,
                                        onAddWorkItem = { navController.navigate(WatchRoutes.editItem()) }
                                    )
                                }
                            }
                        }
                    }
                }

                composable(
                    route = WatchRoutes.PROJECT_DETAIL,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { entry ->
                    val projectId = entry.arguments?.getString("projectId").orEmpty()
                    WatchProjectDetailScreen(
                        projectId = projectId,
                        data = dashboardData,
                        nowMillis = effectiveNowMillis,
                        onEditProject = { navController.navigate(WatchRoutes.editProject(projectId)) },
                        onDeleteProject = { children ->
                            watchViewModel.deleteProject(projectId, children)
                            navController.popBackStack()
                        },
                        onAddWorkItem = { navController.navigate(WatchRoutes.editItem(projectId = projectId)) },
                        onInspectItem = { navController.navigate(WatchRoutes.itemDetail(it.id)) },
                        onToggleFocus = toggleFocusWithPermission
                    )
                }

                composable(
                    route = WatchRoutes.ITEM_DETAIL,
                    arguments = listOf(navArgument("itemId") { type = NavType.StringType })
                ) { entry ->
                    val itemId = entry.arguments?.getString("itemId").orEmpty()
                    WatchWorkItemDetailScreen(
                        itemId = itemId,
                        data = dashboardData,
                        nowMillis = effectiveNowMillis,
                        onStartSession = startFocusWithPermission,
                        onPauseSession = { watchViewModel.pauseSession(it) },
                        onAdvancePhase = { watchViewModel.advancePhase(it) },
                        onCompleteSession = { watchViewModel.completeSession(it) },
                        onReopenItem = { watchViewModel.reopenWorkItem(it) },
                        onPostponeItem = { watchViewModel.postponeWorkItem(it) },
                        onDuplicateItem = { watchViewModel.duplicateWorkItem(it) },
                        onUpdateProgress = { item, pct -> watchViewModel.updateProgress(item, pct) },
                        onEditItem = { item ->
                            navController.navigate(WatchRoutes.editItem(itemId = item.id, projectId = item.projectId))
                        },
                        onDeleteItem = { item ->
                            watchViewModel.deleteWorkItem(item)
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = WatchRoutes.EDIT_PROJECT,
                    arguments = listOf(
                        navArgument("projectId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    val editId = entry.arguments?.getString("projectId")
                    val existing = editId?.let { dashboardData.projectFor(it) }
                    WatchProjectEditorScreen(
                        existingProject = existing,
                        onSave = { name, desc, colorKey, iconKey, deadlineMillis ->
                            watchViewModel.saveProject(editId, name, desc, colorKey, iconKey, deadlineMillis)
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = WatchRoutes.EDIT_ITEM,
                    arguments = listOf(
                        navArgument("itemId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("projectId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    val itemId = entry.arguments?.getString("itemId")
                    val projectId = entry.arguments?.getString("projectId")
                    val existing = itemId?.let { id -> dashboardData.workItems.firstOrNull { it.id == id } }
                    WatchWorkItemEditorScreen(
                        existingItem = existing,
                        projects = dashboardData.projects,
                        initialProjectId = projectId,
                        onSave = { projId, title, notes, priorityKey, startMillis, workMins, breakMins, workPeriods, breakCount, deadlineMillis ->
                            watchViewModel.saveWorkItem(
                                existingItem = existing,
                                projectId = projId,
                                title = title,
                                notes = notes,
                                priorityKey = priorityKey,
                                scheduledStartMillis = startMillis,
                                pomodoroWorkMinutes = workMins,
                                pomodoroBreakMinutes = breakMins,
                                pomodoroWorkPeriods = workPeriods,
                                pomodoroBreakCount = breakCount,
                                deadlineMillis = deadlineMillis
                            )
                            navController.popBackStack()
                        }
                    )
                }

                composable(WatchRoutes.ACCOUNT) {
                    WatchAccountSyncScreen(
                        data = dashboardData,
                        hasNotificationPermission = hasNotificationPermission,
                        onSignInWithGoogle = {
                            WatchAuthHelper.signInWithGoogle(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onSuccess = { bannerMessage = "Signed in to cloud" },
                                onError = { bannerMessage = it },
                                onCancelled = { bannerMessage = "Sign-in cancelled" }
                            )
                        },
                        onSignOut = {
                            WatchAuthHelper.signOut(credentialManager, scope) {
                                watchViewModel.onSignedOut()
                                bannerMessage = "Signed out on watch"
                            }
                        },
                        onSyncFromPhone = {
                            watchViewModel.requestSyncFromPhone()
                            bannerMessage = "Sync requested from phone"
                        },
                        onOpenPhoneApp = {
                            WatchAuthHelper.openOnPairedPhone(context)
                        },
                        onRequestNotificationPermission = requestNotificationPermission
                    )
                }
            }

            if (bannerMessage != null && !isAmbient) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp, start = 20.dp, end = 20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = bannerMessage.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
