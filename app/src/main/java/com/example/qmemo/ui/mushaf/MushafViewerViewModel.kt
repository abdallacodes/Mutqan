package com.example.qmemo.ui.mushaf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.domain.MemoryEngine
import com.example.qmemo.domain.PageStability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

data class MushafViewerState(
    /** Stability score for each of the 604 pages; index = page - 1. */
    val stabilities: List<PageStability> = emptyList(),
    /**
     * Maps each page number (1–604) to the localised display name of its
     * primary Surah (the first Surah that starts on that page, or the Surah
     * that covers most of it when no new Surah begins there).
     */
    val pageSurahMap: Map<Int, String> = emptyMap()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MushafViewerViewModel(private val dao: QuranDao) : ViewModel() {

    // Loaded once from the static verses table — never changes at runtime
    private val _pageSurahMap = MutableStateFlow<Map<Int, String>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = dao.getAllPageSurahMappings()
            _pageSurahMap.value = rows.associate { ref ->
                // Use the first (lowest) Surah ID on the page as the primary surah
                val primarySurahId = ref.surahIds.firstOrNull() ?: return@associate ref.pageNumber to ""
                ref.pageNumber to SurahData.nameOf(primarySurahId)
            }
        }
    }

    // ── Combined pipeline (Standard FSRS Logic) ───────────────────────────────

    val state: StateFlow<MushafViewerState> = combine(
        dao.getAllRevisionLogs(),
        _pageSurahMap
    ) { logs, surahMap ->
        val links = dao.getPageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        // 1. Compute BASE state
        val state = MemoryEngine.computeCurrentState(logs, linkMap)
        
        // 2. Project (0 days)
        val rValues = MemoryEngine.projectRetrievability(state, 0)

        val pages = (1..MemoryEngine.TOTAL_PAGES).map { page ->
            val idx = page - 1
            PageStability(
                page = page,
                score = rValues[idx],
                lastRevised = state.lastRevisionTimestamps[idx].let { if (it == 0L) null else it }
            )
        }

        MushafViewerState(stabilities = pages, pageSurahMap = surahMap)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = MushafViewerState()
    )
}

// ── Factory ───────────────────────────────────────────────────────────────────

class MushafViewerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MushafViewerViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
