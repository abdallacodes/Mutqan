package com.example.mutqan.ui.mushaf

import com.example.mutqan.quran.QuranAyah
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.graphics.lerp
import com.example.mutqan.ui.components.glassSource
import com.example.mutqan.ui.components.rememberGlassBackdrop
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import com.example.mutqan.ui.theme.statusColors
import androidx.compose.ui.semantics.semantics
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.ui.saved.SavePassageSheet
import com.example.mutqan.domain.SavedPassage
import com.example.mutqan.domain.SavedKind
import com.example.mutqan.data.SurahData
import androidx.compose.material3.OutlinedButton
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.mutqan.quran.PartialReveal
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
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlin.math.abs
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Density
import com.example.mutqan.quran.MUSHAF_PAGE_ASPECT
import kotlin.math.ceil
import kotlin.math.max
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import com.example.mutqan.domain.audio.RecitationController
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.ui.audio.RecitationBar
import com.example.mutqan.ui.audio.RecitationSheet
import com.example.mutqan.ui.audio.rememberAskForNotifications
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.mutqan.ui.components.UiText
import com.example.mutqan.quran.QuranRub
import com.example.mutqan.domain.QuarterMap
import com.example.mutqan.ui.components.QuickPeekBottomSheet
import com.example.mutqan.ui.components.QuickPeekTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.mutqan.data.MushafViewerState
import kotlinx.coroutines.flow.first
import com.example.mutqan.ui.components.PaperOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import com.example.mutqan.ui.components.PeekAction
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Layers
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import com.example.mutqan.ui.topics.VerseTheme

private const val TOTAL_PAGES  = 604
// 302 spreads: spread 0 = (right=1, left=2), spreads 1–301 = (right=odd, left=even)
private const val TOTAL_SPREADS = 302

/** The size of the bar's buttons: a finger wide, with room for the quarter link beside three of them. */
private val BAR_BUTTON = 44.dp

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
    /** What is shown while the text is covered for reciting (verses whole or in part); null when it is not covered. */
    val cover: PageCover? = null,
    val onReveal: (ayahId: Int) -> Unit = {},
    /** Ids of the words that open a hizb quarter. */
    val quarterStarts: Set<Int> = emptySet(),
    /**
     * Tints that only colour the page (the user's subjects): a tap on a verse that has no other mark still goes to the page,
     * which shows or hides the bar, instead of opening the verse sheet.
     */
    val backdrop: Map<Int, Color> = emptyMap()
)

private val LocalAyahMarks = compositionLocalOf<AyahMarks?> { null }

/** Pages that hold a bookmark or a weak passage, with the colour of the ribbon drawn on them. */
private val LocalPageRibbons = compositionLocalOf<Map<Int, Color>> { emptyMap() }
private val MushafSelectionTint = Color(0x6027A3A0)
private val MushafPlayingTint = Color(0x66E58A2B)
private val MushafSpotTint = Color(0x66D9B13A)

private val MushafGroupTint = Color(0x40C9A227)
private val MushafBookmarkTint = Color(0x403F8EDC)
private val MushafWeakTint = Color(0x50E9897F)

// The user's subjects: soft, and apart from the other marks (gold, blue, red), taking turns along the page.
private val MushafSubjectTintA = Color(0x248E6FD8)
private val MushafSubjectTintB = Color(0x244FAE8B)

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

/** The smallest width (dp) from which a screen counts as a tablet: the orientation is not locked there. */
private const val LARGE_SCREEN_DP = 600

/** Below this height (dp) the screen is a phone held sideways: the bars are kept to one low row each. */
private const val COMPACT_SCREEN_HEIGHT_DP = 480

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
    /** An ayah (global id) to tint on arrival, because the reader came here to look at it; 0 for none. */
    startAyah:   Int = 0,
    onBack:      () -> Unit,
    onOpenSaved: () -> Unit = {},
    onOpenTafsir: (Int) -> Unit = {},
    onOpenAudioStorage: () -> Unit = {},
    /** Opens the map of a juz (its quarters and the user's subjects). */
    onOpenJuz: (Int) -> Unit = {}
) {
    val context     = LocalContext.current
    val viewModel   = viewModel<MushafViewerViewModel>(factory = MushafViewerViewModelFactory(context))
    val state       by viewModel.state.collectAsState()

    val quran  = remember(context) { QuranRepository.getInstance(context) }
    val fonts  = remember(context) { MushafFontManager.getInstance(context) }

    // Reading theme, orientation and the other per-reader choices.
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    // How the reader left the Mushaf view (two pages, the bars, the verse tints, cover mode), read once before anything is
    // shown, so it opens as it was instead of changing a moment later (two pages also turns the screen).
    var left by remember { mutableStateOf<MushafViewerState?>(null) }
    LaunchedEffect(prefs) { left = prefs.mushafViewerState.first() }
    val start = left
    if (start == null) {
        Box(Modifier.fillMaxSize().background(MushafParchment))
        return
    }

    // Tracks the \"primary\" Mushaf page for the top bar; persists across rotations.
    var currentPage by rememberSaveable { mutableIntStateOf(startPage.coerceIn(1, TOTAL_PAGES)) }

    // The page the user specifically navigated to; the two-page view marks its number so it can be told from its neighbour.
    // Fixed for the lifetime of this screen; does not change on swipe.
    var activePage by remember { mutableIntStateOf(startPage.coerceIn(1, TOTAL_PAGES)) }

    // Chrome hidden by default so pages use the full display; tap toggles.
    var showHeader  by remember { mutableStateOf(start.barsShown) }

    // Two-page spread vs single page — user-controlled (not tied to orientation).
    var useSpreadLayout by rememberSaveable { mutableStateOf(start.twoPage) }

    // With the screen held sideways: the pages take the whole width (taller than the screen) and the view scrolls down them.
    var fillWidth by rememberSaveable { mutableStateOf(start.fillWidth) }

    // Tint ayahs that belong to a mutashabihat group or were saved; tapping a tinted ayah, or
    // long-pressing any ayah, opens a quick peek where it can be bookmarked or marked weak.
    val groupAyahs by viewModel.mutashabihatAyahs.collectAsState()
    val savedPassages by viewModel.savedPassages.collectAsState()
    var showGroupAyahs by rememberSaveable { mutableStateOf(start.groupAyahs) }
    var peekTarget by remember { mutableStateOf<QuickPeekTarget?>(null) }
    var peekAyahId by remember { mutableStateOf<Int?>(null) }
    var peekWordId by remember { mutableIntStateOf(0) }
    var peekGroups by remember { mutableStateOf<List<AyahGroup>>(emptyList()) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }

    val themeName by prefs.mushafTheme.collectAsState(initial = "")
    // Null until the choice has been read, so the screen is not locked to one orientation on a guess and turned again.
    val rotatePref by prefs.mushafRotate.collectAsState(initial = null)
    val rotateWithDevice = rotatePref ?: false
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val readingTheme = MushafTheme.fromName(themeName)
    val colors = readingTheme.colors(appIsDark)
    val coroutineScope = rememberCoroutineScope()

    // Recitation: it plays on whatever the screen does; here the verse sounding is tinted and the page follows it.
    val recitation = remember(context) { RecitationController.get(context) }
    val playback by recitation.player.state.collectAsState()
    val askForNotifications = rememberAskForNotifications()
    var showRecitationSheet by remember { mutableStateOf(false) }
    fun listenFrom(ayahId: Int, page: Int) {
        askForNotifications()
        coroutineScope.launch { recitation.playFrom(ayahId, page) }
    }
    val pageMemorisedDone = stringResource(R.string.mushaf_page_memorised_done)
    val revisedSaved = stringResource(R.string.mushaf_revised_saved)

    var showJump by remember { mutableStateOf(false) }
    var showRevised by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    // Cover mode: the text is hidden for reciting from memory, and a tap reveals one verse.
    var coverOn by rememberSaveable { mutableStateOf(start.cover) }
    // While covered a tap goes to the verse, so the page cannot bring the bars or send them away: a button does, and a
    // small one stays to bring them back. The cover bar always shows with the capsules, so there is always a way to hide them.
    var coverBarsHidden by rememberSaveable { mutableStateOf(start.coverBarsHidden) }
    // Kept as they change, for the next opening.
    LaunchedEffect(useSpreadLayout, fillWidth, showGroupAyahs, showHeader, coverOn, coverBarsHidden) {
        prefs.setMushafViewerState(
            MushafViewerState(useSpreadLayout, fillWidth, showGroupAyahs, showHeader, coverOn, coverBarsHidden)
        )
    }
    var revealed by remember { mutableStateOf(emptySet<Int>()) }
    // With "reveal in steps" on, a tap shows a verse a step at a time; these are the verses shown in part so far.
    var partial by remember { mutableStateOf(emptyMap<Int, PartialReveal>()) }
    val revealSteps by prefs.mushafRevealSteps.collectAsState(initial = false)

    // The Mushaf as a book, one switch for all of it: each page a page of an open book on a desk (the page in its printed
    // shape, the edges of the other pages and the cover on its outer side, the middle of the book along its bound edge,
    // so the side of the book always shows), and its leaves turning like paper. It takes some width, so the page is a
    // little smaller: off unless the reader turns it on. With Android's animations off the book stays, but pages slide.
    val bookPref by prefs.mushafBookView.collectAsState(initial = false)
    val animationsOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val bookOn = bookPref
    // The two views are then the same book: one page of it, or with two pages the whole open book, always in sight; what
    // the one-page view shows halfway through a turn (see BookSpread). Its pager is the one-page view's.
    val bookSpread = bookOn && useSpreadLayout
    val paperTurn = bookOn && !animationsOff
    val paperLook = remember(colors) { PaperLook(colors.dark, colors.bg) }
    val densityValue = LocalDensity.current.density
    // As if read in a dark room by a warm reading lamp, in any page colour and any view (see ReadingLamp).
    val lampOn by prefs.mushafReadingLamp.collectAsState(initial = false)
    val bookLook = remember(colors, paperLook, densityValue, lampOn) { BookLook(colors, paperLook, densityValue, lit = lampOn) }
    // How high the finger holds the page, 0 at the top to 1 at the bottom: it decides which corner lifts first. Low by
    // default, where a thumb takes a page.
    val grip = remember { mutableFloatStateOf(0.75f) }
    // The bottom corners are where a page is taken to turn it: the left one to go on, the right one to go back. There
    // Android's back gesture would take the swipe and close the Mushaf, so those corners are kept for the page (Android
    // allows up to 200dp of each edge). The rest of each edge still goes back.
    val density = LocalDensity.current
    val turnCorner = remember(density) { with(density) { Size(TURN_CORNER_WIDTH.toPx(), TURN_CORNER_HEIGHT.toPx()) } }
    fun coverAgain() { revealed = emptySet(); partial = emptyMap() }
    fun revealTap(ayahId: Int) {
        // A verse that is shown whole is covered again by a tap, whichever way it was shown.
        if (!revealSteps || ayahId in revealed) {
            revealed = if (ayahId in revealed) revealed - ayahId else revealed + ayahId
            partial = partial - ayahId
            return
        }
        coroutineScope.launch {
            val ayah = withContext(Dispatchers.IO) { quran.ayahById(ayahId) } ?: return@launch
            val first = ayah.firstWordId
            val last = first + ayah.wordsCount - 1
            val now = partial[ayahId]
            when {
                // First word; a verse of one word has nothing to show in parts.
                now == null && ayah.wordsCount >= 2 -> partial = partial + (ayahId to PartialReveal(first, last, withLast = false))
                // Then the last word too; a verse of two words would show all of it, so it goes straight to whole.
                now != null && !now.withLast && ayah.wordsCount >= 3 -> partial = partial + (ayahId to PartialReveal(first, last, withLast = true))
                else -> { revealed = revealed + ayahId; partial = partial - ayahId }
            }
        }
    }

    val memorised by viewModel.coverage.collectAsState()
    val pageInfo by produceState<Pair<Int, Int>?>(null, currentPage) {
        value = withContext(Dispatchers.IO) { quran.ayahsOnPage(currentPage).firstOrNull()?.let { it.juz to it.hizb } }
    }
    // The hizb quarter the page opens in, so the reader sees the subject the user gave that part of the juz.
    val quarterHere by produceState<QuranRub?>(null, currentPage) {
        value = withContext(Dispatchers.IO) {
            val first = quran.ayahsOnPage(currentPage).firstOrNull() ?: return@withContext null
            quran.rubs(first.juz).firstOrNull { it.number == first.rub }
        }
    }
    val allSubjects by viewModel.subjects.collectAsState()
    val userSubjects by viewModel.userSubjects.collectAsState()
    var showSubjectDialog by remember { mutableStateOf(false) }
    val subjectSaved = stringResource(R.string.mushaf_subject_saved)
    val subjectHere = remember(quarterHere, allSubjects, currentPage) {
        quarterHere?.let { rub ->
            val firstOnPage = quran.ayahsOnPage(currentPage).firstOrNull()?.id ?: rub.startAyahId
            QuarterMap.subjectAt(rub, allSubjects, firstOnPage)
        }
    }
    val allRubs by produceState(emptyList<QuranRub>()) {
        value = withContext(Dispatchers.IO) { (1..30).flatMap { quran.rubs(it) } }
    }
    val quarterStarts by produceState(emptySet<Int>(), allRubs) {
        value = withContext(Dispatchers.IO) { allRubs.mapNotNull { quran.ayahById(it.startAyahId)?.firstWordId }.toSet() }
    }
    // The verses of each subject the user gave, tinted in two colours that take turns so where one ends and the next
    // begins can be seen. A switch in the menu turns it off.
    val subjectTintOn by prefs.mushafSubjectTint.collectAsState(initial = true)
    val subjectTints = remember(allSubjects, allRubs, subjectTintOn) {
        val tints = HashMap<Int, Color>()
        if (subjectTintOn && allSubjects.isNotEmpty()) {
            QuarterMap.subjectSpans(allRubs, allSubjects).forEachIndexed { index, span ->
                val tint = if (index % 2 == 0) MushafSubjectTintA else MushafSubjectTintB
                for (ayah in span.startAyahId..span.endAyahId) tints[ayah] = tint
            }
        }
        tints
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
    var spotlight by rememberSaveable { mutableIntStateOf(startAyah) }
    LaunchedEffect(peekAyahId) { if (peekAyahId != null) spotlight = 0 }
    val marks = remember(groupAyahs, savedPassages, showGroupAyahs, subjectTints, peekAyahId, coverOn, revealed, partial, quarterStarts, playback.ayahId, spotlight) {
        // The subjects go first: every other mark (groups, saved, the verse sounding, the one chosen) is drawn over them.
        val tints = HashMap<Int, Color>(subjectTints)
        if (showGroupAyahs) {
            groupAyahs.forEach { tints[it] = MushafGroupTint }
            savedPassages.forEach { saved ->
                val tint = if (saved.kind == SavedKind.WEAK && !saved.isResolved) MushafWeakTint else MushafBookmarkTint
                for (ayah in saved.startAyahId..minOf(saved.endAyahId, saved.startAyahId + 200)) tints[ayah] = tint
            }
        }
        if (spotlight != 0) tints[spotlight] = MushafSpotTint
        playback.ayahId?.let { tints[it] = MushafPlayingTint }
        peekAyahId?.let { tints[it] = MushafSelectionTint }
        AyahMarks(
            highlights = tints,
            onTap = { wordId, ayahId -> peekWordId = wordId; peekAyahId = ayahId },
            onLongPress = { wordId, ayahId -> peekWordId = wordId; peekAyahId = ayahId },
            cover = if (coverOn) PageCover(revealed, partial) else null,
            onReveal = { ayahId -> revealTap(ayahId) },
            quarterStarts = quarterStarts,
            backdrop = subjectTints
        )
    }

    // Turn to the verse being recited when it is not on the page (or two pages) in view.
    LaunchedEffect(playback.ayahId) {
        val id = playback.ayahId ?: return@LaunchedEffect
        val index = quran.ayahIndex
        if (id !in index) return@LaunchedEffect
        val page = index.firstPageOf(id)
        val inView = if (useSpreadLayout) (page - 1) / 2 == (currentPage - 1) / 2 else page == currentPage
        if (!inView) jumpTarget = page
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

    // A phone is held upright for one page or sideways for two; a tablet is left to turn as its owner holds it, because
    // a lock there turns the screen away from how it is held (and, undone and redone with every restart of the screen,
    // used to make it spin back and forth).
    val configuration = LocalConfiguration.current
    val largeScreen = configuration.smallestScreenWidthDp >= LARGE_SCREEN_DP
    // Held sideways the pages can fill the width; on a phone, where the page then has little height, the controls are low.
    val sideways = configuration.screenWidthDp > configuration.screenHeightDp
    val compactBars = sideways && configuration.screenHeightDp < COMPACT_SCREEN_HEIGHT_DP
    LaunchedEffect(useSpreadLayout, rotatePref, largeScreen) {
        val act = context.findActivity() ?: return@LaunchedEffect
        val wanted = when {
            rotatePref == null || rotatePref == true || largeScreen -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            useSpreadLayout -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (act.requestedOrientation != wanted) act.requestedOrientation = wanted
    }

    // Reading: the screen stays on, and the system bars go away until the page is tapped for the bar.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val window = context.findActivity()?.window
    DisposableEffect(window, showHeader, colors.dark, bookOn, lampOn) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        // In the book view the bar lies over the dark desk, not over the page.
        controller?.isAppearanceLightStatusBars = !colors.dark && !bookOn && !lampOn
        if (showHeader) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    val appBarsLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(window) {
        onDispose {
            window?.let {
                val controller = WindowCompat.getInsetsController(it, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = appBarsLight
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val act = context.findActivity() ?: return@onDispose
            // Turning the screen restarts the activity and so ends this composition too; letting go of the lock then
            // would turn the screen back and the lock would turn it again, over and over.
            if (!act.isChangingConfigurations) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // What the floating capsules look through: the page, recorded once and drawn again, blurred, inside each of them.
    val backdrop = rememberGlassBackdrop()

    // ── Root: full-bleed pager + floating chrome (no permanent top bar eating height)
    CompositionLocalProvider(LocalMushafColors provides colors, LocalPageRibbons provides ribbons, LocalMushafGlassSolid provides (bookOn || lampOn)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MushafParchment)
    ) {
        // The lamp's light falls on what the glass sees too, so the bars frost the lit page.
        Box(Modifier.fillMaxSize().glassSource(backdrop, MushafParchment).then(if (lampOn) Modifier.readingLamp() else Modifier)) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PaperLoading(color = MushafInk, delayMillis = 0)
            }
        } else {
            key(useSpreadLayout, bookSpread) {
                val pageCount    = if (useSpreadLayout && !bookSpread) TOTAL_SPREADS else TOTAL_PAGES
                val initialIndex = when {
                    bookSpread      -> 2 * ((currentPage - 1) / 2)
                    useSpreadLayout -> (currentPage - 1) / 2
                    else            -> currentPage - 1
                }

                val pagerState = rememberPagerState(
                    initialPage = initialIndex,
                    pageCount   = { pageCount }
                )
                // With pages turning like paper, a page goes at the pace of paper however hard it is flicked, so the eye
                // can follow the turn (see PaperSettle).
                // In the two-page book a turn is two of its pages long.
                val paperSettle = remember(pagerState, bookSpread) {
                    PaperSettle { pagerState.layoutInfo.pageSize * if (bookSpread) 2 else 1 }
                }

                LaunchedEffect(jumpTarget) {
                    val target = jumpTarget ?: return@LaunchedEffect
                    val index = when {
                        bookSpread      -> 2 * ((target - 1) / 2)
                        useSpreadLayout -> (target - 1) / 2
                        else            -> target - 1
                    }
                    // The next or previous page is turned, as a hand would turn it (the recitation reaching it, say);
                    // anything further is opened at once rather than leafing through every page between.
                    if (paperTurn && abs(index - pagerState.currentPage) == if (bookSpread) 2 else 1) {
                        pagerState.animateScrollToPage(index, animationSpec = paperSettle)
                    } else {
                        pagerState.scrollToPage(index)
                    }
                    jumpTarget = null
                }

                // A light tick when a leaf the reader turned lands, as paper settles (moving across the open book to the
                // facing page turns no paper, so it is quiet).
                LaunchedEffect(pagerState, paperTurn) {
                    if (!paperTurn) return@LaunchedEffect
                    var settled = pagerState.currentPage
                    var dragged = false
                    launch {
                        pagerState.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true }
                    }
                    snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
                        if (scrolling) return@collect
                        val now = pagerState.currentPage
                        if (dragged && now != settled && (bookSpread || LeafTurn.turnsLeafAfter(minOf(now, settled)))) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        settled = now
                        dragged = false
                    }
                }

                // One zoom over the whole page area, whatever it shows (see PageZoom).
                val zoom = remember(pagerState) { PageZoomState(coroutineScope) }
                val zoomed by remember(zoom) { derivedStateOf { zoom.zoomed } }

                LaunchedEffect(pagerState.currentPage) {
                    val n = pagerState.currentPage
                    // With two pages the pager is at a spread (the two-page book at an even pager page). The place stays
                    // on the page it was on while that page is in view, so going back to one page goes back to it; it
                    // once moved to the spread's right-hand page whenever the view changed.
                    val spread = when {
                        bookSpread      -> n / 2
                        useSpreadLayout -> n
                        else            -> -1
                    }
                    currentPage = when {
                        spread < 0                         -> n + 1
                        (currentPage - 1) / 2 == spread    -> currentPage
                        else                               -> 2 * spread + 1
                    }.coerceIn(1, TOTAL_PAGES)
                    zoom.reset()
                }

                // In the book view the desk lies under all the pages and stays where it is: the book moves over it, and
                // is seen smaller on it while a leaf turns, larger when zoomed in, higher up when scrolled down.
                if (bookOn) BookDesk(bookLook)

                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (paperTurn) Modifier
                                .systemGestureExclusion { place ->
                                    val height = place.size.height.toFloat()
                                    Rect(0f, height - turnCorner.height, turnCorner.width, height)
                                }
                                .systemGestureExclusion { place ->
                                    val width = place.size.width.toFloat()
                                    val height = place.size.height.toFloat()
                                    Rect(width - turnCorner.width, height - turnCorner.height, width, height)
                                }
                            else Modifier
                        )
                ) {
                    // Filling the width is for a screen held sideways, where a page fitted to the height is small: the
                    // pages take the whole width and the view scrolls down them, both pages of two together.
                    val fill = fillWidth && maxWidth > maxHeight
                    val pagesHeight = if (fill) {
                        max(constraints.maxHeight, pagesHeightToFill(constraints.maxWidth, bookOn, bookSpread, useSpreadLayout, density))
                    } else constraints.maxHeight
                    val fillScroll = remember(fill) { ScrollState(0) }
                    // The zoom's gestures run on from the first composition, so they read the scroll as it is now.
                    val scrollNow by rememberUpdatedState(if (fill) fillScroll else null)
                    // Another page is read from its top.
                    LaunchedEffect(pagerState.settledPage) { if (fillScroll.value > 0) fillScroll.animateScrollTo(0) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pageZoom(
                                zoom,
                                onTap = { showHeader = !showHeader },
                                // Zoomed in, a finger that takes the page to its top or bottom edge goes on down the pages.
                                overflow = { left -> scrollNow?.dispatchRawDelta(-left.y / zoom.scale) }
                            )
                    ) {
                        // USER REQUIREMENT:
                        // Swiping LEFT always moves to PREV page.
                        // Swiping RIGHT always moves to NEXT page.
                        // This is achieved by forcing LTR layout and using reverseLayout = true.
                        val spreadFling = rememberSpreadFling(pagerState, if (paperTurn) paperSettle else spring<Float>(stiffness = Spring.StiffnessMediumLow))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalAyahMarks provides marks) {
                            HorizontalPager(
                                state                   = pagerState,
                                // The two-page book: two pages side by side, and the pages of the next spread ready to turn.
                                pageSize                = if (bookSpread) HalfPage else PageSize.Fill,
                                reverseLayout           = true,
                                beyondViewportPageCount = if (bookSpread) 2 else 1,
                                flingBehavior           = when {
                                    bookSpread -> spreadFling
                                    paperTurn  -> PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = paperSettle)
                                    else       -> PagerDefaults.flingBehavior(state = pagerState)
                                },
                                // Zoomed in, a finger moves the page instead of turning it.
                                userScrollEnabled       = !zoomed,
                                verticalAlignment       = Alignment.CenterVertically,
                                modifier                = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (fill) Modifier.verticalScroll(fillScroll).height(with(density) { pagesHeight.toDp() })
                                        else Modifier.fillMaxHeight()
                                    )
                                    // Watches where the finger holds the page, for the paper turn; it takes nothing from the page.
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                                                if (change.pressed && size.height > 0) grip.floatValue = (change.position.y / size.height).coerceIn(0f, 1f)
                                            }
                                        }
                                    }
                            ) { pageIndex ->
                                if (useSpreadLayout && !bookSpread) {
                                    SpreadView(
                                        rightPage  = spreadRightPage(pageIndex),
                                        leftPage   = spreadLeftPage(pageIndex),
                                        activePage = activePage,
                                        quran      = quran,
                                        fonts      = fonts,
                                        onTap      = { showHeader = !showHeader }
                                    )
                                } else {
                                    val page = pageIndex + 1
                                    SinglePageView(
                                        page     = page,
                                        quran    = quran,
                                        fonts    = fonts,
                                        onTap    = { showHeader = !showHeader },
                                        book     = if (bookOn) bookLook else null,
                                        modifier = if (paperTurn) {
                                            Modifier.leafTurn(
                                                pagerState, pageIndex, { grip.floatValue }, paperLook,
                                                book = bookOn, spread = bookSpread, drawBack = !fill
                                            )
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // The controls float. A capsule at the top holds the place and the menu, the quarter hangs under it, and the
        // tools sit in a capsule at the bottom, where a thumb reaches. Nothing runs across the page: it shows around the
        // capsules and, where the phone can blur, through them.
        AnimatedVisibility(
            visible  = showHeader,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            val juzHizb = pageInfo?.let { stringResource(R.string.mushaf_juz_hizb, it.first, it.second) }
            val pageLine = listOfNotNull(juzHizb, pageBarLabel).joinToString(" · ")
            val rubHere = quarterHere
            val placeLine: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // How well each page in view is known, in the colour it has everywhere else.
                    val inView = if (useSpreadLayout) {
                        listOfNotNull(spreadRightPage((currentPage - 1) / 2), spreadLeftPage((currentPage - 1) / 2))
                    } else listOf(currentPage)
                    inView.forEach { StabilityDot(state.stabilities.getOrNull(it - 1), Modifier.padding(end = 6.dp)) }
                    // Two lines at most: with large text the page number wraps rather than being cut off. On the one
                    // low row of a screen held sideways, one.
                    Text(
                        text     = pageLine,
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MushafInkMuted,
                        maxLines = if (compactBars) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // The quarter with the subject the user gave it, if any; a tap opens that juz's map. With no subject it
            // says where the tap leads, which is where one is added.
            val quarterChip: @Composable (Modifier) -> Unit = { chipModifier ->
                if (rubHere != null) {
                    MushafQuarterChip(
                        backdrop = backdrop,
                        colors   = colors,
                        label    = if (subjectHere != null) stringResource(R.string.mushaf_quarter_subject, rubHere.quarterInJuz, subjectHere)
                                   else stringResource(R.string.mushaf_quarter_open, rubHere.quarterInJuz),
                        onClick  = { onOpenJuz(rubHere.juz) },
                        modifier = chipModifier
                    )
                }
            }
            if (compactBars) {
                // Held sideways: one low row, the place beside the name and the quarter beside the capsule, each only as
                // wide as it needs, so the page shows around them.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MushafTopCapsule(
                        backdrop  = backdrop,
                        colors    = colors,
                        title     = surahName.ifEmpty { pageBarLabel },
                        onBack    = onBack,
                        onTitle   = { showJump = true },
                        onMenu    = { menuOpen = true },
                        placeLine = placeLine,
                        modifier  = Modifier.weight(1f, fill = false),
                        compact   = true
                    )
                    quarterChip(Modifier.widthIn(max = QUARTER_CHIP_WIDEST))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MushafTopCapsule(
                        backdrop  = backdrop,
                        colors    = colors,
                        title     = surahName.ifEmpty { pageBarLabel },
                        onBack    = onBack,
                        onTitle   = { showJump = true },
                        onMenu    = { menuOpen = true },
                        placeLine = placeLine
                    )
                    quarterChip(Modifier)
                }
            }
        }

        // While the text is covered a bar stays at the bottom: what to do, and the way out. It can be tucked away
        // with everything else; it comes back with the capsules.
        val coverBarShown = coverOn && (!coverBarsHidden || showHeader)
        if (coverBarShown) {
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
                    text = stringResource(if (revealSteps) R.string.mushaf_cover_hint_steps else R.string.mushaf_cover_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MushafInkMuted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { coverAgain() }) { Text(stringResource(R.string.mushaf_cover_again)) }
                TextButton(onClick = { coverOn = false; coverAgain() }) { Text(stringResource(R.string.mushaf_cd_uncover)) }
                IconButton(onClick = { coverBarsHidden = true; showHeader = false }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.mushaf_cd_hide_bars),
                        tint = MushafInkMuted
                    )
                }
            }
        }

        // The bottom: the tools while the bar is up, and the player card while a recitation is under way.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = if (coverBarShown) 48.dp else 0.dp)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = if (compactBars) 4.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The bars are away while the text is covered: one small button brings back the cover bar.
            if (coverOn && !coverBarShown) {
                MushafGlass(backdrop, colors, CircleShape, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)) {
                    IconButton(onClick = { coverBarsHidden = false }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.mushaf_cd_show_bars),
                            tint = colors.ink
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible  = showHeader,
                enter    = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit     = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                val tools = buildList {
                    add(MushafTool(
                        icon = Icons.Filled.TaskAlt,
                        label = stringResource(R.string.mushaf_tool_revised),
                        description = stringResource(R.string.mushaf_cd_revised),
                        active = false,
                        onClick = { showRevised = true }
                    ))
                    add(MushafTool(
                        icon = if (coverOn) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        label = stringResource(if (coverOn) R.string.mushaf_tool_uncover else R.string.mushaf_tool_cover),
                        description = stringResource(if (coverOn) R.string.mushaf_cd_uncover else R.string.mushaf_cd_cover),
                        active = coverOn,
                        onClick = { coverOn = !coverOn; coverAgain(); showHeader = false; coverBarsHidden = false }
                    ))
                    // The two-page view is switched here: it is changed as often as the cover is.
                    add(MushafTool(
                        icon = Icons.Filled.MenuBook,
                        label = stringResource(if (useSpreadLayout) R.string.mushaf_tool_one_page else R.string.mushaf_tool_two_page),
                        description = stringResource(if (useSpreadLayout) R.string.mushaf_cd_switch_one_page else R.string.mushaf_cd_switch_two_page),
                        active = useSpreadLayout,
                        onClick = { useSpreadLayout = !useSpreadLayout }
                    ))
                    // With a recitation under way its card has the controls, so there is nothing to start.
                    if (!playback.active) {
                        add(MushafTool(
                            icon = Icons.Filled.Headphones,
                            label = stringResource(R.string.peek_act_listen),
                            description = stringResource(R.string.peek_act_listen),
                            active = false,
                            onClick = {
                                coroutineScope.launch {
                                    val first = withContext(Dispatchers.IO) { quran.ayahsOnPage(currentPage).firstOrNull()?.id }
                                    if (first != null) listenFrom(first, currentPage)
                                }
                            }
                        ))
                    }
                }
                // Held sideways, one low row only as wide as the tools.
                MushafToolCapsule(
                    backdrop = backdrop,
                    colors   = colors,
                    tools    = tools,
                    modifier = if (compactBars) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    compact  = compactBars
                )
            }
            if (playback.active) {
                RecitationBar(
                    state = playback,
                    onOpenSettings = { showRecitationSheet = true },
                    container = lerp(MushafParchment, MushafInk, 0.07f),
                    ink = MushafInk,
                    muted = MushafInkMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (menuOpen) {
        MushafMenuSheet(
            showGroupAyahs = showGroupAyahs,
            subjectTint = subjectTintOn,
            revealSteps = revealSteps,
            fillWidth = fillWidth,
            sideways = sideways,
            rotateWithDevice = rotateWithDevice,
            bookView = bookPref,
            readingLamp = lampOn,
            theme = readingTheme,
            onListen = {
                menuOpen = false
                coroutineScope.launch {
                    val first = withContext(Dispatchers.IO) { quran.ayahsOnPage(currentPage).firstOrNull()?.id }
                    if (first != null) listenFrom(first, currentPage)
                }
            },
            onRecitation = { menuOpen = false; showRecitationSheet = true },
            onGroupAyahs = { showGroupAyahs = it },
            onSubjectTint = { coroutineScope.launch { prefs.setMushafSubjectTint(it) } },
            onRevealSteps = { on -> coroutineScope.launch { prefs.setMushafRevealSteps(on) }; coverAgain() },
            onSaved = { menuOpen = false; onOpenSaved() },
            onMarkMemorised = {
                menuOpen = false
                coroutineScope.launch {
                    viewModel.versesOfPage(currentPage)?.let { viewModel.setMemorised(it, true) }
                    Toast.makeText(context, pageMemorisedDone, Toast.LENGTH_SHORT).show()
                }
            },
            onFillWidth = { fillWidth = it },
            onRotate = { coroutineScope.launch { prefs.setMushafRotate(it) } },
            onBookView = { coroutineScope.launch { prefs.setMushafBookView(it) } },
            onReadingLamp = { coroutineScope.launch { prefs.setMushafReadingLamp(it) } },
            onTheme = { coroutineScope.launch { prefs.setMushafTheme(it.name) }; menuOpen = false },
            onDismiss = { menuOpen = false }
        )
    }

    if (showRecitationSheet) RecitationSheet(
        onDismiss = { showRecitationSheet = false },
        onOpenStorage = { showRecitationSheet = false; onOpenAudioStorage() }
    )

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
                Toast.makeText(context, revisedSaved, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showRevised = false }
        )
    }

    peekTarget?.let { target ->
        val isMemorised = HifzCoverage.contains(memorised, target.verseId)
        val startedHere = userSubjects.firstOrNull { it.startAyahId == target.verseId }
        QuickPeekBottomSheet(
            target    = target,
            onDismiss = { peekTarget = null; peekAyahId = null; peekGroups = emptyList() },
            actions = listOf(
                PeekAction(
                    icon = Icons.Default.PlayArrow,
                    label = stringResource(R.string.peek_act_listen),
                    onClick = {
                        peekTarget = null; peekAyahId = null; peekGroups = emptyList()
                        listenFrom(target.verseId, target.pageNumber)
                    }
                ),
                PeekAction(
                    icon = if (peekSaved == null) Icons.Default.BookmarkBorder else Icons.Default.Bookmark,
                    label = stringResource(R.string.peek_act_mark),
                    onClick = { showSaveSheet = true },
                    active = peekSaved != null
                ),
                PeekAction(
                    icon = if (isMemorised) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                    label = stringResource(R.string.peek_act_memorised),
                    onClick = { viewModel.setMemorised(target.verseId..target.verseId, !isMemorised) },
                    active = isMemorised
                ),
                PeekAction(
                    icon = Icons.Default.Layers,
                    label = stringResource(R.string.peek_act_group),
                    onClick = { showGroupSheet = true },
                    active = peekGroups.isNotEmpty()
                ),
                PeekAction(
                    icon = Icons.Default.AutoStories,
                    label = stringResource(R.string.peek_act_tafsir),
                    onClick = { peekTarget = null; peekAyahId = null; peekGroups = emptyList(); onOpenTafsir(target.verseId) }
                )
            ),
            extraContent = {
                // Make this verse the start of a subject (or change the one that starts here).
                SubjectRow(startedHere?.subjectText) { showSubjectDialog = true }
                Spacer(Modifier.height(12.dp))
                VerseTheme(target.verseId)
                if (peekGroups.isNotEmpty()) AyahGroupsSection(peekGroups)
                SimilarAyahsSection(target.verseId, onOpenMushaf = null)
            }
        )

        if (showGroupSheet) {
            val allGroups by viewModel.groups.collectAsState()
            val ayahId = peekAyahId
            AddToGroupSheet(
                surah = target.surahId,
                ayah = target.ayahNumber,
                groups = allGroups,
                memberOf = peekGroups.mapTo(HashSet()) { it.group.id },
                onPick = { entry ->
                    if (ayahId != null) coroutineScope.launch {
                        viewModel.addToGroup(entry.group.id, ayahId)
                        peekGroups = viewModel.groupsOfAyah(ayahId)
                        showGroupSheet = false
                        Toast.makeText(context, UiText(R.string.add_to_group_done, entry.group.description).asString(context), Toast.LENGTH_SHORT).show()
                    }
                },
                onCreate = { name ->
                    if (ayahId != null) coroutineScope.launch {
                        viewModel.createGroupWith(name, ayahId)
                        peekGroups = viewModel.groupsOfAyah(ayahId)
                        showGroupSheet = false
                        Toast.makeText(context, UiText(R.string.add_to_group_done, name).asString(context), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showGroupSheet = false }
            )
        }

        if (showSubjectDialog) {
            SubjectHereDialog(
                reference = stringResource(R.string.saved_ref, SurahData.nameOf(target.surahId), target.ayahNumber),
                existing = startedHere,
                onSave = { title ->
                    if (startedHere == null) viewModel.addSubject(title, target.verseId) else viewModel.renameSubject(startedHere, title)
                    showSubjectDialog = false
                    peekTarget = null; peekAyahId = null; peekGroups = emptyList()
                    Toast.makeText(context, subjectSaved, Toast.LENGTH_SHORT).show()
                },
                onDelete = { subject ->
                    viewModel.deleteSubject(subject)
                    showSubjectDialog = false
                    peekTarget = null; peekAyahId = null; peekGroups = emptyList()
                },
                onDismiss = { showSubjectDialog = false }
            )
        }

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

// ── What a screen reader says for a page ─────────────────────────────────────

/**
 * Each of [pages] as its number and the verses on it ("Page 51. Al Imran, verses 10–15"): the page itself is drawn
 * glyphs, which a screen reader cannot read.
 */
@Composable
private fun rememberPageDescription(quran: QuranRepository, pages: List<Int>): String {
    val ayahs by produceState(emptyMap<Int, List<QuranAyah>>(), pages) {
        value = withContext(Dispatchers.IO) { pages.associateWith { quran.ayahsOnPage(it) } }
    }
    val described = pages.map { page ->
        val parts = ayahs[page].orEmpty().groupBy { it.surah }.map { (surah, onPage) ->
            val first = onPage.minOf { it.ayah }
            val last = onPage.maxOf { it.ayah }
            if (first == last) stringResource(R.string.mushaf_cd_page_part_one, SurahData.nameOf(surah), first)
            else stringResource(R.string.mushaf_cd_page_part, SurahData.nameOf(surah), first, last)
        }
        stringResource(R.string.mushaf_cd_page, page, parts.joinToString(". "))
    }
    return described.joinToString(" ")
}

// ── Portrait: single page ─────────────────────────────────────────────────────

@Composable
private fun SinglePageView(
    page:     Int,
    quran:    QuranRepository,
    fonts:    MushafFontManager,
    /** What a screen reader's tap does (a finger's tap is taken by the zoom over the pages, see PageZoom). */
    onTap:    () -> Unit,
    modifier: Modifier = Modifier,
    /** How the page looks as a page of a book on a desk, or null to show the page alone, filling the screen. */
    book:     BookLook? = null
) {
    val description = rememberPageDescription(quran, listOf(page))
    val toggleLabel = stringResource(R.string.mushaf_cd_toggle_bar)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = description
                onClick(label = toggleLabel) { onTap(); true }
            }
    ) {
        if (book == null) {
            PageImage(
                page          = page,
                quran         = quran,
                fonts         = fonts,
                modifier      = Modifier.fillMaxSize()
            )
        } else {
            // An odd page is a right-hand page, bound on its left; an even page is bound on its right. The text keeps
            // clear of the middle of the book along that edge.
            val boundOnLeft = page % 2 == 1
            BookPage(page = page, look = book) {
                PageImage(
                    page     = page,
                    quran    = quran,
                    fonts    = fonts,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (boundOnLeft) MIDDLE_WIDTH else 0.dp, end = if (boundOnLeft) 0.dp else MIDDLE_WIDTH)
                )
            }
        }
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
// Under the two pages, a strip with the number of each: filled for the page the reader came to, quiet for its
// neighbour. A soft shade runs down the middle where the pages meet, like the gutter of an open book.

@Composable
private fun SpreadView(
    rightPage:  Int?,
    leftPage:   Int?,
    activePage: Int,
    quran:      QuranRepository,
    fonts:      MushafFontManager,
    /** What a screen reader's tap does (a finger's tap is taken by the zoom over the pages, see PageZoom). */
    onTap:      () -> Unit,
    modifier:   Modifier = Modifier
) {
    // Right page first: the order they are read in.
    val description = rememberPageDescription(quran, listOfNotNull(rightPage, leftPage))
    val toggleLabel = stringResource(R.string.mushaf_cd_toggle_bar)
    Column(modifier = modifier.fillMaxSize().background(MushafParchment)) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clipToBounds()
            .semantics {
                contentDescription = description
                onClick(label = toggleLabel) { onTap(); true }
            }
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
                        PageImage(page = leftPage, quran = quran, fonts = fonts, modifier = Modifier.fillMaxSize())
                    }
                }

                // Hairline “spine” — pages sit closer like an open book
                Box(
                    Modifier
                        .width(SPINE_WIDTH)
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
                        PageImage(page = rightPage, quran = quran, fonts = fonts, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // The gutter: a soft shade where the two pages meet.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(GUTTER_WIDTH)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, MushafInk.copy(alpha = 0.10f), Color.Transparent)))
        )
    }

    // The numbers under the pages, in the same left-to-right order; a fixed height, so the height the pages need to fill
    // the width is known (see pagesHeightToFill).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().height(SPREAD_NUMBERS_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(leftPage, rightPage).forEach { page ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (page != null) SpreadPageNumber(page = page, active = page == activePage)
                }
            }
        }
    }
    }
}

/**
 * The height of the page area in which the pages take the whole of a screen [width] wide, in pixels: one page, the two
 * plain pages side by side with their numbers under them, or the open book (one page of it, or both).
 */
private fun pagesHeightToFill(width: Int, book: Boolean, bookSpread: Boolean, twoPage: Boolean, density: Density): Int =
    with(density) {
        when {
            // The pager gives each page of the open book half the width.
            bookSpread -> BookFrame.heightToFill((width / 2).toFloat(), density)
            book       -> BookFrame.heightToFill(width.toFloat(), density)
            twoPage    -> ceil((width - SPINE_WIDTH.toPx()) / 2f / MUSHAF_PAGE_ASPECT + SPREAD_NUMBERS_HEIGHT.toPx()).toInt()
            else       -> ceil(width / MUSHAF_PAGE_ASPECT).toInt()
        }
    }

private val SPINE_WIDTH = 0.5.dp
private val SPREAD_NUMBERS_HEIGHT = 30.dp
private val GUTTER_WIDTH = 28.dp

/** The widest the quarter chip grows beside the top capsule on a screen held sideways; a long subject is cut short. */
private val QUARTER_CHIP_WIDEST = 320.dp


/** The corner of the left edge kept for turning pages, out of reach of Android's back gesture. */
private val TURN_CORNER_WIDTH = 40.dp
private val TURN_CORNER_HEIGHT = 200.dp

/** The number of a page in the two-page view: filled for the page the reader came to, quiet for its neighbour. */
@Composable
private fun SpreadPageNumber(page: Int, active: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = page.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) scheme.onPrimary else MushafInkMuted,
        modifier = modifier
            .background(if (active) scheme.primary else MushafInk.copy(alpha = 0.07f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 3.dp)
    )
}

// ── Single Mushaf page ────────────────────────────────────────────────────────
//
// The page is drawn from the QPC V2 layout and its own font (see MushafPageView); the
// canvas keeps the page aspect ratio, so the full page is always visible (no edge crop).

@Composable
private fun PageImage(
    page:     Int,
    quran:    QuranRepository,
    fonts:    MushafFontManager,
    modifier: Modifier = Modifier
) {
    val marks = LocalAyahMarks.current

    Box(
        modifier         = modifier.background(MushafParchment),
        contentAlignment = Alignment.Center
    ) {
        MushafPageView(
            page     = page,
            quran    = quran,
            fonts    = fonts,
            ink      = MushafInk,
            modifier = Modifier.fillMaxSize(),
            highlights = marks?.highlights.orEmpty(),
            onWordTap  = marks?.let { m -> { wordId, ayahId -> wordTap(m, wordId, ayahId) } },
            onWordLongPress = marks?.let { m -> { wordId, ayahId -> wordLongPress(m, wordId, ayahId) } },
            cover = marks?.cover,
            quarterStarts = marks?.quarterStarts.orEmpty()
        )
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
    val tint = marks.highlights[ayahId] ?: return false
    if (tint == marks.backdrop[ayahId]) return false
    marks.onTap(wordId, ayahId)
    return true
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
    // The dot sits on the page, whose colour can differ from the app's (a light page in a dark app).
    val status = statusColors(onLight = MushafParchment.luminance() > 0.5f)
    val color = when {
        stability == null || !stability.isTracked -> return
        stability.score >= 0.60f                  -> status.good
        stability.score >= 0.30f                  -> status.fair
        else                                       -> status.weak
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
