package com.example.mutqan.domain

import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranWord
import kotlin.math.min
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
     * Where [words] and its lookalikes part, and how each goes on. Of the places in the verse (with something after them)
     * where lookalikes share at least [MIN_SHARED] words up to it and then go on differently, the one with the most of
     * them, then the one where they share the most. It is shown as the lead, up to that place; the options are how the
     * verse and those lookalikes go on, the closest first. It once stopped after the longest stretch shared with any one
     * lookalike, and when that one ended there (Taha 116 beside Al-Baqarah 34) or was the only one to share it, there was
     * no question, or only two options: for nearly half the verses of the ready-made groups. Null when no lookalike
     * parts from the verse anywhere, or the endings cannot be told apart.
     */
    fun finish(words: List<QuranWord>, others: List<List<QuranWord>>, random: Random): ChoiceSet? {
        val norm = words.map { QuizText.normalize(it.hafs) }
        val otherNorms = others.map { o -> o.map { QuizText.normalize(it.hafs) } }
        // runs[k][i][j]: how many words the verse and lookalike k share, ending at the verse's word i and its word j.
        val runs = otherNorms.map { o ->
            Array(norm.size + 1) { IntArray(o.size + 1) }.also { r ->
                for (i in 1..norm.size) for (j in 1..o.size) {
                    if (norm[i - 1].isNotEmpty() && norm[i - 1] == o[j - 1]) r[i][j] = r[i - 1][j - 1] + 1
                }
            }
        }
        var bestCut = 0
        var best = emptyList<Pair<Int, Int>>()
        var bestShared = 0
        for (cut in MIN_SHARED until norm.size) {
            val seen = hashSetOf(norm.subList(cut, min(norm.size, cut + MAX_CONTINUATION)))
            // For each lookalike: where it shares the most words up to here, if it goes on after them.
            val parting = others.indices.mapNotNull { k ->
                val r = runs[k][cut]
                var at = -1
                for (j in 1 until otherNorms[k].size) if (r[j] >= MIN_SHARED && (at < 0 || r[j] > r[at])) at = j
                if (at < 0) null else Triple(k, at, r[at])
            }.sortedByDescending { it.third }.filter { (k, at, _) ->
                seen.add(otherNorms[k].subList(at, min(otherNorms[k].size, at + MAX_CONTINUATION)))
            }
            val shared = parting.sumOf { it.third }
            if (parting.size > best.size || (parting.size == best.size && parting.isNotEmpty() && shared > bestShared)) {
                bestCut = cut
                best = parting.map { it.first to it.second }
                bestShared = shared
            }
        }
        if (best.isEmpty()) return null
        val rivals = best.map { (k, at) -> others[k].drop(at) }
        val own = words.drop(bestCut)

        for (length in MIN_CONTINUATION..MAX_CONTINUATION) {
            fun cut(w: List<QuranWord>) = MushafSpan.of(w.take(length))
            fun key(span: MushafSpan) = QuizText.normalize(span.plain)
            val correct = cut(own)
            val texts = linkedMapOf(key(correct) to correct)
            rivals.forEach { r -> if (texts.size < MAX_OPTIONS) texts.putIfAbsent(key(cut(r)), cut(r)) }
            val clear = rivals.all { key(cut(it)) != key(correct) }
            if (!clear && length < MAX_CONTINUATION) continue
            if (texts.size < 2) return null
            // No ellipsis: an option that is a lookalike's whole ending (Taha's "أبى") would seem to go on.
            val options = texts.values.toList()
            val shuffled = options.shuffled(random)
            return ChoiceSet(
                lead = QuizText.tail(words.take(bestCut), LEAD_WORDS) + MushafSpan.More,
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
