package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.PageBoundaries
import com.example.mutqan.domain.PageEdgeQuestion
import com.example.mutqan.domain.PageEdgeQuiz
import com.example.mutqan.domain.PageOrder
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PageQuizUiState(
    /** False: which verse starts the page; true: which ends it. */
    val end: Boolean,
    /** The page being asked about. */
    val page: Int = 1,
    val order: PageOrder = PageOrder.FORWARD,
    /** With choices to pick from, or none and the answer recalled and then revealed. */
    val withOptions: Boolean = true,
    val loading: Boolean = true,
    val question: PageEdgeQuestion? = null,
    val picked: Int? = null,
    /** Without choices: the answer has been shown. */
    val revealed: Boolean = false,
    /** With choices: one was picked. */
    val answered: Boolean = false,
    val correct: Boolean = false,
    val asked: Int = 0,
    val right: Int = 0
)

/**
 * The page-edge quiz: a page number, and which verse starts (or ends) it. It has no end; each question is followed by the
 * next page, a random page the Practice choice allows, or the same page again. With choices the app checks the pick;
 * without them the user recalls the verse, looks, and says how it went. Every answer is recorded for the page.
 */
class PageQuizViewModel(
    end: Boolean,
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val prefs: UserPreferencesRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PageQuizUiState(end = end))
    val state: StateFlow<PageQuizUiState> = _state.asStateFlow()

    private var quiz: PageEdgeQuiz? = null
    private var pool: List<Int> = emptyList()
    private val recent = ArrayDeque<Int>()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val covered = study.coveredPages().first()
            val logs = study.revisionLogs().first()
            val allowed = prefs.practiceScope.first().mask(covered, PassageStates.compute(logs), study.selfRatedStrength().first(), logs)
            // Random pages come from the Practice choice; when it allows none, from the whole Mushaf.
            pool = PageBoundaries.pool(allowed, null, ExerciseType.PAGE_EDGES).ifEmpty { PageBoundaries.pool(null, null, ExerciseType.PAGE_EDGES) }
            quiz = PageEdgeQuiz(quran, SimilarLibrary.load(appContext))
            load(_state.value.page)
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun setEnd(end: Boolean) {
        if (_state.value.end == end) return
        _state.update { it.copy(end = end) }
        load(_state.value.page)
    }

    fun setOrder(order: PageOrder) = _state.update { it.copy(order = order) }

    fun setWithOptions(on: Boolean) {
        if (_state.value.withOptions == on) return
        _state.update { it.copy(withOptions = on) }
        load(_state.value.page)
    }

    /** Asks about [page] (1..604) now. */
    fun goTo(page: Int) = load(page.coerceIn(1, PageEdgeQuiz.PAGES))

    /** Asks about the page [delta] pages from this one, round the Mushaf. */
    fun step(delta: Int) = load(Math.floorMod(_state.value.page - 1 + delta, PageEdgeQuiz.PAGES) + 1)

    // ── Answering ────────────────────────────────────────────────────────────

    fun pick(index: Int) {
        val s = _state.value
        val q = s.question ?: return
        if (s.loading || !s.withOptions || s.answered) return
        val correct = index == q.correct
        _state.update {
            it.copy(picked = index, answered = true, correct = correct, asked = it.asked + 1, right = it.right + if (correct) 1 else 0)
        }
        record(q, if (correct) RecallOutcome.INDEPENDENT else RecallOutcome.UNABLE)
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    /** Without choices: how recalling it went; the next question follows. */
    fun rate(outcome: RecallOutcome) {
        val s = _state.value
        val q = s.question ?: return
        if (s.loading || s.withOptions || !s.revealed) return
        _state.update { it.copy(asked = it.asked + 1, right = it.right + if (outcome == RecallOutcome.INDEPENDENT) 1 else 0) }
        record(q, outcome)
        next()
    }

    /** The next question, without recording this one. */
    fun next() {
        val s = _state.value
        if (s.loading) return
        val page = s.order.next(s.page, pool, recent)
        load(page)
    }

    fun retry() = load(_state.value.page)

    private fun record(q: PageEdgeQuestion, outcome: RecallOutcome) {
        val type = if (q.end) ExerciseType.QUIZ_PAGE_END else ExerciseType.QUIZ_PAGE_START
        viewModelScope.launch { study.addExerciseResult(type, q.page, q.page, outcome) }
    }

    /** Builds the question for [page]. The one on screen stays, but cannot be answered again, until the new one is ready. */
    private fun load(page: Int) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true) }
        recent.addLast(page)
        while (recent.size > RECENT_PAGES) recent.removeFirst()
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val engine = quiz ?: return@launch
            val q = try {
                engine.build(page, _state.value.end)
            } catch (e: Exception) {
                android.util.Log.w("PageQuiz", "Could not build the question for page $page", e)
                null
            }
            // A newer question was asked for while this one was built: it must not be overwritten by this one.
            ensureActive()
            _state.update {
                it.copy(page = page, loading = false, question = q, picked = null, revealed = false, answered = false, correct = false)
            }
        }
    }

    private companion object {
        const val RECENT_PAGES = 12
    }
}

class PageQuizViewModelFactory(private val context: Context, private val end: Boolean) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PageQuizViewModel(
            end,
            StudyRepository.create(context),
            QuranRepository.getInstance(context),
            UserPreferencesRepository(context.applicationContext),
            context.applicationContext
        ) as T
}
