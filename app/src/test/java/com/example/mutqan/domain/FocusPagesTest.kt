package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPagesTest {

    private fun states(vararg marks: Pair<Int, PassageState>): Array<PassageState> {
        val all = Array(MemoryEngine.TOTAL_PAGES) { PassageState.NOT_ASSESSED }
        marks.forEach { (page, state) -> all[page - 1] = state }
        return all
    }

    private fun covered(vararg pages: Int) = BooleanArray(MemoryEngine.TOTAL_PAGES).also { c -> pages.forEach { c[it - 1] = true } }

    @Test
    fun `pages needing attention come before pages still developing`() {
        val focus = FocusPages.pick(
            covered(1, 2, 3),
            states(1 to PassageState.DEVELOPING, 2 to PassageState.NEEDS_ATTENTION, 3 to PassageState.NEEDS_ATTENTION)
        )!!
        assertEquals(PassageState.NEEDS_ATTENTION, focus.state)
        assertEquals(setOf(2, 3), focus.pages)
    }

    @Test
    fun `developing pages are used when nothing needs attention`() {
        val focus = FocusPages.pick(covered(5, 6), states(5 to PassageState.DEVELOPING, 6 to PassageState.RELIABLE))!!
        assertEquals(PassageState.DEVELOPING, focus.state)
        assertEquals(setOf(5), focus.pages)
    }

    @Test
    fun `pages that are not memorised are left out`() {
        val focus = FocusPages.pick(covered(1), states(1 to PassageState.DEVELOPING, 2 to PassageState.NEEDS_ATTENTION))!!
        assertEquals(PassageState.DEVELOPING, focus.state)
        assertEquals(setOf(1), focus.pages)
    }

    @Test
    fun `untested or reliable pages give no focus`() {
        assertNull(FocusPages.pick(covered(1, 2), states(2 to PassageState.STABLE_OVER_TIME)))
    }

    @Test
    fun `without a memorised list every page counts`() {
        val focus = FocusPages.pick(null, states(600 to PassageState.NEEDS_ATTENTION))!!
        assertTrue(600 in focus.pages)
    }
}
