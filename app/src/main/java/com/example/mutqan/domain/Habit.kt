package com.example.mutqan.domain

/** How one day looks in the run of days the user kept to the Quran. */
enum class DayMark {
    /** Something revised, or [Habit.QUESTIONS_A_DAY] practice answers. */
    KEPT,

    /** Missed, and forgiven: one missed day in any [Habit.FORGIVE_EVERY] does not break the run. */
    FORGIVEN,

    /** A rest day of the plan: it neither adds to the run nor breaks it. */
    REST,

    /** Missed, and it ended the run before it. */
    MISSED,

    /** Today, with nothing yet: it does not break the run while the day lasts. */
    OPEN
}

/** The run of days ending today (or yesterday, while today is still open). */
data class Streak(
    /** Days kept in the run; forgiven and rest days are inside it but do not count. */
    val days: Int,
    /** The first kept day of the run (epoch day); null when there is no run. */
    val since: Long?,
    /** True once today is kept. */
    val todayKept: Boolean
)

/**
 * The habit of coming back each day, kept forgiving on purpose ("the most beloved deeds are the most constant, even if
 * small"): a day counts when the user revised anything at all or answered a short set of questions; a rest day of the
 * plan is neither kept nor missed; and one missed day in any week is forgiven, so a single bad day never wipes out a
 * month. Two missed days within a week end the run. Days are local epoch days.
 */
object Habit {
    /** Practice answers in one day that count as a kept day without any revision. */
    const val QUESTIONS_A_DAY = 10

    /** Two forgiven days are at least this many days apart. */
    const val FORGIVE_EVERY = 7

    /** The days kept: those with a revision logged, and those with [QUESTIONS_A_DAY] practice answers or more. */
    fun keptDays(revisionDays: Collection<Long>, answerDays: Collection<Long>): Set<Long> {
        val kept = HashSet<Long>(revisionDays)
        answerDays.groupingBy { it }.eachCount().forEach { (day, answers) -> if (answers >= QUESTIONS_A_DAY) kept += day }
        return kept
    }

    /**
     * Each day from [today] back to [from] (inclusive), latest first. A missed day is forgiven only when it bridges a
     * run: the working day before it was kept, the working day after it was kept (or is today, still open), and no day
     * within [FORGIVE_EVERY] days after it was forgiven. Otherwise it is [DayMark.MISSED], and the run behind it is
     * judged on its own. Today not kept yet is [DayMark.OPEN] ([DayMark.REST] on a rest day) and holds the run.
     */
    fun marks(kept: Set<Long>, isRest: (Long) -> Boolean, today: Long, from: Long): List<Pair<Long, DayMark>> {
        val out = ArrayList<Pair<Long, DayMark>>()
        var lastForgiven: Long? = null
        // The nearest later working day's mark (rest days are skipped); today counts as kept while it lasts.
        var later: DayMark? = null
        var day = today
        while (day >= from) {
            val mark = when {
                day in kept -> DayMark.KEPT
                day == today -> if (isRest(day)) DayMark.REST else DayMark.OPEN
                isRest(day) -> DayMark.REST
                (later == DayMark.KEPT || later == DayMark.OPEN) && keptBefore(day, kept, isRest) &&
                    (lastForgiven.let { it == null || it - day >= FORGIVE_EVERY }) -> DayMark.FORGIVEN.also { lastForgiven = day }
                else -> DayMark.MISSED.also { lastForgiven = null }
            }
            if (day == today && mark != DayMark.KEPT) later = DayMark.OPEN
            else if (mark != DayMark.REST) later = mark
            out += day to mark
            day--
        }
        return out
    }

    /** True when the nearest day before [day] that is not a rest day was kept. */
    private fun keptBefore(day: Long, kept: Set<Long>, isRest: (Long) -> Boolean): Boolean {
        var d = day - 1
        var skipped = 0
        while (isRest(d) && skipped < FORGIVE_EVERY) {
            d--
            skipped++
        }
        return d in kept
    }

    /** The run ending today: kept days back to the first missed one, never further back than the first kept day. */
    fun streak(kept: Set<Long>, isRest: (Long) -> Boolean, today: Long): Streak {
        val first = kept.minOrNull() ?: return Streak(0, null, false)
        var days = 0
        var since: Long? = null
        for ((day, mark) in marks(kept, isRest, today, minOf(first, today))) {
            when (mark) {
                DayMark.KEPT -> { days++; since = day }
                DayMark.MISSED -> break
                else -> Unit
            }
        }
        return Streak(days, since, today in kept)
    }

    /** The longest run ever, in kept days. */
    fun best(kept: Set<Long>, isRest: (Long) -> Boolean, today: Long): Int {
        val first = kept.minOrNull() ?: return 0
        var best = 0
        var run = 0
        for ((_, mark) in marks(kept, isRest, today, minOf(first, today))) {
            when (mark) {
                DayMark.KEPT -> { run++; if (run > best) best = run }
                DayMark.MISSED -> run = 0
                else -> Unit
            }
        }
        return best
    }

    /** The last [count] days ending today, oldest first, as the week strip shows them. */
    fun week(kept: Set<Long>, isRest: (Long) -> Boolean, today: Long, count: Int = 7): List<Pair<Long, DayMark>> =
        marks(kept, isRest, today, today - count + 1).reversed()
}

/**
 * Today's ward: the plan's portions for today and the day's questions, and how far each is. [portions] is 0 when there
 * is nothing to revise today (no plan, a rest day, a paused plan), and then the questions are the whole ward.
 */
data class Ward(
    val portionsDone: Int,
    val portions: Int,
    val answered: Int,
    val questions: Int = Habit.QUESTIONS_A_DAY
) {
    val hasPortions: Boolean get() = portions > 0
    val revisionDone: Boolean get() = portionsDone >= portions
    val questionsDone: Boolean get() = answered >= questions
    val complete: Boolean get() = revisionDone && questionsDone

    /** How far the revision part is, 0 to 1 (1 when there is none). */
    val revisionShare: Float get() = if (portions <= 0) 1f else (portionsDone.toFloat() / portions).coerceIn(0f, 1f)

    /** How far the questions are, 0 to 1. */
    val questionsShare: Float get() = (answered.toFloat() / questions).coerceIn(0f, 1f)

    /** Questions still to answer today. */
    val questionsLeft: Int get() = (questions - answered).coerceAtLeast(0)

    companion object {
        /** Today's ward from the plan's view of today (null without a plan) and today's practice answers. */
        fun of(today: TodayPlan?, answeredToday: Int): Ward {
            val working = today != null && !today.isRestDay && !today.isPaused
            val total = if (working) today!!.done.size + today.due.size else 0
            return Ward(
                portionsDone = if (working) today!!.done.size else 0,
                portions = total,
                answered = answeredToday.coerceAtLeast(0)
            )
        }
    }
}

/**
 * Full passes over everything memorised ("خَتمة مراجعة"): going through the logs in date order, a pass ends on the day
 * the last memorised page not yet revised in it is revised; the next pass starts the day after. Measured against what is
 * memorised now.
 */
object RevisionPasses {
    data class Pass(val number: Int, val startDay: Long, val endDay: Long) {
        val days: Int get() = (endDay - startDay + 1).toInt()
    }

    /** [logs] with their local days ([dayOf]); [covered] is index 0 = page 1. */
    fun completed(logs: List<RevisionLog>, covered: BooleanArray?, dayOf: (RevisionLog) -> Long): List<Pass> {
        if (covered == null) return emptyList()
        val wanted = covered.count { it }
        if (wanted == 0) return emptyList()
        val sorted = logs.map { dayOf(it) to it }.sortedWith(compareBy({ it.first }, { it.second.timestamp }))
        val seen = BooleanArray(covered.size)
        var count = 0
        var start: Long? = null
        val passes = ArrayList<Pass>()
        for ((day, log) in sorted) {
            if (start == null) start = day
            val from = log.startPage.coerceIn(1, covered.size)
            val to = log.endPage.coerceIn(from, covered.size)
            for (page in from..to) {
                if (covered[page - 1] && !seen[page - 1]) {
                    seen[page - 1] = true
                    count++
                }
            }
            if (count == wanted) {
                passes += Pass(passes.size + 1, start, day)
                seen.fill(false)
                count = 0
                start = null
            }
        }
        return passes
    }
}

/** Something worth a full-screen moment, shown once. [key] tells whether it was shown already. */
sealed interface Milestone {
    val key: String

    /** [days] kept in a row (one of [Milestones.STREAKS]), in the run that began on [since]. */
    data class Days(val days: Int, val since: Long) : Milestone {
        override val key: String get() = "days:$since:$days"
    }

    /** Pass number [number] over everything memorised, done in [days] days. */
    data class Pass(val number: Int, val days: Int) : Milestone {
        override val key: String get() = "pass:$number"
    }

    /** Every page of juz [juz] memorised and strong. */
    data class StrongJuz(val juz: Int) : Milestone {
        override val key: String get() = "juz:$juz"
    }
}

object Milestones {
    /** The runs of days that get their moment. */
    val STREAKS = listOf(3, 7, 30, 100, 365)

    /** Everything reached now: the run's thresholds, every finished pass, every strong juz. */
    fun reached(streak: Streak, passes: List<RevisionPasses.Pass>, strongJuz: Collection<Int>): List<Milestone> {
        val out = ArrayList<Milestone>()
        streak.since?.let { since -> STREAKS.filter { streak.days >= it }.forEach { out += Milestone.Days(it, since) } }
        passes.forEach { out += Milestone.Pass(it.number, it.days) }
        strongJuz.sorted().forEach { out += Milestone.StrongJuz(it) }
        return out
    }

    /**
     * The one to show now, if any: of those not [seen], the biggest run first, then a pass, then a juz. Several reached at
     * once (a long run hitting 7 and 30 on the same day cannot happen, but a pass and a juz can) come one at a time.
     */
    fun next(reached: List<Milestone>, seen: Set<String>): Milestone? {
        val fresh = reached.filter { !wasShown(it, seen) }
        return fresh.filterIsInstance<Milestone.Days>().maxByOrNull { it.days }
            ?: fresh.filterIsInstance<Milestone.Pass>().maxByOrNull { it.number }
            ?: fresh.filterIsInstance<Milestone.StrongJuz>().firstOrNull()
    }

    /**
     * Whether [milestone] was shown already. A run's threshold also counts as shown when it was shown for a start day
     * inside this run: a forgotten day logged before the run moves its start back, and must not bring the same moment
     * again. A new run after a break starts later than every older key, so it gets its own.
     */
    fun wasShown(milestone: Milestone, seen: Set<String>): Boolean {
        if (milestone.key in seen) return true
        if (milestone !is Milestone.Days) return false
        return seen.any { key ->
            val parts = key.split(':')
            parts.size == 3 && parts[0] == "days" && parts[2].toIntOrNull() == milestone.days &&
                (parts[1].toLongOrNull() ?: return@any false) >= milestone.since
        }
    }

    /** The keys to mark as shown along with [shown]: for a run, its smaller thresholds too, so none comes after it. */
    fun keysShown(shown: Milestone): List<String> = when (shown) {
        is Milestone.Days -> STREAKS.filter { it <= shown.days }.map { Milestone.Days(it, shown.since).key }
        else -> listOf(shown.key)
    }

    /**
     * Juz whose pages are all memorised and all [PlanTier.STRONG]. [juzPages] maps juz to its pages; [strongPages] are the
     * memorised pages now in the strong tier.
     */
    fun strongJuz(juzPages: Map<Int, List<Int>>, covered: BooleanArray?, strongPages: Collection<Int>): Set<Int> {
        if (covered == null) return emptySet()
        val strong = strongPages.toHashSet()
        return juzPages.filter { (_, pages) ->
            pages.isNotEmpty() && pages.all { it in 1..covered.size && covered[it - 1] && it in strong }
        }.keys
    }
}
