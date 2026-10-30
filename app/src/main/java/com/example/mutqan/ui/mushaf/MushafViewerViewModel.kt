package com.example.mutqan.ui.mushaf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.domain.AyahGroup
import com.example.mutqan.domain.MemoryEngine
import com.example.mutqan.domain.PageStability
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranRepository
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
     * primary Surah.
     */
    val pageSurahMap: Map<Int, String> = emptyMap(),
    /** True if the initial FSRS calculation is still running. */
    val isLoading: Boolean = true
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MushafViewerViewModel(
    private val dao: StudyDao,
    private val study: StudyRepository,
    private val quran: QuranRepository
) : ViewModel() {

    // Loaded once from the bundled Quran layout — never changes at runtime
    private val _pageSurahMap = MutableStateFlow<Map<Int, String>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _pageSurahMap.value = quran.firstSurahByPage().mapValues { (_, surahId) -> SurahData.nameOf(surahId) }
        }
    }

    /** Global ids of every ayah that belongs to a mutashabihat group, for highlighting on the page. */
    val mutashabihatAyahs: StateFlow<Set<Int>> = dao.observeMembers()
        .map { members -> members.mapTo(HashSet()) { it.verseId } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    suspend fun groupsOfAyah(ayahId: Int): List<AyahGroup> = study.groupsOfAyah(ayahId)

    // ── Combined pipeline (Standard FSRS Logic) ───────────────────────────────

    val state: StateFlow<MushafViewerState> = combine(
        study.revisionLogs(),
        _pageSurahMap
    ) { logs, surahMap ->
        // Heavy lifting moved to Default dispatcher via flowOn
        val links = study.pageSimilarityLinks()
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
        MushafViewerViewModel(
            AppDatabase.getInstance(context).studyDao(),
            StudyRepository.create(context),
            QuranRepository.getInstance(context)
        ) as T
}
