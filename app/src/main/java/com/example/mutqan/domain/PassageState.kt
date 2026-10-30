package com.example.mutqan.domain

/**
 * How much recall evidence there is for a page. This is a description of the history, not a
 * score: it is never computed from one attempt, and self-reported evidence stays self-reported.
 */
enum class PassageState {
    /** No recall attempt has been logged. */
    NOT_ASSESSED,

    /** The last attempt failed, or most of the recent attempts needed help. */
    NEEDS_ATTENTION,

    /** Some recall shown, but not yet repeated. */
    DEVELOPING,

    /** Independent recall on at least two different days, the latest two attempts both successful. */
    RELIABLE,

    /** Reliable, and the successes are spread over at least [PassageStates.STABLE_SPAN_DAYS] days. */
    STABLE_OVER_TIME
}

object PassageStates {

    const val STABLE_SPAN_DAYS = 30
    private const val MS_PER_DAY = 86_400_000L

    /** An attempt at one page: when it happened and what it showed. */
    private class Attempt(val day: Long, val time: Long, val outcome: RecallOutcome)

    /**
     * The state of each page (index 0 = page 1) from [logs]. Practice-only logs are ignored. A
     * log is dated by its [RevisionLog.dateMillis] when set, otherwise by its timestamp.
     */
    fun compute(logs: List<RevisionLog>, totalPages: Int = MemoryEngine.TOTAL_PAGES): Array<PassageState> {
        val attempts = Array(totalPages) { ArrayList<Attempt>() }
        for (log in logs) {
            if (!log.outcome.isRecallAttempt) continue
            val time = if (log.dateMillis > 0L) log.dateMillis else log.timestamp
            val attempt = Attempt(day = time / MS_PER_DAY, time = time, outcome = log.outcome)
            val start = log.startPage.coerceIn(1, totalPages)
            val end = log.endPage.coerceIn(start, totalPages)
            for (page in start..end) attempts[page - 1].add(attempt)
        }
        return Array(totalPages) { stateOf(attempts[it]) }
    }

    private fun stateOf(all: List<Attempt>): PassageState {
        if (all.isEmpty()) return PassageState.NOT_ASSESSED
        val attempts = all.sortedBy { it.time }
        val last = attempts.last()
        val lastThree = attempts.takeLast(3)

        if (last.outcome == RecallOutcome.UNABLE) return PassageState.NEEDS_ATTENTION
        if (lastThree.count { !it.outcome.isSuccess } >= 2) return PassageState.NEEDS_ATTENTION

        val successes = attempts.filter { it.outcome.isSuccess }
        val lastTwoSucceeded = attempts.takeLast(2).let { it.size == 2 && it.all { a -> a.outcome.isSuccess } }
        val successDays = successes.map { it.day }.distinct()

        if (lastTwoSucceeded && successDays.size >= 2) {
            val spanDays = (successes.last().day - successes.first().day)
            return if (successDays.size >= 3 && spanDays >= STABLE_SPAN_DAYS) PassageState.STABLE_OVER_TIME
            else PassageState.RELIABLE
        }
        return PassageState.DEVELOPING
    }
}
