package com.example.mutqan.domain

/**
 * Which pages the practice questions are drawn from: those at the chosen levels, and the pages last reviewed. Both
 * count together; with nothing chosen every memorised page does.
 */
data class PracticeScope(val tiers: Set<PlanTier> = emptySet(), val lastReviewed: Int = 0) {

    val isAll: Boolean get() = tiers.isEmpty() && lastReviewed == 0

    /** The levels as one number, one bit for each, to keep in the settings. */
    val tierMask: Int get() = tiers.fold(0) { mask, tier -> mask or (1 shl (tier.code - 1)) }

    fun withTier(tier: PlanTier, on: Boolean) = copy(tiers = if (on) tiers + tier else tiers - tier)

    /**
     * The pages (1-based) the questions may be about. [covered] is what is memorised (null: not set, so every page);
     * [states] and [selfRated] give each page's level; [logs] give what was reviewed and when.
     */
    fun pages(
        covered: BooleanArray?,
        states: Array<PassageState>,
        selfRated: Map<Int, PlanTier>,
        logs: List<RevisionLog>,
        totalPages: Int = MemoryEngine.TOTAL_PAGES
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
        if (lastReviewed > 0) {
            val recent = LinkedHashSet<Int>()
            for (log in logs.sortedByDescending { if (it.dateMillis > 0L) it.dateMillis else it.timestamp }) {
                for (page in log.startPage.coerceAtLeast(1)..log.endPage.coerceAtMost(totalPages)) {
                    if (recent.size >= lastReviewed) break
                    if (memorised(page)) recent += page
                }
                if (recent.size >= lastReviewed) break
            }
            out += recent
        }
        return out
    }

    companion object {
        fun fromMask(mask: Int, lastReviewed: Int) = PracticeScope(
            tiers = PlanTier.entries.filter { mask and (1 shl (it.code - 1)) != 0 }.toSet(),
            lastReviewed = lastReviewed.coerceAtLeast(0)
        )

        /** How many pages "last reviewed" means when it is first switched on, and the choices after that. */
        const val DEFAULT_LAST = 10
        val LAST_CHOICES = listOf(5, 10, 20, 50)
    }
}
