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

    /** Retired: a verse with some words hidden. No longer asked; kept so results recorded under it still read. */
    QUIZ_FILL(6),

    /** Consecutive verses, shuffled; put them back in order. */
    QUIZ_ORDER(7),

    /** Given a verse, name its surah and verse number. */
    QUIZ_WHERE(8),

    /** Given a verse, pick the topic it belongs to. */
    QUIZ_TOPIC(9),

    /** Given any verse, say the first verse of its page. */
    AYAH_PAGE_START(10),

    /** Given any verse, say the last verse of its page. */
    AYAH_PAGE_END(11),

    /** A verse with its last words hidden, among verses that begin or close alike; pick the words that end it. */
    QUIZ_FINISH(12),

    /** Given how a hizb quarter ends, recall how the next one begins (the "joint" between two quarters). */
    QUARTER_JOINT(13),

    /** Given a page number, pick (or say) the verse that starts it. */
    QUIZ_PAGE_START(14),

    /** Given a page number, pick (or say) the verse that ends it. */
    QUIZ_PAGE_END(15);

    companion object {
        fun fromCode(code: Int): ExerciseType? = entries.firstOrNull { it.code == code }

        /** The exercises that start from a page: its number, its first verse, or the verses either side. */
        val FROM_PAGE: List<ExerciseType> = listOf(PAGE_EDGES, FIRST_TO_LAST, NEIGHBOURS)

        /** The exercises that start from a verse and ask for the edge of its page. */
        val FROM_VERSE: List<ExerciseType> = listOf(AYAH_PAGE_START, AYAH_PAGE_END)

        /** Every page-edge exercise. */
        val PAGE_BOUNDARIES: List<ExerciseType> = FROM_PAGE + FROM_VERSE

        /** The page-edge quiz: which verse starts, or ends, a page. */
        val PAGE_QUIZ: List<ExerciseType> = listOf(QUIZ_PAGE_START, QUIZ_PAGE_END)

        /** The questions with a right answer the app can check. */
        val QUIZ: List<ExerciseType> = listOf(QUIZ_ENDING, QUIZ_NEXT, QUIZ_FINISH, QUIZ_ORDER, QUIZ_WHERE)
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

    /**
     * The verse to show for [type] from the verses [onPage] (global ids, in order): any of them but the one that is
     * the answer, so the question is never answered by what it shows. A page with a single verse shows that one.
     */
    fun promptVerse(onPage: List<Int>, type: ExerciseType, random: Random = Random): Int? {
        val answer = when (type) {
            ExerciseType.AYAH_PAGE_START -> onPage.firstOrNull()
            ExerciseType.AYAH_PAGE_END -> onPage.lastOrNull()
            else -> null
        }
        val others = onPage.filter { it != answer }
        return (others.ifEmpty { onPage }).randomOrNull(random)
    }

    /** A random page of [pool] that is not among the [recent] ones, unless that leaves nothing. */
    fun pick(pool: List<Int>, recent: Collection<Int>, random: Random = Random): Int? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it !in recent }
        return (fresh.ifEmpty { pool }).random(random)
    }
}
