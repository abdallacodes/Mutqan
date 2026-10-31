package com.example.mutqan.domain

import kotlin.random.Random

/** The kinds of mutashabihat test: say it from memory, finish it among lookalike endings, or tell which verse it is. */
enum class TestType { RECALL, FINISH, WHICH }

/** A multiple-choice question with its options already shuffled; [correct] indexes [options]. */
data class ChoiceSet(val lead: String, val options: List<String>, val correct: Int)

/** Builds the multiple-choice mutashabihat questions from a verse's words and those of its lookalikes. */
object SimilarityQuestions {

    private const val MAX_OPTIONS = 4
    private const val LEAD_WORDS = 8
    private const val MIN_CONTINUATION = 2
    private const val MAX_CONTINUATION = 8

    /** How many words from the start the two verses share, ignoring diacritics. */
    fun commonPrefix(a: List<String>, b: List<String>): Int {
        var n = 0
        while (n < a.size && n < b.size && QuizText.normalize(a[n]) == QuizText.normalize(b[n])) n++
        return n
    }

    /**
     * The shared opening of [words] and its closest lookalike, shown as the lead; the options are how each
     * verse that shares that whole opening goes on. Null when no lookalike shares an opening, or the endings
     * cannot be told apart.
     */
    fun finish(words: List<String>, others: List<List<String>>, random: Random): ChoiceSet? {
        val shared = others.map { commonPrefix(words, it) }
        val prefix = shared.maxOrNull()?.takeIf { it in 1 until words.size } ?: return null
        val rivals = others.filterIndexed { i, o -> shared[i] == prefix && o.size > prefix }
        if (rivals.isEmpty()) return null

        for (length in MIN_CONTINUATION..MAX_CONTINUATION) {
            fun cut(w: List<String>) = w.drop(prefix).take(length).joinToString(" ")
            val correct = cut(words)
            val texts = linkedMapOf(QuizText.normalize(correct) to correct)
            rivals.forEach { r -> if (texts.size < MAX_OPTIONS) texts.putIfAbsent(QuizText.normalize(cut(r)), cut(r)) }
            val clear = rivals.all { QuizText.normalize(cut(it)) != QuizText.normalize(correct) }
            if (!clear && length < MAX_CONTINUATION) continue
            if (texts.size < 2) return null
            val options = texts.values.toList().map { it + if (words.size - prefix > length) " …" else "" }
            val shuffled = options.shuffled(random)
            return ChoiceSet(
                lead = QuizText.tail(words.take(prefix), LEAD_WORDS) + " …",
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
        words: List<String>,
        correct: String,
        others: List<Pair<String, List<String>>>,
        random: Random
    ): ChoiceSet? {
        val own = QuizText.normalize(words.joinToString(" "))
        if (others.isEmpty() || others.any { QuizText.normalize(it.second.joinToString(" ")) == own }) return null
        val options = listOf(correct) + others.map { it.first }.filter { it != correct }.distinct().shuffled(random).take(MAX_OPTIONS - 1)
        if (options.size < 2) return null
        val shuffled = options.shuffled(random)
        return ChoiceSet(lead = "", options = shuffled, correct = shuffled.indexOf(correct))
    }
}
