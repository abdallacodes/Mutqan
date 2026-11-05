package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.ChoiceQuestion
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.TopicLanguage
import com.example.mutqan.domain.TopicsLibrary
import com.example.mutqan.domain.OrderQuestion
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.QuizEngine
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.QuizQuestion
import com.example.mutqan.domain.QuizText
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.WhereQuestion
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.mutqan.domain.VerseClosingsLibrary

@Immutable
data class QuizUiState(
    val mode: QuizMode,
    val loading: Boolean = true,
    /** True when no question could be built, e.g. nothing memorised yet. */
    val noMaterial: Boolean = false,
    /** True when that is because no page fits the choice of pages made on the Practice list. */
    val scopeEmpty: Boolean = false,
    val hard: Boolean = false,
    val question: QuizQuestion? = null,
    /** Where-is-this: 0 = naming the surah, 1 = the surah was right and the verse number is open as an optional bonus. */
    val stage: Int = 0,
    val pickedOption: Int? = null,
    val pickedSurah: Int? = null,
    val pickedAyah: Int? = null,
    /** Order question: indices into the shuffled list, in the order tapped. */
    val orderTaps: List<Int> = emptyList(),
    val answered: Boolean = false,
    val correct: Boolean = false,
    /** Questions answered in this sitting, and how many were right. */
    val asked: Int = 0,
    val right: Int = 0,
    /** Daily set: the set is over. */
    val finished: Boolean = false,
    /** Pages answered wrongly in this sitting, for the summary. */
    val missedPages: List<Int> = emptyList()
) {
    val isDaily: Boolean get() = mode == QuizMode.DAILY
}

/**
 * Runs a sitting of quiz questions: one kind in a row, or a daily set of ten mixed ones. Pages are drawn
 * mostly from what is memorised, favouring pages that are weak or were missed recently. Each answer is
 * kept as an exercise result so Today can point at pages that keep going wrong.
 */
class QuizViewModel(
    private val mode: QuizMode,
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(QuizUiState(mode = mode))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var engine: QuizEngine? = null
    private var weights = IntArray(PAGES)
    private val recent = ArrayDeque<Int>()
    private val random = Random.Default
    private var schedule: List<ExerciseType> = emptyList()
    private var scopeEmpty = false

    init {
        start()
    }

    private fun start() {
        _state.update { QuizUiState(mode = mode, hard = it.hard) }
        recent.clear()
        schedule = if (mode == QuizMode.DAILY) {
            (ExerciseType.QUIZ + ExerciseType.QUIZ).shuffled(random).take(QuizMode.DAILY_SIZE)
        } else emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            val covered = study.coveredPages().first()
            val states = PassageStates.compute(study.revisionLogs().first())
            val misses = study.quizMissesSince(System.currentTimeMillis() - MISS_WINDOW_MS).first().map { it.page }.toSet()
            val scope = UserPreferencesRepository(appContext).practiceScope.first()
            val allowed = scope.pages(
                covered, states, study.selfRatedStrength().first(), study.revisionLogs().first()
            )
            scopeEmpty = !scope.isAll && allowed.isEmpty()
            weights = IntArray(PAGES) { i ->
                val memorised = (i + 1) in allowed
                if (!memorised) 0
                else BASE_WEIGHT + (if (states[i] == PassageState.NEEDS_ATTENTION) WEAK_BONUS else 0) + (if (i + 1 in misses) MISS_BONUS else 0)
            }
            val language = TopicLanguage(arabicUi = appContext.resources.configuration.locales[0].language == "ar")
            engine = QuizEngine(
                quran, SimilarLibrary.load(appContext), random, VerseClosingsLibrary.load(appContext),
                TopicsLibrary.load(appContext), language
            )
            loadNext()
        }
    }

    fun toggleHard() {
        _state.update { it.copy(hard = !it.hard) }
        viewModelScope.launch(Dispatchers.Default) { loadNext() }
    }

    fun restart() = start()

    fun next() {
        val s = _state.value
        if (s.isDaily && s.asked >= QuizMode.DAILY_SIZE) {
            _state.update { it.copy(finished = true) }
            return
        }
        viewModelScope.launch(Dispatchers.Default) { loadNext() }
    }

    // ── Answering ────────────────────────────────────────────────────────────

    fun chooseOption(index: Int) {
        val s = _state.value
        val q = s.question as? ChoiceQuestion ?: return
        if (s.answered) return
        _state.update { it.copy(pickedOption = index) }
        answer(index == q.correct)
    }

    fun chooseSurah(surah: Int) {
        val s = _state.value
        val q = s.question as? WhereQuestion ?: return
        if (s.answered || s.stage != 0) return
        // Naming the surah is the question; it is recorded at once. The verse number that follows is a bonus.
        _state.update { it.copy(pickedSurah = surah, stage = if (surah == q.subject.surah) 1 else 0) }
        answer(surah == q.subject.surah)
    }

    /** The optional bonus: the verse number, once the surah was named rightly. It changes neither the score nor the record. */
    fun chooseAyah(ayah: Int) {
        val s = _state.value
        if (!s.answered || s.stage != 1 || s.pickedAyah != null) return
        _state.update { it.copy(pickedAyah = ayah) }
    }

    fun tapOrder(index: Int) {
        val s = _state.value
        if (s.answered || index in s.orderTaps) return
        _state.update { it.copy(orderTaps = it.orderTaps + index) }
        val q = _state.value.question as? OrderQuestion ?: return
        if (_state.value.orderTaps.size == q.shuffled.size) answer(_state.value.orderTaps == q.correctOrder)
    }

    fun clearOrder() {
        if (!_state.value.answered) _state.update { it.copy(orderTaps = emptyList()) }
    }

    private fun answer(correct: Boolean) {
        val q = _state.value.question ?: return
        _state.update {
            it.copy(
                answered = true,
                correct = correct,
                asked = it.asked + 1,
                right = it.right + if (correct) 1 else 0,
                missedPages = if (correct) it.missedPages else (it.missedPages + q.subject.page).distinct()
            )
        }
        viewModelScope.launch {
            study.addExerciseResult(q.type, q.subject.page, q.subject.page, if (correct) RecallOutcome.INDEPENDENT else RecallOutcome.UNABLE)
        }
    }

    // ── Choosing the next question ───────────────────────────────────────────

    private fun loadNext() {
        val engine = engine ?: return
        val s = _state.value
        val wanted: List<ExerciseType> = when {
            mode.type != null -> listOf(mode.type)
            else -> {
                val first = schedule.getOrNull(s.asked) ?: ExerciseType.QUIZ.random(random)
                listOf(first) + ExerciseType.QUIZ.filter { it != first }.shuffled(random)
            }
        }
        for (type in wanted) {
            val q = tryBuild(engine, type, s.hard)
            if (q != null) {
                _state.update {
                    it.copy(
                        loading = false, noMaterial = false, question = q, stage = 0, pickedOption = null,
                        pickedSurah = null, pickedAyah = null, orderTaps = emptyList(), answered = false, correct = false
                    )
                }
                return
            }
        }
        _state.update { it.copy(loading = false, noMaterial = true, scopeEmpty = scopeEmpty, question = null) }
    }

    private fun tryBuild(engine: QuizEngine, type: ExerciseType, hard: Boolean): QuizQuestion? {
        repeat(TRIES) {
            val page = pickPage() ?: return null
            val ayahs = quran.ayahsOnPage(page)
            if (ayahs.isEmpty()) return@repeat
            val q = engine.build(type, ayahs.random(random).id, hard)
            if (q != null) {
                recent.addLast(page)
                while (recent.size > RECENT_PAGES) recent.removeFirst()
                return q
            }
        }
        return null
    }

    private fun pickPage(): Int? {
        val w = IntArray(PAGES) { i -> if (i + 1 in recent) 0 else weights[i] }
        val index = QuizText.weightedPick(w, random) ?: QuizText.weightedPick(weights, random)
        return index?.plus(1)
    }

    private companion object {
        const val PAGES = 604
        const val BASE_WEIGHT = 2
        const val WEAK_BONUS = 6
        const val MISS_BONUS = 4
        const val TRIES = 60
        const val RECENT_PAGES = 8
        const val MISS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}

class QuizViewModelFactory(private val context: Context, private val mode: QuizMode) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        QuizViewModel(mode, StudyRepository.create(context), QuranRepository.getInstance(context), context.applicationContext) as T
}
