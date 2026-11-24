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
 * A part of the Mushaf the questions are kept to: whole surahs, whole juz, or a run of pages. [ids] are what was chosen
 * (surah or juz numbers, or the pages themselves) and [pages] the pages they cover, kept with them so the choice needs
 * no Quran data to be used.
 */
data class ScopePlace(val kind: Kind, val ids: Set<Int>, val pages: Set<Int>) {

    enum class Kind(val code: Int) {
        SURAHS(1), JUZ(2), PAGES(3);

        companion object {
            fun fromCode(code: Int): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    companion object {
        /** [numbers] as runs, "1-3,7", to keep in the settings. */
        fun encode(numbers: Set<Int>): String {
            val sorted = numbers.filter { it > 0 }.sorted()
            val out = StringBuilder()
            var i = 0
            while (i < sorted.size) {
                var j = i
                while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
                if (out.isNotEmpty()) out.append(',')
                out.append(sorted[i])
                if (j > i) out.append('-').append(sorted[j])
                i = j + 1
            }
            return out.toString()
        }

        /** The numbers [encode] wrote; nothing for what cannot be read. */
        fun decode(text: String?): Set<Int> {
            val out = LinkedHashSet<Int>()
            for (part in text.orEmpty().split(',')) {
                val ends = part.split('-').mapNotNull { it.trim().toIntOrNull() }
                when (ends.size) {
                    1 -> if (ends[0] > 0) out += ends[0]
                    2 -> if (ends[0] in 1..ends[1] && ends[1] - ends[0] < MAX_RUN) for (n in ends[0]..ends[1]) out += n
                }
            }
            return out
        }

        /** A kept choice; null when there is none, or it cannot be read. */
        fun fromStored(kind: Int, ids: String?, pages: String?): ScopePlace? {
            val k = Kind.fromCode(kind) ?: return null
            val chosen = decode(ids)
            val covered = decode(pages)
            return if (chosen.isEmpty() || covered.isEmpty()) null else ScopePlace(k, chosen, covered)
        }

        /** No run is longer than the Mushaf. */
        private const val MAX_RUN = 1000
    }
}

/**
 * Which pages the practice questions are drawn from. First how far back to look: every memorised page, the latest
 * [recentAmount] pages reviewed (unit [RecencyUnit.PAGES]), or everything reviewed within the last [recentAmount]
 * days, weeks, months or years; and, when a [place] is chosen, only its pages. Then, of those, only the chosen
 * [tiers]; with none chosen every level stays.
 */
data class PracticeScope(
    val tiers: Set<PlanTier> = emptySet(),
    val recentAmount: Int = 0,
    val recentUnit: RecencyUnit = RecencyUnit.PAGES,
    val place: ScopePlace? = null
) {

    /** How far back the questions look. */
    enum class Reach { ALL, COUNT, TIME }

    val reach: Reach
        get() = when {
            recentAmount == 0 -> Reach.ALL
            recentUnit == RecencyUnit.PAGES -> Reach.COUNT
            else -> Reach.TIME
        }

    val isAll: Boolean get() = tiers.isEmpty() && recentAmount == 0 && place == null

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
        val reached = reached(covered, logs, totalPages, now)
        if (tiers.isEmpty()) return reached
        return reached.filterTo(LinkedHashSet()) { RevisionPlanner.tierOf(states[it - 1], selfRated[it]) in tiers }
    }

    /** What a choice gives: [reached] pages by the reach alone, how many of them are at each level, and [total] after the levels. */
    class Preview(val reached: Int, val byTier: Map<PlanTier, Int>, val total: Int)

    /** The counts the picker shows while the choice is being made. */
    fun preview(
        covered: BooleanArray?,
        states: Array<PassageState>,
        selfRated: Map<Int, PlanTier>,
        logs: List<RevisionLog>,
        totalPages: Int = MemoryEngine.TOTAL_PAGES,
        now: Long = System.currentTimeMillis()
    ): Preview {
        val reached = reached(covered, logs, totalPages, now)
        val byTier = PlanTier.entries.associateWith { 0 }.toMutableMap()
        for (page in reached) RevisionPlanner.tierOf(states[page - 1], selfRated[page]).let { byTier[it] = byTier.getValue(it) + 1 }
        val total = if (tiers.isEmpty()) reached.size else tiers.sumOf { byTier.getValue(it) }
        return Preview(reached.size, byTier, total)
    }

    /** [pages] as a yes or no for each page, for exercises that take a list of memorised pages; null when every page is allowed. */
    fun mask(
        covered: BooleanArray?,
        states: Array<PassageState>,
        selfRated: Map<Int, PlanTier>,
        logs: List<RevisionLog>,
        totalPages: Int = MemoryEngine.TOTAL_PAGES,
        now: Long = System.currentTimeMillis()
    ): BooleanArray? {
        if (isAll) return covered
        val allowed = pages(covered, states, selfRated, logs, totalPages, now)
        return BooleanArray(totalPages) { it + 1 in allowed }
    }

    /**
     * The pages the reach (and the [place], when there is one) allows, before the levels are looked at. A place chosen
     * with none of its pages marked memorised gives its pages as they are: the reader chose it.
     */
    private fun reached(covered: BooleanArray?, logs: List<RevisionLog>, totalPages: Int, now: Long): Set<Int> {
        val all = reachedAnywhere(covered, logs, totalPages, now)
        val place = place ?: return all
        val inPlace = all.filterTo(LinkedHashSet()) { it in place.pages }
        if (inPlace.isEmpty() && reach == Reach.ALL) return place.pages.filter { it in 1..totalPages }.sorted().toCollection(LinkedHashSet())
        return inPlace
    }

    /** The pages the reach alone allows, anywhere in the Mushaf. */
    private fun reachedAnywhere(covered: BooleanArray?, logs: List<RevisionLog>, totalPages: Int, now: Long): Set<Int> {
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
        return reached
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
        fun fromStored(mask: Int, amount: Int, unit: Int, place: ScopePlace? = null) = PracticeScope(
            tiers = PlanTier.entries.filter { mask and (1 shl (it.code - 1)) != 0 }.toSet(),
            recentAmount = amount.coerceAtLeast(0),
            recentUnit = RecencyUnit.fromCode(unit),
            place = place
        )

        /** What "last reviewed" means when it is first switched on. */
        const val DEFAULT_AMOUNT = 10

        /** The most that can be asked for in each unit. */
        const val MAX_AMOUNT = 999
    }
}
