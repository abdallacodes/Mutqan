package com.example.mutqan.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The plan by strength (2026-11-20, with the user). The user chooses one thing: how many pages to revise a day. The app
 * spreads them over the hifz by how well each page holds: within the cycle a weak page comes about three times as often
 * as a strong one, a fair page about twice as often. So the more pages a day, the shorter the cycle and the stronger the
 * hifz, up to the golden goal of every page every week. Each day is at most two connected stretches of pages, the ones
 * most overdue for their strength (and every page revised today counts).
 *
 * Before, the user gave pages a day for each strength, which only made sense against how many pages each held: with 100
 * weak pages, 1 fair and 2 strong at 3 + 2 + 1 a day, the strong pages came every 2 days and the weak ones every 34.
 */
object SmartPlan {

    /** How often a page of each strength comes within the cycle, a strong page's being 1. */
    fun weight(tier: PlanTier): Int = when (tier) {
        PlanTier.WEAK -> 3
        PlanTier.FAIR -> 2
        PlanTier.STRONG -> 1
    }

    /** The pages a day the plan offers, from a light start up; the amount for every page every week is added to them. */
    val STEPS = listOf(2, 3, 5, 7, 10, 15, 20, 30, 40, 60, 80, 100)

    /** The most pages a day the plan takes. */
    const val MAX_PAGES = 300

    /** Stretches a day: the user wants at most two places. More only when two cannot hold the day's pages. */
    const val STRETCHES = 2
    private const val MAX_STRETCHES = 6

    /** How long ago a page never revised counts as revised, in days: before any page that was. */
    private const val NEVER = 3650

    /** Pages weighted by how often their strength comes around: three for a weak page, two for a fair one, one for a strong one. */
    fun weighted(counts: Map<PlanTier, Int>): Int = PlanTier.entries.sumOf { weight(it) * (counts[it] ?: 0) }

    /** Days of the week with revision. */
    fun workingDays(restDays: Set<Int>): Int = (7 - restDays.count { it in 1..7 }).coerceAtLeast(1)

    /**
     * The cycle, in days: how long a strong page takes to come around at [pagesPerDay] (a weak one a third of it). The
     * weighted pages over the pages a day give working days, stretched by the rest days. 0 when there is nothing to do.
     */
    fun cycleDays(weighted: Int, pagesPerDay: Int, restDays: Set<Int>): Float =
        if (weighted <= 0 || pagesPerDay <= 0) 0f else weighted.toFloat() / pagesPerDay * 7f / workingDays(restDays)

    /** How often a page of [tier] comes around in a cycle of [cycle] days. */
    fun intervalDays(tier: PlanTier, cycle: Float): Float = cycle / weight(tier)

    /**
     * The pages a day for every page every week, weak pages more often: the golden goal. A cycle of 7 days needs the
     * weighted pages over the working days of a week.
     */
    fun weeklyPages(weighted: Int, restDays: Set<Int>): Int =
        if (weighted <= 0) 0 else ceil(weighted.toFloat() / workingDays(restDays)).toInt()

    /** The steps up to the golden goal, which ends them: the pages a day offered and suggested for this hifz. */
    fun steps(weekly: Int): List<Int> =
        if (weekly <= 0) STEPS.take(1) else (STEPS.filter { it < weekly } + weekly)

    /**
     * A modest start: about a month for the hifz, between 3 and 10 pages a day, never more than the golden goal. Only a
     * hint; the user picks what fits their day.
     */
    fun goodStart(pages: Int, weekly: Int): Int {
        val target = (pages / 30f).coerceIn(3f, 10f)
        val step = STEPS.minBy { kotlin.math.abs(it - target) }
        return if (weekly > 0) minOf(step, weekly) else step
    }

    /**
     * Today for a plan by strength: the most overdue pages for their strength, [RevisionPlan.amountPerDay] of them a day
     * less the pages already revised today, in at most two connected stretches (most overdue first). A page is as overdue
     * as the days since it was revised over how often its strength comes around; a page never revised comes first.
     */
    fun today(
        plan: RevisionPlan,
        covered: BooleanArray?,
        pageLastRevised: LongArray,
        passageStates: Array<PassageState>,
        today: Long,
        selfRated: Map<Int, PlanTier> = emptyMap()
    ): TodayPlan {
        val pagesPerDay = plan.amountPerDay.coerceAtLeast(1)
        val restDay = RevisionPlanner.isoWeekday(today) in plan.restDays
        val tiers = RevisionPlanner.tierPages(covered, passageStates, selfRated)
        val tierOf = HashMap<Int, PlanTier>()
        tiers.forEach { (tier, pages) -> pages.forEach { tierOf[it] = tier } }
        val weighted = weighted(tiers.mapValues { it.value.size })
        val cycle = cycleDays(weighted, pagesPerDay, plan.restDays)

        val doneToday = tierOf.keys.filter { pageLastRevised[it - 1] == today }.sorted()
        val slots = if (plan.paused || restDay) 0 else (pagesPerDay - doneToday.size).coerceAtLeast(0)

        // How overdue each page is; pages not memorised, or revised today, are out (and part no stretch).
        val urgency = FloatArray(TOTAL_PAGES) { Float.NaN }
        for ((page, tier) in tierOf) {
            val last = pageLastRevised[page - 1]
            if (last == today) continue
            val since = if (last <= 0L) NEVER.toFloat() else (today - last).toFloat().coerceAtLeast(0f)
            urgency[page - 1] = since / intervalDays(tier, cycle).coerceAtLeast(MIN_INTERVAL)
        }
        val stretches = stretches(urgency, slots)
            .sortedByDescending { range -> range.sumOf { urgency[it - 1].toDouble() } }

        fun item(range: IntRange) = PlanItem(
            unit = PlanUnit.PAGE,
            number = range.first,
            startPage = range.first,
            endPage = range.last,
            tier = range.map { tierOf.getValue(it) }.distinct().singleOrNull(),
            why = why(range, tierOf, pageLastRevised, today)
        )
        return TodayPlan(
            itemCount = tierOf.size,
            done = runs(doneToday).map(::item),
            due = stretches.map(::item),
            isRestDay = restDay,
            isPaused = plan.paused,
            cycleDays = cycle.roundToInt(),
            dailyPages = pagesPerDay
        )
    }

    /** What a stretch holds and how long ago its stalest page was revised. */
    private fun why(range: IntRange, tierOf: Map<Int, PlanTier>, pageLastRevised: LongArray, today: Long): PlanWhy {
        val tiers = range.map { tierOf.getValue(it) }
        val lasts = range.map { pageLastRevised[it - 1] }
        val daysAgo = if (lasts.any { it <= 0L }) null else (today - lasts.min()).toInt()
        return PlanWhy(
            weak = tiers.count { it == PlanTier.WEAK },
            fair = tiers.count { it == PlanTier.FAIR },
            strong = tiers.count { it == PlanTier.STRONG },
            daysAgo = daysAgo
        )
    }

    /**
     * Up to [STRETCHES] runs of consecutive pages (more only when two cannot hold [slots] pages) with the most overdue
     * pages in all: [urgency] holds each page's (index 0 = page 1), NaN for a page that cannot be taken. All pages are
     * taken when there are no more than [slots]. Ties go to the earlier pages, so a new plan starts in Mushaf order.
     */
    fun stretches(urgency: FloatArray, slots: Int): List<IntRange> {
        val open = urgency.indices.filter { !urgency[it].isNaN() }
        if (slots <= 0 || open.isEmpty()) return emptyList()
        if (open.size <= slots) return runs(open.map { it + 1 })
        var best = emptyList<IntRange>()
        for (most in STRETCHES..MAX_STRETCHES) {
            best = bestRuns(urgency, slots, most)
            if (best.sumOf { it.last - it.first + 1 } >= slots) break
        }
        return best
    }

    /** The runs, at most [most] of them and [slots] pages in all, with the largest sum of [urgency]. */
    private fun bestRuns(urgency: FloatArray, slots: Int, most: Int): List<IntRange> {
        val n = urgency.size
        // State: runs begun (0..most), pages taken (0..slots), whether the page before was taken.
        val states = (most + 1) * (slots + 1) * 2
        fun at(k: Int, t: Int, taking: Int) = (k * (slots + 1) + t) * 2 + taking
        var score = FloatArray(states) { Float.NEGATIVE_INFINITY }
        var next = FloatArray(states)
        score[at(0, 0, 0)] = 0f
        // How each state was reached at each page: for a state not taking the page, whether the page before was taken;
        // for one taking it, 0 when it went on from the page before, 1 when it began a new run.
        val came = Array(n) { ByteArray(states) }
        for (p in 0 until n) {
            next.fill(Float.NEGATIVE_INFINITY)
            val u = urgency[p]
            val way = came[p]
            for (k in 0..most) for (t in 0..slots) for (taking in 0..1) {
                val s = score[at(k, t, taking)]
                if (s == Float.NEGATIVE_INFINITY) continue
                val skip = at(k, t, 0)
                if (s > next[skip]) { next[skip] = s; way[skip] = taking.toByte() }
                if (u.isNaN() || t == slots) continue
                if (taking == 1) {
                    val on = at(k, t + 1, 1)
                    if (s + u > next[on]) { next[on] = s + u; way[on] = 0 }
                } else if (k < most) {
                    val begin = at(k + 1, t + 1, 1)
                    if (s + u > next[begin]) { next[begin] = s + u; way[begin] = 1 }
                }
            }
            val swap = score; score = next; next = swap
        }
        var end = -1
        for (s in 0 until states) if (score[s] != Float.NEGATIVE_INFINITY && (end < 0 || score[s] > score[end])) end = s
        if (end < 0) return emptyList()
        // Walk back from the best end, collecting the pages taken.
        var k = end / 2 / (slots + 1)
        var t = end / 2 % (slots + 1)
        var taking = end % 2
        val taken = ArrayList<Int>()
        for (p in n - 1 downTo 0) {
            val way = came[p][at(k, t, taking)].toInt()
            if (taking == 0) {
                taking = way
            } else {
                taken += p + 1
                if (way == 1) { k -= 1; taking = 0 } else taking = 1
                t -= 1
            }
        }
        return runs(taken.sorted())
    }

    /** Joins consecutive page numbers (ascending) into runs. */
    fun runs(pages: List<Int>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < pages.size) {
            var j = i
            while (j + 1 < pages.size && pages[j + 1] == pages[j] + 1) j++
            out += pages[i]..pages[j]
            i = j + 1
        }
        return out
    }

    private const val TOTAL_PAGES = 604
    /** The shortest interval counted, so a tiny hifz on a large amount does not divide by almost nothing. */
    private const val MIN_INTERVAL = 0.25f
}

/**
 * Why a stretch is due: how many of its pages are weak, fair and strong, and how many days ago its stalest page was
 * revised (null when one never was).
 */
data class PlanWhy(val weak: Int, val fair: Int, val strong: Int, val daysAgo: Int?)
