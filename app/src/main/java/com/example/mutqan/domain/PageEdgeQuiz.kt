package com.example.mutqan.domain

import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlin.random.Random

/** How the page quiz goes from one page to the next. */
enum class PageOrder {
    /** The page after this one, round to the first after the last. */
    FORWARD,

    /** Any page the Practice choice allows. */
    RANDOM,

    /** The same page again, with other choices. */
    STAY;

    /** The page to ask after [page]; [pool] are the pages [RANDOM] may draw from and [recent] the ones it just used. */
    fun next(page: Int, pool: List<Int>, recent: Collection<Int>, random: Random = Random.Default): Int = when (this) {
        FORWARD -> page % PageEdgeQuiz.PAGES + 1
        STAY -> page
        RANDOM -> PageBoundaries.pick(pool, recent + page, random) ?: page
    }
}

/**
 * Which verse starts (or ends) page [page]? [options] show the start (or the end) of five verses; [refs] says which verse
 * each is, and [correct] is the index of the right one. [answer] is the right verse and [answerText] all of it.
 */
data class PageEdgeQuestion(
    val page: Int,
    val end: Boolean,
    val options: List<MushafSpan>,
    val refs: List<QuizRef>,
    val correct: Int,
    val answer: QuizRef,
    val answerText: MushafSpan
)

/**
 * Builds the page-edge question. The wrong choices are verses that are really near the right one, so only knowing the
 * page tells them apart: the other verses on it, the verses either side of it, look-alikes of the right verse, and verses
 * from pages close by. Each choice is cut to [SHOWN] words: the first ones for a page's start, the last ones for its end.
 */
class PageEdgeQuiz(
    private val quran: QuranRepository,
    private val similar: SimilarData,
    private val random: Random = Random.Default
) {

    fun build(page: Int, end: Boolean): PageEdgeQuestion? {
        val onPage = quran.ayahsOnPage(page)
        val correct = (if (end) onPage.lastOrNull() else onPage.firstOrNull()) ?: return null
        val before = if (page > 1) quran.ayahsOnPage(page - 1).lastOrNull() else null
        val after = if (page < PAGES) quran.ayahsOnPage(page + 1).firstOrNull() else null

        val here = onPage.filter { it.id != correct.id }.shuffled(random)
        val either = listOfNotNull(before, after).shuffled(random)
        val alike = similar.similarTo(correct.id).take(6).mapNotNull { quran.ayahById(it.ayahId) }.shuffled(random).take(3)
        val close = (1..NEAR_TRIES).mapNotNull {
            quran.ayahsOnPage((page + random.nextInt(-NEAR_PAGES, NEAR_PAGES + 1)).coerceIn(1, PAGES)).randomOrNull(random)
        }
        val candidates = QuizText.interleave(listOf(here, either, alike, close)).filter { it.id != correct.id }.distinctBy { it.id }

        val correctSpan = shown(correct, end)
        val spans = candidates.map { shown(it, end) }
        val options = QuizText.options(correctSpan, spans, OPTIONS)
        if (options.size < 2) return null
        // QuizText.options hands back the very spans it was given, so each can be traced to its verse.
        val owners = options.map { span -> if (span === correctSpan) correct else candidates[spans.indexOfFirst { it === span }] }

        val order = options.indices.shuffled(random)
        return PageEdgeQuestion(
            page = page,
            end = end,
            options = order.map { options[it] },
            refs = order.map { ref(owners[it]) },
            correct = order.indexOf(0),
            answer = ref(correct),
            answerText = MushafSpan.of(wordsOf(correct))
        )
    }

    private fun wordsOf(a: QuranAyah): List<QuranWord> = quran.wordsOfAyah(a.surah, a.ayah).filter { !it.isEnd }

    private fun shown(a: QuranAyah, end: Boolean): MushafSpan =
        wordsOf(a).let { if (end) QuizText.tail(it, SHOWN) else QuizText.opening(it, SHOWN) }

    private fun ref(a: QuranAyah) = QuizRef(a.surah, a.ayah, a.firstPage, a.id)

    companion object {
        const val PAGES = 604
        const val OPTIONS = 5
        const val SHOWN = 6
        private const val NEAR_PAGES = 4
        private const val NEAR_TRIES = 8
    }
}
