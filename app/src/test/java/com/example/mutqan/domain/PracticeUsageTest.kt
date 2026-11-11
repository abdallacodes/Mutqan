package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeUsageTest {

    private val kinds = listOf(
        listOf(ExerciseType.QUIZ_WHERE),
        listOf(ExerciseType.QUIZ_NEXT),
        listOf(ExerciseType.QUIZ_ENDING),
        ExerciseType.FROM_PAGE
    )

    @Test
    fun `a new reader sees the first entries as listed`() {
        assertEquals(listOf(0, 1), PracticeUsage.mostPractised(emptyMap(), kinds, 2))
    }

    @Test
    fun `the most answered come first`() {
        val counts = mapOf(ExerciseType.QUIZ_ENDING to 9, ExerciseType.QUIZ_NEXT to 4, ExerciseType.QUIZ_WHERE to 1)
        assertEquals(listOf(2, 1), PracticeUsage.mostPractised(counts, kinds, 2))
    }

    @Test
    fun `an entry adds up all the types it records under`() {
        val counts = mapOf(ExerciseType.FIRST_TO_LAST to 3, ExerciseType.NEIGHBOURS to 3, ExerciseType.QUIZ_WHERE to 5)
        assertEquals(listOf(3, 0), PracticeUsage.mostPractised(counts, kinds, 2))
    }

    @Test
    fun `ties keep the order of the list`() {
        val counts = mapOf(ExerciseType.QUIZ_NEXT to 2, ExerciseType.QUIZ_ENDING to 2)
        assertEquals(listOf(1, 2, 0), PracticeUsage.mostPractised(counts, kinds, 3))
    }
}
