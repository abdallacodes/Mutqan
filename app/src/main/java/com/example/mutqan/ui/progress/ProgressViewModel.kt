package com.example.mutqan.ui.progress

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.RevisionPlanner
import com.example.mutqan.domain.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.util.TimeZone

/** A juz with pages whose recall needs attention, and the first of those pages. */
@Immutable
data class AttentionJuz(val juz: Int, val pages: Int, val firstPage: Int)

/** One juz on the map: how many of its pages are memorised, and how many of those are in each recall state. */
@Immutable
data class JuzMemory(val juz: Int, val covered: Int, val total: Int, val byState: Map<PassageState, Int>) {
    /** The share of the juz that is memorised, 0 to 1. */
    val share: Float get() = if (total == 0) 0f else covered.toFloat() / total
}

@Immutable
data class ProgressUiState(
    val isLoaded: Boolean = false,
    val coveredPages: Int = 0,
    /** Memorised pages per recall-evidence state; they add up to [coveredPages]. */
    val stateCounts: Map<PassageState, Int> = emptyMap(),
    /** The last [WINDOW_DAYS] days, oldest first: true when something was revised that day. */
    val revisedDays: List<Boolean> = emptyList(),
    val attention: List<AttentionJuz> = emptyList(),
    /** The thirty juz in order, for the map strip. */
    val juzMap: List<JuzMemory> = emptyList()
) {
    val daysRevised: Int get() = revisedDays.count { it }
}

const val WINDOW_DAYS = 28
private const val MAX_ATTENTION = 5
private const val TOTAL_PAGES = 604

/** Coverage, what the recall history shows, consistency and the places worth a look: all from what was recorded. */
class ProgressViewModel(private val study: StudyRepository) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(study.revisionLogs(), study.coveredPages()) { logs, covered ->
        val geometry = study.planGeometry()
        val states = PassageStates.compute(logs)
        val inCoverage = (1..TOTAL_PAGES).filter { covered?.get(it - 1) == true }

        val counts = PassageState.entries.associateWith { 0 }.toMutableMap()
        inCoverage.forEach { counts[states[it - 1]] = counts.getValue(states[it - 1]) + 1 }

        val today = Math.floorDiv(System.currentTimeMillis() + TimeZone.getDefault().getOffset(System.currentTimeMillis()), 86_400_000L)
        val revised = logs.map { RevisionPlanner.logDay(it, ::offsetAt) }.toSet()
        val days = (WINDOW_DAYS - 1 downTo 0).map { (today - it) in revised }

        val attention = geometry.juzPages.mapNotNull { (juz, pages) ->
            val needing = pages.filter { it in inCoverage && states[it - 1] == PassageState.NEEDS_ATTENTION }
            if (needing.isEmpty()) null else AttentionJuz(juz, needing.size, needing.min())
        }.sortedWith(compareByDescending<AttentionJuz> { it.pages }.thenBy { it.juz }).take(MAX_ATTENTION)

        val juzMap = geometry.juzPages.entries.sortedBy { it.key }.map { (juz, pages) ->
            val memorised = pages.filter { it in inCoverage }
            JuzMemory(juz, memorised.size, pages.size, memorised.groupingBy { states[it - 1] }.eachCount())
        }

        ProgressUiState(true, inCoverage.size, counts, days, attention, juzMap)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    private fun offsetAt(time: Long): Int = TimeZone.getDefault().getOffset(time)
}

class ProgressViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgressViewModel(StudyRepository.create(context)) as T
}
