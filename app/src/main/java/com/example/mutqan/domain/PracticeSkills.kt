package com.example.mutqan.domain

import kotlin.random.Random

/*
 * The Practice screen by skill (2026-11-21, with the owner): the exercises grouped by what they train, each skill at
 * two levels, "choose" (the app checks the answer) and "recite" (the user says it from memory and rates it), with how
 * the last 30 days went.
 */

/**
 * What an exercise trains. [choose] are the types the app checks, [recite] the ones the user rates; together they are
 * what the skill's score counts.
 */
enum class Skill(val choose: List<ExerciseType>, val recite: List<ExerciseType>) {
    /** What comes after each verse: the next verse, the order of verses, going on from a verse, quarter joints. */
    LINKS(
        choose = listOf(ExerciseType.QUIZ_NEXT, ExerciseType.QUIZ_ORDER),
        recite = listOf(ExerciseType.RECITE_ON, ExerciseType.QUARTER_JOINT)
    ),

    /** Where each verse is: its surah, and the verses that start and end its page. */
    PLACES(
        choose = listOf(ExerciseType.QUIZ_WHERE, ExerciseType.QUIZ_PAGE_START, ExerciseType.QUIZ_PAGE_END),
        recite = ExerciseType.PAGE_BOUNDARIES
    ),

    /** Telling look-alike verses apart: which ending is the verse's own. (The Vault test is not recorded.) */
    LOOKALIKES(
        choose = listOf(ExerciseType.QUIZ_ENDING, ExerciseType.QUIZ_FINISH),
        recite = emptyList()
    );

    val types: List<ExerciseType> get() = choose + recite

    /** The types of the skill a mixed set of quiz questions can ask. */
    val quizTypes: List<ExerciseType> get() = choose.filter { it in ExerciseType.QUIZ }
}

/** How a skill went: [answered] answers in the period, and the share of them right ([score] 0..1), null with too few. */
data class SkillScore(val answered: Int, val score: Float?)

/** One count of answers: of [type], with [outcome], [count] of them. */
data class OutcomeCount(val type: ExerciseType, val outcome: RecallOutcome, val count: Int)

object PracticeSkills {
    /** Answers a skill needs before its score is shown: fewer say too little. */
    const val MIN_ANSWERS = 10

    /** The days looked back over for the scores. */
    const val DAYS = 30

    /** How much an answer counts as right: recalled (or self-corrected) fully, with a hint half, missed not at all. */
    fun credit(outcome: RecallOutcome): Float? = when (outcome) {
        RecallOutcome.INDEPENDENT, RecallOutcome.SELF_CORRECTED -> 1f
        RecallOutcome.HINTED -> 0.5f
        RecallOutcome.UNABLE -> 0f
        RecallOutcome.PRACTICE_ONLY -> null
    }

    fun scores(counts: List<OutcomeCount>): Map<Skill, SkillScore> = Skill.entries.associateWith { skill ->
        var answered = 0
        var right = 0f
        for (c in counts) {
            if (c.type !in skill.types) continue
            val credit = credit(c.outcome) ?: continue
            answered += c.count
            right += credit * c.count
        }
        SkillScore(answered, if (answered >= MIN_ANSWERS) right / answered else null)
    }

    /** The skill with the lowest score, among those with enough answers to have one; null when none has. */
    fun weakest(scores: Map<Skill, SkillScore>): Skill? =
        scores.entries.filter { it.value.score != null }.minByOrNull { it.value.score!! }?.key
}

/** The kinds of question of a day's set of ten. */
object DailySchedule {
    /** Questions of the weakest skill added to the set, in place of others. */
    const val EXTRA = 2

    /**
     * Each quiz type twice, shuffled; with a [weakest] skill, [EXTRA] of the other questions are of its kinds instead,
     * so a day's set leans to what needs it.
     */
    fun make(weakest: Skill?, random: Random = Random.Default): List<ExerciseType> {
        val set = (ExerciseType.QUIZ + ExerciseType.QUIZ).toMutableList()
        val extra = weakest?.quizTypes.orEmpty()
        if (extra.isNotEmpty()) {
            repeat(EXTRA) { i ->
                // Only a kind still asked twice gives way, so every kind stays in the set.
                val others = set.indices.filter { set[it] !in extra && set.count { t -> t == set[it] } > 1 }
                if (others.isNotEmpty()) set[others.random(random)] = extra[i % extra.size]
            }
        }
        return set.shuffled(random)
    }
}
