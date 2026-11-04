package com.example.mutqan.ui.navigation

import androidx.compose.foundation.layout.padding
import com.example.mutqan.ui.components.Hairline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.mutqan.ui.practice.PracticeScreen
import com.example.mutqan.ui.progress.ProgressScreen
import com.example.mutqan.ui.practice.QuizScreen
import com.example.mutqan.ui.root.RootScreen
import com.example.mutqan.ui.tafsir.TafsirScreen
import com.example.mutqan.ui.topics.TopicScreen
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.ui.vault.ReadyListScreen
import com.example.mutqan.ui.vault.SimilarityTestScreen

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
    fun mushafViewer(page: Int)                  = "mushaf_viewer/$page"
    fun topic(topicId: Int)                      = "topic/$topicId"
    fun root(rootId: Int)                        = "root/$rootId"
    fun tafsir(ayahId: Int)                      = "tafsir/$ayahId"
    fun quiz(mode: QuizMode)                     = "quiz/${mode.route}"
}

// ── Bottom nav items (built inside Composable to respect string resources) ────

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
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

    val startDestination = if (isFirstRun == true) Routes.ONBOARDING else Routes.HOME

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
    Scaffold(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (inMushaf) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            Column {
            if (playback.active && currentRoute?.startsWith(Routes.MUSHAF_VIEWER) != true) {
                RecitationBar(state = playback, onOpenSettings = { showRecitationSheet = true })
            }
            if (showBottomBar) {
                MutqanBottomBar(
                    currentRoute  = currentRoute,
                    onNavSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding)
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

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenPlan      = { navController.navigate(Routes.PLAN) },
                    onOpenCoverage  = { navController.navigate(Routes.COVERAGE) },
                    onOpenProgress  = { navController.navigate(Routes.PROGRESS) },
                    onOpenMushaf    = { page -> navController.openMushaf(page) },
                    onLogRevision   = { navController.navigate(Routes.REVISION_LOG) },
                    onSettingsClick = { showSettingsSheet = true }
                )
            }

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

            composable(Routes.SURAH_LIST) {
                SurahListScreen(
                    onSurahClick   = { id -> navController.navigate(Routes.surahDetail(id)) },
                    onOpenMushaf    = { page -> navController.openMushaf(page) },
                    onSettingsClick = { showSettingsSheet = true },
                    onSearchClick   = { navController.navigate(Routes.VERSE_SEARCH) },
                    onOpenRoot      = { root -> navController.navigate(Routes.root(root)) },
                    onOpenTopic     = { topic -> navController.navigate(Routes.topic(topic)) },
                    onOpenAudioStorage = { navController.navigate(Routes.AUDIO_STORAGE) }
                )
            }

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

            composable(Routes.PRACTICE) {
                PracticeScreen(
                    onOpenBoundaries = { navController.navigate(Routes.PAGE_BOUNDARIES) },
                    onOpenQuiz       = { mode -> navController.navigate(Routes.quiz(mode)) },
                    onRandomVerse    = { navController.navigate(Routes.VERSE_SEARCH) },
                    onSimilarityTest = { navController.navigate(Routes.similarityTest(null, null)) },
                    onOpenVault      = { navController.navigate(Routes.VAULT_LIST) },
                    onSettingsClick  = { showSettingsSheet = true }
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    onOpenProgress  = { navController.navigate(Routes.PROGRESS) },
                    onOpenBrain     = { navController.navigate(Routes.HEATMAP) },
                    onLogRevision   = { navController.navigate(Routes.REVISION_LOG) },
                    onOpenHistory   = { navController.navigate(Routes.REVISION_HISTORY) },
                    onOpenCoverage  = { navController.navigate(Routes.COVERAGE) },
                    onOpenPlan      = { navController.navigate(Routes.PLAN) },
                    onSettingsClick = { showSettingsSheet = true }
                )
            }

            composable(Routes.PAGE_BOUNDARIES) {
                PageBoundariesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { page -> navController.openMushaf(page) }
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
                route     = "${Routes.MUSHAF_VIEWER}/{page}",
                arguments = listOf(navArgument("page") { type = NavType.IntType })
            ) { entry ->
                val page = entry.arguments?.getInt("page") ?: 1
                MushafViewerScreen(
                    startPage   = page,
                    onBack      = { navController.popBackStack() },
                    onOpenSaved = { navController.navigate(Routes.SAVED) },
                    onOpenTopic = { topic -> navController.navigate(Routes.topic(topic)) },
                    onOpenTafsir = { ayah -> navController.navigate(Routes.tafsir(ayah)) },
                    onOpenAudioStorage = { navController.navigate(Routes.AUDIO_STORAGE) }
                )
            }
        }
    }
}

/** Opens the Mushaf at [page]. A second tap while it is already open replaces it instead of stacking another one, which Back would then land on. */
private fun NavHostController.openMushaf(page: Int) {
    navigate(Routes.mushafViewer(page)) { launchSingleTop = true }
}

// ── Bottom bar composable ─────────────────────────────────────────────────────

@Composable
private fun MutqanBottomBar(
    currentRoute: String?,
    onNavSelected: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem(
            route = Routes.HOME,
            label = stringResource(R.string.nav_home),
            selectedIcon = Icons.Default.Today,
            unselectedIcon = Icons.Outlined.Today
        ),
        BottomNavItem(
            route = Routes.SURAH_LIST,
            label = stringResource(R.string.nav_quran),
            selectedIcon = Icons.Default.MenuBook,
            unselectedIcon = Icons.Outlined.MenuBook
        ),
        BottomNavItem(
            route = Routes.PRACTICE,
            label = stringResource(R.string.nav_practice),
            selectedIcon = Icons.Default.Quiz,
            unselectedIcon = Icons.Outlined.Quiz
        ),
        BottomNavItem(
            route = Routes.INSIGHTS,
            label = stringResource(R.string.nav_insights),
            selectedIcon = Icons.Default.Insights,
            unselectedIcon = Icons.Outlined.Insights
        )
    )

    // A quiet bar: a rule on top, a small gold dot over the current tab, no filled pill.
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Hairline()
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val tint = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onNavSelected(item.route) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(if (selected) MaterialTheme.colorScheme.tertiary else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
