package com.example.mutqan.ui.heatmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.MemoryEngine
import com.example.mutqan.domain.QuarterHealth
import com.example.mutqan.domain.QuarterMap
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PageStability
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.RecallOutcome
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
    private val sharingManager: StructuralSharingManager,
    val          juzId: Int
) : ViewModel() {

    private val _surahRange = MutableStateFlow<IntRange?>(null)
    val surahRange: StateFlow<IntRange?> = _surahRange.asStateFlow()

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
        study.coveredPages(),
        MutableStateFlow(0)
    ) { logs, covered, forecastDays ->
        val links = study.pageSimilarityLinks()
        val linkMap = links.groupBy({ it.pageA }, { it.pageB })

        val state = MemoryEngine.computeCurrentState(logs, linkMap, covered)
        val passageStates = PassageStates.compute(logs)
        val rValues = MemoryEngine.projectRetrievability(state, forecastDays)

        juzPages.map { page ->
            val idx = page - 1
            PageWithSurahs(
                stability = PageStability(
                    page = page,
                    score = rValues[idx],
                    lastRevised = state.lastRevisionTimestamps[idx].let { if (it == 0L) null else it }
                ),
                surahNames = surahsByPage[page].orEmpty().map { id -> SurahData.nameOf(id) },
                state = if (covered == null || covered[idx]) passageStates[idx] else PassageState.NOT_ASSESSED
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
                    SubjectWithText(subject, ayahsById[subject.startAyahId]?.ayah ?: 0)
                }

                AnchorCardData(
                    unit = unit,
                    subjects = subjectsWithText,
                    pages = (unit.startPage..unit.endPage).toList(),
                    ayahsInQuarter = unitAyahs.mapNotNull { ayahsById[it] }
                )
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The map: each of the juz's eight quarters with how well it is held, ready to draw. The cards, their subjects and
     * the page strengths come from the two flows above.
     */
    val quarters: StateFlow<List<QuarterCardData>> = combine(timelineItems, pagesWithSurahs) { cards, pages ->
        val byPage = pages.associateBy { it.stability.page }
        cards.map { card ->
            val inQuarter = card.pages.mapNotNull { byPage[it] }
            QuarterCardData(
                card = card,
                health = QuarterMap.health(card.unit) { page -> byPage[page]?.stability?.takeIf { it.isTracked }?.score },
                pages = inQuarter
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPage = MutableStateFlow<PageSelection?>(null)
    val selectedPage: StateFlow<PageSelection?> = _selectedPage.asStateFlow()

    private val _ayahPreview = MutableStateFlow<QuranAyah?>(null)
    val ayahPreview: StateFlow<QuranAyah?> = _ayahPreview.asStateFlow()

    fun onPageTap(page: Int, surahLabel: String, state: PassageState = PassageState.NOT_ASSESSED) {
        _selectedPage.value = PageSelection(page, surahLabel, state)
    }

    // ── Joint test: the end of one quarter against the start of the next ──────

    private val _joint = MutableStateFlow<JointTest?>(null)
    val joint: StateFlow<JointTest?> = _joint.asStateFlow()

    /** Opens the joint test for the end of [rub]; does nothing after the last quarter of the Quran. */
    fun startJoint(rub: QuranRub) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = QuarterMap.following(rub, rubs, if (juzId < 30) quran.rubs(juzId + 1) else emptyList()) ?: return@launch
            _joint.value = JointTest(rub, next)
        }
    }

    fun dismissJoint() { _joint.value = null }

    /** Records how the joint went (apart from page revisions: it never changes a page's strength) and closes the test. */
    fun finishJoint(outcome: RecallOutcome) {
        val test = _joint.value ?: return
        _joint.value = null
        viewModelScope.launch { study.addExerciseResult(ExerciseType.QUARTER_JOINT, test.from.endPage, test.to.startPage, outcome) }
    }

    fun showAyahPreview(startAyahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val verse = quran.ayahById(startAyahId)
            _ayahPreview.value = verse?.copy(text = verse.text.toCleanQuranicText())
        }
    }

    fun dismissAyahPreview() { _ayahPreview.value = null }

    fun dismissDialog() { _selectedPage.value = null }

    /** Saves a subject; [editing] is the one being changed, null for a new one. A verse keeps only one subject. */
    fun saveSubject(text: String, startAyahId: Int, editing: UserSubjectEntity?) {
        viewModelScope.launch(Dispatchers.IO) { dao.saveUserSubject(text, startAyahId, editing) }
    }

    fun deleteSubject(subject: UserSubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteUserSubject(subject)
        }
    }

    fun logPage(page: Int, outcome: RecallOutcome, tags: Set<MistakeTag>) {
        _selectedPage.value = null
        viewModelScope.launch {
            study.addRevision(page, page, outcome, tags)
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

/** A quarter on the map: its card (subjects and ayahs), how well it is held, and its pages with their strength. */
data class QuarterCardData(
    val card: AnchorCardData,
    val health: QuarterHealth,
    val pages: List<PageWithSurahs>
)

/** A joint to test: [from] ends, and the user recalls how [to] begins. */
data class JointTest(val from: QuranRub, val to: QuranRub)

data class AnchorCardData(
    val unit: QuranRub,
    val subjects: List<SubjectWithText>,
    val pages: List<Int>,
    val ayahsInQuarter: List<QuranAyah>
)

data class SubjectWithText(
    val entity: UserSubjectEntity,
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
            StructuralSharingManager(context),
            juzId
        ) as T
}
