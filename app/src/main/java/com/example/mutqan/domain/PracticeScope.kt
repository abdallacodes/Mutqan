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
 * Which pages the practice questions are drawn from. First how far back to look: every memorised page, the latest
 * [recentAmount] pages reviewed (unit [RecencyUnit.PAGES]), or everything reviewed within the last [recentAmount]
 * days, weeks, months or years. Then, of those, only the chosen [tiers]; with none chosen every level stays.
 */
data class PracticeScope(
    val tiers: Set<PlanTier> = emptySet(),
    val recentAmount: Int = 0,
    val recentUnit: RecencyUnit = RecencyUnit.PAGES
) {

    /** How far back the questions look. */
    enum class Reach { ALL, COUNT, TIME }

    val reach: Reach
        get() = when {
            recentAmount == 0 -> Reach.ALL
            recentUnit == RecencyUnit.PAGES -> Reach.COUNT
            else -> Reach.TIME
        }

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
        fun reviewedAt(log: RevisionLog) = if (log.dateMillis > 0L) log.dateMillis else log.timestamp
        fun pagesOf(log: RevisionLog) = log.startPage.coerceAtLeast(1)..log.endPage.coerceAtMost(totalPages)

        val reached = LinkedHashSet<Int>()
        when (reach) {
            Reach.ALL -> for (page in 1..totalPages) if (memorised(page)) reached += page
            Reach.COUNT -> {
                for (log in logs.sortedByDescending(::reviewedAt)) {
                    for (page in pagesOf(log)) {
                        if (reached.size >= recentAmount) break
                        if (memorised(page)) reached += page
                    }
                    if (reached.size >= recentAmount) break
                }
            }
            Reach.TIME -> {
                val since = cutoff(now)
                for (log in logs) {
                    if (reviewedAt(log) < since) continue
                    for (page in pagesOf(log)) if (memorised(page)) reached += page
                }
            }
        }
        if (tiers.isEmpty()) return reached
        return reached.filterTo(LinkedHashSet()) { RevisionPlanner.tierOf(states[it - 1], selfRated[it]) in tiers }
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
