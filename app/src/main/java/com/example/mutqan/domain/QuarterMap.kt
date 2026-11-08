package com.example.mutqan.domain

import com.example.mutqan.quran.QuranRub

/**
 * How well one hizb quarter is held: [average] is the mean strength (0..1) of its pages the user has revised, null when
 * none of them has been; [tracked] of [total] pages have been.
 */
data class QuarterHealth(val average: Float?, val tracked: Int, val total: Int)

/** One subject the user gave and the verses it covers (global ayah ids, both ends included). */
data class SubjectSpan(val startAyahId: Int, val endAyahId: Int, val title: String)

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

    /**
     * The verses each subject covers, in Mushaf order: from the verse it starts at up to the verse before the next subject
     * of the same quarter, or to the quarter's last verse. A subject never runs into the next quarter, and the verses of a
     * quarter before its first subject belong to none. A subject that starts at the same verse as a later one is covered by
     * it (it would have no verses). [subjects] are (start ayah id, title) pairs, in any order.
     */
    fun subjectSpans(rubs: List<QuranRub>, subjects: List<Pair<Int, String>>): List<SubjectSpan> {
        val sorted = subjects.sortedBy { it.first }
        val spans = ArrayList<SubjectSpan>()
        for (rub in rubs.sortedBy { it.startAyahId }) {
            val inQuarter = sorted.filter { it.first in rub.startAyahId..rub.endAyahId }
            inQuarter.forEachIndexed { index, (start, title) ->
                val end = if (index + 1 < inQuarter.size) inQuarter[index + 1].first - 1 else rub.endAyahId
                if (end >= start) spans += SubjectSpan(start, end, title)
            }
        }
        return spans
    }

    /** The quarter whose beginning joins the end of [rub]: the next one in [rubs] (this juz), else the first of [nextJuz]; null after the last of the Quran. */
    fun following(rub: QuranRub, rubs: List<QuranRub>, nextJuz: List<QuranRub>): QuranRub? =
        rubs.firstOrNull { it.number == rub.number + 1 } ?: nextJuz.firstOrNull { it.number == rub.number + 1 }
}
