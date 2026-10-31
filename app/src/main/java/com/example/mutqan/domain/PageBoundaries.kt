package com.example.mutqan.domain

import kotlin.random.Random

/** The kinds of exercise that are recorded. [code] is stored, so existing codes must never change. */
enum class ExerciseType(val code: Int) {
    /** Given a page number, recite its first and last verse. */
    PAGE_EDGES(1),

    /** Given the first verse of a page, recite its last verse. */
    FIRST_TO_LAST(2),

    /** Given the last verse of the previous page and the first of the next, recite the page between. */
    NEIGHBOURS(3),

    /** A verse with look-alikes starts; pick its true ending. */
    QUIZ_ENDING(4),

    /** Given a verse, pick how the next one begins. */
    QUIZ_NEXT(5),

    /** A verse with some words hidden; pick the missing words. */
    QUIZ_FILL(6),

    /** Consecutive verses, shuffled; put them back in order. */
    QUIZ_ORDER(7),

    /** Given a verse, name its surah and verse number. */
    QUIZ_WHERE(8),

    /** Given a verse, pick the topic it belongs to. */
    QUIZ_TOPIC(9);

    companion object {
        fun fromCode(code: Int): ExerciseType? = entries.firstOrNull { it.code == code }

        /** The page-boundary exercises, which share one place in the Practice list. */
        val PAGE_BOUNDARIES: List<ExerciseType> = listOf(PAGE_EDGES, FIRST_TO_LAST, NEIGHBOURS)

        /** The questions with a right answer the app can check. */
        val QUIZ: List<ExerciseType> = listOf(QUIZ_ENDING, QUIZ_NEXT, QUIZ_FILL, QUIZ_ORDER, QUIZ_WHERE)
    }
}

/** Choosing which page to ask about. */
object PageBoundaries {

    private const val TOTAL_PAGES = 604

    /** A page that can be asked in [type]: the neighbour exercise needs a page on both sides. */
    fun isAskable(page: Int, type: ExerciseType): Boolean =
        if (type == ExerciseType.NEIGHBOURS) page in 2 until TOTAL_PAGES else page in 1..TOTAL_PAGES

    /**
     * The pages to draw from: [covered] (index 0 = page 1) when given, else every page; limited to
     * the pages of [juzPages] when a juz is chosen; and only those that are askable in [type].
     */
    fun pool(covered: BooleanArray?, juzPages: Collection<Int>?, type: ExerciseType): List<Int> =
        (juzPages?.sorted() ?: (1..TOTAL_PAGES).toList())
            .filter { isAskable(it, type) && (covered == null || covered.getOrElse(it - 1) { false }) }

    /** A random page of [pool] that is not among the [recent] ones, unless that leaves nothing. */
    fun pick(pool: List<Int>, recent: Collection<Int>, random: Random = Random): Int? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it !in recent }
        return (fresh.ifEmpty { pool }).random(random)
    }
}
