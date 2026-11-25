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
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import com.example.mutqan.domain.VerseListDetail
import com.example.mutqan.domain.VerseListsRepository
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** How the found verses are listed: by surah, or lined up to compare what follows the words, or what comes before them. */
enum class ResultView { SURAH, AFTER, BEFORE }

/**
 * A found verse lined up for comparing: the searched words with the words after them (or before them), running into
 * the next (or previous) verse when the words end (or begin) this one.
 */
@Immutable
data class CompareRow(
    val hit: SearchHit,
    val span: MushafSpan,
    /** The searched words, drawn in the accent colour. */
    val matchedIds: Set<Int>,
    /** Words of the verse after (or before), drawn muted. */
    val otherIds: Set<Int>,
    /** True when the first compared word differs from the row above's: a new run of alike places starts here. */
    val runStart: Boolean
)

/** The lined-up verses of one search and view, so a list made for an earlier search is never shown for a newer one. */
@Immutable
data class CompareList(val query: String, val scope: SearchScope, val view: ResultView, val rows: List<CompareRow>)

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
    val searched: Boolean = false,
    /** True while the search index is still being built (the first search after the app starts). */
    val loading: Boolean = false
) {
    /** Whether the verses can be lined up to compare: at least two, and not so many that the eye cannot follow. */
    val comparable: Boolean get() = total in 2..MAX_COMPARE
}

/** The most verses lined up for comparing. */
const val MAX_COMPARE = 300

@Immutable
data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.All,
    /** Whether anything is marked memorised, for the "my hifz" choice. */
    val hasHifz: Boolean = false,
    val recent: List<String> = emptyList(),
    val view: ResultView = ResultView.SURAH,
    /** The verses ticked: while comparing, to save as a look-alike group; while picking for a list, to add to it. */
    val chosen: Set<Int> = emptySet()
)

/** The search index, built once a run from the Quran database (a fraction of a second), off the main thread. */
object VerseSearchIndex {
    @Volatile
    private var index: VerseIndex? = null

    fun get(quran: QuranRepository): VerseIndex =
        index ?: synchronized(this) { index ?: VerseIndex(quran.hafsOfAllAyahs()).also { index = it } }
}

/** The verse search: one box for words, references, surahs, pages and roots (see VerseSearch.kt). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class VerseSearchViewModel(
    private val appContext: Context,
    private val quran: QuranRepository,
    private val study: StudyRepository,
    private val prefs: UserPreferencesRepository,
    private val verseLists: VerseListsRepository
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
    private val view = MutableStateFlow(ResultView.SURAH)
    private val chosen = MutableStateFlow<Set<Int>>(emptySet())
    /** The list verses are being picked for (0: none, a plain search). */
    private val pickList = MutableStateFlow(0)
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

    private val choice = combine(view, chosen) { v, c -> v to c }

    val uiState: StateFlow<SearchUiState> = combine(query, scope, covered, prefs.searchRecent, choice) { q, s, c, recent, (v, picked) ->
        SearchUiState(q, s, hasHifz = c?.any { it } == true, recent = recent, view = v, chosen = picked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private val asked = combine(query.debounce(DEBOUNCE_MS), scope, covered) { q, s, c -> Triple(q, s, c) }
    private val ready = combine(index, places, morphology) { i, p, m -> Triple(i, p, m) }

    val results: StateFlow<SearchResults> = combine(asked, ready) { (q, s, c), (i, p, m) -> search(q, s, c, i, p, m) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    /** The found verses lined up for comparing, when a compare view is chosen and there are 2 to [MAX_COMPARE] of them. */
    val compare: StateFlow<CompareList?> = combine(results, view, scope) { r, v, s ->
        if (v == ResultView.SURAH || r.total !in 2..MAX_COMPARE) null else CompareList(r.query, s, v, lineUp(r, v))
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The list being picked for, with its passages to mark what it already holds; null in a plain search. */
    val picking: StateFlow<VerseListDetail?> = pickList
        .flatMapLatest { id -> if (id == 0) flowOf(null) else verseLists.list(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Picks verses for list [listId] (0 for a plain search). */
    fun pickFor(listId: Int) {
        pickList.value = listId
    }

    /** True while verses are picked for a group being edited: like those picked for a list, they stay ticked from one search to the next. */
    private var pickingForGroup = false

    /** Picks verses for the group being edited (the editor adds the ticked ones itself). */
    fun pickForGroup() {
        pickingForGroup = true
    }

    fun setQuery(text: String) {
        // Verses picked for a list or a group stay picked through several searches; a look-alike group is one search's.
        if (pickList.value == 0 && !pickingForGroup && text.trim() != query.value.trim()) chosen.value = emptySet()
        query.value = text
    }

    /** Adds the ticked verses to the list being picked for; [onDone] gets how many were new and the list's name. */
    fun addPicked(onDone: (added: Int, name: String) -> Unit) {
        val listId = pickList.value
        val ayahs = chosen.value
        if (listId == 0 || ayahs.isEmpty()) return
        viewModelScope.launch {
            val added = verseLists.addAll(listId, ayahs)
            chosen.value = emptySet()
            onDone(added, picking.value?.name.orEmpty())
        }
    }

    fun setView(value: ResultView) {
        view.value = value
    }

    /** Ticks [ayahId] for a group, or unticks it. */
    fun toggleChosen(ayahId: Int) {
        chosen.value = chosen.value.let { if (ayahId in it) it - ayahId else it + ayahId }
    }

    fun clearChosen() {
        chosen.value = emptySet()
    }

    /** Saves the ticked verses, in reading order, as a new look-alike group called [name] in the Vault. */
    fun saveGroup(name: String, onSaved: () -> Unit) {
        val ayahs = chosen.value.sorted()
        if (ayahs.size < 2 || name.isBlank()) return
        viewModelScope.launch {
            study.createGroup(name, ayahs)
            chosen.value = emptySet()
            onSaved()
        }
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

    /**
     * The verses of [results] with the words searched for and what follows them ([ResultView.AFTER]) or comes before
     * them ([ResultView.BEFORE]), ordered by those words so alike places sit together. When the words end the verse,
     * what follows is the start of the next one (and when they begin it, the end of the one before), after its marker.
     */
    private fun lineUp(results: SearchResults, view: ResultView): List<CompareRow> {
        val index = quran.ayahIndex
        val read = HashMap<Int, List<QuranWord>>()
        fun words(id: Int): List<QuranWord> = read.getOrPut(id) {
            if (id in index) quran.wordsOfAyah(index.surahOf(id), index.ayahOf(id)) else emptyList()
        }
        class Lined(val row: CompareRow, val key: String, val first: String)

        val lined = results.groups.flatMap { it.hits }.map { hit ->
            val all = words(hit.ayahId)
            val body = all.filter { !it.isEnd }
            val match = hit.matched.first()
            val phrase = body.filter { it.position in match }
            val compared: List<QuranWord>
            val other: List<QuranWord>
            val span: MushafSpan
            if (view == ResultView.AFTER) {
                val after = body.filter { it.position > match.last }
                compared = after.take(CONTEXT)
                other = if (after.isNotEmpty()) emptyList() else {
                    val next = words(hit.ayahId + 1).filter { !it.isEnd }.take(OTHER_VERSE)
                    if (next.isEmpty()) emptyList() else listOfNotNull(all.lastOrNull { it.isEnd }) + next
                }
                span = MushafSpan.of(phrase + compared + other) +
                    (if (after.size > CONTEXT || other.isNotEmpty()) MushafSpan.More else MushafSpan.Empty)
            } else {
                val before = body.filter { it.position < match.first }
                compared = before.takeLast(CONTEXT)
                other = if (before.isNotEmpty()) emptyList() else {
                    val previous = words(hit.ayahId - 1)
                    val tail = previous.filter { !it.isEnd }.takeLast(OTHER_VERSE)
                    if (tail.isEmpty()) emptyList() else tail + listOfNotNull(previous.lastOrNull { it.isEnd })
                }
                span = (if (before.size > CONTEXT || other.isNotEmpty()) MushafSpan.More else MushafSpan.Empty) +
                    MushafSpan.of(other + compared + phrase)
            }
            // Ordered by the compared words, nearest the searched words first.
            val sortWords = (compared.ifEmpty { other.filter { !it.isEnd } }).let { if (view == ResultView.AFTER) it else it.reversed() }
            val keyWords = sortWords.map { SearchText.normalize(it.hafs) }
            Lined(
                CompareRow(hit, span, phrase.mapTo(HashSet()) { it.id }, other.mapTo(HashSet()) { it.id }, runStart = false),
                keyWords.joinToString(" "),
                keyWords.firstOrNull().orEmpty()
            )
        }.sortedWith(compareBy<Lined> { it.key }.thenBy { it.row.hit.ayahId })

        return lined.mapIndexed { i, l -> l.row.copy(runStart = i == 0 || l.first != lined[i - 1].first) }
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
        val enough = SearchText.normalize(q).count { it in 'ء'..'ي' } >= VerseIndex.MIN_LETTERS
        val searched = index != null && enough
        val hits = if (!searched) emptyList() else index!!.find(q, accept).map { m ->
            SearchHit(m.ayahId, ayahs.surahOf(m.ayahId), ayahs.ayahOf(m.ayahId), ayahs.firstPageOf(m.ayahId), m.matched)
        }
        val groups = hits.groupBy { it.surah }.map { (surah, list) -> SurahHits(surah, list) }
        return SearchResults(q, goTo, roots, groups, hits.size, searched, loading = index == null && enough)
    }

    private companion object {
        /** Words shown after (or before) the searched words. */
        const val CONTEXT = 6
        /** Words shown of the next (or previous) verse when the searched words end (or begin) one. */
        const val OTHER_VERSE = 4
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
        UserPreferencesRepository(context.applicationContext),
        VerseListsRepository.create(context.applicationContext)
    ) as T
}
