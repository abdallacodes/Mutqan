package com.example.mutqan.domain

import com.example.mutqan.quran.QuranSurah
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * The wrong answers of "which surah is this verse in?". They have to be surahs the verse could really be taken for, so a
 * long surah is never offered next to a three-verse one: first the surahs whose wording resembles the verse, then the
 * ones most like the right surah in length, in place of revelation and in where they stand in the Mushaf.
 */
object SurahChoices {

    /** Longer than this many times the other, and two surahs are not alike in length at all. */
    private const val LENGTH_SPAN = 4.0

    /** Surahs further apart in length than this are used only when too few others exist. */
    private const val MAX_RATIO = 5.0

    /** How many surahs that merely word things alike may be offered; more would make the question about them alone. */
    private const val MAX_LOOKALIKES = 2

    /** How much chance may shuffle surahs that are about equally alike, so the same four do not always come up. */
    private const val JITTER = 0.9

    private fun ratio(a: QuranSurah, b: QuranSurah): Double =
        maxOf(a.versesCount, b.versesCount).toDouble() / minOf(a.versesCount, b.versesCount).coerceAtLeast(1)

    /** How easily [other] is mistaken for [answer], from 0 (not at all) up to about 4.4. Length counts most. */
    fun closeness(answer: QuranSurah, other: QuranSurah): Double {
        val length = (1.0 - ln(ratio(answer, other)) / ln(LENGTH_SPAN)).coerceAtLeast(0.0)
        val place = if (answer.revelationPlace.equals(other.revelationPlace, ignoreCase = true)) 0.8 else 0.0
        val gap = abs(answer.id - other.id)
        val neighbour = when {
            gap <= 2 -> 0.8
            gap <= 6 -> 0.4
            else -> 0.0
        }
        val juz = if (answer.startJuz == other.startJuz) 0.3 else 0.0
        val orderGap = abs(answer.revelationOrder - other.revelationOrder)
        val period = when {
            orderGap <= 4 -> 0.5
            orderGap <= 12 -> 0.25
            else -> 0.0
        }
        return 2.0 * length + place + neighbour + juz + period
    }

    /**
     * Up to [count] surahs other than [answer] to offer beside it. [lookalikes] are the surahs of verses that resemble this
     * one, best first; at most two of them are used. The rest are the ones most like [answer], with some chance in it.
     */
    fun distractors(answer: QuranSurah, all: List<QuranSurah>, lookalikes: List<Int>, count: Int, random: Random): List<Int> {
        val known = all.map { it.id }.toSet()
        val chosen = LinkedHashSet<Int>()
        lookalikes.filter { it != answer.id && it in known }.take(MAX_LOOKALIKES).forEach { chosen += it }
        val ranked = all
            .filter { it.id != answer.id && it.id !in chosen }
            .map { it to closeness(answer, it) + random.nextDouble() * JITTER }
            .sortedByDescending { it.second }
            .map { it.first }
        val (near, far) = ranked.partition { ratio(answer, it) <= MAX_RATIO }
        for (s in near + far) {
            if (chosen.size >= count) break
            chosen += s.id
        }
        return chosen.take(count)
    }
}
