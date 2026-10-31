package com.example.mutqan.domain

import com.example.mutqan.quran.ArabicNormalization
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import kotlin.math.abs
import kotlin.random.Random

/** A verse and the page it starts on. */
data class QuizRef(val surah: Int, val ayah: Int, val page: Int)

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
    val prompt: String,
    val options: List<String>,
    val correct: Int,
    val revealed: String,
    val revealRef: QuizRef,
    val showRef: Boolean,
    /** True when the options are Quran text (shown in the Mushaf's type); false for plain labels such as topics. */
    val optionsAreVerses: Boolean = true,
    /** A line shown with the answer, e.g. the topics of the verse. */
    val note: String? = null
) : QuizQuestion

/** Put [shuffled] back in order by tapping them; [correctOrder] lists indices into [shuffled] in the true order. */
data class OrderQuestion(
    override val type: ExerciseType,
    override val subject: QuizRef,
    val shuffled: List<String>,
    val correctOrder: List<Int>,
    /** Verse numbers in the true order, for showing the answer. */
    val ayahNumbers: List<Int>
) : QuizQuestion

/** Name the surah of [text], then (if right) its verse number. */
data class WhereQuestion(
    override val type: ExerciseType,
    override val subject: QuizRef,
    val text: String,
    val surahOptions: List<Int>,
    val ayahOptions: List<Int>
) : QuizQuestion

/** What a Practice entry asks: one kind of question, or a mixed set. */
enum class QuizMode(val route: String, val type: ExerciseType?) {
    ENDING("ending", ExerciseType.QUIZ_ENDING),
    NEXT("next", ExerciseType.QUIZ_NEXT),
    FILL("fill", ExerciseType.QUIZ_FILL),
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

    const val BLANK = "_____"

    fun normalize(text: String): String = ArabicNormalization.normalizeForSearch(text)

    /** The first [count] words, with an ellipsis when more follow. */
    fun opening(words: List<String>, count: Int): String =
        words.take(count).joinToString(" ") + if (words.size > count) " …" else ""

    /** The last [count] words, with an ellipsis before them when words were left out. */
    fun tail(words: List<String>, count: Int): String =
        (if (words.size > count) "… " else "") + words.takeLast(count).joinToString(" ")

    /**
     * [correct] followed by up to [max] - 1 of [candidates] that differ from it and from each other
     * once diacritics are ignored. The result may be shorter; its first item is always [correct].
     */
    fun options(correct: String, candidates: List<String>, max: Int): List<String> {
        val seen = hashSetOf(normalize(correct))
        val result = arrayListOf(correct)
        for (c in candidates) {
            if (result.size >= max) break
            if (c.isNotBlank() && seen.add(normalize(c))) result += c
        }
        return result
    }

    /** [words] with [length] words from [start] replaced by a blank, cut to a window of [window] words either side. */
    fun blanked(words: List<String>, start: Int, length: Int, window: Int): String {
        val from = maxOf(0, start - window)
        val to = minOf(words.size, start + length + window)
        val parts = words.subList(from, start) + BLANK + words.subList(start + length, to)
        return (if (from > 0) "… " else "") + parts.joinToString(" ") + if (to < words.size) " …" else ""
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
    private val morphology: Morphology? = null,
    private val topics: TopicIndex? = null,
    private val topicLanguage: TopicLanguage? = null
) {

    fun build(type: ExerciseType, ayahId: Int, hard: Boolean): QuizQuestion? = when (type) {
        ExerciseType.QUIZ_ENDING -> ending(ayahId)
        ExerciseType.QUIZ_NEXT -> next(ayahId, hard)
        ExerciseType.QUIZ_FILL -> fill(ayahId, hard)
        ExerciseType.QUIZ_ORDER -> order(ayahId)
        ExerciseType.QUIZ_WHERE -> where(ayahId)
        ExerciseType.QUIZ_TOPIC -> topic(ayahId)
        else -> null
    }

    private fun wordsOf(a: QuranAyah): List<String> =
        quran.wordsOfAyah(a.surah, a.ayah).filter { !it.isEnd }.map { it.hafs }

    private fun ref(a: QuranAyah) = QuizRef(a.surah, a.ayah, a.firstPage)

    private fun longest(ranges: List<IntRange>): IntRange? = ranges.maxByOrNull { it.last - it.first }

    private fun finish(
        type: ExerciseType, subject: QuranAyah, prompt: String, correct: String, distractors: List<String>,
        revealed: QuranAyah, showRef: Boolean, optionsAreVerses: Boolean = true, note: String? = null
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
            revealed = wordsOf(revealed).joinToString(" "),
            revealRef = ref(revealed),
            showRef = showRef,
            optionsAreVerses = optionsAreVerses,
            note = note
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
        val distractors = arrayListOf<String>()
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

    /** Hide one to three words of a verse; the wrong choices are spans from its look-alikes and neighbours. */
    private fun fill(id: Int, hard: Boolean): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val words = wordsOf(x)
        if (words.size < 5) return null
        val length = if (hard) 3 else random.nextInt(1, 3)
        val start = random.nextInt(0, words.size - length + 1)
        val correct = words.subList(start, start + length).joinToString(" ")

        fun spansNear(other: List<String>): List<String> =
            (0..other.size - length).sortedBy { abs(it - start) }.take(2)
                .map { other.subList(it, it + length).joinToString(" ") }

        val lookalikes = arrayListOf<String>()
        similar.similarTo(id).take(5).forEach { m ->
            quran.ayahById(m.ayahId)?.let { lookalikes += spansNear(wordsOf(it)) }
        }
        // What fits the gap is judged by the words around it, not by chance: other forms of the hidden words,
        // what follows the word before the gap (or comes before the word after it) elsewhere in the Quran, and
        // the same stretch of a look-alike. Spans of random neighbouring verses are plainly wrong, so they only
        // fill up the options when nothing better exists. The harder level leads with the closest forms.
        val variants = morphVariants(x, words, start, length)
        val fitting = fittingSpans(x, words, start, length)
        val pool = if (hard) variants + fitting + lookalikes else variants.take(1) + fitting + lookalikes + variants.drop(1)
        // A span made only of words already shown around the gap cannot be what was removed.
        val visible = words.filterIndexed { i, _ -> i < start || i >= start + length }.map(QuizText::normalize).toSet()
        val ranked = pool.sortedBy { span -> if (span.split(" ").all { QuizText.normalize(it) in visible }) 1 else 0 }
        val fillers = arrayListOf<String>()
        if (QuizText.options(correct, ranked, 4).size < 4) {
            repeat(6) {
                val other = quran.ayahById(id + random.nextInt(-30, 31))
                if (other != null && other.id != id) fillers += spansNear(wordsOf(other))
            }
        }
        val ordered = ranked + fillers
        return finish(
            ExerciseType.QUIZ_FILL, x, QuizText.blanked(words, start, length, FILL_WINDOW), correct, ordered, x, showRef = true
        )
    }

    /**
     * Spans of the same length that stand where the hidden one does in other verses: those that follow the word
     * before the gap, and those that come before the word after it, found through that word's lemma. The two
     * kinds are interleaved. Empty without morphology data.
     */
    private fun fittingSpans(x: QuranAyah, words: List<String>, start: Int, length: Int): List<String> {
        val m = morphology ?: return emptyList()
        val here = m.wordsOfAyah(x.surah, x.ayah).associateBy { it.position }

        fun spans(anchor: MorphWord?, startIn: (WordLocation) -> Int): List<String> {
            if (anchor == null || anchor.lemma < 0) return emptyList()
            val found = arrayListOf<String>()
            for (p in m.occurrencesOfLemma(anchor.lemma).filter { !(it.surah == x.surah && it.ayah == x.ayah) }.shuffled(random).take(FIT_TRIES)) {
                val other = quran.wordsOfAyah(p.surah, p.ayah).filter { !it.isEnd }.map { it.hafs }
                val from = startIn(p)
                if (from < 0 || from + length > other.size) continue
                found += other.subList(from, from + length).joinToString(" ")
            }
            return found
        }

        // The word before the gap is at position `start` (1-based); the one after it at `start + length + 1`.
        val after = if (start > 0) spans(here[start]) { it.position } else emptyList()
        val before = if (start + length < words.size) spans(here[start + length + 1]) { it.position - 1 - length } else emptyList()
        return (0 until maxOf(after.size, before.size)).flatMap { listOfNotNull(after.getOrNull(it), before.getOrNull(it)) }
    }

    /**
     * Copies of the hidden span in which one word is swapped for another form of its lemma, then of its root,
     * taken from elsewhere in the Quran. Empty without morphology data.
     */
    private fun morphVariants(x: QuranAyah, words: List<String>, start: Int, length: Int): List<String> {
        val m = morphology ?: return emptyList()
        val here = m.wordsOfAyah(x.surah, x.ayah).associateBy { it.position }
        val ofLemma = arrayListOf<String>()
        val ofRoot = arrayListOf<String>()

        fun swap(index: Int, places: List<WordLocation>, into: MutableList<String>) {
            for (p in places.filter { !(it.surah == x.surah && it.ayah == x.ayah) }.shuffled(random).take(MORPH_TRIES)) {
                if (into.size >= MORPH_MAX) return
                val alt = quran.wordsOfAyah(p.surah, p.ayah).getOrNull(p.position - 1)?.takeIf { !it.isEnd }?.hafs ?: continue
                if (QuizText.normalize(alt) == QuizText.normalize(words[index])) continue
                val span = words.subList(start, start + length).toMutableList()
                span[index - start] = alt
                into += span.joinToString(" ")
            }
        }

        for (index in start until start + length) {
            val word = here[index + 1] ?: continue
            if (word.lemma >= 0) swap(index, m.occurrencesOfLemma(word.lemma), ofLemma)
            if (word.root >= 0) swap(index, m.occurrencesOfRoot(word.root), ofRoot)
        }
        return ofLemma + ofRoot
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
            if (words.size > TOPIC_WORDS) words.take(TOPIC_WORDS).joinToString(" ") + " …" else words.joinToString(" "),
            language.title(correct),
            (siblings + others).map(language::title),
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
            if (w.size > 9) w.take(8).joinToString(" ") + " …" else w.joinToString(" ")
        }
        val perm = QuizText.shuffledOrder(ORDER_COUNT, random)
        return OrderQuestion(
            type = ExerciseType.QUIZ_ORDER,
            subject = ref(first),
            shuffled = perm.map { texts[it] },
            correctOrder = (0 until ORDER_COUNT).map { perm.indexOf(it) },
            ayahNumbers = ayahs.map { it.ayah }
        )
    }

    /** Name the surah of a verse, then its number. Verses that are repeated word for word are skipped. */
    private fun where(id: Int): QuizQuestion? {
        val x = quran.ayahById(id) ?: return null
        val words = wordsOf(x)
        if (words.size < 4) return null
        val lookalikes = similar.similarTo(id)
        if (lookalikes.any { it.score >= IDENTICAL_SCORE }) return null

        val surahs = arrayListOf(x.surah)
        lookalikes.forEach { m -> quran.ayahById(m.ayahId)?.surah?.let { if (it !in surahs && surahs.size < 4) surahs += it } }
        var guard = 0
        while (surahs.size < 4 && guard++ < 30) {
            val s = random.nextInt(1, 115)
            if (s !in surahs) surahs += s
        }

        val count = quran.surah(x.surah)?.versesCount ?: return null
        val ayahs = linkedSetOf(x.ayah)
        for (d in listOf(1, -1, 2, -2, 3, -3)) {
            if (ayahs.size >= 4) break
            if (x.ayah + d in 1..count) ayahs += x.ayah + d
        }
        guard = 0
        while (ayahs.size < minOf(4, count) && guard++ < 30) ayahs += random.nextInt(1, count + 1)

        return WhereQuestion(
            type = ExerciseType.QUIZ_WHERE,
            subject = ref(x),
            text = if (words.size > WHERE_WORDS) words.take(WHERE_WORDS).joinToString(" ") + " …" else words.joinToString(" "),
            surahOptions = surahs.shuffled(random),
            ayahOptions = ayahs.toList().sorted()
        )
    }

    private companion object {
        const val ENDING_WORDS = 5
        const val NEXT_WORDS = 6
        const val FILL_WINDOW = 14
        const val ORDER_COUNT = 4
        const val WHERE_WORDS = 22
        const val IDENTICAL_SCORE = 95
        const val MORPH_TRIES = 12
        const val MORPH_MAX = 4
        const val FIT_TRIES = 12
        const val TOPIC_MIN_VERSES = 3
        const val TOPIC_MAX_VERSES = 150
        const val TOPIC_WORDS = 30
        const val TOPIC_NOTE = 4
    }
}
