package com.example.qmemo.ui.heatmap

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.StructuralPrefsRepository
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.domain.MemoryEngine
import com.example.qmemo.domain.PageStability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Supporting data classes ───────────────────────────────────────────────────

@Immutable
data class DashboardStats(
    val revisionDebt: Int     = 0,
    val stabilityIndex: Float = 0f,
    val trackedPages: Int     = 0,
    val criticalCount: Int    = 0
)

@Immutable
data class HeatmapUiState(
    val stats:        DashboardStats = DashboardStats(),
    val juzSummaries: List<JuzSummary> = emptyList(),
    val forecastDays: Int = 0,
    val isStructuralMode: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HeatmapViewModel(
    private val dao: QuranDao,
    private val prefs: StructuralPrefsRepository
) : ViewModel() {

    private val _juzToPages = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())
    private val _forecastDays = MutableStateFlow(0)
    val forecastDays: StateFlow<Int> = _forecastDays.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val mappings = dao.getAllPageJuzMappings()
            _juzToPages.value = mappings.groupBy({ it.juzId }, { it.pageNumber })
        }
    }

    /**
     * Optimized State pipeline.
     */
    val uiState: StateFlow<HeatmapUiState> = combine(
        dao.getAllRevisionLogs(),
        _juzToPages,
        _forecastDays,
        prefs.isStructuralMode
    ) { logs, juzToPages, forecastDays, isStructuralMode ->
        try {
            // 1. Fetch similarity links for semantic interference
            val links = dao.getPageSimilarityLinks()
            val linkMap = links.groupBy({ it.pageA }, { it.pageB })

            // 2. Compute the BASE state (Current Stability S)
            val currentState = MemoryEngine.computeCurrentState(logs, linkMap)

            // 3. Project Retrievability R for the requested forecast day
            val rValues = MemoryEngine.projectRetrievability(currentState, forecastDays)
            
            var revisionDebt = 0
            var criticalCount = 0
            var trackedSum = 0f
            var trackedPages = 0
            
            for (i in 0 until MemoryEngine.TOTAL_PAGES) {
                val s = currentState.stability[i]
                if (s == 0f) continue
                
                val r = rValues[i]
                trackedPages++
                trackedSum += r
                if (r < 0.5f) revisionDebt++
                if (r < 0.25f) criticalCount++
            }

            val stats = DashboardStats(
                revisionDebt   = revisionDebt,
                stabilityIndex = if (trackedPages == 0) 0f else trackedSum / trackedPages,
                trackedPages   = trackedPages,
                criticalCount  = criticalCount
            )

            if (juzToPages.isEmpty()) {
                return@combine HeatmapUiState(
                    stats = stats, 
                    juzSummaries = emptyList(), 
                    forecastDays = forecastDays,
                    isStructuralMode = isStructuralMode
                )
            }

            // Pack colors using primitive-first approach
            val colorPacked = ULongArray(MemoryEngine.TOTAL_PAGES) { i ->
                val r = rValues[i]
                val isTracked = currentState.stability[i] > 0f
                pageColorValue(r, isTracked)
            }

            val summaries = (1..30).map { juzId ->
                val pageNums = juzToPages[juzId].orEmpty()
                var sumR = 0f
                var nTracked = 0
                val minimapColors = buildList(pageNums.size) {
                    for (page in pageNums) {
                        val idx = page - 1
                        add(colorPacked[idx])
                        if (currentState.stability[idx] > 0f) {
                            sumR += rValues[idx]
                            nTracked++
                        }
                    }
                }
                
                // Juz Quality is the average retrievability of its TRACKED pages
                val avgR = if (nTracked == 0) 0f else sumR / nTracked

                val healthTone: ULong?
                val borderTone: ULong?
                if (nTracked == 0) {
                    healthTone = null
                    borderTone = null
                } else {
                    val h = healthColor(avgR)
                    healthTone = h.value
                    borderTone = h.copy(alpha = 0.45f).value
                }

                JuzSummary(
                    juzId         = juzId,
                    totalPages    = pageNums.size,
                    minimapColors = minimapColors,
                    healthPercent = (avgR * 100f).roundToInt(),
                    trackedCount  = nTracked,
                    healthTone    = healthTone,
                    borderTone    = borderTone
                )
            }

            HeatmapUiState(
                stats = stats, 
                juzSummaries = summaries, 
                forecastDays = forecastDays,
                isStructuralMode = isStructuralMode
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            HeatmapUiState()
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = HeatmapUiState()
        )
    
    fun toggleStructuralMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setStructuralMode(enabled)
        }
    }

    private val _selectedPage = MutableStateFlow<PageStability?>(null)
    val selectedPage: StateFlow<PageStability?> = _selectedPage.asStateFlow()

    fun onForecastChange(days: Int) {
        _forecastDays.value = days
    }

    fun onPageTap(pageIndex: Int, score: Float, lastRevised: Long) {
        _selectedPage.value = PageStability(pageIndex + 1, score, lastRevised)
    }

    fun dismissDialog() { _selectedPage.value = null }

    fun quickLog(page: Int) {
        _selectedPage.value = null
        viewModelScope.launch {
            dao.insertRevisionLog(
                com.example.qmemo.data.local.entity.RevisionLogEntity(
                    startPage  = page,
                    endPage    = page,
                    timestamp  = System.currentTimeMillis(),
                    manualStability = 1.0f
                )
            )
        }
    }
}

class HeatmapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HeatmapViewModel(
            AppDatabase.getInstance(context).quranDao(),
            StructuralPrefsRepository(context)
        ) as T
}
