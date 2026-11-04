package com.example.mutqan.ui.practice

import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.PassageStates

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.PageBoundaries
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AyahRef(val id: Int, val surah: Int, val ayah: Int)

/** One question: the page asked about, its first and last verse, and the verses just outside it. */
@Immutable
data class BoundaryRound(
    val page: Int,
    val first: AyahRef,
    val last: AyahRef,
    val previousLast: AyahRef?,
    val nextFirst: AyahRef?,
    /** For the exercises that start from a verse: the verse to show. */
    val prompt: AyahRef? = null
)

@Immutable
data class BoundaryUiState(
    val variant: ExerciseType = ExerciseType.PAGE_EDGES,
    val hasCoverage: Boolean = false,
    /** Draw only from the memorised pages (when there are any). */
    val memorisedOnly: Boolean = true,
    /** 0 = every juz. */
    val juz: Int = 0,
    val round: BoundaryRound? = null,
    val revealed: Boolean = false,
    val noPages: Boolean = false,
    /** Rounds finished in this sitting. */
    val done: Int = 0
)

/** Page-boundary exercises: know where each page begins and ends, and what lies on either side. */
class PageBoundariesViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val prefs: UserPreferencesRepository,
    /** True for the exercises that start from a verse; false for the ones that start from a page. */
    fromVerse: Boolean
) : ViewModel() {

    private val _state = MutableStateFlow(BoundaryUiState(variant = (if (fromVerse) ExerciseType.FROM_VERSE else ExerciseType.FROM_PAGE).first()))
    val state: StateFlow<BoundaryUiState> = _state.asStateFlow()

    /** The pages the Practice list's choice allows, as a yes or no for each; null when any page may come up. */
    private var allowed: BooleanArray? = null
    private var juzPages: Map<Int, List<Int>> = emptyMap()
    private val recent = ArrayDeque<Int>()

    init {
        viewModelScope.launch {
            val covered = study.coveredPages().first()
            val logs = study.revisionLogs().first()
            allowed = prefs.practiceScope.first().mask(covered, PassageStates.compute(logs), study.selfRatedStrength().first(), logs)
            juzPages = study.planGeometry().juzPages
            _state.update { it.copy(hasCoverage = allowed != null, memorisedOnly = allowed != null) }
            nextRound()
        }
    }

    fun setVariant(variant: ExerciseType) {
        _state.update { it.copy(variant = variant) }
        viewModelScope.launch { nextRound() }
    }

    fun setMemorisedOnly(on: Boolean) {
        _state.update { it.copy(memorisedOnly = on) }
        viewModelScope.launch { nextRound() }
    }

    fun changeJuz(delta: Int) {
        _state.update { it.copy(juz = (it.juz + delta).coerceIn(0, 30)) }
        viewModelScope.launch { nextRound() }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    /** Records how the round went and moves to the next page. */
    fun finish(outcome: RecallOutcome) {
        val s = _state.value
        val round = s.round ?: return
        viewModelScope.launch {
            study.addExerciseResult(s.variant, round.page, round.page, outcome)
            _state.update { it.copy(done = it.done + 1) }
            nextRound()
        }
    }

    private suspend fun nextRound() {
        val s = _state.value
        val pool = PageBoundaries.pool(
            covered = if (s.memorisedOnly) allowed else null,
            juzPages = if (s.juz > 0) juzPages[s.juz] else null,
            type = s.variant
        )
        val page = PageBoundaries.pick(pool, recent)
        if (page == null) {
            _state.update { it.copy(round = null, revealed = false, noPages = true) }
            return
        }
        recent.addLast(page)
        while (recent.size > RECENT_PAGES) recent.removeFirst()
        val round = withContext(Dispatchers.IO) { build(page, s.variant) }
        _state.update { it.copy(round = round, revealed = false, noPages = round == null) }
    }

    private fun build(page: Int, variant: ExerciseType): BoundaryRound? {
        val ayahs = quran.ayahsOnPage(page)
        val first = ayahs.firstOrNull()?.ref() ?: return null
        val last = ayahs.last().ref()
        return BoundaryRound(
            page = page,
            first = first,
            last = last,
            previousLast = if (page > 1) quran.ayahsOnPage(page - 1).lastOrNull()?.ref() else null,
            nextFirst = if (page < 604) quran.ayahsOnPage(page + 1).firstOrNull()?.ref() else null,
            prompt = PageBoundaries.promptVerse(ayahs.map { it.id }, variant)?.let { id -> ayahs.first { it.id == id }.ref() }
        )
    }

    private fun QuranAyah.ref() = AyahRef(id, surah, ayah)

    private companion object {
        const val RECENT_PAGES = 12
    }
}

class PageBoundariesViewModelFactory(private val context: Context, private val fromVerse: Boolean) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PageBoundariesViewModel(
            StudyRepository.create(context),
            QuranRepository.getInstance(context),
            UserPreferencesRepository(context.applicationContext),
            fromVerse
        ) as T
}
