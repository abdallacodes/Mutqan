package com.example.qmemo.ui.heatmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.RevisionLogEntity
import com.example.qmemo.domain.MemoryEngine
import com.example.qmemo.domain.PageStability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Supporting data classes ───────────────────────────────────────────────────

/**
 * Snapshot of the user's overall memory health, derived from [HeatmapState.stabilities].
 * All counts are only over **tracked** pages (pages revised at least once).
 */
data class DashboardStats(
    /** Pages whose stability has dropped below 50% — the "rust" counter. */
    val revisionDebt: Int     = 0,
    /** Average stability score (0.0–1.0) across all tracked pages. */
    val stabilityIndex: Float = 0f,
    /** Total pages revised at least once. */
    val trackedPages: Int     = 0,
    /** Pages below 25% stability — demand immediate attention. */
    val criticalCount: Int    = 0
)

/**
 * Single state object emitted by the [HeatmapViewModel].
 * Keeping both lists in one class ensures [MemoryEngine.computeStabilities] runs
 * only once per revision-log update and the two consumers stay in sync.
 */
data class HeatmapState(
    val stabilities: List<PageStability> = emptyList(),
    val stats: DashboardStats            = DashboardStats()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HeatmapViewModel(private val dao: QuranDao) : ViewModel() {

    // ── Juz → pages static mapping ────────────────────────────────────────────

    /**
     * Loaded once on init from the static verses table.
     * Maps juzId (1–30) → ordered list of page numbers belonging to that Juz.
     * Emits empty map until the DB read completes (instant in practice).
     */
    private val _juzToPages = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val mappings = dao.getAllPageJuzMappings()
            _juzToPages.value = mappings.groupBy({ it.juzId }, { it.pageNumber })
        }
    }

    // ── Combined heatmap + dashboard state ───────────────────────────────────

    /**
     * Pipeline:
     *  1. [QuranDao.getAllRevisionLogs] emits on Room's IO thread whenever logs change.
     *  2. [MemoryEngine.computeStabilities] runs on [Dispatchers.Default] (CPU-bound).
     *  3. Dashboard stats are derived from the same stability list — one pass, zero waste.
     *  4. [flowOn] shifts the expensive work off the main thread.
     *  5. [stateIn] caches the latest result so recomposition is free.
     */
    val heatmapState: StateFlow<HeatmapState> = dao
        .getAllRevisionLogs()
        .map { logs ->
            val stabilities = MemoryEngine.computeStabilities(logs)
            val tracked     = stabilities.filter { it.isTracked }
            HeatmapState(
                stabilities = stabilities,
                stats = DashboardStats(
                    revisionDebt   = tracked.count { it.score < 0.5f },
                    stabilityIndex = if (tracked.isEmpty()) 0f
                                     else tracked.map { it.score }.average().toFloat(),
                    trackedPages   = tracked.size,
                    criticalCount  = tracked.count { it.score < 0.25f }
                )
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = HeatmapState()
        )

    // ── 30-Juz summaries ─────────────────────────────────────────────────────

    /**
     * Derives a [JuzSummary] for each of the 30 Juz by intersecting the global
     * stability list with the page→juz mapping. Re-emits whenever either source
     * changes (revision log update or initial juz map load).
     */
    val juzSummaries: StateFlow<List<JuzSummary>> = combine(
        heatmapState,
        _juzToPages
    ) { state, juzToPages ->
        if (juzToPages.isEmpty()) return@combine emptyList()
        (1..30).map { juzId ->
            val pages   = juzToPages[juzId] ?: emptyList()
            val stabs   = pages.map { page ->
                state.stabilities.getOrNull(page - 1)
                    ?: PageStability(page, 0f, null, null)
            }
            val tracked = stabs.filter { it.isTracked }
            val avg     = if (tracked.isEmpty()) 0f
                          else tracked.map { it.score }.average().toFloat()
            JuzSummary(
                juzId           = juzId,
                pageStabilities = stabs,
                healthPercent   = (avg * 100).roundToInt(),
                trackedCount    = tracked.size
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // ── Page selection (kept for potential raw-heatmap use) ───────────────────

    private val _selectedPage = MutableStateFlow<PageStability?>(null)
    val selectedPage: StateFlow<PageStability?> = _selectedPage.asStateFlow()

    fun onPageTap(ps: PageStability) { _selectedPage.value = ps }
    fun dismissDialog()              { _selectedPage.value = null }

    // ── Quick log ─────────────────────────────────────────────────────────────

    fun quickLog(page: Int) {
        _selectedPage.value = null
        viewModelScope.launch {
            dao.insertRevisionLog(
                RevisionLogEntity(
                    startPage  = page,
                    endPage    = page,
                    timestamp  = System.currentTimeMillis(),
                    difficulty = 1
                )
            )
        }
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

class HeatmapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HeatmapViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
