package com.example.mutqan.domain

import java.util.Calendar

/** How "last reviewed" is measured: a number of pages, or a stretch of time looking back from now. */
enum class RecencyUnit(val code: Int) {
    PAGES(0), DAYS(1), WEEKS(2), MONTHS(3), YEARS(4);

    companion object {
        fun fromCode(code: Int): RecencyUnit = entries.firstOrNull { it.code == code } ?: PAGES
    }
}

/**
 * Which pages the practice questions are drawn from: those at the chosen levels, and the pages last reviewed, either
 * the latest [recentAmount] pages or everything reviewed within the last [recentAmount] days, weeks, months or years
 * ([recentUnit]). Both count together; with nothing chosen every memorised page does.
 */
data class PracticeScope(
    val tiers: Set<PlanTier> = emptySet(),
    val recentAmount: Int = 0,
    val recentUnit: RecencyUnit = RecencyUnit.PAGES
) {

    val isAll: Boolean get() = tiers.isEmpty() && recentAmount == 0

    /** The levels as one number, one bit for each, to keep in the settings. */
    val tierMask: Int get() = tiers.fold(0) { mask, tier -> mask or (1 shl (tier.code - 1)) }

    fun withTier(tier: PlanTier, on: Boolean) = copy(tiers = if (on) tiers + tier else tiers - tier)

    /**
     * The pages (1-based) the questions may be about. [covered] is what is memorised (null: not set, so every page);
     * [states] and [selfRated] give each page's level; [logs] give what was reviewed and when; [now] is the moment
     * time spans are counted back from.
     */
    fun pages(
        covered: BooleanArray?,
        states: Array<PassageState>,
        selfRated: Map<Int, PlanTier>,
        logs: List<RevisionLog>,
        totalPages: Int = MemoryEngine.TOTAL_PAGES,
        now: Long = System.currentTimeMillis()
    ): Set<Int> {
        fun memorised(page: Int) = covered?.getOrElse(page - 1) { false } ?: true
        val out = LinkedHashSet<Int>()
        if (isAll) {
            for (page in 1..totalPages) if (memorised(page)) out += page
            return out
        }
        if (tiers.isNotEmpty()) {
            for (page in 1..totalPages) {
                if (memorised(page) && RevisionPlanner.tierOf(states[page - 1], selfRated[page]) in tiers) out += page
            }
        }
        if (recentAmount > 0) {
            fun reviewedAt(log: RevisionLog) = if (log.dateMillis > 0L) log.dateMillis else log.timestamp
            if (recentUnit == RecencyUnit.PAGES) {
                val recent = LinkedHashSet<Int>()
                for (log in logs.sortedByDescending(::reviewedAt)) {
                    for (page in log.startPage.coerceAtLeast(1)..log.endPage.coerceAtMost(totalPages)) {
                        if (recent.size >= recentAmount) break
                        if (memorised(page)) recent += page
                    }
                    if (recent.size >= recentAmount) break
                }
                out += recent
            } else {
                val since = cutoff(now)
                for (log in logs) {
                    if (reviewedAt(log) < since) continue
                    for (page in log.startPage.coerceAtLeast(1)..log.endPage.coerceAtMost(totalPages)) {
                        if (memorised(page)) out += page
                    }
                }
            }
        }
        return out
    }

    /** The moment [recentAmount] units of time before [now]. */
    private fun cutoff(now: Long): Long {
        val field = when (recentUnit) {
            RecencyUnit.DAYS -> Calendar.DAY_OF_YEAR
            RecencyUnit.WEEKS -> Calendar.WEEK_OF_YEAR
            RecencyUnit.MONTHS -> Calendar.MONTH
            RecencyUnit.YEARS -> Calendar.YEAR
            RecencyUnit.PAGES -> return Long.MIN_VALUE
        }
        return Calendar.getInstance().apply {
            timeInMillis = now
            add(field, -recentAmount)
        }.timeInMillis
    }

    companion object {
        fun fromStored(mask: Int, amount: Int, unit: Int) = PracticeScope(
            tiers = PlanTier.entries.filter { mask and (1 shl (it.code - 1)) != 0 }.toSet(),
            recentAmount = amount.coerceAtLeast(0),
            recentUnit = RecencyUnit.fromCode(unit)
        )

        /** What "last reviewed" means when it is first switched on. */
        const val DEFAULT_AMOUNT = 10

        /** The most that can be asked for in each unit. */
        const val MAX_AMOUNT = 999
    }
}
