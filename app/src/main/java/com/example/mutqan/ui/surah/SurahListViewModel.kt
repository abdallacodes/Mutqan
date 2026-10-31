package com.example.mutqan.ui.surah

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.SurahInfo
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.Morphology
import com.example.mutqan.domain.MorphologyLibrary
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How much of a surah the user has said they have memorised. */
enum class SurahMemorised { NONE, PARTLY, FULL }

@Immutable
data class SurahListItem(
    val info: SurahInfo,
    val verseCount: Int,
    val startJuz: Int,
    /** Mushaf page (1–604) where this Surah begins; 0 if not loaded yet. */
    val startPage: Int,
    val groupCount: Int,
    val memorised: SurahMemorised = SurahMemorised.NONE
)

/** One juz as a line in the index: where it starts and which surah it opens with. */
@Immutable
data class JuzListItem(val juz: Int, val startPage: Int, val firstSurah: Int)

class SurahListViewModel(
    study: StudyRepository,
    private val quran: QuranRepository,
    preferences: UserPreferencesRepository,
    private val appContext: Context
) : ViewModel() {

    /** Roots and lemmas, once loaded; the search field uses them to offer a root when the text is one. */
    private val _morphology = MutableStateFlow<Morphology?>(null)
    val morphology: StateFlow<Morphology?> = _morphology

    private val surahRanges = MutableStateFlow<Map<Int, IntRange>>(emptyMap())

    private val _juz = MutableStateFlow<List<JuzListItem>>(emptyList())
    val juz: StateFlow<List<JuzListItem>> = _juz

    init {
        viewModelScope.launch { _morphology.value = MorphologyLibrary.load(appContext) }
        viewModelScope.launch(Dispatchers.Default) {
            surahRanges.value = (1..114).mapNotNull { s ->
                val first = quran.ayah(s, 1)?.id
                val last = quran.surah(s)?.let { quran.ayah(s, it.versesCount)?.id }
                if (first != null && last != null) s to (first..last) else null
            }.toMap()
            _juz.value = (1..30).mapNotNull { j ->
                val page = quran.pagesOfJuz(j).firstOrNull()
                val surah = quran.surahRangeOfJuz(j)?.first
                if (page != null && surah != null) JuzListItem(j, page, surah) else null
            }
        }
    }

    /**
     * Merges the hardcoded Surah names with the Quran metadata, the user's group counts and which
     * surahs they have memorised. Reactive: it updates whenever a group is linked or coverage changes.
     */
    val surahs: StateFlow<List<SurahListItem>> = combine(study.surahMetaList(), study.coverage(), surahRanges) { metaList, ranges, bounds ->
        val metaById = metaList.associateBy { it.surahId }
        SurahData.ALL.map { info ->
            val meta = metaById[info.id]
            val range = bounds[info.id]
            val memorised = when {
                range == null -> SurahMemorised.NONE
                HifzCoverage.containsAll(ranges, range) -> SurahMemorised.FULL
                ranges.any { it.first <= range.last && it.last >= range.first } -> SurahMemorised.PARTLY
                else -> SurahMemorised.NONE
            }
            SurahListItem(
                info = info,
                verseCount = meta?.verseCount ?: 0,
                startJuz = meta?.startJuz ?: 0,
                startPage = meta?.startPage ?: 0,
                groupCount = meta?.groupCount ?: 0,
                memorised = memorised
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SurahData.ALL.map { SurahListItem(it, 0, 0, 0, 0) }
        )

    /** The Mushaf page the user last read, to offer "continue". */
    val lastPage: StateFlow<Int?> = preferences.lastMushafPage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class SurahListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SurahListViewModel(
            StudyRepository.create(context),
            QuranRepository.getInstance(context),
            UserPreferencesRepository(context.applicationContext),
            context.applicationContext
        ) as T
}
