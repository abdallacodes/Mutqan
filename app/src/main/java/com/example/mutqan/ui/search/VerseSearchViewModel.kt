package com.example.mutqan.ui.search

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.GoTo
import com.example.mutqan.domain.Morphology
import com.example.mutqan.domain.MorphologyLibrary
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.RevisionPlanner
import com.example.mutqan.domain.SearchQuery
import com.example.mutqan.domain.SearchText
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.SurahName
import com.example.mutqan.domain.VerseIndex
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the verses are looked for. */
sealed interface SearchScope {
    data object All : SearchScope
    data object Hifz : SearchScope
    data class Juz(val juz: Set<Int>) : SearchScope
    data class Surah(val surah: Int) : SearchScope
}

/** A found verse: where it is, and which of its words (1-based positions) matched. */
@Immutable
data class SearchHit(val ayahId: Int, val surah: Int, val ayah: Int, val page: Int, val matched: List<IntRange>)

/** The verses found in one surah, in reading order. */
@Immutable
data class SurahHits(val surah: Int, val hits: List<SearchHit>)

/** A root whose letters were typed, offered beside the verses. */
@Immutable
data class RootOffer(val root: Int, val spaced: String, val words: Int, val verses: Int)

@Immutable
data class SearchResults(
    val query: String = "",
    val goTo: List<GoTo> = emptyList(),
    val roots: List<RootOffer> = emptyList(),
    val groups: List<SurahHits> = emptyList(),
    val total: Int = 0,
    /** True when the query had enough letters for the verses to be looked through. */
    val searched: Boolean = false
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.All,
    /** Whether anything is marked memorised, for the "my hifz" choice. */
    val hasHifz: Boolean = false,
    val recent: List<String> = emptyList()
)

/** The search index, built once a run from the Quran database (a fraction of a second), off the main thread. */
object VerseSearchIndex {
    @Volatile
    private var index: VerseIndex? = null

    fun get(quran: QuranRepository): VerseIndex =
        index ?: synchronized(this) { index ?: VerseIndex(quran.hafsOfAllAyahs()).also { index = it } }
}

/** The verse search: one box for words, references, surahs, pages and roots (see VerseSearch.kt). */
@OptIn(FlowPreview::class)
class VerseSearchViewModel(
    private val appContext: Context,
    private val quran: QuranRepository,
    study: StudyRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    /** What the search needs to know of the surahs and juz; read once. */
    private class Places(
        val surahs: List<SurahName>,
        val surahPage: IntArray,
        val firstAyahId: IntArray,
        val juzPage: IntArray
    )

    private val query = MutableStateFlow("")
    private val scope = MutableStateFlow<SearchScope>(SearchScope.All)
    private val places = MutableStateFlow<Places?>(null)
    private val index = MutableStateFlow<VerseIndex?>(null)
    private val morphology = MutableStateFlow<Morphology?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val surahs = quran.surahs()
            val names = surahs.map { s ->
                val info = SurahData.getById(s.id)
                SurahName(s.id, info?.nameArabic ?: s.nameArabic, info?.nameEnglish ?: s.nameSimple, s.versesCount)
            }
            val surahPage = IntArray(SURAHS + 1).also { a -> surahs.forEach { a[it.id] = it.firstPage } }
            val firstAyahId = IntArray(SURAHS + 2).also { a ->
                a[1] = 1
                for (s in surahs) if (s.id + 1 < a.size) a[s.id + 1] = a[s.id] + s.versesCount
            }
            val juzPage = IntArray(JUZ + 1).also { a -> quran.pagesByJuz().forEach { (juz, pages) -> if (juz in 1..JUZ) a[juz] = pages.firstOrNull() ?: 1 } }
            places.value = Places(names, surahPage, firstAyahId, juzPage)
        }
        // Built now, so the first search does not wait for it.
        viewModelScope.launch(Dispatchers.IO) { index.value = VerseSearchIndex.get(quran) }
        viewModelScope.launch { morphology.value = MorphologyLibrary.load(appContext) }
    }

    private val covered: StateFlow<BooleanArray?> = study.coveredPages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The level of each memorised page, for the dot by a verse's page number. */
    val tiers: StateFlow<Map<Int, PlanTier>> =
        combine(study.coveredPages(), study.revisionLogs(), study.selfRatedStrength()) { covered, logs, rated ->
            val byTier = RevisionPlanner.tierPages(covered, PassageStates.compute(logs), rated)
            buildMap { byTier.forEach { (tier, pages) -> pages.forEach { put(it, tier) } } }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val uiState: StateFlow<SearchUiState> = combine(query, scope, covered, prefs.searchRecent) { q, s, c, recent ->
        SearchUiState(q, s, hasHifz = c?.any { it } == true, recent = recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private val asked = combine(query.debounce(DEBOUNCE_MS), scope, covered) { q, s, c -> Triple(q, s, c) }
    private val ready = combine(index, places, morphology) { i, p, m -> Triple(i, p, m) }

    val results: StateFlow<SearchResults> = combine(asked, ready) { (q, s, c), (i, p, m) -> search(q, s, c, i, p, m) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(text: String) {
        query.value = text
    }

    /** Sets the box to [text] if it is still empty (a search started from elsewhere). */
    fun start(text: String) {
        if (query.value.isEmpty() && text.isNotBlank()) query.value = text
    }

    fun setScope(value: SearchScope) {
        scope.value = value
    }

    /** Adds [juz] to the juz searched, or takes it out; with none left, the whole Quran. */
    fun toggleJuz(juz: Int) {
        val now = (scope.value as? SearchScope.Juz)?.juz.orEmpty()
        val next = if (juz in now) now - juz else now + juz
        scope.value = if (next.isEmpty()) SearchScope.All else SearchScope.Juz(next)
    }

    /** Keeps the present search among the latest, once something was opened from it. */
    fun rememberQuery() {
        val q = query.value.trim()
        if (q.isNotEmpty()) viewModelScope.launch { prefs.addSearchRecent(q) }
    }

    fun clearRecent() {
        viewModelScope.launch { prefs.clearSearchRecent() }
    }

    /** Where [goTo] opens the Mushaf: the page, and the verse to light up there (null for none). */
    fun target(goTo: GoTo): Pair<Int, Int?> {
        val p = places.value
        return when (goTo) {
            is GoTo.Page -> goTo.page to null
            is GoTo.Surah -> (p?.surahPage?.getOrNull(goTo.surah)?.takeIf { it > 0 } ?: 1) to null
            is GoTo.Juz -> (p?.juzPage?.getOrNull(goTo.juz)?.takeIf { it > 0 } ?: 1) to null
            is GoTo.Verse -> {
                val id = (p?.firstAyahId?.getOrNull(goTo.surah) ?: 0) + goTo.ayah - 1
                val index = quran.ayahIndex
                if (id in index) index.firstPageOf(id) to id else 1 to null
            }
        }
    }

    private fun search(
        text: String,
        scope: SearchScope,
        covered: BooleanArray?,
        index: VerseIndex?,
        places: Places?,
        morphology: Morphology?
    ): SearchResults {
        val q = text.trim()
        if (q.isEmpty()) return SearchResults()
        val goTo = places?.let { SearchQuery.goTo(q, it.surahs) }.orEmpty()
        val roots = morphology?.takeIf { SearchText.digits(q).none(Char::isDigit) }?.let { m ->
            m.findRoots(q).take(MOST_ROOTS).map { r -> m.infoOfRoot(r).let { RootOffer(r, m.rootSpaced(r), it.words, it.verses) } }
        }.orEmpty()

        val ayahs = quran.ayahIndex
        val accept: (Int) -> Boolean = when (scope) {
            SearchScope.All -> { _ -> true }
            SearchScope.Hifz -> { id ->
                covered != null && (ayahs.firstPageOf(id)..ayahs.lastPageOf(id)).any { covered.getOrElse(it - 1) { false } }
            }
            is SearchScope.Juz -> { id -> ayahs.juzOf(id) in scope.juz }
            is SearchScope.Surah -> { id -> ayahs.surahOf(id) == scope.surah }
        }
        val searched = index != null && SearchText.normalize(q).count { it in 'ء'..'ي' } >= VerseIndex.MIN_LETTERS
        val hits = if (!searched) emptyList() else index!!.find(q, accept).map { m ->
            SearchHit(m.ayahId, ayahs.surahOf(m.ayahId), ayahs.ayahOf(m.ayahId), ayahs.firstPageOf(m.ayahId), m.matched)
        }
        val groups = hits.groupBy { it.surah }.map { (surah, list) -> SurahHits(surah, list) }
        return SearchResults(q, goTo, roots, groups, hits.size, searched)
    }

    private companion object {
        const val SURAHS = 114
        const val JUZ = 30
        const val MOST_ROOTS = 3
        const val DEBOUNCE_MS = 200L
    }
}

class VerseSearchViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VerseSearchViewModel(
        context.applicationContext,
        QuranRepository.getInstance(context),
        StudyRepository.create(context),
        UserPreferencesRepository(context.applicationContext)
    ) as T
}
