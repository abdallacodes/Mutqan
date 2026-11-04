package com.example.mutqan.domain

import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranWord
import kotlin.random.Random

/** The kinds of mutashabihat test: say it from memory, finish it among lookalike endings, or tell which verse it is. */
enum class TestType { RECALL, FINISH, WHICH }

/** A multiple-choice question with its options already shuffled; [correct] indexes [options]. */
data class ChoiceSet(val lead: MushafSpan, val options: List<MushafSpan>, val correct: Int)

/** Builds the multiple-choice mutashabihat questions from a verse's words and those of its lookalikes. */
object SimilarityQuestions {

    private const val MAX_OPTIONS = 4
    private const val LEAD_WORDS = 8
    private const val MIN_SHARED = 2
    private const val MIN_CONTINUATION = 2
    private const val MAX_CONTINUATION = 8

    /** How many words from the start the two verses share, ignoring diacritics. */
    fun commonPrefix(a: List<String>, b: List<String>): Int {
        var n = 0
        while (n < a.size && n < b.size && QuizText.normalize(a[n]) == QuizText.normalize(b[n])) n++
        return n
    }

    /**
     * The longest stretch [words] shares with a lookalike, anywhere in the verse (at least two words, with
     * something after it). It is shown as the lead, up to its end; the options are how each lookalike that has
     * the same stretch goes on. Null when no lookalike shares one, or the endings cannot be told apart.
     */
    fun finish(words: List<QuranWord>, others: List<List<QuranWord>>, random: Random): ChoiceSet? {
        val norm = words.map { QuizText.normalize(it.hafs) }
        var bestLength = 0
        var bestEnd = 0
        for (other in others) {
            val o = other.map { QuizText.normalize(it.hafs) }
            // Length of the common run ending at (i, j), kept one row at a time.
            var previous = IntArray(o.size + 1)
            for (i in 1..norm.size) {
                val current = IntArray(o.size + 1)
                for (j in 1..o.size) {
                    if (norm[i - 1] == o[j - 1]) {
                        current[j] = previous[j - 1] + 1
                        if (i < norm.size && current[j] > bestLength) { bestLength = current[j]; bestEnd = i }
                    }
                }
                previous = current
            }
        }
        if (bestLength < MIN_SHARED) return null
        val run = norm.subList(bestEnd - bestLength, bestEnd)

        // What follows the same stretch in each lookalike that has it.
        val rivals = others.mapNotNull { other ->
            val o = other.map { QuizText.normalize(it.hafs) }
            val at = (0..o.size - bestLength).firstOrNull { k -> o.subList(k, k + bestLength) == run } ?: return@mapNotNull null
            other.drop(at + bestLength).takeIf { it.isNotEmpty() }
        }
        if (rivals.isEmpty()) return null
        val own = words.drop(bestEnd)

        for (length in MIN_CONTINUATION..MAX_CONTINUATION) {
            fun cut(w: List<QuranWord>) = MushafSpan.of(w.take(length))
            fun key(span: MushafSpan) = QuizText.normalize(span.plain)
            val correct = cut(own)
            val texts = linkedMapOf(key(correct) to correct)
            rivals.forEach { r -> if (texts.size < MAX_OPTIONS) texts.putIfAbsent(key(cut(r)), cut(r)) }
            val clear = rivals.all { key(cut(it)) != key(correct) }
            if (!clear && length < MAX_CONTINUATION) continue
            if (texts.size < 2) return null
            val options = texts.values.toList().map { it + if (own.size > length) MushafSpan.More else MushafSpan.Empty }
            val shuffled = options.shuffled(random)
            return ChoiceSet(
                lead = QuizText.tail(words.take(bestEnd), LEAD_WORDS) + MushafSpan.More,
                options = shuffled,
                correct = shuffled.indexOf(options.first())
            )
        }
        return null
    }

    /**
     * Which of the group's verses is [correct]? The options are the references of the verse and its lookalikes.
     * Null when the verse is word for word the same as one of them, because then it cannot be told apart.
     */
    fun which(
        words: List<QuranWord>,
        correct: String,
        others: List<Pair<String, List<QuranWord>>>,
        random: Random
    ): ChoiceSet? {
        val own = QuizText.normalize(words.joinToString(" ") { it.hafs })
        if (others.isEmpty() || others.any { QuizText.normalize(it.second.joinToString(" ") { w -> w.hafs }) == own }) return null
        val options = listOf(correct) + others.map { it.first }.filter { it != correct }.distinct().shuffled(random).take(MAX_OPTIONS - 1)
        if (options.size < 2) return null
        val shuffled = options.shuffled(random)
        return ChoiceSet(lead = MushafSpan.Empty, options = shuffled.map { MushafSpan.label(it) }, correct = shuffled.indexOf(correct))
    }
}
