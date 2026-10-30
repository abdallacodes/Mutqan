package com.example.mutqan.ui.heatmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.data.StructuralPrefsRepository
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.domain.MemoryEngine
import com.example.mutqan.domain.PageStability
import com.example.mutqan.domain.StructuralSharingManager
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranRub
import com.example.mutqan.quran.toCleanQuranicText
import com.example.mutqan.ui.components.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JuzDetailViewModel(
    private val dao:   StudyDao,
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val prefs: StructuralPrefsRepository,
    private val sharingManager: StructuralSharingManager,
    val          juzId: Int
) : ViewModel() {

    private val _surahRange = MutableStateFlow<IntRange?>(null)
    val surahRange: StateFlow<IntRange?> = _surahRange.asStateFlow()

    val isStructuralMode: StateFlow<Boolean> = prefs.isStructuralMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = MutableSharedFlow<SharingEvent>()
    val events: SharedFlow<SharingEvent> = _events

    // Static Quran data for this juz, read once on first use.
    private val juzPages by lazy { quran.pagesOfJuz(juzId) }
    private val surahsByPage by lazy { quran.surahsByPageInJuz(juzId) }
    private val ayahIds by lazy { quran.ayahIdRangeOfJuz(juzId) }
    private val rubs by lazy { quran.rubs(juzId) }
    private val ayahsById by lazy { ayahIds?.let { quran.ayahsInRange(it.first, it.last) }.orEmpty().associateBy { it.id } }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _surahRange.value = quran.surahRangeOfJuz(juzId)
        }
    }

    /**
     * Standard reactive pipeline for pages.
     */
    val pagesWithSurahs: StateFlow<List<PageWithSurahs>> = combine(
        study.revisionLogs(),
        MutableStateFlow(0)
    ) { logs, forecastDays ->
        val links = study.pageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        val state = MemoryEngine.computeCurrentState(logs, linkMap)
        val rValues = MemoryEngine.projectRetrievability(state, forecastDays)

        juzPages.map { page ->
            val idx = page - 1
            PageWithSurahs(
                stability = PageStability(
                    page = page,
                    score = rValues[idx],
                    lastRevised = state.lastRevisionTimestamps[idx].let { if (it == 0L) null else it }
                ),
                surahNames = surahsByPage[page].orEmpty().map { id -> SurahData.nameOf(id) }
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Combined timeline items for Structural Flow mode (Anchor Cards): the juz's eight hizb
     * quarters, each with the user's subjects that start inside it.
     */
    val timelineItems: StateFlow<List<AnchorCardData>> = (
        ayahIds?.let { dao.observeUserSubjectsBetween(it.first, it.last) } ?: flowOf(emptyList())
    ).let { subjectsFlow ->
        subjectsFlow.combine(flowOf(Unit)) { subjects, _ ->
            rubs.map { unit ->
                val unitAyahs = unit.startAyahId..unit.endAyahId
                val subjectsWithText = subjects.filter { it.startAyahId in unitAyahs }.map { subject ->
                    val verse = ayahsById[subject.startAyahId]
                    SubjectWithText(subject, verse?.text?.toCleanQuranicText() ?: "", verse?.ayah ?: 0)
                }

                AnchorCardData(
                    unit = unit,
                    subjects = subjectsWithText,
                    startAyahText = ayahsById[unit.startAyahId]?.text?.toCleanQuranicText() ?: "",
                    endAyahText = ayahsById[unit.endAyahId]?.text?.toCleanQuranicText() ?: "",
                    pages = (unit.startPage..unit.endPage).toList(),
                    ayahsInQuarter = unitAyahs.mapNotNull { ayahsById[it] }
                )
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPage = MutableStateFlow<PageSelection?>(null)
    val selectedPage: StateFlow<PageSelection?> = _selectedPage.asStateFlow()

    private val _peekData = MutableStateFlow<PagePeekData?>(null)
    val peekData: StateFlow<PagePeekData?> = _peekData.asStateFlow()

    private val _ayahPreview = MutableStateFlow<QuranAyah?>(null)
    val ayahPreview: StateFlow<QuranAyah?> = _ayahPreview.asStateFlow()

    fun onPageTap(page: Int, surahLabel: String) {
        if (isStructuralMode.value) {
            peekPage(page)
        } else {
            _selectedPage.value = PageSelection(page, surahLabel)
        }
    }

    fun showAyahPreview(startAyahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val verse = quran.ayahById(startAyahId)
            _ayahPreview.value = verse?.copy(text = verse.text.toCleanQuranicText())
        }
    }

    fun dismissAyahPreview() { _ayahPreview.value = null }

    private fun peekPage(pageNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val words = quran.pageWords(pageNumber)
                .map { it.toCleanQuranicText() }
                .filter { it.isNotBlank() }
            if (words.isNotEmpty()) {
                val startSnippet = words.take(10).joinToString(" ")
                val endSnippet = words.takeLast(10).joinToString(" ")

                _peekData.value = PagePeekData(pageNumber, startSnippet, endSnippet)
            }
        }
    }

    fun dismissPeek() { _peekData.value = null }
    fun dismissDialog() { _selectedPage.value = null }

    fun addSubject(text: String, startAyahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertUserSubject(
                UserSubjectEntity(
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
            study.addRevision(page, page, quality)
        }
    }

    // ── Sharing ───────────────────────────────────────────────

    fun exportSubjects(uri: android.net.Uri) {
        viewModelScope.launch {
            val res = sharingManager.exportSubjects(uri, juzId)
            if (res.isSuccess) _events.emit(SharingEvent.Success(UiText(R.string.subjects_exported)))
            else _events.emit(SharingEvent.Error(UiText(R.string.msg_export_error_detail, res.exceptionOrNull()?.message ?: "")))
        }
    }

    fun importSubjects(uri: android.net.Uri) {
        viewModelScope.launch {
            val res = sharingManager.importSubjects(uri)
            if (res.isSuccess) _events.emit(SharingEvent.Success(UiText(R.string.subjects_imported)))
            else _events.emit(SharingEvent.Error(UiText(R.string.msg_import_error_detail, res.exceptionOrNull()?.message ?: "")))
        }
    }
}

sealed class SharingEvent {
    data class Success(val message: UiText) : SharingEvent()
    data class Error(val message: UiText) : SharingEvent()
}

data class PagePeekData(
    val pageNumber: Int,
    val startSnippet: String,
    val endSnippet: String
)

data class AnchorCardData(
    val unit: QuranRub,
    val subjects: List<SubjectWithText>,
    val startAyahText: String,
    val endAyahText: String,
    val pages: List<Int>,
    val ayahsInQuarter: List<QuranAyah>
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
            AppDatabase.getInstance(context).studyDao(),
            StudyRepository.create(context),
            QuranRepository.getInstance(context),
            StructuralPrefsRepository(context),
            StructuralSharingManager(context),
            juzId
        ) as T
}
