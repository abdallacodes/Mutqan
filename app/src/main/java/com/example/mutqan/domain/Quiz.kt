package com.example.mutqan.domain

import com.example.mutqan.quran.ArabicNormalization
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranSurah
import com.example.mutqan.quran.QuranWord
import kotlin.random.Random

/** A verse, the page it starts on, and its global id (0 when unknown). */
data class QuizRef(val surah: Int, val ayah: Int, val page: Int, val id: Int = 0)

/** One quiz question. [subject] is the verse it is about; its page is where a miss is recorded. */
sealed interface QuizQuestion {
    val type: ExerciseType
    val subject: QuizRef
}

/**
 * Pick one of [options]. [prompt] is the verse (or part of it) shown, [revealed] the full text shown
 * afterwards, [revealRef] where that text is. [showRef] says whether the question names the subject verse.
 */
data class ChoiceQuestion(
    override val type: ExerciseType,
    override val subject: QuizRef,
    val prompt: MushafSpan,
    val options: List<MushafSpan>,
    val correct: Int,
    val revealed: MushafSpan,
    val revealRef: QuizRef,
    val showRef: Boolean,
    /** True when the options are Quran text (shown in the Mushaf's type); false for plain labels such as topics. */
    val optionsAreVerses: Boolean = true,
    /** A line shown with the answer, e.g. the topics of the verse. */
    val note: String? = null,
    /** For the end-of-verse question: what makes its choices alike. */
    val kind: EndingKind? = null
) : QuizQuestion

/** Why the choices of an end-of-verse question are hard to tell apart. */
enum class EndingKind {
    /** The verse and its look-alikes begin the same way and end differently. */
    LOOKALIKE,

    /** Many verses close with the same words and then a different name or two. */
    CLOSING
}

/** Put [shuffled] back in order by tapping them; [correctOrder] lists indices into [shuffled] in the true order. */
data class OrderQuestion(
    override val type: ExerciseType,
    override val subject: QuizRef,
    val shuffled: List<MushafSpan>,
    val correctOrder: List<Int>,
    /** Verse numbers in the true order, for showing the answer. */
    val ayahNumbers: List<Int>,
    /** Global ids of the verses in the true order, to open them in the Mushaf. */
    val ayahIds: List<Int>
) : QuizQuestion

/** Name the surah of [text], then (if right) its verse number. */
data class WhereQuestion(
    override val type: ExerciseType,
    override val subject: QuizRef,
    val text: MushafSpan,
    val surahOptions: List<Int>,
    val ayahOptions: List<Int>
) : QuizQuestion

/** What a Practice entry asks: one kind of question, or a mixed set. */
enum class QuizMode(val route: String, val type: ExerciseType?) {
    ENDING("ending", ExerciseType.QUIZ_ENDING),
    NEXT("next", ExerciseType.QUIZ_NEXT),
    FINISH("finish", ExerciseType.QUIZ_FINISH),
    ORDER("order", ExerciseType.QUIZ_ORDER),
    WHERE("where", ExerciseType.QUIZ_WHERE),
    DAILY("daily", null);

    companion object {
        fun fromRoute(route: String?): QuizMode = entries.firstOrNull { it.route == route } ?: DAILY
        const val DAILY_SIZE = 10
    }
}

/** Plain text helpers for building questions; no Quran database needed. */
object QuizText {

    fun normalize(text: String): String = ArabicNormalization.normalizeForSearch(text)

    /** The first [count] words, with an ellipsis when more follow. */
    fun opening(words: List<QuranWord>, count: Int): MushafSpan =
        MushafSpan.of(words.take(count)) + if (words.size > count) MushafSpan.More else MushafSpan.Empty

    /** The last [count] words, with an ellipsis before them when words were left out. */
    fun tail(words: List<QuranWord>, count: Int): MushafSpan =
        (if (words.size > count) MushafSpan.More else MushafSpan.Empty) + MushafSpan.of(words.takeLast(count))

    /**
     * [correct] followed by up to [max] - 1 of [candidates] that differ from it and from each other
     * once diacritics are ignored. The result may be shorter; its first item is always [correct].
     */
    fun options(correct: MushafSpan, candidates: List<MushafSpan>, max: Int): List<MushafSpan> {
        val seen = hashSetOf(normalize(correct.plain))
        val result = arrayListOf(correct)
        for (c in candidates) {
            if (result.size >= max) break
            if (c.plain.isNotBlank() && seen.add(normalize(c.plain))) result += c
        }
        return result
    }

    /** The items of [lists] one from each in turn: the first of every list, then the second of every list, and so on. */
    fun <T> interleave(lists: List<List<T>>): List<T> {
        val longest = lists.maxOfOrNull { it.size } ?: 0
        return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }

    /** A shuffle of 0 until [n] that is not already in order (when n > 1). */
    fun shuffledOrder(n: Int, random: Random): List<Int> {
        val base = (0 until n).toList()
        if (n < 2) return base
        repeat(20) {
            val s = base.shuffled(random)
            if (s != base) return s
        }
        return base.reversed()
    }

    /** An index drawn with chance proportional to its weight; null when every weight is zero. */
    fun weightedPick(weights: IntArray, random: Random): Int? {
        val total = weights.sumOf { it.coerceAtLeast(0) }
        if (total <= 0) return null
        var r = random.nextInt(total)
        for (i in weights.indices) {
            val w = weights[i].coerceAtLeast(0)
            if (r < w) return i
            r -= w
        }
        return null
    }
}

/**
 * Builds quiz questions from the Quran text and the look-alike data. Each builder returns null when the
 * verse does not suit that question (no look-alike, last verse of a surah, too short); the caller
 * then tries another verse.
 */
class QuizEngine(
    private val quran: QuranRepository,
    private val similar: SimilarData,
    private val random: Random = Random.Default,
    private val closings: VerseClosings? = null,
    private val topics: TopicIndex? = null,
    private val topicLanguage: TopicLanguage? = null
) {

    fun build(type: ExerciseType, ayahId: Int, hard: Boolean): QuizQuestion? = when (type) {
        ExerciseType.QUIZ_ENDING -> ending(ayahId)
        ExerciseType.QUIZ_NEXT -> next(ayahId, hard)
        ExerciseType.QUIZ_FINISH -> finishing(ayahId)
        ExerciseType.QUIZ_ORDER -> order(ayahId)
        ExerciseType.QUIZ_WHERE -> where(ayahId)
        ExerciseType.QUIZ_TOPIC -> topic(ayahId)
        else -> null
    }

    /** The 114 surahs, read once: the question needs their lengths and where they were revealed. */
    private val surahTable: List<QuranSurah> by lazy { quran.surahs() }

    private fun wordsOf(a: QuranAyah): List<QuranWord> =
        quran.wordsOfAyah(a.surah, a.ayah).filter { !it.isEnd }

    private fun ref(a: QuranAyah) = QuizRef(a.surah, a.ayah, a.firstPage, a.id)

    private fun longest(ranges: List<IntRange>): IntRange? = ranges.maxByOrNull { it.last - it.first }

    private fun finish(
        type: ExerciseType, subject: QuranAyah, prompt: MushafSpan, correct: MushafSpan, distractors: List<MushafSpan>,
        revealed: QuranAyah, showRef: Boolean, optionsAreVerses: Boolean = true, note: String? = null,
        kind: EndingKind? = null
    ): ChoiceQuestion? {
        val options = QuizText.options(correct, distractors, 4)
        if (options.size < 2) return null
        val shuffled = options.shuffled(random)
        return ChoiceQuestion(
            type = type,
            subject = ref(subject),
            prompt = prompt,
            options = shuffled,
            correct = shuffled.indexOf(correct),
            revealed = MushafSpan.of(wordsOf(revealed)),
            revealRef = ref(revealed),
            showRef = showRef,
            optionsAreVerses = optionsAreVerses,
            note = note,
            kind = kind
        )
    }

    /** A verse that shares its beginning with others: which ending belongs to this one? */
    private fun ending(id: Int): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val matches = similar.similarTo(id).filter { it.matchedWords.isNotEmpty() }.take(6)
        if (matches.isEmpty()) return null
        val xWords = wordsOf(x)

        val range = matches.shuffled(random).firstNotNullOfOrNull { m ->
            longest(similar.similarTo(m.ayahId).firstOrNull { it.ayahId == id }?.matchedWords.orEmpty())
        }?.takeIf { it.last in 3 until xWords.size } ?: return null

        val correct = QuizText.opening(xWords.drop(range.last), ENDING_WORDS)
        val distractors = matches.mapNotNull { m ->
            val y = quran.ayahById(m.ayahId) ?: return@mapNotNull null
            val yWords = wordsOf(y)
            val yRange = longest(m.matchedWords) ?: return@mapNotNull null
            if (yRange.last >= yWords.size) null else QuizText.opening(yWords.drop(yRange.last), ENDING_WORDS)
        }
        return finish(
            ExerciseType.QUIZ_ENDING, x, QuizText.tail(xWords.take(range.last), 10), correct, distractors, x, showRef = true
        )
    }

    /** Which opening belongs to the verse after this one? Distractors are what follows its look-alikes. */
    private fun next(id: Int, hard: Boolean): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val n = quran.ayahById(id + 1)?.takeIf { it.surah == x.surah } ?: return null
        val xWords = wordsOf(x)
        if (xWords.size < 2) return null

        val correct = QuizText.opening(wordsOf(n), NEXT_WORDS)
        val distractors = arrayListOf<MushafSpan>()
        similar.similarTo(id).take(5).forEach { m ->
            quran.ayahById(m.ayahId + 1)?.let { distractors += QuizText.opening(wordsOf(it), NEXT_WORDS) }
        }
        repeat(8) {
            val other = quran.ayahById(id + random.nextInt(-40, 41))
            if (other != null && other.id != n.id && other.id != id) distractors += QuizText.opening(wordsOf(other), NEXT_WORDS)
        }
        return finish(
            ExerciseType.QUIZ_NEXT, x, QuizText.tail(xWords, if (hard) 4 else 22), correct, distractors, n, showRef = true
        )
    }

    /**
     * Complete the end of a verse: its last words are hidden and the choices are what other verses put there.
     * Either the verse has look-alikes that begin the same way and end differently, or it closes with words many
     * verses close with ("إن الله" and then a name or two). Whichever of the two the verse has.
     */
    private fun finishing(id: Int): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val words = wordsOf(x)
        return listOf(EndingKind.LOOKALIKE, EndingKind.CLOSING).shuffled(random).firstNotNullOfOrNull { kind ->
            when (kind) {
                EndingKind.LOOKALIKE -> sameStartOtherEnd(x, words)
                EndingKind.CLOSING -> sameClosing(x, words)
            }
        }
    }

    /** A look-alike that opens with the same words as the verse: where they stop being the same, in each of the two. */
    private class Divergence(val ayahId: Int, val inVerse: IntRange, val inOther: IntRange)

    /** The verse with its last words hidden, where look-alikes that open the same way end differently. */
    private fun sameStartOtherEnd(x: QuranAyah, words: List<QuranWord>): ChoiceQuestion? {
        // The shared opening: a stretch of at least three words that starts within the first few.
        fun opening(ranges: List<IntRange>) =
            ranges.filter { it.first <= START_WITHIN && it.last - it.first >= 2 }.maxByOrNull { it.last - it.first }

        val found = similar.similarTo(x.id).filter { it.matchedWords.isNotEmpty() }.take(LOOKALIKES).mapNotNull { m ->
            val other = opening(m.matchedWords) ?: return@mapNotNull null
            val mine = opening(similar.similarTo(m.ayahId).firstOrNull { it.ayahId == x.id }?.matchedWords.orEmpty())
                ?: return@mapNotNull null
            Divergence(m.ayahId, mine, other)
        }
        // What is hidden is what follows the shared opening, and it is the end of the verse: only a few words.
        val chosen = found.filter { words.size - it.inVerse.last in 1..MAX_HIDDEN }.randomOrNull(random) ?: return null
        val cut = chosen.inVerse.last
        val hidden = words.size - cut

        // Look-alikes that stop being the same at the same word come first: they differ only in the ending.
        val distractors = found.sortedBy { if (it.inVerse.last == cut) 0 else 1 }.mapNotNull { d ->
            val y = quran.ayahById(d.ayahId) ?: return@mapNotNull null
            wordsOf(y).drop(d.inOther.last).take(hidden).takeIf { it.isNotEmpty() }?.let { MushafSpan.of(it) }
        }
        return finish(
            ExerciseType.QUIZ_FINISH, x, QuizText.tail(words.take(cut), FINISH_SHOWN) + MushafSpan.Blank,
            MushafSpan.of(words.drop(cut)), distractors, x, showRef = true, kind = EndingKind.LOOKALIKE
        )
    }

    /** The verse with its last words hidden, where many other verses close with other words after the same two. */
    private fun sameClosing(x: QuranAyah, words: List<QuranWord>): ChoiceQuestion? {
        val choice = closings?.choiceFor(x.id, random) ?: return null
        if (words.size < choice.length) return null
        val distractors = choice.rivals.mapNotNull { r ->
            val y = quran.ayahById(r.ayahId) ?: return@mapNotNull null
            wordsOf(y).takeLast(choice.length).takeIf { it.size == choice.length }?.let { MushafSpan.of(it) }
        }
        return finish(
            ExerciseType.QUIZ_FINISH, x, QuizText.tail(words.dropLast(choice.length), FINISH_SHOWN) + MushafSpan.Blank,
            MushafSpan.of(words.takeLast(choice.length)), distractors, x, showRef = true, kind = EndingKind.CLOSING
        )
    }

    /**
     * Which topic is this verse about? The right answer is one of its topics (not a very broad one); the wrong
     * ones are sibling topics first, which is what makes it hard, then others the verse is not listed under.
     */
    private fun topic(id: Int): QuizQuestion? {
        val index = topics ?: return null
        val language = topicLanguage ?: return null
        val x = quran.ayahById(id) ?: return null
        val words = wordsOf(x)
        if (words.size < 4) return null

        val ofVerse = index.topicsOf(id)
        val correct = ofVerse.filter { language.visible(it) && it.verses.size in TOPIC_MIN_VERSES..TOPIC_MAX_VERSES }
            .randomOrNull(random) ?: return null
        val own = ofVerse.map { it.id }.toSet()

        fun usable(t: Topic) = t.id !in own && language.visible(t) && t.verses.size in TOPIC_MIN_VERSES..TOPIC_MAX_VERSES
        val siblings = listOf(correct.thematicParent, correct.ontologyParent, correct.parent)
            .filter { it != -1 }.flatMap(index::childrenOf).filter(::usable).shuffled(random)
        val others = index.topics.shuffled(random).asSequence().filter(::usable).take(20).toList()

        return finish(
            ExerciseType.QUIZ_TOPIC, x,
            QuizText.opening(words, TOPIC_WORDS),
            MushafSpan.label(language.title(correct)),
            (siblings + others).map { MushafSpan.label(language.title(it)) },
            x, showRef = false, optionsAreVerses = false,
            note = ofVerse.filter { language.visible(it) }.take(TOPIC_NOTE).joinToString(" · ") { language.title(it) }.ifEmpty { null }
        )
    }

    /** Four consecutive verses of one surah, shuffled. */
    private fun order(id: Int): QuizQuestion? {
        val first = quran.ayahById(id) ?: return null
        val ayahs = (0 until ORDER_COUNT).map { quran.ayahById(id + it) ?: return null }
        if (ayahs.any { it.surah != first.surah }) return null
        val texts = ayahs.map { a ->
            val w = wordsOf(a)
            if (w.size > 9) QuizText.opening(w, 8) else MushafSpan.of(w)
        }
        val perm = QuizText.shuffledOrder(ORDER_COUNT, random)
        return OrderQuestion(
            type = ExerciseType.QUIZ_ORDER,
            subject = ref(first),
            shuffled = perm.map { texts[it] },
            correctOrder = (0 until ORDER_COUNT).map { perm.indexOf(it) },
            ayahNumbers = ayahs.map { it.ayah },
            ayahIds = ayahs.map { it.id }
        )
    }

    /** Name the surah of a verse, then its number. Verses that are repeated word for word are skipped. */
    private fun where(id: Int): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val words = wordsOf(x)
        if (words.size < 4) return null
        val lookalikes = similar.similarTo(id)
        if (lookalikes.any { it.score >= IDENTICAL_SCORE }) return null

        // The wrong surahs are ones this verse could be taken for: look-alikes first, then surahs like the right one in
        // length and period (see SurahChoices), never a three-verse surah beside Al-Baqarah.
        val table = surahTable
        val answer = table.firstOrNull { it.id == x.surah } ?: return null
        val resembling = lookalikes.filter { it.score >= LOOKALIKE_SURAH_SCORE }.mapNotNull { quran.ayahById(it.ayahId)?.surah }.distinct()
        val surahs = listOf(x.surah) + SurahChoices.distractors(answer, table, resembling, WHERE_OPTIONS - 1, random)

        val count = answer.versesCount
        val ayahs = linkedSetOf(x.ayah)
        for (d in listOf(1, -1, 2, -2, 3, -3)) {
            if (ayahs.size >= 4) break
            if (x.ayah + d in 1..count) ayahs += x.ayah + d
        }
        var guard = 0
        while (ayahs.size < minOf(4, count) && guard++ < 30) ayahs += random.nextInt(1, count + 1)

        return WhereQuestion(
            type = ExerciseType.QUIZ_WHERE,
            subject = ref(x),
            text = QuizText.opening(words, WHERE_WORDS),
            surahOptions = surahs.shuffled(random),
            ayahOptions = ayahs.toList().sorted()
        )
    }

    private companion object {
        const val ENDING_WORDS = 5
        const val NEXT_WORDS = 6
        const val FINISH_SHOWN = 12
        const val START_WITHIN = 3
        const val MAX_HIDDEN = 5
        const val LOOKALIKES = 8
        const val ORDER_COUNT = 4
        const val WHERE_WORDS = 22
        const val IDENTICAL_SCORE = 95
        const val WHERE_OPTIONS = 5
        const val LOOKALIKE_SURAH_SCORE = 60
        const val TOPIC_MIN_VERSES = 3
        const val TOPIC_MAX_VERSES = 150
        const val TOPIC_WORDS = 30
        const val TOPIC_NOTE = 4
    }
}
