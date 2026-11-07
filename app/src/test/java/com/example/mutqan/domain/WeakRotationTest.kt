package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeakRotationTest {
    private val hundred = (1..100).toList()

    @Test
    fun aShortListIsShownWhole() {
        assertEquals(listOf(1, 2), WeakRotation.pick(listOf(1, 2), day = 5, others = 3, size = 2))
        assertEquals(listOf(7), WeakRotation.pick(listOf(7), day = 5, others = 0, size = 2))
        assertEquals(emptyList<Int>(), WeakRotation.pick(emptyList<Int>(), day = 5, others = 0, size = 2))
    }

    @Test
    fun atMostTwoAreShownWhateverTheNumber() {
        for (day in 0L..50L) assertEquals(2, WeakRotation.pick(hundred, day, 0, 2).size)
    }

    @Test
    fun theSameDayShowsTheSameOnesUntilOthersAreAskedFor() {
        assertEquals(WeakRotation.pick(hundred, 20_000L, 0, 2), WeakRotation.pick(hundred, 20_000L, 0, 2))
        assertTrue(WeakRotation.pick(hundred, 20_000L, 0, 2) != WeakRotation.pick(hundred, 20_000L, 1, 2))
    }

    @Test
    fun eachDayShowsOtherOnesAndNoneRepeatUntilTheListHasBeenAround() {
        val seen = (0L until 50L).flatMap { WeakRotation.pick(hundred, 20_000L + it, 0, 2) }
        assertEquals(100, seen.size)
        assertEquals(100, seen.toSet().size)
    }

    @Test
    fun theTwoShownAreAlwaysDifferentFromEachOther() {
        for (day in 0L..200L) for (n in listOf(3, 4, 5, 7, 100)) {
            val shown = WeakRotation.pick((1..n).toList(), day, 0, 2)
            assertEquals(2, shown.toSet().size)
        }
    }

    @Test
    fun theWindowCarriesOnFromTheStartOfTheListAtItsEnd() {
        // Three items, window two: after [1,2] comes [3,1].
        val list = listOf(1, 2, 3)
        assertEquals(listOf(1, 2), WeakRotation.pick(list, 0, 0, 2))
        assertEquals(listOf(3, 1), WeakRotation.pick(list, 1, 0, 2))
    }

    @Test
    fun aDayBeforeTheEpochStillWorks() {
        val shown = WeakRotation.pick(hundred, -3L, 0, 2)
        assertEquals(2, shown.size)
        assertTrue(shown.all { it in hundred })
    }
}
