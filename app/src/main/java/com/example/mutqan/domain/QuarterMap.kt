package com.example.mutqan.domain

import com.example.mutqan.quran.QuranRub

/**
 * How well one hizb quarter is held: [average] is the mean strength (0..1) of its pages the user has revised, null when
 * none of them has been; [tracked] of [total] pages have been.
 */
data class QuarterHealth(val average: Float?, val tracked: Int, val total: Int)

/** The numbers behind the juz map: a strength per quarter, the one to look at first, and which quarter follows which. */
object QuarterMap {

    /** The health of [rub] from [scoreOf], the strength of a page, which is null for a page the user has not revised. */
    fun health(rub: QuranRub, scoreOf: (Int) -> Float?): QuarterHealth {
        val pages = rub.startPage..rub.endPage
        val scores = pages.mapNotNull(scoreOf)
        return QuarterHealth(
            average = if (scores.isEmpty()) null else scores.average().toFloat(),
            tracked = scores.size,
            total = pages.count()
        )
    }

    /**
     * The index in [quarters] of the weakest one that has been revised at all (the earliest on a tie), or null when none
     * has: a quarter with no revisions has no strength to compare, so it is never "the weakest".
     */
    fun weakest(quarters: List<QuarterHealth>): Int? =
        quarters.withIndex()
            .filter { it.value.average != null }
            .minByOrNull { it.value.average ?: Float.MAX_VALUE }
            ?.index

    /**
     * The title of the subject the reader is in at [ayahId]: of the [subjects] (start ayah id to title) that start inside
     * [rub], the last one that starts at or before it, else the first one of the quarter; null when the quarter has none.
     */
    fun subjectAt(rub: QuranRub, subjects: List<Pair<Int, String>>, ayahId: Int): String? {
        val inQuarter = subjects.filter { it.first in rub.startAyahId..rub.endAyahId }.sortedBy { it.first }
        return (inQuarter.lastOrNull { it.first <= ayahId } ?: inQuarter.firstOrNull())?.second
    }

    /** The quarter whose beginning joins the end of [rub]: the next one in [rubs] (this juz), else the first of [nextJuz]; null after the last of the Quran. */
    fun following(rub: QuranRub, rubs: List<QuranRub>, nextJuz: List<QuranRub>): QuranRub? =
        rubs.firstOrNull { it.number == rub.number + 1 } ?: nextJuz.firstOrNull { it.number == rub.number + 1 }
}
