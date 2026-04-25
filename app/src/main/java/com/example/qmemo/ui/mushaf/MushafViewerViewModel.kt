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
import kotlinx.coroutines.flow.map
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

    // ── Stability pipeline (mirrors HeatmapViewModel) ─────────────────────────

    private val stabilities: StateFlow<List<PageStability>> = dao
        .getAllRevisionLogs()
        .map { logs -> MemoryEngine.computeStabilities(logs) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Combined output ───────────────────────────────────────────────────────

    val state: StateFlow<MushafViewerState> = combine(
        stabilities,
        _pageSurahMap
    ) { stabs, surahMap ->
        MushafViewerState(stabilities = stabs, pageSurahMap = surahMap)
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
