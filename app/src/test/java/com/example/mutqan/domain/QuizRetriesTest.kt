package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizRetriesTest {

    private val next = ExerciseType.QUIZ_NEXT
    private val where = ExerciseType.QUIZ_WHERE

    @Test
    fun `a missed question comes back the next day, two days later when missed again`() {
        val once = QuizRetries.missed(emptyList(), next, 255, today = 100, again = false)
        assertEquals(listOf(QuizRetry(next, 255, 101)), once)
        assertTrue(QuizRetries.due(once, 100).isEmpty())
        assertEquals(once, QuizRetries.due(once, 101))
        val twice = QuizRetries.missed(once, next, 255, today = 101, again = true)
        assertEquals(listOf(QuizRetry(next, 255, 103)), twice)
    }

    @Test
    fun `answered right, it is done with`() {
        val list = listOf(QuizRetry(next, 1, 5), QuizRetry(where, 1, 5), QuizRetry(next, 2, 6))
        assertEquals(listOf(QuizRetry(where, 1, 5), QuizRetry(next, 2, 6)), QuizRetries.answered(list, next, 1))
    }

    @Test
    fun `a set asks at most three, the longest waiting first, and the oldest are dropped past forty`() {
        val list = (1..5).map { QuizRetry(next, it, 10L - it) }
        assertEquals(listOf(5, 4, 3), QuizRetries.due(list, 10).map { it.ayahId })
        var many = emptyList<QuizRetry>()
        for (ayah in 1..45) many = QuizRetries.missed(many, where, ayah, 1, again = false)
        assertEquals(40, many.size)
        assertEquals(6, many.first().ayahId)
    }

    @Test
    fun `kept as text and read back, skipping what cannot be read`() {
        val list = listOf(QuizRetry(next, 255, 101), QuizRetry(where, 6236, 7))
        assertEquals(list, QuizRetries.parse(QuizRetries.format(list)))
        assertEquals(listOf(QuizRetry(next, 3, 4)), QuizRetries.parse("5:3:4,99:1:1,bad,8:x:2"))
        assertTrue(QuizRetries.parse(null).isEmpty())
    }
}
