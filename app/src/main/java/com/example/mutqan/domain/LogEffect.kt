package com.example.mutqan.domain

/**
 * What logging one revision would change for the pages it covers: how many of them sit in each tier of the plan
 * before and after, how many move up or down, and the memory model's estimate of how long recall holds.
 * Pages outside what the user has memorised are not tracked ([total] - [tracked] of them).
 */
data class LogEffect(
    val total: Int,
    val tracked: Int,
    val before: Map<PlanTier, Int>,
    val after: Map<PlanTier, Int>,
    val up: Int,
    val down: Int,
    /** Mean days that recall holds at 90%, over the tracked pages; null while none of them has an estimate yet. */
    val holdBefore: Float?,
    val holdAfter: Float?,
    /** True for reading or listening only, which is recorded but changes nothing. */
    val practiceOnly: Boolean
) {
    val unchanged: Int get() = tracked - up - down
}

object LogEffects {

    /** The effect of logging each outcome for pages [startPage]..[endPage] on [dateMillis] (0: today). */
    fun previewAll(
        logs: List<RevisionLog>,
        startPage: Int,
        endPage: Int,
        dateMillis: Long,
        now: Long,
        covered: BooleanArray?,
        selfRated: Map<Int, PlanTier>
    ): Map<RecallOutcome, LogEffect> = RecallOutcome.entries.associateWith { outcome ->
        preview(
            logs,
            RevisionLog(
                startPage = startPage,
                endPage = endPage,
                timestamp = now,
                manualStability = outcome.quality,
                dateMillis = dateMillis,
                outcome = outcome
            ),
            covered,
            selfRated
        )
    }

    /**
     * The effect of adding [added] to [logs]. [covered] is what the user has memorised (null: everything), and
     * [selfRated] the levels they gave pages before any history.
     */
    fun preview(
        logs: List<RevisionLog>,
        added: RevisionLog,
        covered: BooleanArray?,
        selfRated: Map<Int, PlanTier>
    ): LogEffect {
        val first = added.startPage.coerceIn(1, MemoryEngine.TOTAL_PAGES)
        val last = added.endPage.coerceIn(first, MemoryEngine.TOTAL_PAGES)
        val pages = (first..last).filter { covered == null || covered[it - 1] }

        val statesBefore = PassageStates.compute(logs)
        val statesAfter = PassageStates.compute(logs + added)
        val before = PlanTier.entries.associateWith { 0 }.toMutableMap()
        val after = PlanTier.entries.associateWith { 0 }.toMutableMap()
        var up = 0
        var down = 0
        for (page in pages) {
            val b = RevisionPlanner.tierOf(statesBefore[page - 1], selfRated[page])
            val a = RevisionPlanner.tierOf(statesAfter[page - 1], selfRated[page])
            before[b] = before.getValue(b) + 1
            after[a] = after.getValue(a) + 1
            if (a.code > b.code) up++ else if (a.code < b.code) down++
        }

        val stabilityBefore = MemoryEngine.computeCurrentState(logs, emptyMap(), covered).stability
        val stabilityAfter = MemoryEngine.computeCurrentState(logs + added, emptyMap(), covered).stability
        fun hold(s: FloatArray): Float? =
            pages.map { s[it - 1] }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        return LogEffect(
            total = last - first + 1,
            tracked = pages.size,
            before = before,
            after = after,
            up = up,
            down = down,
            holdBefore = hold(stabilityBefore),
            holdAfter = hold(stabilityAfter),
            practiceOnly = !added.outcome.isRecallAttempt
        )
    }
}
