package com.example.mutqan.domain

import android.content.Context
import kotlinx.coroutines.flow.first
import java.util.TimeZone

/** The habit as of one day: today's ward, the run of days, the week strip, and the milestones reached. */
data class HabitSnapshot(
    val day: Long,
    val ward: Ward,
    val streak: Streak,
    /** The longest run ever, in kept days. */
    val bestStreak: Int,
    /** The last seven days, oldest first. */
    val week: List<Pair<Long, DayMark>>,
    val reached: List<Milestone>,
    /** Full passes over everything memorised so far. */
    val passes: Int
)

object HabitSnapshots {
    private const val DAY_MS = 86_400_000L

    /**
     * The habit from what was recorded: revision [logs], the times of practice answers ([answerTimes], epoch millis),
     * the plan and its view of today (both null without a plan), what is memorised, and the memorised pages now strong.
     * [offset] gives the time zone's offset at a moment, for local days.
     */
    fun of(
        plan: RevisionPlan?,
        today: TodayPlan?,
        logs: List<RevisionLog>,
        covered: BooleanArray?,
        strongPages: Collection<Int>,
        juzPages: Map<Int, List<Int>>,
        answerTimes: List<Long>,
        day: Long,
        offset: (Long) -> Int
    ): HabitSnapshot {
        val answerDays = answerTimes.map { Math.floorDiv(it + offset(it), DAY_MS) }
        val kept = Habit.keptDays(logs.map { RevisionPlanner.logDay(it, offset) }, answerDays)
        // The plan as it is now says which weekdays are rest days; how it was set in the past is not kept.
        val isRest: (Long) -> Boolean = { d -> plan != null && RevisionPlanner.isoWeekday(d) in plan.restDays }
        val streak = Habit.streak(kept, isRest, day)
        val passes = RevisionPasses.completed(logs, covered) { RevisionPlanner.logDay(it, offset) }
        val strongJuz = Milestones.strongJuz(juzPages, covered, strongPages)
        return HabitSnapshot(
            day = day,
            ward = Ward.of(today, answerDays.count { it == day }),
            streak = streak,
            bestStreak = Habit.best(kept, isRest, day),
            week = Habit.week(kept, isRest, day),
            reached = Milestones.reached(streak, passes, strongJuz),
            passes = passes.size
        )
    }

    /** The local epoch day now. */
    fun localDay(now: Long = System.currentTimeMillis()): Long = Math.floorDiv(now + offsetAt(now), DAY_MS)

    fun offsetAt(time: Long): Int = TimeZone.getDefault().getOffset(time)

    /** The habit as of now, read from the database: for the daily reminder, which runs outside any screen. */
    suspend fun load(context: Context): Pair<HabitSnapshot, TodayPlan?> {
        val study = StudyRepository.create(context.applicationContext)
        val plan = study.plans().first().firstOrNull()
        val logs = study.revisionLogs().first()
        val covered = study.coveredPages().first()
        val selfRated = study.selfRatedStrength().first()
        val times = study.exerciseTimes().first()
        val geometry = study.planGeometry()
        val day = localDay()
        val states = PassageStates.compute(logs)
        val today = plan?.let { TodayPlans.of(it, logs, covered, states, selfRated, day, ::offsetAt) { geometry } }
        val strong = RevisionPlanner.tierPages(covered, states, selfRated)[PlanTier.STRONG].orEmpty()
        return of(plan, today, logs, covered, strong, geometry.juzPages, times, day, ::offsetAt) to today
    }
}

/** What a plan asks for today: one place for Today and the daily reminder, so both tell the same. */
object TodayPlans {
    private const val PAGES = 604

    /**
     * [plan]'s view of [day]. [states] are the recall states of the pages from the logs; a rotation ignores them.
     * [geometry] is read only for a plan by juz, surah or page.
     */
    suspend fun of(
        plan: RevisionPlan,
        logs: List<RevisionLog>,
        covered: BooleanArray?,
        states: Array<PassageState>,
        selfRated: Map<Int, PlanTier>,
        day: Long,
        offset: (Long) -> Int,
        geometry: suspend () -> PlanGeometry
    ): TodayPlan {
        val lastRevised = RevisionPlanner.lastRevisedDays(logs, offset)
        val used = if (plan.order == PlanOrder.ROTATION) Array(PAGES) { PassageState.NOT_ASSESSED } else states
        return if (plan.order == PlanOrder.BY_STRENGTH) {
            SmartPlan.today(plan, covered, lastRevised, used, day, selfRated)
        } else {
            RevisionPlanner.todayPlan(plan, RevisionPlanner.buildItems(plan.unit, covered, geometry()), lastRevised, used, day)
        }
    }
}
