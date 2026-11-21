package com.example.mutqan.ui.home

import com.example.mutqan.data.ReminderSettings
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.HabitSnapshot
import com.example.mutqan.domain.HabitSnapshots
import com.example.mutqan.domain.Milestone
import com.example.mutqan.domain.Milestones
import com.example.mutqan.domain.PlanGeometry
import com.example.mutqan.domain.TodayPlans
import kotlinx.coroutines.flow.first
import com.example.mutqan.data.local.dao.PageMiss
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PlanOrder
import com.example.mutqan.domain.PlanItem
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.RevisionPlan
import com.example.mutqan.domain.RevisionPlanner
import com.example.mutqan.domain.AdviceMemory
import com.example.mutqan.domain.PlanAdvice
import com.example.mutqan.domain.PlanAdvisor
import kotlin.math.roundToInt
import com.example.mutqan.domain.SmartPlan
import com.example.mutqan.domain.WeakRotation
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    val ayahId: Int
)

/**
 * A suggestion to change the plan's pages a day, with how often the whole hifz comes around now ([cycleNow] days) and
 * would with the suggested amount ([cycleThen]).
 */
@Immutable
data class AdviceCard(val advice: PlanAdvice, val cycleNow: Int, val cycleThen: Int)

/** A page where practice questions keep going wrong. */
@Immutable
data class MissedPage(val page: Int, val count: Int)

/** What the habit needs besides the plan: practice answers, milestones shown, and the reminder's state. */
private data class HabitInputs(
    val answerTimes: List<Long>,
    val seen: Pair<Boolean, Set<String>>,
    val celebratedDay: Long,
    val reminder: ReminderSettings,
    val offerClosed: Boolean
)

private data class Extras(
    val day: Long,
    val hidden: Set<Int>,
    val lastPage: Int?,
    val misses: List<PageMiss>,
    val selfRated: Map<Int, PlanTier>,
    /** How many times the user asked for other weak passages today. */
    val others: Int
)

@Immutable
data class HomeUiState(
    val isLoaded: Boolean = false,
    /** True once the user has said what they have memorised. */
    val hasCoverage: Boolean = false,
    val plan: RevisionPlan? = null,
    val today: TodayPlan? = null,
    /** A few of the open weak passages, not all: [WEAK_SHOWN] at most, other ones each day. */
    val weak: List<WeakSuggestion> = emptyList(),
    /** How many open weak passages there are in all, to offer the full list when it is longer than what is shown. */
    val weakTotal: Int = 0,
    /** Pages missed at least twice in practice this week and not revised since. */
    val missed: List<MissedPage> = emptyList(),
    /** The Mushaf page the user last read, to offer "continue". */
    val lastPage: Int? = null,
    /** A suggestion to change the pages a day of a plan by strength, now and then (see PlanAdvisor). */
    val advice: AdviceCard? = null,
    /** Today's ward, the run of days and the week strip; null until something is memorised. */
    val habit: HabitSnapshot? = null,
    /** A milestone reached and not shown yet, to show now. */
    val milestone: Milestone? = null,
    /** Today's ward is done and its moment has not been shown yet. */
    val celebrateWard: Boolean = false,
    val reminder: ReminderSettings = ReminderSettings(false, ReminderSettings.DEFAULT_MINUTES),
    /** Offer the daily reminder on the ward card: it is off and the offer was never answered. */
    val offerReminder: Boolean = false
)

class HomeViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val dismissed = MutableStateFlow<Set<Int>>(emptySet())

    /** Counts the "show others" taps; the window's start is also moved by the day, so each day begins on other ones. */
    private val others = MutableStateFlow(0)

    /** The local calendar day, re-read every minute while the screen is visible so it rolls over at midnight. */
    private val epochDay = flow {
        while (true) {
            emit(localEpochDay())
            delay(CLOCK_TICK_MS)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val misses = epochDay.flatMapLatest { study.quizMissesSince(System.currentTimeMillis() - MISS_WINDOW_MS) }

    /** What the user did to the weak list on this screen: the ones put away, and how often other ones were asked for. */
    private val weakView = combine(dismissed, others) { hidden, asked -> hidden to asked }

    private val extras = combine(epochDay, weakView, preferences.lastMushafPage, misses, study.selfRatedStrength()) { day, view, last, missed, rated ->
        Extras(day, view.first, last, missed, rated, view.second)
    }

    private val habitInputs = combine(
        study.exerciseTimes(), preferences.milestonesSeen, preferences.wardCelebratedDay, preferences.reminder, preferences.reminderOfferClosed
    ) { times, seen, celebrated, reminder, offerClosed -> HabitInputs(times, seen, celebrated, reminder, offerClosed) }

    private val extrasAndAdvice = combine(extras, preferences.planAdviceMemory, habitInputs) { extra, memory, habit -> Triple(extra, memory, habit) }

    /** Juz and surah page spans, read once. */
    private var geometryCache: PlanGeometry? = null

    private suspend fun geometry(): PlanGeometry = geometryCache ?: study.planGeometry().also { geometryCache = it }

    val uiState: StateFlow<HomeUiState> = combine(
        study.plans(), study.revisionLogs(), study.coveredPages(), study.savedPassages(), extrasAndAdvice
    ) { plans, logs, covered, saved, (extra, memory, habitIn) ->
        val (day, hidden, lastPage) = extra
        val plan = plans.firstOrNull()
        val lastRevised = RevisionPlanner.lastRevisedDays(logs, ::offsetAt)
        val recall = PassageStates.compute(logs)
        val states = if (plan == null || plan.order == PlanOrder.ROTATION) Array(PAGES) { PassageState.NOT_ASSESSED } else recall
        val today = plan?.let { TodayPlans.of(it, logs, covered, recall, extra.selfRated, day, ::offsetAt) { geometry() } }
        val habit = covered?.let {
            val strong = RevisionPlanner.tierPages(it, recall, extra.selfRated)[PlanTier.STRONG].orEmpty()
            HabitSnapshots.of(plan, today, logs, it, strong, geometry().juzPages, habitIn.answerTimes, day, ::offsetAt)
        }
        // Until the ones reached before this existed are set aside, none is shown (see init).
        val (baselineDone, seen) = habitIn.seen
        val milestone = if (habit != null && baselineDone) Milestones.next(habit.reached, seen) else null
        val advice = if (plan != null && today != null && plan.order == PlanOrder.BY_STRENGTH) {
            advise(plan, today, logs, covered, states, extra.selfRated, memory, day)
        } else null
        val openWeak = saved.filter { it.kind == SavedKind.WEAK && !it.isResolved }
        val weak = WeakRotation.pick(
            items = openWeak
                .filter { it.id !in hidden }
                .sortedWith(compareByDescending<SavedPassage> { it.priority }.thenByDescending { it.createdAt }),
            day = day,
            others = extra.others,
            size = WEAK_SHOWN
        ).mapNotNull(::describe)
        val missed = extra.misses
            .filter { it.count >= MISS_MIN && lastRevised.getOrElse(it.page - 1) { 0L } < Math.floorDiv(it.lastTime + offsetAt(it.lastTime), 86_400_000L) }
            .take(MAX_SUGGESTIONS)
            .map { MissedPage(it.page, it.count) }
        HomeUiState(
            isLoaded = true, hasCoverage = covered != null, plan = plan, today = today, weak = weak, weakTotal = openWeak.size,
            missed = missed, lastPage = lastPage, advice = advice,
            habit = habit,
            milestone = milestone,
            celebrateWard = habit?.ward?.complete == true && habitIn.celebratedDay != day,
            reminder = habitIn.reminder,
            offerReminder = !habitIn.reminder.on && !habitIn.offerClosed
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Milestones reached before they existed (a long run, passes done long ago) are set aside once, without a moment.
        viewModelScope.launch {
            if (preferences.milestonesSeen.first().first) return@launch
            val habit = uiState.first { it.isLoaded && (it.habit != null || !it.hasCoverage) }.habit
            preferences.addMilestonesSeen(habit?.reached.orEmpty().map { it.key }, baseline = true)
        }
    }

    /** A verse from what the user memorised, other each day: the reward shown when the day's ward is done. */
    val verseOfDay: StateFlow<Int?> = combine(epochDay, study.coverage()) { day, ranges ->
        val total = ranges.sumOf { it.last - it.first + 1 }
        if (total <= 0) return@combine null
        var index = Math.floorMod(day * 2654435761L, total.toLong()).toInt()
        for (range in ranges) {
            val size = range.last - range.first + 1
            if (index < size) return@combine range.first + index
            index -= size
        }
        null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** [milestone] was shown: it (and, for a run of days, the smaller ones of the same run) does not come again. */
    fun milestoneShown(milestone: Milestone) {
        viewModelScope.launch { preferences.addMilestonesSeen(Milestones.keysShown(milestone)) }
    }

    /** Today's finished ward had its moment. */
    fun wardCelebrated() {
        viewModelScope.launch { preferences.setWardCelebrated(localEpochDay()) }
    }

    fun closeReminderOffer() {
        viewModelScope.launch { preferences.closeReminderOffer() }
    }

    /** Whether to suggest a higher or lower number of pages a day today (see PlanAdvisor). */
    private fun advise(
        plan: RevisionPlan,
        today: TodayPlan,
        logs: List<com.example.mutqan.domain.RevisionLog>,
        covered: BooleanArray?,
        states: Array<PassageState>,
        selfRated: Map<Int, PlanTier>,
        memory: AdviceMemory,
        day: Long
    ): AdviceCard? {
        if (covered == null) return null
        // Before any change was recorded, the amount counts from the day the plan was made.
        val known = if (memory.amountSince > 0L) memory
        else memory.copy(amountSince = Math.floorDiv(plan.createdAt + offsetAt(plan.createdAt), 86_400_000L))
        val tiers = RevisionPlanner.tierPages(covered, states, selfRated)
        val weakNow = tiers.getValue(PlanTier.WEAK).size
        val before = logs.filter { RevisionPlanner.logDay(it, ::offsetAt) <= day - PlanAdvisor.WINDOW }
        val weakBefore = RevisionPlanner.tierPages(covered, PassageStates.compute(before), selfRated).getValue(PlanTier.WEAK).size
        val weighted = SmartPlan.weighted(tiers.mapValues { it.value.size })
        val pagesByDay = PlanAdvisor.pagesByDay(logs, covered, day - 2L * PlanAdvisor.WINDOW - 7, ::offsetAt)
        val advice = PlanAdvisor.advise(
            amount = plan.amountPerDay,
            restDays = plan.restDays,
            paused = plan.paused,
            today = day,
            memory = known,
            pagesOn = { pagesByDay[it] ?: 0 },
            weakNow = weakNow,
            weakBefore = weakBefore,
            weekly = SmartPlan.weeklyPages(weighted, plan.restDays)
        ) ?: return null
        return AdviceCard(advice, today.cycleDays, SmartPlan.cycleDays(weighted, advice.to, plan.restDays).roundToInt())
    }

    /** Takes a suggestion: the plan's pages a day become the suggested ones. */
    fun takeAdvice(card: AdviceCard) {
        val plan = uiState.value.plan ?: return
        val day = localEpochDay()
        viewModelScope.launch {
            study.savePlan(plan.copy(amountPerDay = card.advice.to))
            preferences.setPlanAmountSet(day, triedFrom = if (card.advice.kind == PlanAdvice.Kind.MORE) card.advice.from else 0)
            preferences.setPlanAdviceAnswered(day)
        }
    }

    /** Puts a suggestion off: none for a while, and no more offers to go back from an amount being tried. */
    fun notNowAdvice(card: AdviceCard) {
        viewModelScope.launch {
            preferences.setPlanAdviceAnswered(localEpochDay(), keepTrying = card.advice.kind != PlanAdvice.Kind.BACK)
        }
    }

    private fun describe(p: SavedPassage): WeakSuggestion? {
        val first = quran.ayahById(p.startAyahId) ?: return null
        return WeakSuggestion(p, first.surah, first.ayah, first.firstPage, first.id)
    }

    /** Records that [item] was revised now, which moves it from "due" to "done". */
    fun log(item: PlanItem, outcome: RecallOutcome, tags: Set<MistakeTag>) {
        viewModelScope.launch { study.addRevision(item.startPage, item.endPage, outcome, tags) }
    }

    fun dismissSuggestion(id: Int) {
        dismissed.update { it + id }
    }

    /** Moves the weak window along to the next ones. */
    fun showOtherWeak() {
        others.update { it + 1 }
    }

    private companion object {
        const val PAGES = 604
        const val MAX_SUGGESTIONS = 3
        const val WEAK_SHOWN = 2
        const val SNIPPET_CHARS = 80
        const val CLOCK_TICK_MS = 60_000L
        const val MISS_MIN = 2
        const val MISS_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

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
        HomeViewModel(StudyRepository.create(context), QuranRepository.getInstance(context), UserPreferencesRepository(context.applicationContext)) as T
}
