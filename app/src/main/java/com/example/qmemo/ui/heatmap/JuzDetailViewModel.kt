package com.example.qmemo.ui.heatmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.JuzSurahRange
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JuzDetailViewModel(
    private val dao:   QuranDao,
    val          juzId: Int
) : ViewModel() {

    private val _surahRange = MutableStateFlow<JuzSurahRange?>(null)
    val surahRange: StateFlow<JuzSurahRange?> = _surahRange.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _surahRange.value = dao.getSurahRangeForJuz(juzId)
        }
    }

    /**
     * Standard reactive pipeline using the optimized FSRS MemoryEngine.
     */
    val pagesWithSurahs: StateFlow<List<PageWithSurahs>> = combine(
        dao.getAllRevisionLogs(),
        // We use a fixed 0-day projection here for simplicity. 
        // A future update could add a forecast slider to this screen as well.
        MutableStateFlow(0) 
    ) { logs, forecastDays ->
        val pages = dao.getPagesByJuz(juzId)
        val pageSurahs = dao.getPageSurahsForJuz(juzId)
        val links = dao.getPageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        val surahMap: Map<Int, List<String>> = pageSurahs.associate { ref ->
            ref.pageNumber to ref.surahIds.map { id -> SurahData.nameOf(id) }
        }

        // 1. Compute BASE state
        val state = MemoryEngine.computeCurrentState(logs, linkMap)
        
        // 2. Project
        val rValues = MemoryEngine.projectRetrievability(state, forecastDays)

        pages.map { page ->
            val idx = page - 1
            PageWithSurahs(
                stability = PageStability(
                    page = page,
                    score = rValues[idx],
                    lastRevised = state.lastRevisionTimestamps[idx].let { if (it == 0L) null else it }
                ),
                surahNames = surahMap[page] ?: emptyList()
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _selectedPage = MutableStateFlow<PageSelection?>(null)
    val selectedPage: StateFlow<PageSelection?> = _selectedPage.asStateFlow()

    fun onPageTap(page: Int, surahLabel: String) {
        _selectedPage.value = PageSelection(page, surahLabel)
    }

    fun dismissDialog() { _selectedPage.value = null }

    fun logPage(page: Int, quality: Float) {
        _selectedPage.value = null
        viewModelScope.launch {
            dao.insertRevisionLog(
                RevisionLogEntity(
                    startPage  = page,
                    endPage    = page,
                    timestamp  = System.currentTimeMillis(),
                    manualStability = quality
                )
            )
        }
    }
}

class JuzDetailViewModelFactory(
    private val context: Context,
    private val juzId:   Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JuzDetailViewModel(AppDatabase.getInstance(context).quranDao(), juzId) as T
}
