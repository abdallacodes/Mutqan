package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PracticeSkillsTest {

    @Test
    fun `every recorded exercise belongs to one skill, retired ones to none`() {
        val all = Skill.entries.flatMap { it.types }
        assertEquals(all.size, all.toSet().size)
        val unskilled = ExerciseType.entries - all.toSet()
        assertEquals(setOf(ExerciseType.QUIZ_FILL, ExerciseType.QUIZ_TOPIC), unskilled.toSet())
    }

    @Test
    fun `a score counts hints as half and needs ten answers`() {
        val scores = PracticeSkills.scores(
            listOf(
                OutcomeCount(ExerciseType.QUIZ_NEXT, RecallOutcome.INDEPENDENT, 8),
                OutcomeCount(ExerciseType.QUIZ_NEXT, RecallOutcome.UNABLE, 2),
                OutcomeCount(ExerciseType.RECITE_ON, RecallOutcome.HINTED, 2),
                OutcomeCount(ExerciseType.RECITE_ON, RecallOutcome.PRACTICE_ONLY, 5),
                OutcomeCount(ExerciseType.QUIZ_WHERE, RecallOutcome.INDEPENDENT, 5)
            )
        )
        assertEquals(12, scores.getValue(Skill.LINKS).answered)
        assertEquals(0.75f, scores.getValue(Skill.LINKS).score!!, 0.0001f)
        assertEquals(5, scores.getValue(Skill.PLACES).answered)
        assertNull(scores.getValue(Skill.PLACES).score)
        assertEquals(0, scores.getValue(Skill.LOOKALIKES).answered)
    }

    @Test
    fun `the weakest is the lowest score among those that have one`() {
        val scores = mapOf(
            Skill.LINKS to SkillScore(20, 0.9f),
            Skill.PLACES to SkillScore(15, 0.6f),
            Skill.LOOKALIKES to SkillScore(3, null)
        )
        assertEquals(Skill.PLACES, PracticeSkills.weakest(scores))
        assertNull(PracticeSkills.weakest(mapOf(Skill.LINKS to SkillScore(4, null))))
    }

    @Test
    fun `a day's set asks every kind twice, or leans to the weakest skill`() {
        repeat(50) { seed ->
            val plain = DailySchedule.make(null, Random(seed))
            assertEquals(10, plain.size)
            ExerciseType.QUIZ.forEach { type -> assertEquals(2, plain.count { it == type }) }

            val places = DailySchedule.make(Skill.PLACES, Random(seed))
            assertEquals(10, places.size)
            assertEquals(4, places.count { it == ExerciseType.QUIZ_WHERE })
            ExerciseType.QUIZ.forEach { type -> assertTrue(places.count { it == type } >= 1) }

            val links = DailySchedule.make(Skill.LINKS, Random(seed))
            assertEquals(6, links.count { it in Skill.LINKS.quizTypes })
            ExerciseType.QUIZ.forEach { type -> assertTrue(links.count { it == type } >= 1) }
        }
    }
}
