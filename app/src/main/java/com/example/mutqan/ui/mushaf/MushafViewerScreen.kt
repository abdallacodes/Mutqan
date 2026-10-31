package com.example.mutqan.ui.mushaf

import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.ui.saved.SavePassageSheet
import com.example.mutqan.domain.SavedPassage
import com.example.mutqan.domain.SavedKind
import com.example.mutqan.data.SurahData
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Bookmarks
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import com.example.mutqan.quran.PageCover
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.AyahGroup
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.PageStability
import com.example.mutqan.quran.MushafFontManager
import com.example.mutqan.quran.MushafPageView
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.QuickPeekBottomSheet
import com.example.mutqan.ui.components.QuickPeekTarget
import com.example.mutqan.ui.topics.VerseTopics
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TOTAL_PAGES  = 604
// 302 spreads: spread 0 = (right=1, left=2), spreads 1–301 = (right=odd, left=even)
private const val TOTAL_SPREADS = 302

// The page colours follow the reading theme (see MushafTheme), so night reading gets a dark page.
private val MushafParchment: Color   @Composable get() = LocalMushafColors.current.bg
private val MushafHeaderScrim: Color @Composable get() = LocalMushafColors.current.scrim
private val MushafInk: Color         @Composable get() = LocalMushafColors.current.ink
private val MushafInkMuted: Color    @Composable get() = LocalMushafColors.current.muted
private val MushafSpineLine: Color   @Composable get() = LocalMushafColors.current.spine

/** Ayahs to tint on every page, and what a tap on one of them does. Null = plain pages. */
private class AyahMarks(
    val highlights: Map<Int, Color>,
    val onTap: (wordId: Int, ayahId: Int) -> Unit,
    val onLongPress: (wordId: Int, ayahId: Int) -> Unit,
    /** The ayahs revealed while the text is covered for reciting; null when it is not covered. */
    val cover: Set<Int>? = null,
    val onReveal: (ayahId: Int) -> Unit = {},
    /** Ids of the words that open a hizb quarter. */
    val quarterStarts: Set<Int> = emptySet()
)

private val LocalAyahMarks = compositionLocalOf<AyahMarks?> { null }

/** Pages that hold a bookmark or a weak passage, with the colour of the ribbon drawn on them. */
private val LocalPageRibbons = compositionLocalOf<Map<Int, Color>> { emptyMap() }
private val MushafSelectionTint = Color(0x6027A3A0)

private val MushafGroupTint = Color(0x40C9A227)
private val MushafBookmarkTint = Color(0x403F8EDC)
private val MushafWeakTint = Color(0x50E9897F)

// ── Spread geometry helpers ───────────────────────────────────────────────────
//
// Spread layout (all 1-based, RTL → odd page on RIGHT, even page on LEFT):
//
//   spread 0   : right=1,   left=2       (Fatiha & Baqarah)
//   spread 1   : right=3,   left=4
//   spread n   : right=2n+1, left=2n+2
//
// spreadIndex(page P) = (P - 1) / 2

private fun spreadRightPage(n: Int): Int? = (2 * n + 1).let { if (it > TOTAL_PAGES) null else it }
private fun spreadLeftPage(n: Int):  Int? = (2 * n + 2).let { if (it > TOTAL_PAGES) null else it }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity         -> this
    is ContextWrapper   -> baseContext.findActivity()
    else                -> null
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafViewerScreen(
    startPage:   Int,
    onBack:      () -> Unit,
    onOpenSaved: () -> Unit = {},
    onOpenTopic: (Int) -> Unit = {},
    onOpenTafsir: (Int) -> Unit = {}
) {
    val context     = LocalContext.current
    val viewModel   = viewModel<MushafViewerViewModel>(factory = MushafViewerViewModelFactory(context))
    val state       by viewModel.state.collectAsState()

    val quran  = remember(context) { QuranRepository.getInstance(context) }
    val fonts  = remember(context) { MushafFontManager.getInstance(context) }

    // Tracks the \"primary\" Mushaf page for the top bar; persists across rotations.
    var currentPage by rememberSaveable { mutableIntStateOf(startPage.coerceIn(1, TOTAL_PAGES)) }

    // The page the user specifically navigated to — used to draw the green border.
    // Fixed for the lifetime of this screen; does not change on swipe.
    var activePage by remember { mutableIntStateOf(startPage.coerceIn(1, TOTAL_PAGES)) }

    // Chrome hidden by default so pages use the full display; tap toggles.
    var showHeader  by remember { mutableStateOf(false) }

    // Pager scroll is locked when the user has zoomed in.
    var currentScale by remember { mutableFloatStateOf(1f) }

    // Two-page spread vs single page — user-controlled (not tied to orientation).
    var useSpreadLayout by rememberSaveable { mutableStateOf(false) }

    // Spread only: each page uses full column width (taller than viewport); scroll vertically.
    var spreadFillWidth by rememberSaveable { mutableStateOf(false) }

    // Tint ayahs that belong to a mutashabihat group or were saved; tapping a tinted ayah, or
    // long-pressing any ayah, opens a quick peek where it can be bookmarked or marked weak.
    val groupAyahs by viewModel.mutashabihatAyahs.collectAsState()
    val savedPassages by viewModel.savedPassages.collectAsState()
    var showGroupAyahs by rememberSaveable { mutableStateOf(true) }
    var peekTarget by remember { mutableStateOf<QuickPeekTarget?>(null) }
    var peekAyahId by remember { mutableStateOf<Int?>(null) }
    var peekWordId by remember { mutableIntStateOf(0) }
    var peekGroups by remember { mutableStateOf<List<AyahGroup>>(emptyList()) }
    var showSaveSheet by remember { mutableStateOf(false) }

    // Reading theme, orientation and the other per-reader choices.
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val themeName by prefs.mushafTheme.collectAsState(initial = "")
    val rotateWithDevice by prefs.mushafRotate.collectAsState(initial = false)
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val readingTheme = MushafTheme.fromName(themeName)
    val colors = readingTheme.colors(appIsDark)
    val coroutineScope = rememberCoroutineScope()

    var showJump by remember { mutableStateOf(false) }
    var showRevised by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    // Cover mode: the text is hidden for reciting from memory, and a tap reveals one verse.
    var coverOn by rememberSaveable { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(emptySet<Int>()) }

    val memorised by viewModel.coverage.collectAsState()
    val pageInfo by produceState<Pair<Int, Int>?>(null, currentPage) {
        value = withContext(Dispatchers.IO) { quran.ayahsOnPage(currentPage).firstOrNull()?.let { it.juz to it.hizb } }
    }
    val quarterStarts by produceState(emptySet<Int>()) {
        value = withContext(Dispatchers.IO) {
            (1..30).flatMap { quran.rubs(it) }.mapNotNull { quran.ayahById(it.startAyahId)?.firstWordId }.toSet()
        }
    }

    LaunchedEffect(peekAyahId) {
        val id = peekAyahId ?: return@LaunchedEffect
        val ayah = withContext(Dispatchers.IO) { quran.ayahById(id) }
        peekGroups = viewModel.groupsOfAyah(id)
        peekTarget = ayah?.let { QuickPeekTarget(it.id, it.surah, it.ayah, it.firstPage) }
    }
    val peekSaved = remember(savedPassages, peekAyahId, peekWordId) {
        val id = peekAyahId
        if (id == null) null
        else savedPassages.firstOrNull { it.startAyahId == id && it.wordId == peekWordId }
            ?: savedPassages.firstOrNull { it.startAyahId == id }
    }
    val ribbons = remember(savedPassages, quran) {
        val index = quran.ayahIndex
        val byPage = HashMap<Int, Color>()
        for (saved in savedPassages) {
            if (saved.startAyahId !in index) continue
            val page = index.firstPageOf(saved.startAyahId)
            val weak = saved.kind == SavedKind.WEAK && !saved.isResolved
            if (weak || page !in byPage) byPage[page] = if (weak) MushafWeakTint.copy(alpha = 0.95f) else MushafBookmarkTint.copy(alpha = 0.95f)
        }
        byPage
    }
    val marks = remember(groupAyahs, savedPassages, showGroupAyahs, peekAyahId, coverOn, revealed, quarterStarts) {
        val tints = HashMap<Int, Color>()
        if (showGroupAyahs) {
            groupAyahs.forEach { tints[it] = MushafGroupTint }
            savedPassages.forEach { saved ->
                val tint = if (saved.kind == SavedKind.WEAK && !saved.isResolved) MushafWeakTint else MushafBookmarkTint
                for (ayah in saved.startAyahId..minOf(saved.endAyahId, saved.startAyahId + 200)) tints[ayah] = tint
            }
        }
        peekAyahId?.let { tints[it] = MushafSelectionTint }
        AyahMarks(
            highlights = tints,
            onTap = { wordId, ayahId -> peekWordId = wordId; peekAyahId = ayahId },
            onLongPress = { wordId, ayahId -> peekWordId = wordId; peekAyahId = ayahId },
            cover = if (coverOn) revealed else null,
            onReveal = { ayahId -> revealed = if (ayahId in revealed) revealed - ayahId else revealed + ayahId },
            quarterStarts = quarterStarts
        )
    }

    val surahName = state.pageSurahMap[currentPage].orEmpty()

    val pageBarLabel = if (useSpreadLayout) {
        val n = (currentPage - 1) / 2
        val l = spreadLeftPage(n)
        val r = spreadRightPage(n)
        when {
            l != null && r != null -> stringResource(R.string.mushaf_pages_range, r, l)
            r != null              -> stringResource(R.string.dialog_page_title, r)
            l != null              -> stringResource(R.string.dialog_page_title, l)
            else                   -> stringResource(R.string.dialog_page_title, currentPage)
        }
    } else {
        stringResource(R.string.dialog_page_title, currentPage)
    }

    // Spread → landscape (open book); single → portrait (upright phone).
    // Remember where the user is so Today can offer to continue from there.
    LaunchedEffect(currentPage) {
        UserPreferencesRepository(context.applicationContext).setLastMushafPage(currentPage)
    }

    LaunchedEffect(useSpreadLayout, rotateWithDevice) {
        val act = context.findActivity() ?: return@LaunchedEffect
        act.requestedOrientation = when {
            rotateWithDevice -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            useSpreadLayout -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (!useSpreadLayout) spreadFillWidth = false
    }

    // Reading: the screen stays on, and the system bars go away until the page is tapped for the bar.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val window = context.findActivity()?.window
    DisposableEffect(window, showHeader, colors.dark) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !colors.dark
        if (showHeader) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    DisposableEffect(window) {
        onDispose { window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }

    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ── Root: full-bleed pager + floating chrome (no permanent top bar eating height)
    CompositionLocalProvider(LocalMushafColors provides colors, LocalPageRibbons provides ribbons) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MushafParchment)
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MushafInk)
            }
        } else {
            key(useSpreadLayout) {
                val pageCount    = if (useSpreadLayout) TOTAL_SPREADS else TOTAL_PAGES
                val initialIndex = if (useSpreadLayout) (currentPage - 1) / 2 else currentPage - 1

                val pagerState = rememberPagerState(
                    initialPage = initialIndex,
                    pageCount   = { pageCount }
                )

                LaunchedEffect(jumpTarget) {
                    val target = jumpTarget ?: return@LaunchedEffect
                    pagerState.scrollToPage(if (useSpreadLayout) (target - 1) / 2 else target - 1)
                    jumpTarget = null
                }

                LaunchedEffect(pagerState.currentPage) {
                    val n = pagerState.currentPage
                    currentPage = if (useSpreadLayout) {
                        (2 * n + 1).coerceIn(1, TOTAL_PAGES)
                    } else {
                        (n + 1).coerceIn(1, TOTAL_PAGES)
                    }
                    currentScale = 1f
                }

                // USER REQUIREMENT: 
                // Swiping LEFT always moves to PREV page.
                // Swiping RIGHT always moves to NEXT page.
                // This is achieved by forcing LTR layout and using reverseLayout = true.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalAyahMarks provides marks) {
                    HorizontalPager(
                        state                   = pagerState,
                        reverseLayout           = true, 
                        beyondViewportPageCount = 1,
                        userScrollEnabled       = currentScale <= 1.05f,
                        verticalAlignment       = Alignment.CenterVertically,
                        modifier                = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        if (useSpreadLayout) {
                            SpreadView(
                                pagerState       = pagerState,
                                spreadFillWidth  = spreadFillWidth,
                                rightPage        = spreadRightPage(pageIndex),
                                leftPage         = spreadLeftPage(pageIndex),
                                rightStability   = spreadRightPage(pageIndex)?.let { state.stabilities.getOrNull(it - 1) },
                                leftStability    = spreadLeftPage(pageIndex)?.let  { state.stabilities.getOrNull(it - 1) },
                                activePage       = activePage,
                                quran            = quran,
                                fonts            = fonts,
                                onScaleChange    = { scale -> currentScale = scale },
                                onTap            = { showHeader = !showHeader }
                            )
                        } else {
                            val page = pageIndex + 1
                            SinglePageView(
                                pagerState    = pagerState,
                                page          = page,
                                stability     = state.stabilities.getOrNull(page - 1),
                                activePage    = activePage,
                                quran         = quran,
                                fonts         = fonts,
                                onScaleChange = { scale -> currentScale = scale },
                                onTap         = { showHeader = !showHeader }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = showHeader,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            val juzHizb = pageInfo?.let { stringResource(R.string.mushaf_juz_hizb, it.first, it.second) }
            val pageLine = listOfNotNull(juzHizb, pageBarLabel).joinToString(" · ")
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(MushafHeaderScrim)
                    .statusBarsPadding()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint               = MushafInk
                    )
                }
                // The name and place of the page; tapping it opens the jump sheet.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) { showJump = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = surahName.ifEmpty { pageBarLabel },
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MushafInk,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = pageLine,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MushafInkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showRevised = true }) {
                    Icon(
                        imageVector        = Icons.Filled.TaskAlt,
                        contentDescription = stringResource(R.string.mushaf_cd_revised),
                        tint               = MushafInk
                    )
                }
                IconButton(onClick = { coverOn = !coverOn; revealed = emptySet(); showHeader = false }) {
                    Icon(
                        imageVector        = if (coverOn) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = stringResource(if (coverOn) R.string.mushaf_cd_uncover else R.string.mushaf_cd_cover),
                        tint               = if (coverOn) MaterialTheme.colorScheme.primary else MushafInk
                    )
                }
                IconButton(onClick = onOpenSaved) {
                    Icon(
                        imageVector        = Icons.Filled.Bookmarks,
                        contentDescription = stringResource(R.string.mushaf_cd_saved),
                        tint               = MushafInk
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector        = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.mushaf_cd_menu),
                            tint               = MushafInk
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(
                                    if (showGroupAyahs) R.string.mushaf_cd_hide_group_ayahs else R.string.mushaf_cd_show_group_ayahs
                                ))
                            },
                            leadingIcon = { Icon(Icons.Filled.Highlight, null) },
                            onClick = { showGroupAyahs = !showGroupAyahs; menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(
                                    if (useSpreadLayout) R.string.mushaf_cd_switch_one_page else R.string.mushaf_cd_switch_two_page
                                ))
                            },
                            leadingIcon = { Icon(if (useSpreadLayout) Icons.Filled.Article else Icons.Filled.MenuBook, null) },
                            onClick = { useSpreadLayout = !useSpreadLayout; menuOpen = false }
                        )
                        if (useSpreadLayout) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(
                                        if (spreadFillWidth) R.string.mushaf_cd_spread_fit_page else R.string.mushaf_cd_spread_fill_width
                                    ))
                                },
                                leadingIcon = { Icon(if (spreadFillWidth) Icons.Filled.FitScreen else Icons.Filled.OpenInFull, null) },
                                onClick = { spreadFillWidth = !spreadFillWidth; menuOpen = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mushaf_rotate)) },
                            leadingIcon = { if (rotateWithDevice) Icon(Icons.Filled.Check, null) },
                            onClick = { coroutineScope.launch { prefs.setMushafRotate(!rotateWithDevice) }; menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mushaf_mark_page_memorised)) },
                            leadingIcon = { Icon(Icons.Filled.TaskAlt, null) },
                            onClick = {
                                menuOpen = false
                                coroutineScope.launch {
                                    viewModel.versesOfPage(currentPage)?.let { viewModel.setMemorised(it, true) }
                                    Toast.makeText(context, context.getString(R.string.mushaf_page_memorised_done), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.mushaf_page_colours),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        MushafTheme.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                leadingIcon = { if (option == readingTheme) Icon(Icons.Filled.Check, null) },
                                onClick = { coroutineScope.launch { prefs.setMushafTheme(option.name) }; menuOpen = false }
                            )
                        }
                    }
                }
            }
        }

        // While the text is covered a bar stays at the bottom: what to do, and the way out.
        if (coverOn) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MushafHeaderScrim)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mushaf_cover_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MushafInkMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { revealed = emptySet() }) { Text(stringResource(R.string.mushaf_cover_again)) }
                TextButton(onClick = { coverOn = false; revealed = emptySet() }) { Text(stringResource(R.string.mushaf_cd_uncover)) }
            }
        }
    }

    if (showJump) {
        MushafJumpSheet(
            quran = quran,
            currentPage = currentPage,
            onGo = { page ->
                showJump = false
                activePage = page
                jumpTarget = page
            },
            onDismiss = { showJump = false }
        )
    }

    if (showRevised) {
        MushafRevisedSheet(
            page = currentPage,
            onSave = { outcome, tags ->
                viewModel.logPageRevision(currentPage, outcome, tags)
                showRevised = false
                Toast.makeText(context, context.getString(R.string.mushaf_revised_saved), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showRevised = false }
        )
    }

    peekTarget?.let { target ->
        QuickPeekBottomSheet(
            target    = target,
            onDismiss = { peekTarget = null; peekAyahId = null; peekGroups = emptyList() },
            extraContent = {
                OutlinedButton(onClick = { showSaveSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (peekSaved == null) R.string.peek_save else R.string.peek_edit_saved))
                }
                val isMemorised = HifzCoverage.contains(memorised, target.verseId)
                OutlinedButton(
                    onClick = { viewModel.setMemorised(target.verseId..target.verseId, !isMemorised) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(if (isMemorised) R.string.peek_unmark_memorised else R.string.peek_mark_memorised)) }
                OutlinedButton(
                    onClick = { peekTarget = null; peekAyahId = null; peekGroups = emptyList(); onOpenTafsir(target.verseId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.tafsir_open)) }
                if (peekGroups.isNotEmpty()) AyahGroupsSection(peekGroups)
                SimilarAyahsSection(target.verseId, onOpenMushaf = null)
                VerseTopics(target.verseId) { topic ->
                    peekTarget = null; peekAyahId = null; peekGroups = emptyList()
                    onOpenTopic(topic)
                }
            }
        )

        if (showSaveSheet) {
            val ayahId = peekAyahId
            SavePassageSheet(
                reference = stringResource(R.string.saved_ref, SurahData.nameOf(target.surahId), target.ayahNumber),
                initial   = peekSaved,
                onSave    = { kind, tags, priority, note ->
                    if (ayahId != null) {
                        viewModel.savePassage(
                            SavedPassage(
                                id          = peekSaved?.id ?: 0,
                                kind        = kind,
                                startAyahId = peekSaved?.startAyahId ?: ayahId,
                                endAyahId   = peekSaved?.endAyahId ?: ayahId,
                                wordId      = peekSaved?.wordId ?: peekWordId,
                                tags        = tags,
                                priority    = priority,
                                note        = note,
                                createdAt   = peekSaved?.createdAt ?: System.currentTimeMillis(),
                                resolvedAt  = peekSaved?.resolvedAt ?: 0L
                            )
                        )
                    }
                    showSaveSheet = false
                    peekTarget = null; peekAyahId = null; peekGroups = emptyList()
                },
                onDelete  = peekSaved?.let { saved ->
                    {
                        viewModel.deleteSavedPassage(saved.id)
                        showSaveSheet = false
                        peekTarget = null; peekAyahId = null; peekGroups = emptyList()
                    }
                },
                onDismiss = { showSaveSheet = false }
            )
        }
    }
    }
}

// ── Portrait: single page ─────────────────────────────────────────────────────

@Composable
private fun SinglePageView(
    pagerState:    PagerState,
    page:          Int,
    stability:     PageStability?,
    activePage:    Int,
    quran:         QuranRepository,
    fonts:         MushafFontManager,
    onScaleChange: (Float) -> Unit,
    onTap:         () -> Unit,
    modifier:      Modifier = Modifier
) {
    ZoomableSurface(
        pagerState    = pagerState,
        onScaleChange = onScaleChange,
        onTap         = onTap,
        modifier      = modifier
    ) {
        PageImage(
            page          = page,
            quran         = quran,
            fonts         = fonts,
            isActive      = page == activePage,
            modifier      = Modifier.fillMaxSize()
        )
        StabilityDot(
            stability = stability,
            modifier  = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
        )
    }
}

// ── Two-page spread ────────────────────────────────────────────────────────────
//
// spread 0   → right=page1 (alone), left=blank
// spread 1   → right=page3, left=page2
//  …
// spread 301 → right=page603, left=page602
// spread 302 → right=blank, left=page604 (alone)
//
// The active page (= startPage) gets a green DifficultySmooth border;
// the companion page has no border.

@Composable
private fun SpreadView(
    pagerState:       PagerState,
    spreadFillWidth:  Boolean,
    rightPage:        Int?,
    leftPage:         Int?,
    rightStability:   PageStability?,
    leftStability:    PageStability?,
    activePage:       Int,
    quran:            QuranRepository,
    fonts:            MushafFontManager,
    onScaleChange:    (Float) -> Unit,
    onTap:            () -> Unit,
    modifier:         Modifier = Modifier
) {
    // One shared offset so both pages move together in fill-width scroll mode.
    val spreadSharedScroll = remember(leftPage, rightPage) { ScrollState(0) }
    LaunchedEffect(spreadFillWidth) {
        spreadSharedScroll.scrollTo(0)
    }

    ZoomableSurface(
        pagerState    = pagerState,
        onScaleChange = onScaleChange,
        onTap         = onTap,
        modifier      = modifier
    ) {
        // ALWAYS FORCE LTR for the Spread View itself.
        // We want Right Column = Odd Page and Left Column = Even Page
        // regardless of the system language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(modifier = Modifier.fillMaxSize()) {

                // ── Left — even page ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MushafParchment)
                        .clipToBounds()
                ) {
                    if (leftPage != null) {
                        PageImage(
                            page                     = leftPage,
                            quran                    = quran,
                            fonts                    = fonts,
                            isActive                 = leftPage == activePage,
                            fillWidthVerticalScroll  = if (spreadFillWidth) spreadSharedScroll else null,
                            modifier                 = Modifier.fillMaxSize()
                        )
                        StabilityDot(
                            stability = leftStability,
                            modifier  = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        )
                    }
                }

                // Hairline “spine” — pages sit closer like an open book
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(MushafSpineLine)
                )

                // ── Right — odd page ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MushafParchment)
                        .clipToBounds()
                ) {
                    if (rightPage != null) {
                        PageImage(
                            page                     = rightPage,
                            quran                    = quran,
                            fonts                    = fonts,
                            isActive                 = rightPage == activePage,
                            fillWidthVerticalScroll  = if (spreadFillWidth) spreadSharedScroll else null,
                            modifier                 = Modifier.fillMaxSize()
                        )
                        StabilityDot(
                            stability = rightStability,
                            modifier  = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Zoom container ────────────────────────────────────────────────────────────
//
// • Pinch-to-zoom via rememberTransformableState + transformable (1×–5×)
// • At ~1× zoom: canPan = false + pageNestedScrollConnection so horizontal swipes
//   reach HorizontalPager (next/prev page). When zoomed in, pan pans the image.
// • Single-tap: toggle header visibility
// • Double-tap: zoom 2.5× / reset to 1×
// • graphicsLayer for GPU-composited 60fps transforms
// • clipToBounds prevents zoomed content from overflowing

@Composable
private fun ZoomableSurface(
    pagerState:    PagerState,
    onTap:         () -> Unit = {},
    onScaleChange: (Float) -> Unit = {},
    modifier:      Modifier = Modifier,
    content:       @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val pageNestedScroll =
        PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        onScaleChange(newScale)
        if (newScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(pageNestedScroll)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(
                    state  = transformState,
                    canPan = { scale > 1.05f }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f; onScaleChange(1f)
                            } else {
                                scale = 2.5f; onScaleChange(2.5f)
                            }
                        }
                    )
                }
                .graphicsLayer {
                    scaleX       = scale
                    scaleY       = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            content = content
        )
    }
}

// ── Single Mushaf page ────────────────────────────────────────────────────────
//
// isActive = true  → 3dp green border (DifficultySmooth) — the navigated page
// isActive = false → no border — companion page in the spread
//
// The page is drawn from the QPC V2 layout and its own font (see MushafPageView); the
// canvas keeps the page aspect ratio, so the full page is always visible (no edge crop).

@Composable
private fun PageImage(
    page:                    Int,
    quran:                   QuranRepository,
    fonts:                   MushafFontManager,
    isActive:                Boolean = false,
    modifier:                Modifier = Modifier,
    /** Spread “fill width” mode: page uses full column width; scroll to see full height. */
    fillWidthVerticalScroll: ScrollState? = null
) {
    val marks = LocalAyahMarks.current
    val borderMod = if (isActive)
        Modifier.border(width = 3.dp, color = DifficultySmooth, shape = RectangleShape)
    else
        Modifier

    Box(
        modifier          = modifier
            .then(borderMod)
            .background(MushafParchment),
        contentAlignment  = if (fillWidthVerticalScroll != null) Alignment.TopCenter else Alignment.Center
    ) {
        if (fillWidthVerticalScroll != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(fillWidthVerticalScroll)
            ) {
                MushafPageView(
                    page     = page,
                    quran    = quran,
                    fonts    = fonts,
                    ink      = MushafInk,
                    modifier = Modifier.fillMaxWidth(),
                    highlights = marks?.highlights.orEmpty(),
                    onWordTap  = marks?.let { m -> { wordId, ayahId -> wordTap(m, wordId, ayahId) } },
                    onWordLongPress = marks?.let { m -> { wordId, ayahId -> wordLongPress(m, wordId, ayahId) } },
                    cover = marks?.cover?.let { PageCover(it) },
                    quarterStarts = marks?.quarterStarts.orEmpty()
                )
            }
        } else {
            MushafPageView(
                page     = page,
                quran    = quran,
                fonts    = fonts,
                ink      = MushafInk,
                modifier = Modifier.fillMaxSize(),
                highlights = marks?.highlights.orEmpty(),
                onWordTap  = marks?.let { m -> { wordId, ayahId -> wordTap(m, wordId, ayahId) } },
                onWordLongPress = marks?.let { m -> { wordId, ayahId -> wordLongPress(m, wordId, ayahId) } },
                cover = marks?.cover?.let { PageCover(it) },
                quarterStarts = marks?.quarterStarts.orEmpty()
            )
        }
        LocalPageRibbons.current[page]?.let { ribbon ->
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = ribbon,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp).size(22.dp)
            )
        }
    }
}

/** A tap on a word: reveals its verse while the text is covered, otherwise opens the sheet of a marked verse. */
private fun wordTap(marks: AyahMarks, wordId: Int, ayahId: Int): Boolean {
    if (marks.cover != null) {
        marks.onReveal(ayahId)
        return true
    }
    return (ayahId in marks.highlights).also { if (it) marks.onTap(wordId, ayahId) }
}

/** A press held on a word opens the verse sheet, except while the text is covered (the sheet would show the verse). */
private fun wordLongPress(marks: AyahMarks, wordId: Int, ayahId: Int): Boolean {
    if (marks.cover != null) return false
    marks.onLongPress(wordId, ayahId)
    return true
}

// ── Stability dot ─────────────────────────────────────────────────────────────

@Composable
private fun StabilityDot(
    stability: PageStability?,
    modifier:  Modifier = Modifier
) {
    val color = when {
        stability == null || !stability.isTracked -> return
        stability.score >= 0.60f                  -> DifficultySmooth
        stability.score >= 0.30f                  -> DifficultyStruggled
        else                                       -> DifficultyCritical
    }

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
