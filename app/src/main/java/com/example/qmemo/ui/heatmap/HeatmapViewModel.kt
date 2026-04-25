package com.example.qmemo.ui.heatmap

import android.content.Context
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Supporting data classes ───────────────────────────────────────────────────

/**
 * Snapshot of the user's overall memory health, derived from stabilities.
 * All counts are only over **tracked** pages (pages revised at least once).
 */
@Immutable
data class DashboardStats(
    val revisionDebt: Int     = 0,
    val stabilityIndex: Float = 0f,
    val trackedPages: Int     = 0,
    val criticalCount: Int    = 0
)

/**
 * Everything the Brain (heatmap) screen needs in one snapshot — one [StateFlow] update
 * per revision-log change, so the UI collects once and skips duplicate work.
 */
@Immutable
data class HeatmapUiState(
    val stats:        DashboardStats = DashboardStats(),
    val juzSummaries: List<JuzSummary> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HeatmapViewModel(private val dao: QuranDao) : ViewModel() {

    private val _juzToPages = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val mappings = dao.getAllPageJuzMappings()
            _juzToPages.value = mappings.groupBy({ it.juzId }, { it.pageNumber })
        }
    }

    /**
     * Single pipeline: revision logs + juz map → stabilities once → stats + 30 summaries
     * (including pre-packed mini-map colors). Runs on the collectors' threads
     * (avoid [flowOn] here — it can interact badly with [stateIn] + Room).
     */
    val uiState: StateFlow<HeatmapUiState> = combine(
        dao.getAllRevisionLogs(),
        _juzToPages
    ) { logs, juzToPages ->
        try {
            val stabilities = MemoryEngine.computeStabilities(logs)

            var revisionDebt = 0
            var criticalCount = 0
            var trackedSum = 0f
            var trackedPages = 0
            for (s in stabilities) {
                if (!s.isTracked) continue
                trackedPages++
                trackedSum += s.score
                if (s.score < 0.5f) revisionDebt++
                if (s.score < 0.25f) criticalCount++
            }
            val stats = DashboardStats(
                revisionDebt   = revisionDebt,
                stabilityIndex = if (trackedPages == 0) 0f else trackedSum / trackedPages,
                trackedPages   = trackedPages,
                criticalCount  = criticalCount
            )

            if (juzToPages.isEmpty()) {
                return@combine HeatmapUiState(stats = stats, juzSummaries = emptyList())
            }

            val colorPacked = ULongArray(stabilities.size) { i -> pageColorValue(stabilities[i]) }

            val summaries = (1..30).map { juzId ->
                val pageNums = juzToPages[juzId].orEmpty()
                var sumTracked = 0f
                var nTracked = 0
                val minimapColors = buildList(pageNums.size) {
                    for (page in pageNums) {
                        val idx = page - 1
                        val s = stabilities.getOrNull(idx)
                            ?: PageStability(page, 0f, null, null)
                        add(if (idx in colorPacked.indices) colorPacked[idx] else pageColorValue(s))
                        if (s.isTracked) {
                            sumTracked += s.score
                            nTracked++
                        }
                    }
                }
                val avg = sumTracked / pageNums.size

                val healthTone: ULong?
                val borderTone: ULong?
                if (nTracked == 0) {
                    healthTone = null
                    borderTone = null
                } else {
                    val h = healthColor(avg)
                    healthTone = h.value
                    borderTone = h.copy(alpha = 0.45f).value
                }

                JuzSummary(
                    juzId         = juzId,
                    totalPages    = pageNums.size,
                    minimapColors = minimapColors,
                    healthPercent = (avg * 100f).roundToInt(),
                    trackedCount  = nTracked,
                    healthTone    = healthTone,
                    borderTone    = borderTone
                )
            }

            HeatmapUiState(stats = stats, juzSummaries = summaries)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            HeatmapUiState()
        }
    }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = HeatmapUiState()
        )

    private val _selectedPage = MutableStateFlow<PageStability?>(null)
    val selectedPage: StateFlow<PageStability?> = _selectedPage.asStateFlow()

    fun onPageTap(ps: PageStability) { _selectedPage.value = ps }
    fun dismissDialog()              { _selectedPage.value = null }

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

class HeatmapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HeatmapViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
