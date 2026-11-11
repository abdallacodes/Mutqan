package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.ChoiceQuestion
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.FocusPages
import com.example.mutqan.domain.OrderQuestion
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.QuizQuestion
import com.example.mutqan.domain.RecallOutcome
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
    /** A set of ten mixed questions (the daily set or a focused one) rather than one kind in a row. */
    val isSet: Boolean get() = mode.isSet
}

/**
 * Runs a sitting of quiz questions: one kind in a row, or a set of ten mixed ones (the daily set, or a focused one on
 * the weakest pages). The pages and the questions come from a [QuizPicker]. Each answer is kept as an exercise result so
 * Today can point at pages that keep going wrong.
 */
class QuizViewModel(
    private val mode: QuizMode,
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(QuizUiState(mode = mode))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var picker: QuizPicker? = null
    private val random = Random.Default
    private var schedule: List<ExerciseType> = emptyList()

    /** The first question of the daily set, when the Practice screen already built it for its card. */
    private var preset: DailyPreview.Held? = null

    init {
        start()
    }

    private fun start() {
        _state.update { QuizUiState(mode = mode, hard = it.hard) }
        preset = if (mode == QuizMode.DAILY) DailyPreview.take() else null
        schedule = preset?.schedule
            ?: if (mode.isSet) (ExerciseType.QUIZ + ExerciseType.QUIZ).shuffled(random).take(QuizMode.DAILY_SIZE) else emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            val only = if (mode == QuizMode.FOCUS) {
                FocusPages.pick(study.coveredPages().first(), PassageStates.compute(study.revisionLogs().first()))?.pages ?: emptySet()
            } else null
            val made = QuizPicker.create(appContext, study, quran, only, random)
            preset?.let { made.markAsked(it.first.subject.page) }
            picker = made
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
        if (s.isSet && s.asked >= QuizMode.DAILY_SIZE) {
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
        val picker = picker ?: return
        val s = _state.value
        preset?.takeIf { s.asked == 0 }?.let {
            preset = null
            show(it.first)
            return
        }
        val wanted: List<ExerciseType> = when {
            mode.type != null -> listOf(mode.type)
            else -> {
                val first = schedule.getOrNull(s.asked) ?: ExerciseType.QUIZ.random(random)
                listOf(first) + ExerciseType.QUIZ.filter { it != first }.shuffled(random)
            }
        }
        for (type in wanted) {
            val q = picker.build(type, s.hard)
            if (q != null) {
                show(q)
                return
            }
        }
        _state.update { it.copy(loading = false, noMaterial = true, scopeEmpty = picker.scopeEmpty, question = null) }
    }

    private fun show(q: QuizQuestion) {
        _state.update {
            it.copy(
                loading = false, noMaterial = false, question = q, stage = 0, pickedOption = null,
                pickedSurah = null, pickedAyah = null, orderTaps = emptyList(), answered = false, correct = false
            )
        }
    }
}

class QuizViewModelFactory(private val context: Context, private val mode: QuizMode) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        QuizViewModel(mode, StudyRepository.create(context), QuranRepository.getInstance(context), context.applicationContext) as T
}
