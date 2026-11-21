package com.example.mutqan.domain

/** A quiz question answered wrongly: its kind and verse, and the day (epoch day) it is asked again from. */
data class QuizRetry(val type: ExerciseType, val ayahId: Int, val due: Long)

/**
 * Questions missed come back: a missed question is asked again in a day's set from the next day on, and if missed
 * again, two days after that; answered right, it is done with. Kept on this device only.
 */
object QuizRetries {
    /** The most questions kept; the oldest go first. */
    const val MOST = 40

    /** The most asked again in one day's set. */
    const val PER_SET = 3

    /** [list] after the question of [type] about verse [ayahId] was missed on [today] ([again]: it was itself asked again). */
    fun missed(list: List<QuizRetry>, type: ExerciseType, ayahId: Int, today: Long, again: Boolean): List<QuizRetry> {
        val due = today + if (again) 2 else 1
        return (list.filterNot { it.type == type && it.ayahId == ayahId } + QuizRetry(type, ayahId, due)).takeLast(MOST)
    }

    /** [list] after the question of [type] about verse [ayahId] was answered right. */
    fun answered(list: List<QuizRetry>, type: ExerciseType, ayahId: Int): List<QuizRetry> =
        list.filterNot { it.type == type && it.ayahId == ayahId }

    /** The questions to ask again in a set on [today], the longest waiting first. */
    fun due(list: List<QuizRetry>, today: Long): List<QuizRetry> = list.filter { it.due <= today }.sortedBy { it.due }.take(PER_SET)

    fun format(list: List<QuizRetry>): String = list.joinToString(",") { "${it.type.code}:${it.ayahId}:${it.due}" }

    fun parse(text: String?): List<QuizRetry> = text.orEmpty().split(',').mapNotNull { part ->
        val bits = part.split(':')
        val type = bits.getOrNull(0)?.toIntOrNull()?.let(ExerciseType::fromCode) ?: return@mapNotNull null
        val ayah = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        val due = bits.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
        QuizRetry(type, ayah, due)
    }
}
