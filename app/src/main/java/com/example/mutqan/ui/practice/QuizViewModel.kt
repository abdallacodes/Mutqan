package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.ChoiceQuestion
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.DailySchedule
import com.example.mutqan.domain.QuizRetries
import com.example.mutqan.domain.QuizRetry
import com.example.mutqan.ui.home.localEpochDay
import com.example.mutqan.domain.OrderQuestion
import com.example.mutqan.domain.PassageState
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
    val missedPages: List<Int> = emptyList(),
    /** The question on screen was missed on an earlier day and is asked again. */
    val retry: Boolean = false
) {
    /** A set of ten mixed questions (the daily set, a skill's, or the pages missed lately) rather than one kind in a row. */
    val isSet: Boolean get() = mode.isSet
}

/**
 * Runs a sitting of quiz questions: one kind in a row, or a set of ten mixed ones (the daily set, a skill's set, or one
 * on the pages missed lately). The pages and the questions come from a [QuizPicker]. Each answer is kept as an exercise
 * result so Today can point at pages that keep going wrong; a missed question comes back in a later day's set.
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

    private val prefs = UserPreferencesRepository(appContext)

    /** Questions missed on earlier days, asked again in the daily set after its first question. */
    private var retries = ArrayDeque<QuizRetry>()

    init {
        start()
    }

    private fun start() {
        _state.update { QuizUiState(mode = mode, hard = it.hard) }
        preset = if (mode == QuizMode.DAILY) DailyPreview.take() else null
        schedule = preset?.schedule ?: when {
            mode == QuizMode.DAILY -> DailySchedule.make(null, random)
            mode.isSet -> List(QuizMode.DAILY_SIZE) { mode.setTypes[it % mode.setTypes.size] }.shuffled(random)
            else -> emptyList()
        }
        viewModelScope.launch(Dispatchers.Default) {
            val only = if (mode == QuizMode.MISSED) {
                study.quizMissesSince(System.currentTimeMillis() - MISSED_WINDOW_MS).first().map { it.page }.take(MISSED_PAGES).toSet()
            } else null
            retries = if (mode == QuizMode.DAILY) ArrayDeque(QuizRetries.due(prefs.quizRetries.first(), localEpochDay())) else ArrayDeque()
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
        if (_state.value.orderTaps.size == q.shuffled.size) answer(q.isRight(_state.value.orderTaps))
    }

    fun clearOrder() {
        if (!_state.value.answered) _state.update { it.copy(orderTaps = emptyList()) }
    }

    private fun answer(correct: Boolean) {
        val q = _state.value.question ?: return
        val again = _state.value.retry
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
            if (q.subject.id > 0) {
                val today = localEpochDay()
                prefs.editQuizRetries { list ->
                    if (correct) QuizRetries.answered(list, q.type, q.subject.id)
                    else QuizRetries.missed(list, q.type, q.subject.id, today, again)
                }
            }
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
        // After the set's first question, the ones missed on earlier days.
        while (s.asked > 0 && retries.isNotEmpty()) {
            val retry = retries.removeFirst()
            val q = picker.buildFor(retry.type, retry.ayahId)
            if (q != null) {
                show(q, retry = true)
                return
            }
        }
        val pool = mode.setTypes
        val wanted: List<ExerciseType> = when {
            mode.type != null -> listOf(mode.type)
            else -> {
                val first = schedule.getOrNull(s.asked) ?: pool.random(random)
                listOf(first) + pool.filter { it != first }.shuffled(random)
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

    private fun show(q: QuizQuestion, retry: Boolean = false) {
        _state.update {
            it.copy(
                loading = false, noMaterial = false, question = q, stage = 0, pickedOption = null,
                pickedSurah = null, pickedAyah = null, orderTaps = emptyList(), answered = false, correct = false,
                retry = retry
            )
        }
    }

    private companion object {
        /** How far back the "missed lately" set looks, and the most pages it draws from. */
        const val MISSED_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        const val MISSED_PAGES = 20
    }
}

class QuizViewModelFactory(private val context: Context, private val mode: QuizMode) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        QuizViewModel(mode, StudyRepository.create(context), QuranRepository.getInstance(context), context.applicationContext) as T
}
