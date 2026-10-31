package com.example.mutqan.domain

import com.example.mutqan.data.local.entity.RevisionPlanEntity

/** What one portion of a plan is: a whole juz, a single page or a surah. [code] is stored. */
enum class PlanUnit(val code: Int) {
    JUZ(0),
    PAGE(1),
    SURAH(2);

    companion object {
        fun fromCode(code: Int): PlanUnit = entries.firstOrNull { it.code == code } ?: JUZ
    }
}

/** How a plan picks what is due first. [code] is stored. */
enum class PlanOrder(val code: Int) {
    /** Whatever was revised longest ago comes first, ties in Mushaf order: a steady rotation. */
    ROTATION(0),

    /** Passages that need attention first, then developing ones, then the rotation. */
    WEAKEST_FIRST(1),

    /**
     * Pages are sorted into [PlanTier]s by how well they hold, and each tier comes around at
     * its own pace; unit and amount per day are not used.
     */
    BY_STRENGTH(2);

    companion object {
        fun fromCode(code: Int): PlanOrder = entries.firstOrNull { it.code == code } ?: ROTATION
    }
}

/** How well a page holds, from its recall history. Weak pages come around often, strong ones rarely. */
enum class PlanTier(val code: Int) {
    STRONG(3), FAIR(2), WEAK(1);

    companion object {
        fun fromCode(code: Int): PlanTier? = entries.firstOrNull { it.code == code }

        fun of(state: PassageState): PlanTier = when (state) {
            PassageState.NEEDS_ATTENTION -> WEAK
            PassageState.NOT_ASSESSED, PassageState.DEVELOPING -> FAIR
            PassageState.RELIABLE, PassageState.STABLE_OVER_TIME -> STRONG
        }
    }
}

/**
 * A personal revision routine over the memorised coverage. The plan only stores the user's
 * choices; what is due is worked out from the revision history ([RevisionPlanner]), so there
 * are no assignments to fall behind on.
 * [restDays] are ISO weekday numbers, Monday = 1 .. Sunday = 7.
 */
data class RevisionPlan(
    val id: Int = 0,
    val name: String,
    val unit: PlanUnit = PlanUnit.JUZ,
    val amountPerDay: Int = 1,
    val restDays: Set<Int> = emptySet(),
    val order: PlanOrder = PlanOrder.ROTATION,
    val paused: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** For [PlanOrder.BY_STRENGTH]: pages a day the user wants from each tier (0 = leave the tier out). */
    val strongPages: Int = DEFAULT_STRONG_PAGES,
    val fairPages: Int = DEFAULT_FAIR_PAGES,
    val weakPages: Int = DEFAULT_WEAK_PAGES
) {
    fun pagesOf(tier: PlanTier): Int = when (tier) {
        PlanTier.STRONG -> strongPages
        PlanTier.FAIR -> fairPages
        PlanTier.WEAK -> weakPages
    }.coerceAtLeast(0)

    fun toEntity() = RevisionPlanEntity(
        id = id,
        name = name.trim(),
        unit = unit.code,
        amountPerDay = amountPerDay.coerceAtLeast(1),
        restDays = restDays.filter { it in 1..7 }.fold(0) { acc, day -> acc or (1 shl (day - 1)) },
        order = order.code,
        paused = if (paused) 1 else 0,
        createdAt = createdAt,
        strongPages = strongPages.coerceAtLeast(0),
        fairPages = fairPages.coerceAtLeast(0),
        weakPages = weakPages.coerceAtLeast(0)
    )

    companion object {
        const val DEFAULT_STRONG_PAGES = 4
        const val DEFAULT_FAIR_PAGES = 6
        const val DEFAULT_WEAK_PAGES = 10

        fun fromEntity(e: RevisionPlanEntity) = RevisionPlan(
            id = e.id,
            name = e.name,
            unit = PlanUnit.fromCode(e.unit),
            amountPerDay = e.amountPerDay.coerceAtLeast(1),
            restDays = (1..7).filterTo(LinkedHashSet()) { e.restDays and (1 shl (it - 1)) != 0 },
            order = PlanOrder.fromCode(e.order),
            paused = e.paused != 0,
            createdAt = e.createdAt,
            strongPages = e.strongPages.coerceAtLeast(0),
            fairPages = e.fairPages.coerceAtLeast(0),
            weakPages = e.weakPages.coerceAtLeast(0)
        )
    }
}

/** One portion of a cycle: [number] is the juz, page or surah number, [startPage]..[endPage] its pages. */
data class PlanItem(
    val unit: PlanUnit,
    val number: Int,
    val startPage: Int,
    val endPage: Int,
    /** Set for strength-based plans, where a portion is a run of pages from one tier. */
    val tier: PlanTier? = null
)

/** Pages of each juz (1..30) and the page span of each surah (index 0 = surah 1), for building items. */
class PlanGeometry(val juzPages: Map<Int, List<Int>>, val surahPages: List<IntRange>)

/** Today's view of a plan. */
data class TodayPlan(
    val itemCount: Int,
    /** Portions already revised today; they use up today's amount. */
    val done: List<PlanItem>,
    /** Portions still to do today, most urgent first. Empty on a rest day or while paused. */
    val due: List<PlanItem>,
    val isRestDay: Boolean,
    val isPaused: Boolean,
    /** Days one full pass over every portion takes at the plan's amount per day. */
    val cycleDays: Int,
    /** Pages a day the plan asks for on a working day (strength-based plans); 0 otherwise. */
    val dailyPages: Int = 0
)

/**
 * Works out what a [RevisionPlan] asks for today. Deterministic and explainable: a portion
 * is as fresh as its stalest page, and the portions revised longest ago are due first, so
 * revising something off-plan simply moves it to the back of the line and a missed day
 * leaves the same portions due instead of piling up a backlog.
 */
object RevisionPlanner {

    private const val TOTAL_PAGES = 604
    private const val MS_PER_DAY = 86_400_000L

    /** The portions of [unit] over the pages marked in [covered] (null = nothing memorised = no portions). */
    fun buildItems(unit: PlanUnit, covered: BooleanArray?, geometry: PlanGeometry): List<PlanItem> {
        if (covered == null) return emptyList()
        fun isCovered(page: Int) = page in 1..TOTAL_PAGES && covered[page - 1]
        return when (unit) {
            PlanUnit.PAGE -> (1..TOTAL_PAGES).filter(::isCovered).map { PlanItem(unit, it, it, it) }
            PlanUnit.JUZ -> (1..30).mapNotNull { juz ->
                val pages = geometry.juzPages[juz].orEmpty().filter(::isCovered)
                if (pages.isEmpty()) null else PlanItem(unit, juz, pages.min(), pages.max())
            }
            PlanUnit.SURAH -> geometry.surahPages.mapIndexedNotNull { i, span ->
                if (span.any(::isCovered)) PlanItem(unit, i + 1, span.first, span.last) else null
            }
        }
    }

    /** The epoch day (days since 1970-01-01) a log belongs to; [offsetMillis] is the local UTC offset at a time. */
    fun logDay(log: RevisionLog, offsetMillis: (Long) -> Int): Long =
        if (log.dateMillis > 0L) log.dateMillis / MS_PER_DAY
        else Math.floorDiv(log.timestamp + offsetMillis(log.timestamp), MS_PER_DAY)

    /** For each page (index 0 = page 1) the latest epoch day a log covered it; 0 = never. */
    fun lastRevisedDays(logs: List<RevisionLog>, offsetMillis: (Long) -> Int): LongArray {
        val last = LongArray(TOTAL_PAGES)
        for (log in logs) {
            val day = logDay(log, offsetMillis)
            val start = log.startPage.coerceIn(1, TOTAL_PAGES)
            val end = log.endPage.coerceIn(start, TOTAL_PAGES)
            for (page in start..end) if (day > last[page - 1]) last[page - 1] = day
        }
        return last
    }

    /** ISO weekday of an epoch day, Monday = 1 .. Sunday = 7 (1970-01-01 was a Thursday). */
    fun isoWeekday(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt() + 1

    fun cycleDays(itemCount: Int, amountPerDay: Int): Int =
        if (itemCount == 0) 0 else (itemCount + amountPerDay.coerceAtLeast(1) - 1) / amountPerDay.coerceAtLeast(1)

    fun todayPlan(
        plan: RevisionPlan,
        items: List<PlanItem>,
        pageLastRevised: LongArray,
        passageStates: Array<PassageState>,
        today: Long
    ): TodayPlan {
        val amount = plan.amountPerDay.coerceAtLeast(1)
        val restDay = isoWeekday(today) in plan.restDays

        fun lastRevised(item: PlanItem): Long =
            (item.startPage..item.endPage).minOf { pageLastRevised[it - 1] }

        fun weakness(item: PlanItem): Int =
            (item.startPage..item.endPage).minOf { weaknessRank(passageStates[it - 1]) }

        val done = items.filter { lastRevised(it) == today }
        val slots = if (plan.paused || restDay) 0 else (amount - done.size).coerceAtLeast(0)
        val rank = items.withIndex().associate { it.value to it.index }
        val due = items.asSequence()
            .filter { lastRevised(it) != today }
            .sortedWith(
                compareBy<PlanItem> { if (plan.order == PlanOrder.WEAKEST_FIRST) weakness(it) else 0 }
                    .thenBy { lastRevised(it) }
                    .thenBy { rank.getValue(it) }
            )
            .take(slots)
            .toList()

        return TodayPlan(
            itemCount = items.size,
            done = done,
            due = due,
            isRestDay = restDay,
            isPaused = plan.paused,
            cycleDays = cycleDays(items.size, amount)
        )
    }

    /** Days one pass over [pageCount] pages takes at [pagesPerDay] a day; 0 when there is nothing to do. */
    fun tierCycleDays(pageCount: Int, pagesPerDay: Int): Int =
        if (pageCount <= 0 || pagesPerDay <= 0) 0 else (pageCount + pagesPerDay - 1) / pagesPerDay

    /**
     * The covered pages in each tier (ascending), from their recall history. A page with no recall
     * evidence yet, or too little to judge (developing), goes to the tier the user gave it in [selfRated] (fair when none); once
     * it has a failed attempt or reliable recall, that decides.
     */
    fun tierPages(
        covered: BooleanArray?,
        passageStates: Array<PassageState>,
        selfRated: Map<Int, PlanTier> = emptyMap()
    ): Map<PlanTier, List<Int>> {
        val tiers = PlanTier.entries.associateWith { ArrayList<Int>() }
        if (covered != null) {
            for (page in 1..TOTAL_PAGES) {
                if (!covered[page - 1]) continue
                val state = passageStates[page - 1]
                val tier = if (state == PassageState.NOT_ASSESSED || state == PassageState.DEVELOPING) selfRated[page] ?: PlanTier.FAIR else PlanTier.of(state)
                tiers.getValue(tier).add(page)
            }
        }
        return tiers
    }

    /**
     * Today for a [PlanOrder.BY_STRENGTH] plan. The user chooses how many pages a day each tier gets
     * (never more than the tier has); of those, the pages revised longest ago are due (never-revised
     * first), minus what today's logs already cover. Neighbouring pages are joined into one portion,
     * weak tier first.
     */
    fun strengthToday(
        plan: RevisionPlan,
        covered: BooleanArray?,
        pageLastRevised: LongArray,
        passageStates: Array<PassageState>,
        today: Long,
        selfRated: Map<Int, PlanTier> = emptyMap()
    ): TodayPlan {
        val restDay = isoWeekday(today) in plan.restDays
        val tiers = tierPages(covered, passageStates, selfRated)
        val due = ArrayList<PlanItem>()
        val done = ArrayList<PlanItem>()
        var daily = 0
        for (tier in listOf(PlanTier.WEAK, PlanTier.FAIR, PlanTier.STRONG)) {
            val pages = tiers.getValue(tier)
            val quota = minOf(plan.pagesOf(tier), pages.size)
            daily += quota
            val (doneToday, rest) = pages.partition { pageLastRevised[it - 1] == today }
            done += runs(doneToday, tier)
            val slots = if (plan.paused || restDay) 0 else (quota - doneToday.size).coerceAtLeast(0)
            val picked = rest.sortedWith(compareBy<Int> { pageLastRevised[it - 1] }.thenBy { it }).take(slots)
            due += runs(picked.sorted(), tier)
        }
        return TodayPlan(
            itemCount = tiers.values.sumOf { it.size },
            done = done,
            due = due,
            isRestDay = restDay,
            isPaused = plan.paused,
            cycleDays = PlanTier.entries.maxOf { tier -> tierCycleDays(tiers.getValue(tier).size, plan.pagesOf(tier)) },
            dailyPages = daily
        )
    }

    /** Joins consecutive page numbers (ascending) into portions. */
    private fun runs(pages: List<Int>, tier: PlanTier): List<PlanItem> {
        val out = ArrayList<PlanItem>()
        var i = 0
        while (i < pages.size) {
            var j = i
            while (j + 1 < pages.size && pages[j + 1] == pages[j] + 1) j++
            out += PlanItem(PlanUnit.PAGE, pages[i], pages[i], pages[j], tier)
            i = j + 1
        }
        return out
    }

    private fun weaknessRank(state: PassageState): Int = when (state) {
        PassageState.NEEDS_ATTENTION -> 0
        PassageState.DEVELOPING -> 1
        PassageState.NOT_ASSESSED -> 2
        PassageState.RELIABLE -> 3
        PassageState.STABLE_OVER_TIME -> 4
    }
}
