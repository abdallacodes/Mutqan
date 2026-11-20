package com.example.mutqan.domain

/*
 * Suggestions to change the pages a day of a plan by strength (2026-11-20, with the user). The user picks what fits their
 * day; the app only suggests going higher or lower, never changes it by itself, and seldom: a small amount kept every day
 * is better than a big one missed.
 *
 * - More: kept up on at least 12 of the last 14 planned days, and the weak pages are not increasing. The next step up; or,
 *   when they have been revising well beyond the plan, the step that covers what they already do.
 * - Less: finished fewer than half of the last 14 planned days. The step below. (More mistakes alone is no reason for
 *   less: if they keep up but pages slip, the cure is more revision.)
 * - Back: a higher amount just tried and missed on most of its first week: back to what it was.
 * - Never in the first 14 planned days of an amount, while paused, or within 14 days of the last suggestion answered.
 */

/** A suggestion to change the pages a day from [from] to [to]; [keptUp] of the last [of] planned days were kept up. */
data class PlanAdvice(
    val kind: Kind,
    val to: Int,
    val from: Int,
    val keptUp: Int,
    val of: Int,
    /** For [Kind.MORE]: the pages a day the user has been revising, when well beyond the plan; 0 otherwise. */
    val beyond: Int = 0
) {
    enum class Kind { MORE, LESS, BACK }
}

/**
 * What the app remembers of its suggestions: the day the present pages a day began, the day a suggestion was last
 * answered (0 never), and, after trying a higher amount, the amount before it and the day it was tried (0 none).
 */
data class AdviceMemory(
    val amountSince: Long = 0L,
    val lastAnswered: Long = 0L,
    val triedFrom: Int = 0,
    val triedOn: Long = 0L
)

object PlanAdvisor {
    /** The planned days looked at. */
    const val WINDOW = 14
    /** Days kept up of the [WINDOW] for more. */
    const val KEPT_FOR_MORE = 12
    /** Days after a suggestion is answered before another. */
    const val QUIET_DAYS = 14
    /** Revising this much more than the plan suggests the step that covers it. */
    private const val WELL_BEYOND = 1.25f
    /** The planned days of a tried amount over which going back may be offered: about its first week. */
    private val FIRST_WEEK = 5..10

    /**
     * The suggestion for today, or null. [pagesOn] is how many memorised pages were revised on an epoch day; [weakNow]
     * and [weakBefore] are the weak pages now and [WINDOW] days ago; [weekly] is the pages a day for every page every
     * week, the last step.
     */
    fun advise(
        amount: Int,
        restDays: Set<Int>,
        paused: Boolean,
        today: Long,
        memory: AdviceMemory,
        pagesOn: (Long) -> Int,
        weakNow: Int,
        weakBefore: Int,
        weekly: Int
    ): PlanAdvice? {
        if (paused || amount <= 0) return null
        val steps = SmartPlan.steps(weekly)

        // A higher amount just tried and hard to keep: back to what it was.
        if (memory.triedFrom > 0 && memory.triedFrom < amount) {
            val tried = plannedDays(today, memory.triedOn, restDays, Int.MAX_VALUE)
            if (tried.size in FIRST_WEEK) {
                val kept = tried.count { pagesOn(it) >= amount }
                if (kept * 2 < tried.size) return PlanAdvice(PlanAdvice.Kind.BACK, memory.triedFrom, amount, kept, tried.size)
            }
        }

        if (today - memory.lastAnswered < QUIET_DAYS) return null
        val window = plannedDays(today, memory.amountSince, restDays, WINDOW)
        if (window.size < WINDOW) return null
        val kept = window.count { pagesOn(it) >= amount }

        if (kept >= KEPT_FOR_MORE && weakNow <= weakBefore) {
            val next = steps.firstOrNull { it > amount } ?: return null
            val average = window.sumOf { pagesOn(it) } / window.size
            return if (average >= amount * WELL_BEYOND) {
                val covering = steps.firstOrNull { it >= average } ?: steps.last()
                PlanAdvice(PlanAdvice.Kind.MORE, maxOf(covering, next), amount, kept, window.size, beyond = average)
            } else {
                PlanAdvice(PlanAdvice.Kind.MORE, next, amount, kept, window.size)
            }
        }
        if (kept * 2 < window.size) {
            val lower = steps.lastOrNull { it < amount } ?: return null
            return PlanAdvice(PlanAdvice.Kind.LESS, lower, amount, kept, window.size)
        }
        return null
    }

    /** The planned days (not rest days) before [today] back to [since], the latest first, at most [most] of them. */
    fun plannedDays(today: Long, since: Long, restDays: Set<Int>, most: Int): List<Long> {
        val out = ArrayList<Long>()
        var day = today - 1
        while (day >= since && out.size < most) {
            if (RevisionPlanner.isoWeekday(day) !in restDays) out += day
            day--
        }
        return out
    }

    /** For each epoch day from [from] on, how many memorised pages were revised that day. */
    fun pagesByDay(logs: List<RevisionLog>, covered: BooleanArray?, from: Long, offsetMillis: (Long) -> Int): Map<Long, Int> {
        if (covered == null) return emptyMap()
        val pages = HashMap<Long, HashSet<Int>>()
        for (log in logs) {
            val day = RevisionPlanner.logDay(log, offsetMillis)
            if (day < from) continue
            val start = log.startPage.coerceIn(1, covered.size)
            val end = log.endPage.coerceIn(start, covered.size)
            val set = pages.getOrPut(day) { HashSet() }
            for (page in start..end) if (covered[page - 1]) set += page
        }
        return pages.mapValues { it.value.size }
    }
}
