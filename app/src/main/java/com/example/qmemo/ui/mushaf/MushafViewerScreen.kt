package com.example.qmemo.ui.mushaf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.domain.MushafDownloadManager
import com.example.qmemo.domain.MushafPageLoader
import com.example.qmemo.domain.MushafRepository
import com.example.qmemo.domain.PageStability
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled

private const val TOTAL_PAGES  = 604
// 302 spreads: spread 0 = (right=1, left=2), spreads 1–301 = (right=odd, left=even)
private const val TOTAL_SPREADS = 302

/** Warm reading background — clearly off-white, easy on the eyes */
private val MushafParchment   = Color(0xFFE8E0D2)
private val MushafHeaderScrim = Color(0xE8E8E0D2)
private val MushafInk         = Color(0xFF353028)
private val MushafInkMuted    = Color(0xFF6B655C)
private val MushafSpineLine   = Color(0x145C5346)

/** Softens harsh white in page PNGs (slight warmth); keeps black verse ink dark */
private val MushafPageWarmthFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1f,     0f,     0f,     0f, 0f,
            0f,     0.99f,  0f,     0f, 0f,
            0f,     0f,     0.93f,  0f, 0f,
            0f,     0f,     0f,     1f, 0f
        )
    )
)

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

@Composable
fun MushafViewerScreen(
    startPage: Int,
    onBack:    () -> Unit
) {
    val context     = LocalContext.current
    val viewModel   = viewModel<MushafViewerViewModel>(factory = MushafViewerViewModelFactory(context))
    val state       by viewModel.state.collectAsState()

    val repository = remember(context) { MushafRepository(context.applicationContext) }
    val downloadManager = remember(context) { MushafDownloadManager(context.applicationContext) }
    val pageLoader = remember(context) { MushafPageLoader(context.applicationContext, repository, downloadManager) }

    DisposableEffect(pageLoader) {
        onDispose { pageLoader.clear() }
    }

    // Tracks the \"primary\" Mushaf page for the top bar; persists across rotations.
    var currentPage by rememberSaveable { mutableIntStateOf(startPage.coerceIn(1, TOTAL_PAGES)) }

    // The page the user specifically navigated to — used to draw the green border.
    // Fixed for the lifetime of this screen; does not change on swipe.
    val activePage = startPage.coerceIn(1, TOTAL_PAGES)

    // Chrome hidden by default so pages use the full display; tap toggles.
    var showHeader  by remember { mutableStateOf(false) }

    // Pager scroll is locked when the user has zoomed in.
    var currentScale by remember { mutableFloatStateOf(1f) }

    // Two-page spread vs single page — user-controlled (not tied to orientation).
    var useSpreadLayout by rememberSaveable { mutableStateOf(false) }

    // Spread only: each page uses full column width (taller than viewport); scroll vertically.
    var spreadFillWidth by rememberSaveable { mutableStateOf(false) }

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
    LaunchedEffect(useSpreadLayout) {
        val act = context.findActivity() ?: return@LaunchedEffect
        act.requestedOrientation = if (useSpreadLayout) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (!useSpreadLayout) spreadFillWidth = false
    }

    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ── Root: full-bleed pager + floating chrome (no permanent top bar eating height)
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                                pageLoader       = pageLoader,
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
                                pageLoader    = pageLoader,
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
                if (surahName.isNotEmpty()) {
                    Text(
                        text       = surahName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MushafInk,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(onClick = { useSpreadLayout = !useSpreadLayout }) {
                    Icon(
                        imageVector = if (useSpreadLayout) Icons.Filled.Article else Icons.Filled.MenuBook,
                        contentDescription = stringResource(
                            if (useSpreadLayout) R.string.mushaf_cd_switch_one_page
                            else R.string.mushaf_cd_switch_two_page
                        ),
                        tint        = MushafInk
                    )
                }
                if (useSpreadLayout) {
                    IconButton(onClick = { spreadFillWidth = !spreadFillWidth }) {
                        Icon(
                            imageVector = if (spreadFillWidth) Icons.Filled.FitScreen else Icons.Filled.OpenInFull,
                            contentDescription = stringResource(
                                if (spreadFillWidth) R.string.mushaf_cd_spread_fit_page
                                else R.string.mushaf_cd_spread_fill_width
                            ),
                            tint = MushafInk
                        )
                    }
                }
                Text(
                    text       = pageBarLabel,
                    style      = MaterialTheme.typography.labelMedium,
                    color      = MushafInkMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.padding(end = 8.dp)
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
    pageLoader:    MushafPageLoader,
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
            pageLoader    = pageLoader,
            isActive      = page == activePage,
            contentScale  = ContentScale.Fit,
            imageAlign    = Alignment.Center,
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
    pageLoader:       MushafPageLoader,
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
                            pageLoader               = pageLoader,
                            isActive                 = leftPage == activePage,
                            contentScale             = ContentScale.Fit,
                            imageAlign               = Alignment.Center,
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
                            pageLoader               = pageLoader,
                            isActive                 = rightPage == activePage,
                            contentScale             = ContentScale.Fit,
                            imageAlign               = Alignment.Center,
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

// ── Single Mushaf page image ──────────────────────────────────────────────────
//
// isActive = true  → 3dp green border (DifficultySmooth) — the navigated page
// isActive = false → no border — companion page in the spread
//
// Use ContentScale.Fit so the full mushaf page is always visible (no edge crop).
// Default Fit for call sites that omit scale.
//
// RGB_565 bitmap config halves memory vs ARGB_8888; imperceptible on
// black-and-white Mushaf pages and beneficial on a Pixel 6a.

@Composable
private fun PageImage(
    page:                    Int,
    pageLoader:              MushafPageLoader,
    isActive:                Boolean = false,
    contentScale:            ContentScale = ContentScale.Fit,
    imageAlign:              Alignment = Alignment.Center,
    modifier:                Modifier = Modifier,
    /** Spread “fill width” mode: image uses full column width; scroll to see full height. */
    fillWidthVerticalScroll: ScrollState? = null
) {
    val loadingState by pageLoader.loadingState.collectAsState()
    val bitmap = loadingState[page]

    LaunchedEffect(page) {
        pageLoader.loadPage(page)
    }

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
        if (bitmap != null) {
            if (fillWidthVerticalScroll != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(fillWidthVerticalScroll)
                ) {
                    Image(
                        bitmap             = bitmap.asImageBitmap(),
                        contentDescription = null,
                        colorFilter        = MushafPageWarmthFilter,
                        alignment          = Alignment.TopCenter,
                        contentScale       = ContentScale.FillWidth,
                        modifier           = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Image(
                    bitmap             = bitmap.asImageBitmap(),
                    contentDescription = null,
                    colorFilter        = MushafPageWarmthFilter,
                    alignment          = imageAlign,
                    contentScale       = contentScale,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        } else {
            CircularProgressIndicator(
                modifier    = Modifier.size(48.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
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
