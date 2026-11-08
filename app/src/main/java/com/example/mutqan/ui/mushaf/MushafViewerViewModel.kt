package com.example.mutqan.ui.mushaf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.GroupWithCount
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.domain.AyahGroup
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.revision.startOfTodayUtcMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.mutqan.domain.MemoryEngine
import com.example.mutqan.domain.SavedPassage
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

    /** The user's subjects as stored, to edit the one that starts at a verse. */
    val userSubjects: StateFlow<List<UserSubjectEntity>> = dao.observeUserSubjectsBetween(1, Int.MAX_VALUE)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The user's subjects (start ayah id to title), for showing the subject of the quarter being read. */
    val subjects: StateFlow<List<Pair<Int, String>>> = userSubjects
        .map { list -> list.map { it.startAyahId to it.subjectText } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A subject called [text] that starts at [ayahId]; a verse that already starts one gets the new title instead. */
    fun addSubject(text: String, ayahId: Int) {
        viewModelScope.launch(Dispatchers.IO) { dao.saveUserSubject(text, ayahId) }
    }

    fun renameSubject(subject: UserSubjectEntity, text: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.saveUserSubject(text, subject.startAyahId, subject) }
    }

    fun deleteSubject(subject: UserSubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteUserSubject(subject) }
    }

    /** All the user's mutashabihat groups, newest first, for the "add to group" sheet. */
    val groups: StateFlow<List<GroupWithCount>> = study.allGroupsWithMemberCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Puts the ayah into a group; false when it was already there. */
    suspend fun addToGroup(groupId: Int, ayahId: Int): Boolean = study.addAyahToGroup(groupId, ayahId)

    /** Starts a new group called [name] holding this ayah. */
    suspend fun createGroupWith(name: String, ayahId: Int): Int = study.createGroupWith(name, ayahId)

    /** What the user has memorised, as merged ayah-id ranges. */
    val coverage: StateFlow<List<IntRange>> = study.coverage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Marks the verses [range] as memorised, or as not memorised. */
    fun setMemorised(range: IntRange, memorised: Boolean) {
        viewModelScope.launch {
            val now = study.coverage().first()
            study.setCoverage(if (memorised) HifzCoverage.add(now, range) else HifzCoverage.remove(now, range))
        }
    }

    /** The verse ids of [page], for marking a whole page. */
    suspend fun versesOfPage(page: Int): IntRange? = withContext(Dispatchers.IO) { quran.ayahIdRangeOfPages(page, page) }

    /** Records that [page] was revised with the given outcome, dated today. */
    fun logPageRevision(page: Int, outcome: RecallOutcome, tags: Set<MistakeTag>) {
        viewModelScope.launch { study.addRevision(page, page, outcome, tags, startOfTodayUtcMillis()) }
    }

    /** Every bookmark and weak passage, for tinting ayahs and for the save sheet. */
    val savedPassages: StateFlow<List<SavedPassage>> = study.savedPassages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePassage(passage: SavedPassage) {
        viewModelScope.launch { study.savePassage(passage) }
    }

    fun deleteSavedPassage(id: Int) {
        viewModelScope.launch { study.deleteSavedPassage(id) }
    }

    // ── Combined pipeline (Standard FSRS Logic) ───────────────────────────────

    val state: StateFlow<MushafViewerState> = combine(
        study.revisionLogs(),
        study.coveredPages(),
        _pageSurahMap
    ) { logs, covered, surahMap ->
        // Heavy lifting moved to Default dispatcher via flowOn
        val links = study.pageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        val state = MemoryEngine.computeCurrentState(logs, linkMap, covered)
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
