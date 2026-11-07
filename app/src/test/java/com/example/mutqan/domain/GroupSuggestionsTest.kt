package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSuggestionsTest {

    private fun data(vararg pairs: Pair<Int, List<SimilarMatch>>) = SimilarData(emptyList(), pairs.toMap())
    private fun m(id: Int, score: Int) = SimilarMatch(id, score, emptyList())

    @Test
    fun `an empty group gets no suggestions`() {
        val similar = data(1 to listOf(m(2, 90)))
        assertTrue(GroupSuggestions.rank(similar, emptyList()).isEmpty())
    }

    @Test
    fun `one ayah is enough and the strongest match comes first`() {
        val similar = data(1 to listOf(m(2, 60), m(3, 95), m(4, 70)))
        assertEquals(listOf(3, 4, 2), GroupSuggestions.rank(similar, listOf(1)).map { it.ayahId })
    }

    @Test
    fun `an ayah that resembles more of the group beats a stronger single match`() {
        val similar = data(
            1 to listOf(m(10, 99), m(11, 70)),
            2 to listOf(m(11, 65))
        )
        val ranked = GroupSuggestions.rank(similar, listOf(1, 2))
        assertEquals(listOf(11, 10), ranked.map { it.ayahId })
        assertEquals(2, ranked.first().hits)
        assertEquals(70, ranked.first().score)
    }

    @Test
    fun `ayahs already in the group or dismissed are left out`() {
        val similar = data(1 to listOf(m(2, 90), m(3, 80), m(4, 70)), 2 to listOf(m(1, 90)))
        val ranked = GroupSuggestions.rank(similar, listOf(1, 2), dismissed = setOf(3))
        assertEquals(listOf(4), ranked.map { it.ayahId })
    }

    @Test
    fun `the limit caps the list`() {
        val similar = data(1 to (2..20).map { m(it, 100 - it) })
        assertEquals(5, GroupSuggestions.rank(similar, listOf(1), limit = 5).size)
    }
}
