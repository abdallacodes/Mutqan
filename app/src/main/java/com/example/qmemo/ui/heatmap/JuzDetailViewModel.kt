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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JuzDetailViewModel(
    private val dao:   QuranDao,
    val          juzId: Int
) : ViewModel() {

    // ── Surah range (subtitle in top bar) ────────────────────────────────────

    private val _surahRange = MutableStateFlow<JuzSurahRange?>(null)
    val surahRange: StateFlow<JuzSurahRange?> = _surahRange.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _surahRange.value = dao.getSurahRangeForJuz(juzId)
        }
    }

    // ── Pages with Surah names + live stabilities ─────────────────────────────

    /**
     * Reactive pipeline — optimised for scroll performance:
     *
     * 1. The first two DB calls ([getPagesByJuz] and [getPageSurahsForJuz]) are
     *    one-shot suspend functions on static data.  Their results are captured
     *    in the [flow] closure and **never re-queried**.
     * 2. [surahMap] (page → sorted surah names) is built once from [getPageSurahsForJuz]
     *    and reused on every revision-log update — zero allocations on scroll.
     * 3. Only [getAllRevisionLogs] is a reactive Flow; it re-emits only when a log
     *    is inserted/deleted, triggering a fresh stability computation.
     */
    val pagesWithSurahs: StateFlow<List<PageWithSurahs>> = flow {
        // One-time static loads — cached for the lifetime of this ViewModel
        val pages     = dao.getPagesByJuz(juzId)
        val pageSurahs = dao.getPageSurahsForJuz(juzId)

        val surahMap: Map<Int, List<String>> = pageSurahs.associate { ref ->
            ref.pageNumber to ref.surahIds.map { id -> SurahData.nameOf(id) }
        }

        emitAll(
            dao.getAllRevisionLogs().map { logs ->
                val all = MemoryEngine.computeStabilities(logs)
                pages.map { page ->
                    PageWithSurahs(
                        stability  = all.getOrNull(page - 1)
                                         ?: PageStability(page, 0f, null, null),
                        surahNames = surahMap[page] ?: emptyList()
                    )
                }
            }
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // ── Quick-status dialog selection ─────────────────────────────────────────

    /**
     * Carries both page number (for [logPage]) and the surah label (for the
     * dialog title).  Null means the dialog is closed.
     */
    private val _selectedPage = MutableStateFlow<PageSelection?>(null)
    val selectedPage: StateFlow<PageSelection?> = _selectedPage.asStateFlow()

    fun onPageTap(page: Int, surahLabel: String) {
        _selectedPage.value = PageSelection(page, surahLabel)
    }

    fun dismissDialog() { _selectedPage.value = null }

    /**
     * Inserts a single-page [RevisionLogEntity] with the chosen [difficulty].
     * The decay logic in [MemoryEngine] applies automatically on the next
     * stability recomputation — no special-casing needed here.
     */
    fun logPage(page: Int, difficulty: Int) {
        _selectedPage.value = null
        viewModelScope.launch {
            dao.insertRevisionLog(
                RevisionLogEntity(
                    startPage  = page,
                    endPage    = page,
                    timestamp  = System.currentTimeMillis(),
                    difficulty = difficulty
                )
            )
        }
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

class JuzDetailViewModelFactory(
    private val context: Context,
    private val juzId:   Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JuzDetailViewModel(AppDatabase.getInstance(context).quranDao(), juzId) as T
}
