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
    val order: PlanOrder = PlanOrder.ROTATION,
    val paused: Boolean = false,
    /** Portions in the memorised coverage at the chosen unit; 0 when nothing is memorised yet. */
    val itemCount: Int = 0,
    val strongDays: Int = RevisionPlan.DEFAULT_STRONG_DAYS,
    val fairDays: Int = RevisionPlan.DEFAULT_FAIR_DAYS,
    val weakDays: Int = RevisionPlan.DEFAULT_WEAK_DAYS,
    /** Memorised pages in each strength tier right now. */
    val tierCounts: Map<PlanTier, Int> = emptyMap()
) {
    val byStrength: Boolean get() = order == PlanOrder.BY_STRENGTH

    fun daysOf(tier: PlanTier): Int = when (tier) {
        PlanTier.STRONG -> strongDays
        PlanTier.FAIR -> fairDays
        PlanTier.WEAK -> weakDays
    }

    /** Pages a day a tier asks for. */
    fun dailyOf(tier: PlanTier): Int = RevisionPlanner.tierQuota(tierCounts[tier] ?: 0, daysOf(tier))

    val dailyTotal: Int get() = PlanTier.entries.sumOf(::dailyOf)

    val cycleDays: Int get() = RevisionPlanner.cycleDays(itemCount, amount)
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
            tierCounts = RevisionPlanner.tierPages(covered, PassageStates.compute(study.revisionLogs().first()))
                .mapValues { it.value.size }
            val plan = existing
            _uiState.value = if (plan == null) PlanUiState(isLoaded = true, amount = 1, tierCounts = tierCounts)
            else PlanUiState(
                isLoaded = true,
                isExisting = true,
                name = plan.name,
                unit = plan.unit,
                amount = plan.amountPerDay,
                restDays = plan.restDays,
                order = plan.order,
                paused = plan.paused,
                strongDays = plan.strongDays,
                fairDays = plan.fairDays,
                weakDays = plan.weakDays,
                tierCounts = tierCounts
            )
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

    /** Moves a tier's cycle by [delta] days, keeping weak <= fair <= strong. */
    fun changeTierDays(tier: PlanTier, delta: Int) = _uiState.update {
        when (tier) {
            PlanTier.WEAK -> it.copy(weakDays = (it.weakDays + delta).coerceIn(MIN_WEAK, it.fairDays))
            PlanTier.FAIR -> it.copy(fairDays = (it.fairDays + delta).coerceIn(it.weakDays, it.strongDays))
            PlanTier.STRONG -> it.copy(strongDays = (it.strongDays + delta).coerceIn(it.fairDays, MAX_STRONG))
        }
    }

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
                    unit = s.unit,
                    amountPerDay = s.amount,
                    restDays = s.restDays,
                    order = s.order,
                    paused = s.paused,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    strongDays = s.strongDays,
                    fairDays = s.fairDays,
                    weakDays = s.weakDays
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
        private const val MIN_WEAK = 2
        private const val MAX_STRONG = 180

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
