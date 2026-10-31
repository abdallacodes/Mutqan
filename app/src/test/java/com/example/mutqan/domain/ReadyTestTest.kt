package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReadyTestTest {

    private fun group(id: Int, vararg ayahs: Int) = ReadyGroup(id, "g$id", ayahs.map { GroupMember(it, emptyList()) })

    private val groups = listOf(group(1, 10, 11), group(2, 20, 21), group(3, 30, 31, 32))

    @Test
    fun `without anything memorised any group can be asked`() {
        val seen = (0 until 60).map { ReadyTest.pick(groups, { false }, false, emptyList(), Random(it))!!.first.id }.toSet()
        assertEquals(setOf(1, 2, 3), seen)
    }

    @Test
    fun `groups with two memorised lookalikes come first`() {
        val memorised = setOf(20, 21, 30)
        repeat(30) {
            val (g, m) = ReadyTest.pick(groups, { it in memorised }, true, emptyList(), Random(it))!!
            assertEquals(2, g.id)
            assertTrue(m.ayahId in memorised)
        }
    }

    @Test
    fun `then groups with one memorised verse, asking about that verse`() {
        repeat(30) {
            val (g, m) = ReadyTest.pick(groups, { it == 31 }, true, emptyList(), Random(it))!!
            assertEquals(3, g.id)
            assertEquals(31, m.ayahId)
        }
    }

    @Test
    fun `when no group has a memorised verse any group is used`() {
        val seen = (0 until 60).map { ReadyTest.pick(groups, { it == 999 }, true, emptyList(), Random(it))!!.first.id }.toSet()
        assertEquals(setOf(1, 2, 3), seen)
    }

    @Test
    fun `recent groups are skipped while others remain, and not when none do`() {
        repeat(30) { assertTrue(ReadyTest.pick(groups, { false }, false, listOf(1, 2), Random(it))!!.first.id == 3) }
        assertEquals(1, ReadyTest.pick(listOf(group(1, 10, 11)), { false }, false, listOf(1))!!.first.id)
    }

    @Test
    fun `no groups gives no question`() {
        assertNull(ReadyTest.pick(emptyList(), { true }, true, emptyList()))
    }

    @Test
    fun `word ranges become positions`() {
        assertEquals(setOf(1, 2, 5), ReadyTest.hits(listOf(1..2, 5..5)))
    }
}
