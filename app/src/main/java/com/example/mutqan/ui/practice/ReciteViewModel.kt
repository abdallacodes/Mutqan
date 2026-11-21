package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.QuizText
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** One round: the verse to go on from, on [page], and the verses after it, shown one by one as they are revealed. */
@Immutable
data class ReciteRound(val start: AyahRef, val page: Int, val following: List<AyahRef>)

@Immutable
data class ReciteUiState(
    val loading: Boolean = true,
    val round: ReciteRound? = null,
    /** How many of the following verses are in view. */
    val revealed: Int = 0,
    /** 0 = every juz the choice of pages allows. */
    val juz: Int = 0,
    val noPages: Boolean = false,
    /** Rounds finished in this sitting. */
    val done: Int = 0
)

/**
 * "Continue from here": a verse from the pages the Practice choice allows (weak and recently missed pages more often),
 * recited on from memory, then checked verse by verse. Kept apart from the revision logs, like every exercise.
 */
class ReciteViewModel(
    private val appContext: Context,
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val random: Random = Random.Default
) : ViewModel() {

    private val _state = MutableStateFlow(ReciteUiState())
    val state: StateFlow<ReciteUiState> = _state.asStateFlow()

    private var weights = IntArray(PAGES)
    private var juzPages: Map<Int, List<Int>> = emptyMap()
    private val recent = ArrayDeque<Int>()

    init {
        viewModelScope.launch {
            weights = QuizPicker.pageWeights(appContext, study).first
            juzPages = study.planGeometry().juzPages
            next()
        }
    }

    fun changeJuz(delta: Int) {
        _state.update { it.copy(juz = (it.juz + delta).coerceIn(0, JUZ)) }
        viewModelScope.launch { next() }
    }

    fun reveal() = _state.update { s -> s.copy(revealed = (s.revealed + 1).coerceAtMost(s.round?.following?.size ?: 0)) }

    /** Records how the round went (the pages from the verse shown to the last one revealed) and moves on. */
    fun finish(outcome: RecallOutcome) {
        val s = _state.value
        val round = s.round ?: return
        viewModelScope.launch {
            val lastPage = withContext(Dispatchers.IO) {
                round.following.getOrNull(s.revealed - 1)?.let { quran.ayahById(it.id)?.lastPage } ?: round.page
            }
            study.addExerciseResult(ExerciseType.RECITE_ON, round.page, maxOf(lastPage, round.page), outcome)
            _state.update { it.copy(done = it.done + 1) }
            next()
        }
    }

    private suspend fun next() {
        val juz = _state.value.juz
        val inJuz = if (juz > 0) juzPages[juz]?.toSet().orEmpty() else null
        fun allowed(page: Int) = inJuz == null || page in inJuz
        val fresh = IntArray(PAGES) { i -> if (allowed(i + 1) && i + 1 !in recent) weights[i] else 0 }
        val any = IntArray(PAGES) { i -> if (allowed(i + 1)) weights[i] else 0 }
        val index = QuizText.weightedPick(fresh, random) ?: QuizText.weightedPick(any, random)
        if (index == null) {
            _state.update { it.copy(loading = false, round = null, revealed = 0, noPages = true) }
            return
        }
        val page = index + 1
        recent.addLast(page)
        while (recent.size > RECENT_PAGES) recent.removeFirst()
        val round = withContext(Dispatchers.IO) { build(page) }
        _state.update { it.copy(loading = false, round = round, revealed = 0, noPages = round == null) }
    }

    private fun build(page: Int): ReciteRound? {
        val onPage = quran.ayahsOnPage(page).filter { it.id < TOTAL_AYAHS }
        // A verse that begins on the page, so the page shown is the one it is read on.
        val start = onPage.filter { it.firstPage == page }.randomOrNull(random) ?: onPage.randomOrNull(random) ?: return null
        val following = quran.ayahsInRange(start.id + 1, minOf(start.id + FOLLOWING, TOTAL_AYAHS)).map { it.ref() }
        return ReciteRound(start.ref(), start.firstPage, following)
    }

    private fun QuranAyah.ref() = AyahRef(id, surah, ayah)

    private companion object {
        const val PAGES = 604
        const val JUZ = 30
        const val TOTAL_AYAHS = 6236
        /** The most verses a round can reveal. */
        const val FOLLOWING = 8
        const val RECENT_PAGES = 12
    }
}

class ReciteViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReciteViewModel(context.applicationContext, StudyRepository.create(context), QuranRepository.getInstance(context)) as T
}
