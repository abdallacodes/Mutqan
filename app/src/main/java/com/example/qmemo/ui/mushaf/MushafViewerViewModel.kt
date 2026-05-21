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
     * primary Surah.
     */
    val pageSurahMap: Map<Int, String> = emptyMap(),
    /** True if the initial FSRS calculation is still running. */
    val isLoading: Boolean = true
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MushafViewerViewModel(private val dao: QuranDao) : ViewModel() {

    // Loaded once from the static verses table — never changes at runtime
    private val _pageSurahMap = MutableStateFlow<Map<Int, String>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = dao.getAllPageSurahMappings()
            _pageSurahMap.value = rows.associate { ref ->
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
        // Heavy lifting moved to Default dispatcher via flowOn
        val links = dao.getPageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        val state = MemoryEngine.computeCurrentState(logs, linkMap)
        val rValues = MemoryEngine.projectRetrievability(state, 0)

        val pages = (1..MemoryEngine.TOTAL_PAGES).map { page ->
            val idx = page - 1
            PageStability(
                page = page,
                score = rValues[idx],
                lastRevised = state.lastRevisionTimestamps[idx].let { if (it == 0L) null else it }
            )
        }

        MushafViewerState(
            stabilities = pages,
            pageSurahMap = surahMap,
            isLoading = false
        )
    }
    .flowOn(Dispatchers.Default) // Ensures all logic inside 'combine' runs off-main
    .stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = MushafViewerState(isLoading = true)
    )
}

// ── Factory ───────────────────────────────────────────────────────────────────

class MushafViewerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MushafViewerViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
