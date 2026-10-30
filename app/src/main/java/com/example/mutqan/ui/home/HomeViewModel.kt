package com.example.mutqan.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PlanOrder
import com.example.mutqan.domain.PlanItem
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.RevisionPlan
import com.example.mutqan.domain.RevisionPlanner
import com.example.mutqan.domain.SavedKind
import com.example.mutqan.domain.SavedPassage
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.TodayPlan
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.toCleanQuranicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone

/** An open weak passage suggested for today, with what is needed to show it. */
@Immutable
data class WeakSuggestion(
    val passage: SavedPassage,
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val snippet: String
)

@Immutable
data class HomeUiState(
    val isLoaded: Boolean = false,
    /** True once the user has said what they have memorised. */
    val hasCoverage: Boolean = false,
    val plan: RevisionPlan? = null,
    val today: TodayPlan? = null,
    val weak: List<WeakSuggestion> = emptyList()
)

class HomeViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository
) : ViewModel() {

    private val dismissed = MutableStateFlow<Set<Int>>(emptySet())

    /** The local calendar day, re-read every minute while the screen is visible so it rolls over at midnight. */
    private val epochDay = flow {
        while (true) {
            emit(localEpochDay())
            delay(CLOCK_TICK_MS)
        }
    }.distinctUntilChanged()

    private val extras = combine(epochDay, dismissed) { day, hidden -> day to hidden }

    val uiState: StateFlow<HomeUiState> = combine(
        study.plans(), study.revisionLogs(), study.coveredPages(), study.savedPassages(), extras
    ) { plans, logs, covered, saved, (day, hidden) ->
        val plan = plans.firstOrNull()
        val today = plan?.let {
            val states = if (it.order == PlanOrder.ROTATION) Array(PAGES) { PassageState.NOT_ASSESSED }
            else PassageStates.compute(logs)
            val lastRevised = RevisionPlanner.lastRevisedDays(logs, ::offsetAt)
            if (it.order == PlanOrder.BY_STRENGTH) {
                RevisionPlanner.strengthToday(it, covered, lastRevised, states, day)
            } else {
                val items = RevisionPlanner.buildItems(it.unit, covered, study.planGeometry())
                RevisionPlanner.todayPlan(it, items, lastRevised, states, day)
            }
        }
        val weak = saved
            .filter { it.kind == SavedKind.WEAK && !it.isResolved && it.id !in hidden }
            .sortedWith(compareByDescending<SavedPassage> { it.priority }.thenByDescending { it.createdAt })
            .mapNotNull(::describe)
            .take(MAX_SUGGESTIONS)
        HomeUiState(isLoaded = true, hasCoverage = covered != null, plan = plan, today = today, weak = weak)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun describe(p: SavedPassage): WeakSuggestion? {
        val first = quran.ayahById(p.startAyahId) ?: return null
        return WeakSuggestion(p, first.surah, first.ayah, first.firstPage, first.text.toCleanQuranicText().take(SNIPPET_CHARS))
    }

    /** Records that [item] was revised now, which moves it from "due" to "done". */
    fun log(item: PlanItem, outcome: RecallOutcome, tags: Set<MistakeTag>) {
        viewModelScope.launch { study.addRevision(item.startPage, item.endPage, outcome, tags) }
    }

    fun dismissSuggestion(id: Int) {
        dismissed.update { it + id }
    }

    private companion object {
        const val PAGES = 604
        const val MAX_SUGGESTIONS = 3
        const val SNIPPET_CHARS = 80
        const val CLOCK_TICK_MS = 60_000L

        fun offsetAt(time: Long): Int = TimeZone.getDefault().getOffset(time)

        fun localEpochDay(): Long {
            val now = System.currentTimeMillis()
            return Math.floorDiv(now + offsetAt(now), 86_400_000L)
        }
    }
}

class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(StudyRepository.create(context), QuranRepository.getInstance(context)) as T
}
