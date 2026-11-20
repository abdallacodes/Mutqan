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
) : QuizQuestion {
    /**
     * Whether [taps] (indices into [shuffled], in the order tapped) put the verses in their true order. It is the order of
     * what is shown that counts, so verses that look the same (Ar-Rahman's refrain) can go either way round: they were
     * once graded by place, and the right order could be marked wrong.
     */
    fun isRight(taps: List<Int>): Boolean =
        taps.size == correctOrder.size && taps.indices.all { QuizText.sameText(shuffled[taps[it]], shuffled[correctOrder[it]]) }

    /** Whether verse [index] of [shuffled] was tapped into a place where it, or a verse that looks the same, belongs. */
    fun placedRight(taps: List<Int>, index: Int): Boolean {
        val at = taps.indexOf(index)
        return at in correctOrder.indices && QuizText.sameText(shuffled[index], shuffled[correctOrder[at]])
    }
}

/** Name the surah of [text], then (if right) its verse number. */
data class WhereQuestion(
    override val type: ExerciseType,
    override val subject: QuizRef,
    val text: MushafSpan,
    val surahOptions: List<Int>,
    val ayahOptions: List<Int>
) : QuizQuestion

/**
 * What a Practice entry asks: one kind of question, or a mixed set of ten. [FOCUS] is a set drawn only from the pages
 * that most need it (see [FocusPages]), whatever choice of pages is saved.
 */
enum class QuizMode(val route: String, val type: ExerciseType?) {
    ENDING("ending", ExerciseType.QUIZ_ENDING),
    NEXT("next", ExerciseType.QUIZ_NEXT),
    FINISH("finish", ExerciseType.QUIZ_FINISH),
    ORDER("order", ExerciseType.QUIZ_ORDER),
    WHERE("where", ExerciseType.QUIZ_WHERE),
    DAILY("daily", null),
    FOCUS("focus", null);

    /** A set of [DAILY_SIZE] mixed questions rather than one kind in a row. */
    val isSet: Boolean get() = type == null

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

    /** Where two verses share a stretch of words: the stretch in each, 1-based and inclusive. */
    data class SharedRun(val inA: IntRange, val inB: IntRange) {
        val length: Int get() = inA.last - inA.first + 1
    }

    /**
     * The stretch of words verse [a] and verse [b] share, word for word ([a] and [b] are their words, each normalized:
     * no diacritics or pause signs): the longest of at least [least] words or, when [hint] is given (the look-alike data's
     * own range in [a]), the longest that overlaps it. The data's ranges are only a hint: they match loosely (Al-Kahf 50's
     * "كان من" for Al-Baqarah 34's "وكان من"), and a choice cut where one of them ends can start in the middle of a phrase.
     */
    fun sharedRun(a: List<String>, b: List<String>, hint: IntRange? = null, least: Int = 2): SharedRun? {
        var best: SharedRun? = null
        var bestOnHint = false
        var previous = IntArray(b.size + 1)
        var current = IntArray(b.size + 1)
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val length = if (a[i - 1].isNotEmpty() && a[i - 1] == b[j - 1]) previous[j - 1] + 1 else 0
                current[j] = length
                if (length < least) continue
                val run = SharedRun((i - length + 1)..i, (j - length + 1)..j)
                val onHint = hint != null && run.inA.first <= hint.last && hint.first <= run.inA.last
                if (best == null || (onHint && !bestOnHint) || (onHint == bestOnHint && length > best.length)) {
                    best = run
                    bestOnHint = onHint
                }
            }
            val done = previous
            previous = current
            current = done
        }
        return best
    }

    /** A look-alike going on from where a question stops: which one ([index] into those given), after [from] of its words. */
    data class Branch(val index: Int, val from: Int)

    /** Where a question stops ([cut]: how many of the verse's words it shows) and the look-alikes going on from there. */
    data class Parting(val cut: Int, val branches: List<Branch>)

    /**
     * Where a question about [verse] and its look-alikes stops, so that every choice goes on from the words shown. Each of
     * [others] (a look-alike's normalized words, and the stretch it shares with the verse) counts at a cut when it shares
     * at least the [shared] words up to it, and goes on from its word matching the cut. Of the cuts [allowed], the one
     * with the most look-alikes going on differently (from the verse and from each other), the earliest of equals.
     * Closest first: those sharing the most past the cut, as Taha 116 shares "أبى" with Al-Baqarah 34 and stops there.
     * A question once stopped where one look-alike parted, Taha's a word later than the rest, and the other endings did
     * not follow the words shown.
     */
    fun parting(verse: List<String>, others: List<Pair<List<String>, SharedRun>>, allowed: (Int) -> Boolean, shared: Int): Parting? {
        var best: Parting? = null
        for (cut in others.map { it.second.inA.last }.distinct().sorted()) {
            if (!allowed(cut)) continue
            val seen = hashSetOf(verse.drop(cut).joinToString(" "))
            val branches = others.indices
                .filter { others[it].second.let { run -> run.inA.first <= cut - shared + 1 && run.inA.last >= cut } }
                .sortedWith(compareByDescending<Int> { others[it].second.inA.last }.thenByDescending { others[it].second.length })
                .mapNotNull { i ->
                    val (words, run) = others[i]
                    val from = run.inB.last - (run.inA.last - cut)
                    val goesOn = words.drop(from).joinToString(" ")
                    if (goesOn.isEmpty() || !seen.add(goesOn)) null else Branch(i, from)
                }
            if (branches.isNotEmpty() && (best == null || branches.size > best.branches.size)) best = Parting(cut, branches)
        }
        return best
    }

    /** Whether a reader may pause after [word]: it carries a pause sign (not "do not stop", nor the breathless sakta). */
    fun pauses(word: QuranWord): Boolean = word.hafs.any { it in PAUSE_SIGNS }

    /** Whether two spans read the same once diacritics are ignored. */
    fun sameText(a: MushafSpan, b: MushafSpan): Boolean = normalize(a.plain) == normalize(b.plain)

    /**
     * What a choice shows of [words], from their start (a verse's opening, or what it goes on with): all of them when the
     * verse ends within [most]; else up to the last pause sign within [most], when that leaves at least [least]; else the
     * first [fallback]. Never an ellipsis: one choice with "…" among choices without stands out from them (the right one,
     * in one "next verse" question in sixteen).
     */
    fun phrase(words: List<QuranWord>, most: Int, fallback: Int, least: Int = 3): MushafSpan {
        if (words.size <= most) return MushafSpan.of(words)
        val pause = (most - 1 downTo least - 1).firstOrNull { pauses(words[it]) }
        return MushafSpan.of(words.take(if (pause != null) pause + 1 else fallback))
    }

    /**
     * What a choice shows of [words] from their end (how a verse closes): all of them when the verse has at most [most];
     * else from just after the earliest pause sign among its last [most], when that leaves at least [least]; else the last
     * [fallback]. Never an ellipsis, as with [phrase].
     */
    fun phraseEnd(words: List<QuranWord>, most: Int, fallback: Int, least: Int = 3): MushafSpan {
        if (words.size <= most) return MushafSpan.of(words)
        val pause = (words.size - most - 1 until words.size - least).firstOrNull { it >= 0 && pauses(words[it]) }
        return MushafSpan.of(if (pause != null) words.drop(pause + 1) else words.takeLast(fallback))
    }

    /**
     * The verses of an order question as shown: a verse of up to [whole] words whole, a longer one by its first [words]
     * and an ellipsis; and more of them wherever two would look the same (Al-Baqarah 68 and 70 share their first eight
     * words), until they differ or are whole. Verses the same word for word stay the same; see [OrderQuestion.isRight].
     */
    fun orderTexts(verses: List<List<QuranWord>>, words: Int, whole: Int): List<MushafSpan> {
        val plain = verses.map { v -> v.map { normalize(it.hafs) } }
        val shown = IntArray(verses.size) { if (verses[it].size > whole) words else verses[it].size }
        while (true) {
            var grew = false
            for (same in verses.indices.groupBy { plain[it].take(shown[it]) }.values) {
                if (same.size < 2) continue
                for (i in same) if (shown[i] < verses[i].size) {
                    shown[i]++
                    grew = true
                }
            }
            if (!grew) break
        }
        return verses.indices.map { i ->
            MushafSpan.of(verses[i].take(shown[i])) + if (shown[i] < verses[i].size) MushafSpan.More else MushafSpan.Empty
        }
    }

    // The small high signs for: may stop (sad-lam-alef), better to stop (qaf-lam-alef), must stop (meem), may stop (jeem),
    // stop at one of the two (three dots).
    private val PAUSE_SIGNS = setOf('\u06D6', '\u06D7', '\u06D8', '\u06DA', '\u06DB')

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

    /** A look-alike of a verse: its words, the same normalized, and the stretch it shares with the verse. */
    private class Lookalike(val words: List<QuranWord>, val plain: List<String>, val run: QuizText.SharedRun)

    private fun plainOf(words: List<QuranWord>): List<String> = words.map { QuizText.normalize(it.hafs) }

    /**
     * The look-alikes of [x] (whose normalized words are [xPlain]) and the stretch each shares with it, found by comparing
     * the words: the look-alike data only hints where (see [QuizText.sharedRun]).
     */
    private fun lookalikes(x: QuranAyah, xPlain: List<String>): List<Lookalike> =
        similar.similarTo(x.id).filter { it.matchedWords.isNotEmpty() }.take(LOOKALIKES).mapNotNull { m ->
            val y = quran.ayahById(m.ayahId) ?: return@mapNotNull null
            val yWords = wordsOf(y)
            val yPlain = plainOf(yWords)
            val hint = longest(similar.similarTo(m.ayahId).firstOrNull { it.ayahId == x.id }?.matchedWords.orEmpty())
            QuizText.sharedRun(xPlain, yPlain, hint, least = SHARED)?.let { Lookalike(yWords, yPlain, it) }
        }

    /**
     * A verse that shares words with others and goes on differently: which ending is this one's? The question stops
     * where they part, the same word for all of them, so every choice goes on from the words shown (see
     * [QuizText.parting]); each choice runs to the end of its verse or to a pause sign.
     */
    private fun ending(id: Int): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val xWords = wordsOf(x)
        val xPlain = plainOf(xWords)
        val others = lookalikes(x, xPlain)
        if (others.isEmpty()) return null
        val parting = QuizText.parting(xPlain, others.map { it.plain to it.run }, { it in SHARED until xWords.size }, SHARED)
            ?: return null
        val cut = parting.cut
        val correct = QuizText.phrase(xWords.drop(cut), ENDING_MOST, ENDING_WORDS)
        val distractors = parting.branches.map { QuizText.phrase(others[it.index].words.drop(it.from), ENDING_MOST, ENDING_WORDS) }
        return finish(ExerciseType.QUIZ_ENDING, x, QuizText.tail(xWords.take(cut), 10), correct, distractors, x, showRef = true)
    }

    /** Which opening belongs to the verse after this one? Distractors are what follows its look-alikes. */
    private fun next(id: Int, hard: Boolean): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val n = quran.ayahById(id + 1)?.takeIf { it.surah == x.surah } ?: return null
        val xWords = wordsOf(x)
        if (xWords.size < 2) return null

        fun opening(a: QuranAyah) = QuizText.phrase(wordsOf(a), NEXT_MOST, NEXT_WORDS)
        val correct = opening(n)
        val distractors = arrayListOf<MushafSpan>()
        // What follows each look-alike, in its own surah: after the last verse of a surah comes another surah's first
        // (Sad 1 was offered after Al-Fatiha 2, for As-Saffat 182).
        similar.similarTo(id).take(5).forEach { m ->
            val y = quran.ayahById(m.ayahId) ?: return@forEach
            quran.ayahById(m.ayahId + 1)?.takeIf { it.surah == y.surah }?.let { distractors += opening(it) }
        }
        repeat(8) {
            val other = quran.ayahById(id + random.nextInt(-40, 41))
            if (other != null && other.id != n.id && other.id != id) distractors += opening(other)
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

    /**
     * The verse with its last words hidden, where look-alikes that open the same way end differently. It stops where they
     * part, the same word for all of them (see [QuizText.parting]), so every choice goes on from the words shown.
     */
    private fun sameStartOtherEnd(x: QuranAyah, words: List<QuranWord>): ChoiceQuestion? {
        // Look-alikes that open the same way: they share a stretch of at least three words starting within the first few
        // of both.
        val plain = plainOf(words)
        val others = lookalikes(x, plain).filter { it.run.inA.first <= START_WITHIN && it.run.inB.first <= START_WITHIN }
        if (others.isEmpty()) return null
        // What is hidden is what follows the shared opening, and it is the end of the verse: only a few words.
        val parting = QuizText.parting(plain, others.map { it.plain to it.run }, { words.size - it in 1..MAX_HIDDEN }, SHARED)
            ?: return null
        val cut = parting.cut
        val hidden = words.size - cut
        val distractors = parting.branches.map { MushafSpan.of(others[it.index].words.drop(it.from).take(hidden)) }
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
        val texts = QuizText.orderTexts(ayahs.map { wordsOf(it) }, words = ORDER_WORDS, whole = ORDER_WHOLE)
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
        /** A choice of the ending question: to the end of its verse or a pause sign within [ENDING_MOST], else this many words. */
        const val ENDING_WORDS = 5
        const val ENDING_MOST = 7
        /** The least words a look-alike shares with the verse up to where the question stops. */
        const val SHARED = 3
        /** A choice of the next-verse question: to a pause sign within [NEXT_MOST] words, else this many. */
        const val NEXT_WORDS = 6
        const val NEXT_MOST = 8
        /** An order question shows a verse of up to [ORDER_WHOLE] words whole, a longer one by its first [ORDER_WORDS]. */
        const val ORDER_WORDS = 8
        const val ORDER_WHOLE = 9
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
