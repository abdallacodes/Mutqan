package com.example.mutqan.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.mutqan.domain.audio.RecitationPlayer
import com.example.mutqan.ui.audio.RecitationBar
import com.example.mutqan.ui.audio.RecitationSheet
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.ui.components.SettingsBottomSheet
import com.example.mutqan.ui.components.SyncNoticeHost
import com.example.mutqan.ui.coverage.CoverageScreen
import com.example.mutqan.ui.heatmap.HeatmapScreen
import com.example.mutqan.ui.heatmap.JuzDetailScreen
import com.example.mutqan.ui.help.HowItWorksScreen
import com.example.mutqan.ui.audio.AudioStorageScreen
import com.example.mutqan.ui.feedback.FeedbackScreen
import com.example.mutqan.ui.home.HomeScreen
import com.example.mutqan.ui.home.PlanScreen
import com.example.mutqan.ui.mushaf.MushafViewerScreen
import com.example.mutqan.ui.onboarding.OnboardingScreen
import com.example.mutqan.ui.revision.RevisionHistoryScreen
import com.example.mutqan.ui.saved.SavedPassagesScreen
import com.example.mutqan.ui.revision.RevisionLogScreen
import com.example.mutqan.ui.search.VerseSearchScreen
import com.example.mutqan.ui.surah.GroupDetailScreen
import com.example.mutqan.ui.surah.SurahDetailScreen
import com.example.mutqan.ui.surah.SurahListScreen
import com.example.mutqan.ui.theme.ThemeViewModel
import com.example.mutqan.ui.theme.ThemeViewModelFactory
import com.example.mutqan.ui.vault.EditSimilarityGroupScreen
import com.example.mutqan.ui.vault.MutashabihatListScreen
import com.example.mutqan.ui.insights.InsightsScreen
import com.example.mutqan.ui.practice.PageBoundariesScreen
import com.example.mutqan.ui.practice.PageQuizScreen
import com.example.mutqan.ui.practice.PracticeScreen
import com.example.mutqan.ui.progress.ProgressScreen
import com.example.mutqan.ui.practice.QuizScreen
import com.example.mutqan.ui.root.RootScreen
import com.example.mutqan.ui.tafsir.TafsirScreen
import com.example.mutqan.ui.topics.TopicScreen
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.ui.vault.ReadyListScreen
import com.example.mutqan.ui.vault.SimilarityTestScreen
import androidx.compose.foundation.layout.width
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.mutqan.ui.home.HomeViewModel
import com.example.mutqan.ui.home.HomeViewModelFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.mutqan.ui.components.FrostedPanel
import com.example.mutqan.ui.theme.FolioTheme
import com.example.mutqan.ui.components.GlassBackdrop
import com.example.mutqan.ui.components.LocalDockInset
import com.example.mutqan.ui.components.glassSource
import com.example.mutqan.ui.components.rememberGlassBackdrop
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavGraphBuilder

// ── Routes ────────────────────────────────────────────────────────────────────

object Routes {
    const val ONBOARDING     = "onboarding"
    const val HOME           = "home"
    const val PLAN           = "plan"
    const val REVISION_LOG   = "revision_log"
    const val VAULT_LIST     = "vault_list"
    const val READY_LIST     = "ready_list"
    const val QUIZ           = "quiz"
    const val ROOT           = "root"
    const val TOPIC          = "topic"
    const val TAFSIR         = "tafsir"
    const val PRACTICE       = "practice"
    const val INSIGHTS       = "insights"
    const val PAGE_BOUNDARIES = "page_boundaries"
    const val PAGE_EDGES_OF_VERSE = "page_edges_of_verse"
    const val PAGE_QUIZ = "page_quiz"
    const val PROGRESS       = "progress"
    const val SURAH_LIST     = "surah_list"
    const val VERSE_SEARCH   = "verse_search"
    const val HEATMAP        = "heatmap"

    const val EDIT_GROUP        = "edit_group"
    const val EDIT_GROUP_NEW    = "edit_group?groupId=-1"
    const val SURAH_DETAIL      = "surah_detail"
    const val GROUP_DETAIL      = "group_detail"
    const val REVISION_HISTORY  = "revision_history"
    const val JUZ_DETAIL        = "juz_detail"
    const val SIMILARITY_TEST   = "similarity_test?folderId={folderId}&groupId={groupId}"
    const val MUSHAF_VIEWER     = "mushaf_viewer"
    const val COVERAGE          = "coverage"
    const val HOW_IT_WORKS      = "how_it_works"
    const val AUDIO_STORAGE     = "audio_storage"
    const val FEEDBACK          = "feedback"
    const val SAVED             = "saved"

    fun editGroup(groupId: Int)                  = "edit_group?groupId=$groupId"
    fun editGroupNew(folderId: Int?)             = "edit_group?groupId=-1${folderId?.let { "&folderId=$it" } ?: ""}"
    fun surahDetail(surahId: Int)                = "surah_detail/$surahId"
    fun groupDetail(groupId: Int, surahId: Int)  = "group_detail/$groupId/$surahId"
    fun juzDetail(juzId: Int)                    = "juz_detail/$juzId"
    fun similarityTest(folderId: Int?, groupId: Int?) = "similarity_test?folderId=${folderId ?: -1}&groupId=${groupId ?: -1}"
    fun mushafViewer(page: Int, ayahId: Int? = null) = "mushaf_viewer/$page" + (ayahId?.let { "?ayah=$it" } ?: "")
    fun topic(topicId: Int)                      = "topic/$topicId"
    fun root(rootId: Int)                        = "root/$rootId"
    fun tafsir(ayahId: Int)                      = "tafsir/$ayahId"
    fun quiz(mode: QuizMode)                     = "quiz/${mode.route}"
    fun pageQuiz(end: Boolean)                   = "page_quiz/$end"
}

// ── Bottom nav items (built inside Composable to respect string resources) ────

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** How many are waiting behind this tab; none shows nothing. */
    val badge: Int = 0
)

// ── Top-level scaffold with bottom bar ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context      = LocalContext.current
    val themeViewModel: ThemeViewModel =
        viewModel(factory = ThemeViewModelFactory(context))
    val currentTheme by themeViewModel.themeKey.collectAsState()

    // Determine the correct start destination before creating the NavHost.
    // `null` means the preference hasn't loaded yet — show nothing to avoid
    // a flash of the wrong screen.
    val userPrefs  = remember { UserPreferencesRepository(context) }
    val isFirstRun by userPrefs.isFirstRun.collectAsState(initial = null)
    if (isFirstRun == null) return

    // The device's data was deleted from Settings: the app starts over from onboarding, as after a first install.
    val wasFirstRun = remember { mutableStateOf(isFirstRun) }
    LaunchedEffect(isFirstRun) {
        if (isFirstRun == true && wasFirstRun.value == false) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
        wasFirstRun.value = isFirstRun
    }
    SyncNoticeHost()

    val startDestination = if (isFirstRun == true) Routes.ONBOARDING else Routes.HOME

    // The Today screen and the badge on its tab read the same plan, so they share one view model.
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    val dueToday by remember(homeViewModel) {
        homeViewModel.uiState.map { it.today?.due?.size ?: 0 }.distinctUntilChanged()
    }.collectAsState(initial = 0)

    val backStack    by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val topLevelRoutes = setOf(Routes.HOME, Routes.SURAH_LIST, Routes.PRACTICE, Routes.INSIGHTS)
    val showBottomBar  = currentRoute in topLevelRoutes

    // A recitation plays whatever the screen; its controls sit above the bottom bar (the Mushaf has its own).
    val appContext = LocalContext.current
    val recitationPlayer = remember(appContext) { RecitationPlayer.get(appContext) }
    val playback by recitationPlayer.state.collectAsState()
    var showRecitationSheet by remember { mutableStateOf(false) }
    if (showRecitationSheet) RecitationSheet(
        onDismiss = { showRecitationSheet = false },
        onOpenStorage = {
            showRecitationSheet = false
            navController.navigate(Routes.AUDIO_STORAGE)
        }
    )
    val audioFailed = stringResource(R.string.audio_failed)
    LaunchedEffect(playback.failed) {
        if (playback.failed) {
            Toast.makeText(appContext, audioFailed, Toast.LENGTH_LONG).show()
            recitationPlayer.clearFailure()
        }
    }

    // Settings bottom sheet state — shared across all top-level screens
    var showSettingsSheet by remember { mutableStateOf(false) }
    // Deleting the device's data starts the app over from onboarding; the sheet that did it must not stay open over it.
    LaunchedEffect(isFirstRun) { if (isFirstRun == true) showSettingsSheet = false }
    if (showSettingsSheet) {
        SettingsBottomSheet(
            currentTheme  = currentTheme,
            onThemeChange = themeViewModel::setTheme,
            onDismiss     = { showSettingsSheet = false },
            onOpenCoverage = {
                showSettingsSheet = false
                navController.navigate(Routes.COVERAGE)
            },
            onOpenHowItWorks = {
                showSettingsSheet = false
                navController.navigate(Routes.HOW_IT_WORKS)
            },
            onOpenAudioStorage = {
                showSettingsSheet = false
                navController.navigate(Routes.AUDIO_STORAGE)
            },
            onOpenFeedback = {
                showSettingsSheet = false
                navController.navigate(Routes.FEEDBACK)
            },
            onOpenSaved = {
                showSettingsSheet = false
                navController.navigate(Routes.SAVED)
            }
        )
    }

    // The Mushaf draws edge to edge under the system bars. If it took their insets as padding, every tap that shows
    // or hides them would resize the page under the reader's eyes.
    val inMushaf = currentRoute?.startsWith(Routes.MUSHAF_VIEWER) == true

    // The tabs run under the floating dock, so each leaves its own bottom room as tall as the dock (and the player over it).
    val backdrop = rememberGlassBackdrop()
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val dockInset = with(LocalDensity.current) { dockHeightPx.toDp() }
    val layoutDirection = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current

    // Which tab the pager shows: the current one, or while it is still leaving, the one a screen is sliding in over.
    val visibleEntries by navController.visibleEntries.collectAsState()
    val tabRoute = if (currentRoute in tabOrder) currentRoute
    else visibleEntries.lastOrNull { it.destination.route in tabOrder }?.destination?.route
    val pagerState = rememberPagerState(initialPage = tabOrder.indexOf(startDestination).coerceAtLeast(0)) { tabOrder.size }
    val tabPageStates = rememberSaveableStateHolder()
    val selectTab: (String) -> Unit = { route ->
        if (route == currentRoute) {
            TabReselects.reselected(route)
        } else {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }
    // A tab picked in the bar (or reached any other way) slides the pager to it ...
    LaunchedEffect(tabRoute) {
        val target = tabOrder.indexOf(tabRoute)
        if (target >= 0 && pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    // ... and a swipe that settles on another tab makes it the current one.
    val latestRoute by rememberUpdatedState(currentRoute)
    val latestSelect by rememberUpdatedState(selectTab)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val route = tabOrder[page]
            if (latestRoute in tabOrder && route != latestRoute) latestSelect(route)
        }
    }
    Scaffold(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (inMushaf) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        // On a tab the player sits in the floating dock's column instead, above the glass.
        bottomBar = {
            if (playback.active && !inMushaf && !showBottomBar) {
                RecitationBar(state = playback, onOpenSettings = { showRecitationSheet = true })
            }
        }
    ) { innerPadding ->

        val mushafOpener = remember(navController) { { page: Int, ayahId: Int? -> navController.openMushaf(page, ayahId) } }
        val pagePadding = if (showBottomBar) {
            PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top   = innerPadding.calculateTopPadding(),
                end   = innerPadding.calculateEndPadding(layoutDirection)
            )
        } else innerPadding
        Box(modifier = Modifier.fillMaxSize()) {
        // The tabs sit side by side in a pager behind the NavHost, so a swipe drags one screen out and the next in under
        // the finger. Navigation still decides which tab is current; the pager and it follow each other.
        Box(modifier = Modifier.fillMaxSize().glassSource(backdrop, MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(
            LocalMushafOpener provides mushafOpener,
            LocalDockInset provides if (showBottomBar) dockInset else 0.dp
        ) {
        if (tabRoute != null) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { tabOrder[it] },
                modifier = Modifier.fillMaxSize().padding(pagePadding)
            ) { page ->
                val route = tabOrder[page]
                tabPageStates.SaveableStateProvider(route) {
                    TabPage(route, navController, homeViewModel, onSettingsClick = { showSettingsSheet = true })
                }
            }
        }
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(pagePadding),
            enterTransition    = { tabEnter() },
            exitTransition     = { tabExit() },
            popEnterTransition = { tabEnter() },
            popExitTransition  = { tabExit() }
        ) {

            // ── Onboarding (shown only on first run) ──────────────────

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                        // First run: ask what the user has memorised before anything else.
                        navController.navigate(Routes.COVERAGE)
                    }
                )
            }

            // ── Top-level tabs ────────────────────────────────────────

            tabDestination(Routes.HOME)

            composable(Routes.PLAN) {
                PlanScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.REVISION_LOG) {
                RevisionLogScreen(
                    onViewHistory   = { navController.navigate(Routes.REVISION_HISTORY) },
                    onSettingsClick = { showSettingsSheet = true },
                    onBack          = { navController.popBackStack() },
                    onOpenHowItWorks = { navController.navigate(Routes.HOW_IT_WORKS) }
                )
            }

            composable(Routes.HOW_IT_WORKS) {
                HowItWorksScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.AUDIO_STORAGE) {
                AudioStorageScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.FEEDBACK) {
                FeedbackScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SAVED) {
                SavedPassagesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
                )
            }

            composable(Routes.COVERAGE) {
                CoverageScreen(onDone = { navController.popBackStack() })
            }

            composable(Routes.REVISION_HISTORY) {
                RevisionHistoryScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.VAULT_LIST) {
                MutashabihatListScreen(
                    onAddGroup      = { folderId -> 
                        navController.navigate(Routes.editGroupNew(folderId)) 
                    },
                    onViewGroup     = { groupId, _ ->
                        navController.navigate(Routes.groupDetail(groupId, 0))
                    },
                    onEditGroup     = { id -> navController.navigate(Routes.editGroup(id)) },
                    onStartTest     = { folderId, groupId -> 
                        navController.navigate(Routes.similarityTest(folderId, groupId)) 
                    },
                    onOpenReadyList = { navController.navigate(Routes.READY_LIST) },
                    onSettingsClick = { showSettingsSheet = true },
                    onBack          = { navController.popBackStack() }
                )
            }

            tabDestination(Routes.SURAH_LIST)

            composable(Routes.VERSE_SEARCH) {
                VerseSearchScreen(
                    onOpenMushaf = { page -> navController.openMushaf(page) },
                    onSettingsClick = { showSettingsSheet = true },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.HEATMAP) {
                HeatmapScreen(
                    onJuzClick      = { juzId -> navController.navigate(Routes.juzDetail(juzId)) },
                    onSettingsClick = { showSettingsSheet = true },
                    onBack          = { navController.popBackStack() }
                )
            }

            tabDestination(Routes.PRACTICE)

            tabDestination(Routes.INSIGHTS)

            composable(Routes.PAGE_BOUNDARIES) {
                PageBoundariesScreen(fromVerse = false, onBack = { navController.popBackStack() })
            }

            composable(Routes.PAGE_EDGES_OF_VERSE) {
                PageBoundariesScreen(fromVerse = true, onBack = { navController.popBackStack() })
            }

            composable(
                route     = "${Routes.PAGE_QUIZ}/{end}",
                arguments = listOf(navArgument("end") { type = NavType.BoolType })
            ) { entry ->
                PageQuizScreen(
                    end = entry.arguments?.getBoolean("end") ?: false,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Sub-screens (bottom bar hidden) ──────────────────────

            composable(
                route     = "${Routes.TOPIC}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                TopicScreen(
                    topicId = entry.arguments?.getInt("id") ?: 0,
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic)) }
                )
            }

            composable(
                route     = "${Routes.TAFSIR}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                TafsirScreen(
                    startAyahId = entry.arguments?.getInt("id") ?: 1,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route     = "${Routes.ROOT}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                RootScreen(
                    rootId = entry.arguments?.getInt("id") ?: 0,
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
                )
            }

            composable(
                route     = "${Routes.QUIZ}/{mode}",
                arguments = listOf(navArgument("mode") { type = NavType.StringType })
            ) { entry ->
                QuizScreen(
                    mode = QuizMode.fromRoute(entry.arguments?.getString("mode")),
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
                )
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCoverage = { navController.navigate(Routes.COVERAGE) },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
                )
            }

            composable(Routes.READY_LIST) {
                ReadyListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
                )
            }

            composable(
                route     = "${Routes.EDIT_GROUP}?groupId={groupId}&folderId={folderId}",
                arguments = listOf(
                    navArgument("groupId") {
                        type         = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("folderId") {
                        type         = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { entry ->
                val rawId = entry.arguments?.getInt("groupId") ?: -1
                val folderId = entry.arguments?.getInt("folderId") ?: -1
                EditSimilarityGroupScreen(
                    groupId = if (rawId == -1) null else rawId,
                    folderId = if (folderId == -1) null else folderId,
                    onBack  = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
                )
            }

            composable(
                route     = Routes.SIMILARITY_TEST,
                arguments = listOf(
                    navArgument("folderId") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("groupId") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                val fId = entry.arguments?.getInt("folderId")?.takeIf { it != -1 }
                val gId = entry.arguments?.getInt("groupId")?.takeIf { it != -1 }
                SimilarityTestScreen(
                    groupId = gId,
                    folderId = fId,
                    onBack = { navController.popBackStack() },
                    onNavigateToGroup = { id -> 
                        navController.navigate(Routes.groupDetail(id, 0))
                    }
                )
            }

            composable(
                route     = "${Routes.SURAH_DETAIL}/{surahId}",
                arguments = listOf(navArgument("surahId") { type = NavType.IntType })
            ) { entry ->
                val surahId = entry.arguments?.getInt("surahId") ?: 1
                SurahDetailScreen(
                    surahId       = surahId,
                    onBack        = { navController.popBackStack() },
                    onGroupClick  = { groupId ->
                        navController.navigate(Routes.groupDetail(groupId, surahId))
                    },
                    onOpenMushaf  = { page -> navController.openMushaf(page) }
                )
            }

            composable(
                route     = "${Routes.GROUP_DETAIL}/{groupId}/{surahId}",
                arguments = listOf(
                    navArgument("groupId") { type = NavType.IntType },
                    navArgument("surahId") { type = NavType.IntType }
                )
            ) { entry ->
                val groupId = entry.arguments?.getInt("groupId") ?: return@composable
                val surahId = entry.arguments?.getInt("surahId") ?: return@composable
                GroupDetailScreen(
                    groupId        = groupId,
                    currentSurahId = surahId,
                    onBack         = { navController.popBackStack() },
                    onOpenMushaf   = { page -> navController.openMushaf(page) }
                )
            }

            composable(
                route     = "${Routes.JUZ_DETAIL}/{juzId}",
                arguments = listOf(navArgument("juzId") { type = NavType.IntType })
            ) { entry ->
                val juzId = entry.arguments?.getInt("juzId") ?: 1
                JuzDetailScreen(
                    juzId        = juzId,
                    onBack       = { navController.popBackStack() },
                    onPageOpen   = { page -> navController.openMushaf(page) }
                )
            }

            composable(
                route     = "${Routes.MUSHAF_VIEWER}/{page}?ayah={ayah}",
                arguments = listOf(
                    navArgument("page") { type = NavType.IntType },
                    navArgument("ayah") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { entry ->
                val page = entry.arguments?.getInt("page") ?: 1
                MushafViewerScreen(
                    startPage   = page,
                    startAyah   = entry.arguments?.getInt("ayah") ?: 0,
                    onBack      = { navController.popBackStack() },
                    onOpenSaved = { navController.navigate(Routes.SAVED) },
                    onOpenTafsir = { ayah -> navController.navigate(Routes.tafsir(ayah)) },
                    onOpenAudioStorage = { navController.navigate(Routes.AUDIO_STORAGE) },
                    onOpenJuz = { juz -> navController.navigate(Routes.juzDetail(juz)) }
                )
            }
        }
        }
        }

        if (showBottomBar) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { dockHeightPx = it.height }
            ) {
                if (playback.active) {
                    RecitationBar(state = playback, onOpenSettings = { showRecitationSheet = true })
                }
                MutqanBottomBar(
                    currentRoute  = if (tabRoute != null) tabOrder[pagerState.targetPage] else currentRoute,
                    dueToday      = dueToday,
                    backdrop      = backdrop,
                    onNavSelected = selectTab
                )
            }
        }
        }
    }
}

// ── Moving between tabs ───────────────────────────────────────────────────────

/** A tab is a stop in the back stack and nothing more: its screen is drawn by the pager behind the NavHost. */
private fun NavGraphBuilder.tabDestination(route: String) {
    composable(
        route              = route,
        enterTransition    = { EnterTransition.None },
        exitTransition     = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition  = { ExitTransition.None }
    ) {}
}

/** The screen of one tab, as the pager shows it. */
@Composable
private fun TabPage(route: String, navController: NavHostController, homeViewModel: HomeViewModel, onSettingsClick: () -> Unit) {
    when (route) {
        Routes.HOME -> HomeScreen(
            viewModel       = homeViewModel,
            onOpenPlan      = { navController.navigate(Routes.PLAN) },
            onOpenCoverage  = { navController.navigate(Routes.COVERAGE) },
            onOpenProgress  = { navController.navigate(Routes.PROGRESS) },
            onOpenMushaf    = { page -> navController.openMushaf(page) },
            onLogRevision   = { navController.navigate(Routes.REVISION_LOG) },
            onSettingsClick = onSettingsClick
        )
        Routes.SURAH_LIST -> SurahListScreen(
            onSurahClick       = { id -> navController.navigate(Routes.surahDetail(id)) },
            onOpenMushaf       = { page -> navController.openMushaf(page) },
            onSettingsClick    = onSettingsClick,
            onSearchClick      = { navController.navigate(Routes.VERSE_SEARCH) },
            onOpenRoot         = { root -> navController.navigate(Routes.root(root)) },
            onOpenTopic        = { topic -> navController.navigate(Routes.topic(topic)) },
            onOpenAudioStorage = { navController.navigate(Routes.AUDIO_STORAGE) }
        )
        Routes.PRACTICE -> PracticeScreen(
            onOpenBoundaries = { navController.navigate(Routes.PAGE_BOUNDARIES) },
            onOpenPageEdges  = { navController.navigate(Routes.PAGE_EDGES_OF_VERSE) },
            onOpenPageQuiz   = { end -> navController.navigate(Routes.pageQuiz(end)) },
            onOpenJuz        = { juz -> navController.navigate(Routes.juzDetail(juz)) },
            onOpenQuiz       = { mode -> navController.navigate(Routes.quiz(mode)) },
            onRandomVerse    = { navController.navigate(Routes.VERSE_SEARCH) },
            onSimilarityTest = { navController.navigate(Routes.similarityTest(null, null)) },
            onOpenVault      = { navController.navigate(Routes.VAULT_LIST) },
            onSettingsClick  = onSettingsClick
        )
        Routes.INSIGHTS -> InsightsScreen(
            onOpenProgress  = { navController.navigate(Routes.PROGRESS) },
            onOpenBrain     = { navController.navigate(Routes.HEATMAP) },
            onLogRevision   = { navController.navigate(Routes.REVISION_LOG) },
            onOpenHistory   = { navController.navigate(Routes.REVISION_HISTORY) },
            onOpenCoverage  = { navController.navigate(Routes.COVERAGE) },
            onOpenPlan      = { navController.navigate(Routes.PLAN) },
            onSettingsClick = onSettingsClick
        )
    }
}

private val tabOrder = listOf(Routes.HOME, Routes.SURAH_LIST, Routes.PRACTICE, Routes.INSIGHTS)
private const val TAB_MS = 300

/**
 * Which way the other tab lies, or null when either end of the move is not a tab. The tabs sit in layout order,
 * so Start and End already mean left and right in a left-to-right layout and the reverse in Arabic.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): AnimatedContentTransitionScope.SlideDirection? {
    val from = tabOrder.indexOf(initialState.destination.route)
    val to = tabOrder.indexOf(targetState.destination.route)
    if (from < 0 || to < 0 || from == to) return null
    return if (to > from) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End
}

/** A short drift toward the tab, under a fade: enough to say where the screen came from, not a full-width swipe. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(): EnterTransition {
    val direction = tabDirection() ?: return fadeIn(animationSpec = tween(700))
    return slideIntoContainer(direction, tween(TAB_MS, easing = FastOutSlowInEasing)) { it / 6 } +
        fadeIn(tween(TAB_MS - 80, delayMillis = 80))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(): ExitTransition {
    val direction = tabDirection() ?: return fadeOut(animationSpec = tween(700))
    return slideOutOfContainer(direction, tween(TAB_MS, easing = FastOutSlowInEasing)) { it / 6 } +
        fadeOut(tween(TAB_MS / 2))
}

/** Opens the Mushaf at [page]. A second tap while it is already open replaces it instead of stacking another one, which Back would then land on. */
private fun NavHostController.openMushaf(page: Int, ayahId: Int? = null) {
    navigate(Routes.mushafViewer(page, ayahId)) { launchSingleTop = true }
}

// ── Bottom bar composable ─────────────────────────────────────────────────────

@Composable
private fun MutqanBottomBar(
    currentRoute: String?,
    dueToday: Int,
    backdrop: GlassBackdrop,
    onNavSelected: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem(
            route = Routes.HOME,
            label = stringResource(R.string.nav_home),
            selectedIcon = Icons.Filled.Today,
            unselectedIcon = Icons.Outlined.Today,
            badge = dueToday
        ),
        BottomNavItem(
            route = Routes.SURAH_LIST,
            label = stringResource(R.string.nav_quran),
            selectedIcon = Icons.Filled.MenuBook,
            unselectedIcon = Icons.Outlined.MenuBook
        ),
        BottomNavItem(
            route = Routes.PRACTICE,
            label = stringResource(R.string.nav_practice),
            selectedIcon = Icons.Filled.Quiz,
            unselectedIcon = Icons.Outlined.Quiz
        ),
        BottomNavItem(
            route = Routes.INSIGHTS,
            label = stringResource(R.string.nav_insights),
            selectedIcon = Icons.Filled.Insights,
            unselectedIcon = Icons.Outlined.Insights
        )
    )
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // A floating dock of frosted glass: the page scrolls behind it and shows through, blurred. The current tab opens
    // into a filled pill with its name beside the icon; the others are icons alone.
    val dark = scheme.background.luminance() < 0.5f
    val folio = FolioTheme.colors
    val colors = DockColors(
        tint   = folio.folio.copy(alpha = if (backdrop.blurs) 0.50f else 0.96f),
        sheen  = Color.White.copy(alpha = if (dark) 0.07f else 0.34f),
        rim    = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.95f), Color.White.copy(alpha = if (dark) 0.06f else 0.30f))
        ),
        pill   = scheme.primary,
        onPill = scheme.onPrimary,
        idle   = scheme.onSurface.copy(alpha = 0.72f)
    )
    val fade = scheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The page fades out under the dock, so nothing crowds the gesture bar below the glass.
            .drawBehind {
                val reach = 28.dp.toPx()
                drawRect(
                    brush   = Brush.verticalGradient(listOf(Color.Transparent, fade.copy(alpha = 0.7f), fade.copy(alpha = 0.97f))),
                    topLeft = Offset(0f, -reach),
                    size    = Size(size.width, size.height + reach)
                )
            }
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)
    ) {
        FrostedPanel(
            backdrop = backdrop,
            shape    = RoundedCornerShape(34.dp),
            tint     = colors.tint,
            sheen    = colors.sheen,
            rim      = colors.rim,
            shadow   = if (dark) Color.Black else folio.shadow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    DockTab(
                        item = item,
                        selected = currentRoute == item.route,
                        colors = colors,
                        onClick = {
                            if (currentRoute != item.route) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onNavSelected(item.route)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** What the dock is made of: the glass (tint, sheen, rim), the pill of the current tab, what is drawn on it, and the other tabs. */
private class DockColors(val tint: Color, val sheen: Color, val rim: Brush, val pill: Color, val onPill: Color, val idle: Color)

/** One tab of the dock. The weight is animated, so the chosen tab grows and the others give way. */
@Composable
private fun RowScope.DockTab(
    item: BottomNavItem,
    selected: Boolean,
    colors: DockColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grow by animateFloatAsState(
        targetValue = if (selected) 2.1f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "dockGrow"
    )
    val pill by animateColorAsState(if (selected) colors.pill else Color.Transparent, label = "dockPill")
    val ink by animateColorAsState(if (selected) colors.onPill else colors.idle, label = "dockInk")
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dockPop"
    )
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .weight(grow)
            .height(52.dp)
            .clip(shape)
            .background(pill, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = item.label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgedBox(
            badge = {
                // The Today screen already says what is due, so the count is for the other tabs.
                if (item.badge > 0 && !selected) {
                    val description = stringResource(R.string.nav_due_badge, item.badge)
                    Badge(
                        containerColor = colors.pill,
                        contentColor = colors.onPill,
                        modifier = Modifier.semantics { contentDescription = description }
                    ) { Text(if (item.badge > 99) "99+" else item.badge.toString()) }
                }
            }
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop }
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(160, delayMillis = 60)) + expandHorizontally(),
            exit = fadeOut(tween(90)) + shrinkHorizontally()
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
