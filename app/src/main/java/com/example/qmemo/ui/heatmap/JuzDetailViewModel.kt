package com.example.qmemo.ui.heatmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.StructuralPrefsRepository
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.JuzSurahRange
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.RevisionLogEntity
import com.example.qmemo.data.local.entity.StructureUnitEntity
import com.example.qmemo.data.local.entity.UserSubjectEntity
import com.example.qmemo.data.local.entity.VerseEntity
import com.example.qmemo.domain.MemoryEngine
import com.example.qmemo.domain.PageStability
import com.example.qmemo.domain.StructuralSharingManager
import com.example.qmemo.util.toCleanQuranicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JuzDetailViewModel(
    private val dao:   QuranDao,
    private val prefs: StructuralPrefsRepository,
    private val sharingManager: StructuralSharingManager,
    val          juzId: Int
) : ViewModel() {

    private val _surahRange = MutableStateFlow<JuzSurahRange?>(null)
    val surahRange: StateFlow<JuzSurahRange?> = _surahRange.asStateFlow()

    val isStructuralMode: StateFlow<Boolean> = prefs.isStructuralMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = MutableSharedFlow<SharingEvent>()
    val events: SharedFlow<SharingEvent> = _events

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _surahRange.value = dao.getSurahRangeForJuz(juzId)
        }
    }

    /**
     * Standard reactive pipeline for pages.
     */
    val pagesWithSurahs: StateFlow<List<PageWithSurahs>> = combine(
        dao.getAllRevisionLogs(),
        MutableStateFlow(0) 
    ) { logs, forecastDays ->
        val pages = dao.getPagesByJuz(juzId)
        val pageSurahs = dao.getPageSurahsForJuz(juzId)
        val links = dao.getPageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        val surahMap: Map<Int, List<String>> = pageSurahs.associate { ref ->
            ref.pageNumber to ref.surahIds.map { id -> SurahData.nameOf(id) }
        }

        val state = MemoryEngine.computeCurrentState(logs, linkMap)
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
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Combined timeline items for Structural Flow mode (Anchor Cards).
     */
    val timelineItems: StateFlow<List<AnchorCardData>> = combine(
        dao.getStructureUnitsByJuz(juzId),
        dao.getUserSubjectsByJuz(juzId)
    ) { units, subjects ->
        val subjectMap = subjects.groupBy { it.unitId }
        
        units.map { unit ->
            val startVerse = dao.getVerseById(unit.startAyahId)
            val endVerse = dao.getVerseById(unit.endAyahId)
            
            val subjectsWithText = (subjectMap[unit.id] ?: emptyList()).map { subject ->
                val verse = dao.getVerseById(subject.startAyahId)
                val ayahText = verse?.textArabic?.toCleanQuranicText() ?: ""
                SubjectWithText(subject, ayahText, verse?.ayahNumber ?: 0)
            }
            
            val ayahsInQuarter = (unit.startAyahId..unit.endAyahId).mapNotNull { id ->
                dao.getVerseById(id)
            }
            
            AnchorCardData(
                unit = unit,
                subjects = subjectsWithText,
                startAyahText = startVerse?.textArabic?.toCleanQuranicText() ?: "",
                endAyahText = endVerse?.textArabic?.toCleanQuranicText() ?: "",
                pages = (unit.startPage..unit.endPage).toList(),
                ayahsInQuarter = ayahsInQuarter
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPage = MutableStateFlow<PageSelection?>(null)
    val selectedPage: StateFlow<PageSelection?> = _selectedPage.asStateFlow()

    private val _peekData = MutableStateFlow<PagePeekData?>(null)
    val peekData: StateFlow<PagePeekData?> = _peekData.asStateFlow()

    private val _ayahPreview = MutableStateFlow<VerseEntity?>(null)
    val ayahPreview: StateFlow<VerseEntity?> = _ayahPreview.asStateFlow()

    fun onPageTap(page: Int, surahLabel: String) {
        if (isStructuralMode.value) {
            peekPage(page)
        } else {
            _selectedPage.value = PageSelection(page, surahLabel)
        }
    }

    fun showAyahPreview(startAyahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val verse = dao.getVerseById(startAyahId)
            _ayahPreview.value = verse?.copy(
                textArabic = verse.textArabic.toCleanQuranicText()
            )
        }
    }

    fun dismissAyahPreview() { _ayahPreview.value = null }

    private fun peekPage(pageNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val verses = dao.getVersesByPage(pageNumber)
            if (verses.isNotEmpty()) {
                val fullText = verses.joinToString(" ") { it.textArabic.toCleanQuranicText() }
                val words = fullText.split(" ").filter { it.isNotBlank() }
                
                val startSnippet = words.take(10).joinToString(" ")
                val endSnippet = words.takeLast(10).joinToString(" ")
                
                _peekData.value = PagePeekData(pageNumber, startSnippet, endSnippet)
            }
        }
    }

    fun dismissPeek() { _peekData.value = null }
    fun dismissDialog() { _selectedPage.value = null }

    fun addSubject(unitId: Int, text: String, startAyahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertUserSubject(
                UserSubjectEntity(
                    unitId = unitId,
                    subjectText = text,
                    startAyahId = startAyahId,
                    orderIndex = 0
                )
            )
        }
    }

    fun updateSubject(entity: UserSubjectEntity, newText: String, newStartAyahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateUserSubject(
                entity.copy(subjectText = newText, startAyahId = newStartAyahId)
            )
        }
    }

    fun deleteSubject(subject: UserSubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteUserSubject(subject)
        }
    }

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

    // ── Sharing ───────────────────────────────────────────────

    fun exportSubjects(uri: android.net.Uri) {
        viewModelScope.launch {
            val res = sharingManager.exportSubjects(uri, juzId)
            if (res.isSuccess) _events.emit(SharingEvent.Success("Subjects exported"))
            else _events.emit(SharingEvent.Error(res.exceptionOrNull()?.message ?: "Export failed"))
        }
    }

    fun importSubjects(uri: android.net.Uri) {
        viewModelScope.launch {
            val res = sharingManager.importSubjects(uri)
            if (res.isSuccess) _events.emit(SharingEvent.Success("Subjects imported"))
            else _events.emit(SharingEvent.Error(res.exceptionOrNull()?.message ?: "Import failed"))
        }
    }
}

sealed class SharingEvent {
    data class Success(val message: String) : SharingEvent()
    data class Error(val message: String) : SharingEvent()
}

sealed class TimelineItem {
    data class Header(val unit: StructureUnitEntity, val subjects: List<UserSubjectEntity>) : TimelineItem()
    data class Page(val pageData: PageWithSurahs) : TimelineItem()
}

data class PagePeekData(
    val pageNumber: Int,
    val startSnippet: String,
    val endSnippet: String
)

data class AnchorCardData(
    val unit: StructureUnitEntity,
    val subjects: List<SubjectWithText>,
    val startAyahText: String,
    val endAyahText: String,
    val pages: List<Int>,
    val ayahsInQuarter: List<VerseEntity>
)

data class SubjectWithText(
    val entity: UserSubjectEntity,
    val startAyahText: String,
    val ayahNumber: Int
)

class JuzDetailViewModelFactory(
    private val context: Context,
    private val juzId:   Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JuzDetailViewModel(
            AppDatabase.getInstance(context).quranDao(),
            StructuralPrefsRepository(context),
            StructuralSharingManager(context),
            juzId
        ) as T
}
