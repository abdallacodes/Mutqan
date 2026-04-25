package com.example.qmemo.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.qmemo.R
import com.example.qmemo.data.UserPreferencesRepository
import com.example.qmemo.ui.components.SettingsBottomSheet
import com.example.qmemo.ui.heatmap.HeatmapScreen
import com.example.qmemo.ui.heatmap.JuzDetailScreen
import com.example.qmemo.ui.mushaf.MushafViewerScreen
import com.example.qmemo.ui.onboarding.OnboardingScreen
import com.example.qmemo.ui.revision.RevisionHistoryScreen
import com.example.qmemo.ui.revision.RevisionLogScreen
import com.example.qmemo.ui.surah.GroupDetailScreen
import com.example.qmemo.ui.surah.SurahDetailScreen
import com.example.qmemo.ui.surah.SurahListScreen
import com.example.qmemo.ui.theme.ThemeViewModel
import com.example.qmemo.ui.theme.ThemeViewModelFactory
import com.example.qmemo.ui.vault.EditSimilarityGroupScreen
import com.example.qmemo.ui.vault.MutashabihatListScreen

// ── Routes ────────────────────────────────────────────────────────────────────

object Routes {
    const val ONBOARDING     = "onboarding"
    const val REVISION_LOG   = "revision_log"
    const val VAULT_LIST     = "vault_list"
    const val SURAH_LIST     = "surah_list"
    const val HEATMAP        = "heatmap"

    const val EDIT_GROUP        = "edit_group"
    const val EDIT_GROUP_NEW    = "edit_group?groupId=-1"
    const val SURAH_DETAIL      = "surah_detail"
    const val GROUP_DETAIL      = "group_detail"
    const val REVISION_HISTORY  = "revision_history"
    const val JUZ_DETAIL        = "juz_detail"
    const val MUSHAF_VIEWER     = "mushaf_viewer"

    fun editGroup(groupId: Int)                  = "edit_group?groupId=$groupId"
    fun editGroupNew(folderId: Int?)             = "edit_group?groupId=-1${folderId?.let { "&folderId=$it" } ?: ""}"
    fun surahDetail(surahId: Int)                = "surah_detail/$surahId"
    fun groupDetail(groupId: Int, surahId: Int)  = "group_detail/$groupId/$surahId"
    fun juzDetail(juzId: Int)                    = "juz_detail/$juzId"
    fun mushafViewer(page: Int)                  = "mushaf_viewer/$page"
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

    val startDestination = if (isFirstRun == true) Routes.ONBOARDING else Routes.REVISION_LOG

    val backStack    by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val topLevelRoutes = setOf(Routes.REVISION_LOG, Routes.VAULT_LIST, Routes.SURAH_LIST, Routes.HEATMAP)
    val showBottomBar  = currentRoute in topLevelRoutes

    // Settings bottom sheet state — shared across all top-level screens
    var showSettingsSheet by remember { mutableStateOf(false) }
    if (showSettingsSheet) {
        SettingsBottomSheet(
            currentTheme  = currentTheme,
            onThemeChange = themeViewModel::setTheme,
            onDismiss     = { showSettingsSheet = false }
        )
    }

    Scaffold(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                QMemoBottomBar(
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
                        navController.navigate(Routes.REVISION_LOG) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // ── Top-level tabs ────────────────────────────────────────

            composable(Routes.REVISION_LOG) {
                RevisionLogScreen(
                    onViewHistory   = { navController.navigate(Routes.REVISION_HISTORY) },
                    onSettingsClick = { showSettingsSheet = true }
                )
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
                    onSettingsClick = { showSettingsSheet = true }
                )
            }

            composable(Routes.SURAH_LIST) {
                SurahListScreen(
                    onSurahClick   = { id -> navController.navigate(Routes.surahDetail(id)) },
                    onOpenMushaf    = { page -> navController.navigate(Routes.mushafViewer(page)) },
                    onSettingsClick = { showSettingsSheet = true }
                )
            }

            composable(Routes.HEATMAP) {
                HeatmapScreen(
                    onJuzClick      = { juzId -> navController.navigate(Routes.juzDetail(juzId)) },
                    onSettingsClick = { showSettingsSheet = true }
                )
            }

            // ── Sub-screens (bottom bar hidden) ──────────────────────

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
                    onBack  = { navController.popBackStack() }
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
                    onOpenMushaf  = { page -> navController.navigate(Routes.mushafViewer(page)) }
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
                    onBack         = { navController.popBackStack() }
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
                    onPageOpen   = { page -> navController.navigate(Routes.mushafViewer(page)) }
                )
            }

            composable(
                route     = "${Routes.MUSHAF_VIEWER}/{page}",
                arguments = listOf(navArgument("page") { type = NavType.IntType })
            ) { entry ->
                val page = entry.arguments?.getInt("page") ?: 1
                MushafViewerScreen(
                    startPage = page,
                    onBack    = { navController.popBackStack() }
                )
            }
        }
    }
}

// ── Bottom bar composable ─────────────────────────────────────────────────────

@Composable
private fun QMemoBottomBar(
    currentRoute: String?,
    onNavSelected: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem(
            route = Routes.REVISION_LOG,
            label = stringResource(R.string.nav_journal),
            selectedIcon = Icons.Default.MenuBook,
            unselectedIcon = Icons.Outlined.MenuBook
        ),
        BottomNavItem(
            route = Routes.VAULT_LIST,
            label = stringResource(R.string.nav_vault),
            selectedIcon = Icons.Default.AutoStories,
            unselectedIcon = Icons.Outlined.AutoStories
        ),
        BottomNavItem(
            route = Routes.SURAH_LIST,
            label = stringResource(R.string.nav_surahs),
            selectedIcon = Icons.Default.Search,
            unselectedIcon = Icons.Outlined.Search
        ),
        BottomNavItem(
            route = Routes.HEATMAP,
            label = stringResource(R.string.nav_brain),
            selectedIcon = Icons.Default.GridView,
            unselectedIcon = Icons.Outlined.GridView
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = { onNavSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text          = item.label,
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = if (selected) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
