package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HifzCoverageTest {

    @Test
    fun `merge joins overlapping and touching ranges and sorts them`() {
        val merged = HifzCoverage.merge(listOf(20..30, 1..5, 6..9, 25..40, 100..100))
        assertEquals(listOf(1..9, 20..40, 100..100), merged)
    }

    @Test
    fun `merge drops empty ranges`() {
        assertEquals(listOf(3..4), HifzCoverage.merge(listOf(5..4, 3..4)))
    }

    @Test
    fun `add merges the new range in`() {
        assertEquals(listOf(1..30), HifzCoverage.add(listOf(1..10, 21..30), 11..20))
    }

    @Test
    fun `remove splits a range that straddles the removed part`() {
        assertEquals(listOf(1..9, 21..30), HifzCoverage.remove(listOf(1..30), 10..20))
    }

    @Test
    fun `remove trims and deletes whole ranges`() {
        val result = HifzCoverage.remove(listOf(1..10, 20..30, 50..60), 8..55)
        assertEquals(listOf(1..7, 56..60), result)
    }

    @Test
    fun `remove outside the coverage changes nothing`() {
        assertEquals(listOf(1..10), HifzCoverage.remove(listOf(1..10), 20..30))
    }

    @Test
    fun `containsAll needs one range to hold the whole span`() {
        val ranges = listOf(1..10, 12..20)
        assertTrue(HifzCoverage.containsAll(ranges, 3..9))
        assertFalse(HifzCoverage.containsAll(ranges, 5..15))
    }

    @Test
    fun `ayahCount sums merged ranges once`() {
        assertEquals(15, HifzCoverage.ayahCount(listOf(1..10, 6..15)))
    }

    @Test
    fun `coveredPages is null without coverage`() {
        assertNull(HifzCoverage.coveredPages(emptyList()) { 1 })
    }

    @Test
    fun `coveredPages marks the page each covered ayah starts on`() {
        // Ayah n starts on page n / 10 + 1.
        val pages = HifzCoverage.coveredPages(listOf(5..12, 95..95)) { it / 10 + 1 }!!
        assertTrue(pages[0])   // ayahs 5..9 -> page 1
        assertTrue(pages[1])   // ayahs 10..12 -> page 2
        assertFalse(pages[2])
        assertTrue(pages[9])   // ayah 95 -> page 10
        assertEquals(3, pages.count { it })
    }
}
