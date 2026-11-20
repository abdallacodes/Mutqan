package com.example.mutqan.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PlanGeometry
import com.example.mutqan.domain.PlanOrder
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.domain.RevisionPlan
import com.example.mutqan.domain.RevisionPlanner
import com.example.mutqan.domain.SmartPlan
import kotlin.math.roundToInt
import com.example.mutqan.domain.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PlanUiState(
    val isLoaded: Boolean = false,
    /** True when editing a saved plan; false when creating the first one. */
    val isExisting: Boolean = false,
    val name: String = "",
    val unit: PlanUnit = PlanUnit.JUZ,
    val amount: Int = 1,
    val restDays: Set<Int> = emptySet(),
    val order: PlanOrder = PlanOrder.BY_STRENGTH,
    val paused: Boolean = false,
    /** Portions in the memorised coverage at the chosen unit; 0 when nothing is memorised yet. */
    val itemCount: Int = 0,
    /** Pages a day of the plan by strength (kept apart from the fixed plan's amount, so switching back and forth keeps both). */
    val pagesPerDay: Int = 5,
    /** Memorised pages in each strength tier right now. */
    val tierCounts: Map<PlanTier, Int> = emptyMap()
) {
    val byStrength: Boolean get() = order == PlanOrder.BY_STRENGTH

    val cycleDays: Int get() = RevisionPlanner.cycleDays(itemCount, amount)

    /** Memorised pages. */
    val hifzPages: Int get() = tierCounts.values.sum()

    private val weighted: Int get() = SmartPlan.weighted(tierCounts)

    /** Pages a day for every page every week: the golden goal, and the last step. */
    val weeklyPages: Int get() = SmartPlan.weeklyPages(weighted, restDays)

    /** The pages a day offered as steps for this hifz. */
    val steps: List<Int> get() = SmartPlan.steps(weeklyPages)

    /** A modest amount to start with: only a hint. */
    val goodStart: Int get() = SmartPlan.goodStart(hifzPages, weeklyPages)

    /** Days for the whole hifz to come around at the chosen pages a day (a strong page's interval). */
    val smartCycle: Int get() = SmartPlan.cycleDays(weighted, pagesPerDay, restDays).roundToInt()

    /** Days for a weak page to come around; 0 when there are no weak pages. */
    val weakEvery: Int get() =
        if ((tierCounts[PlanTier.WEAK] ?: 0) == 0) 0
        else SmartPlan.intervalDays(PlanTier.WEAK, SmartPlan.cycleDays(weighted, pagesPerDay, restDays)).roundToInt().coerceAtLeast(1)
}

/** Creates or edits the user's revision plan (one plan for now). */
class PlanViewModel(private val study: StudyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState

    private var existing: RevisionPlan? = null
    private var covered: BooleanArray? = null
    private var geometry: PlanGeometry? = null
    private var tierCounts: Map<PlanTier, Int> = emptyMap()

    init {
        viewModelScope.launch {
            existing = study.plans().first().firstOrNull()
            covered = study.coveredPages().first()
            geometry = study.planGeometry()
            tierCounts = RevisionPlanner.tierPages(covered, PassageStates.compute(study.revisionLogs().first()), study.selfRatedStrength().first())
                .mapValues { it.value.size }
            val plan = existing
            val fresh = PlanUiState(isLoaded = true, amount = 1, tierCounts = tierCounts)
            val start = fresh.goodStart
            _uiState.value = if (plan == null) fresh.copy(pagesPerDay = start)
            else {
                val byStrength = plan.order == PlanOrder.BY_STRENGTH
                PlanUiState(
                    isLoaded = true,
                    isExisting = true,
                    name = plan.name,
                    unit = if (byStrength) PlanUnit.JUZ else plan.unit,
                    amount = if (byStrength) 1 else plan.amountPerDay,
                    restDays = plan.restDays,
                    order = plan.order,
                    paused = plan.paused,
                    pagesPerDay = if (byStrength) plan.amountPerDay else start,
                    tierCounts = tierCounts
                )
            }
            recount()
        }
    }

    private fun recount() {
        val geo = geometry ?: return
        _uiState.update { it.copy(itemCount = RevisionPlanner.buildItems(it.unit, covered, geo).size) }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(MAX_NAME)) }

    fun setUnit(unit: PlanUnit) {
        _uiState.update { it.copy(unit = unit, amount = it.amount.coerceIn(1, maxAmount(unit))) }
        recount()
    }

    fun setAmount(amount: Int) = _uiState.update { it.copy(amount = amount.coerceIn(1, maxAmount(it.unit))) }

    fun toggleRestDay(day: Int) = _uiState.update {
        it.copy(restDays = if (day in it.restDays) it.restDays - day else it.restDays + day)
    }

    fun setOrder(order: PlanOrder) = _uiState.update { it.copy(order = order) }

    fun setByStrength(on: Boolean) = _uiState.update {
        it.copy(order = if (on) PlanOrder.BY_STRENGTH else if (it.byStrength) PlanOrder.ROTATION else it.order)
    }

    /** Sets the pages a day of the plan by strength. */
    fun setPagesPerDay(pages: Int) = _uiState.update { it.copy(pagesPerDay = pages.coerceIn(1, SmartPlan.MAX_PAGES)) }

    fun setPaused(paused: Boolean) = _uiState.update { it.copy(paused = paused) }

    /** Fills the plan with a ready-made amount, e.g. 5 pages a day. */
    fun applyTemplate(unit: PlanUnit, amount: Int) {
        _uiState.update { it.copy(unit = unit, amount = amount) }
        recount()
    }

    fun save(defaultName: String, onDone: () -> Unit) {
        val s = _uiState.value
        viewModelScope.launch {
            study.savePlan(
                RevisionPlan(
                    id = existing?.id ?: 0,
                    name = s.name.ifBlank { defaultName },
                    unit = if (s.byStrength) PlanUnit.PAGE else s.unit,
                    amountPerDay = if (s.byStrength) s.pagesPerDay else s.amount,
                    restDays = s.restDays,
                    order = s.order,
                    paused = s.paused,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = existing?.id ?: return onDone()
        viewModelScope.launch {
            study.deletePlan(id)
            onDone()
        }
    }

    companion object {
        private const val MAX_NAME = 40

        fun maxAmount(unit: PlanUnit): Int = when (unit) {
            PlanUnit.JUZ -> 10
            PlanUnit.PAGE -> 60
            PlanUnit.SURAH -> 20
        }
    }
}

class PlanViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlanViewModel(StudyRepository.create(context)) as T
}
