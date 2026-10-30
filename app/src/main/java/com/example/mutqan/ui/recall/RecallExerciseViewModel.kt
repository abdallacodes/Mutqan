package com.example.mutqan.ui.recall

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.MistakeTag
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

data class RecallUiState(
    val ayahs: List<QuranAyah> = emptyList(),
    val revealed: Boolean = false,
    /** True once the first words were shown, so the attempt was cued. */
    val cueShown: Boolean = false,
    val showCue: Boolean = false,
    val outcome: RecallOutcome = RecallOutcome.INDEPENDENT,
    val tags: Set<MistakeTag> = emptySet()
)

/** Hide, recite, reveal, then record what it showed. The text stays hidden until the user reveals it. */
class RecallExerciseViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val startPage: Int,
    private val endPage: Int
) : ViewModel() {

    private val _state = MutableStateFlow(RecallUiState())
    val state: StateFlow<RecallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ayahs = withContext(Dispatchers.IO) {
                (startPage..endPage).flatMap { quran.ayahsOnPage(it) }.distinctBy { it.id }.sortedBy { it.id }
            }
            _state.update { it.copy(ayahs = ayahs) }
        }
    }

    fun toggleCue() = _state.update { it.copy(showCue = !it.showCue, cueShown = it.cueShown || !it.showCue) }

    fun reveal() = _state.update {
        // A cued attempt starts at "with a hint"; the user can still change it.
        it.copy(revealed = true, outcome = if (it.cueShown) RecallOutcome.HINTED else it.outcome)
    }

    fun setOutcome(outcome: RecallOutcome) = _state.update { it.copy(outcome = outcome) }

    fun setTags(tags: Set<MistakeTag>) = _state.update { it.copy(tags = tags) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            study.addRevision(startPage, endPage, s.outcome, s.tags)
            onDone()
        }
    }
}

class RecallExerciseViewModelFactory(
    private val context: Context,
    private val startPage: Int,
    private val endPage: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val start = startPage.coerceIn(1, 604)
        return RecallExerciseViewModel(
            StudyRepository.create(context), QuranRepository.getInstance(context), start, endPage.coerceIn(start, 604)
        ) as T
    }
}
