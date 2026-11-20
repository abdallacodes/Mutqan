package com.example.mutqan.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSpreadTest {

    // Pager page n shows Mushaf page n + 1; the two-page book rests on even pager pages: pages 3 and 4 are pager 2 and 3.

    @Test
    fun `at rest on a spread no page takes part in a turn`() {
        for (spread in listOf(0, 2, 4, 300)) {
            val scroll = LeafTurn.spreadScroll(spread.toFloat())
            for (page in spread - 2..spread + 3) assertNull(LeafTurn.partOf(page, scroll, from = spread, landing = true))
        }
    }

    @Test
    fun `going on from a spread turns its left-hand page over onto the right, the next spread's right-hand page on its back`() {
        // From pages 3 and 4 toward 5 and 6, three tenths of the way: 0.6 of the two pages.
        val scroll = LeafTurn.spreadScroll(2.6f)
        val part = { page: Int -> LeafTurn.partOf(page, scroll, from = 2, landing = true) }
        assertEquals(LeafTurn.Role.FRONT, part(3)?.role)
        assertEquals(LeafTurn.Role.BACK, part(4)?.role)
        assertEquals(LeafTurn.Role.BENEATH, part(5)?.role)
        assertEquals(LeafTurn.Role.LANDING, part(2)?.role)
        assertEquals(0.3f, part(3)!!.progress, 0.0001f)
        assertTrue(!part(3)!!.backward)
    }

    @Test
    fun `going back from a spread turns its right-hand page over onto the left`() {
        // From pages 3 and 4 back toward 1 and 2, three tenths of the way.
        val scroll = LeafTurn.spreadScroll(1.4f)
        val part = { page: Int -> LeafTurn.partOf(page, scroll, from = 2, landing = true) }
        assertEquals(LeafTurn.Role.FRONT, part(2)?.role)
        assertEquals(LeafTurn.Role.BACK, part(1)?.role)
        assertEquals(LeafTurn.Role.BENEATH, part(0)?.role)
        assertEquals(LeafTurn.Role.LANDING, part(3)?.role)
        assertEquals(0.3f, part(2)!!.progress, 0.0001f)
        assertTrue(part(2)!!.backward)
    }

    @Test
    fun `a turn let go settles on a spread, the way a fling sends it or else the nearer one`() {
        val fast = 1000f
        // Slow: the nearer spread.
        assertEquals(2, SpreadSnap.target(2.6f, 0f, fast, 603))
        assertEquals(4, SpreadSnap.target(3.2f, 0f, fast, 603))
        // A fling goes its way, even from early in the turn, and back again.
        assertEquals(4, SpreadSnap.target(2.3f, 5000f, fast, 603))
        assertEquals(2, SpreadSnap.target(3.6f, -5000f, fast, 603))
        // Always an even page, never past the last spread or before the first.
        for (i in 0..1206) assertEquals(0, SpreadSnap.target(i / 2f, (i % 3 - 1) * 5000f, fast, 603) % 2)
        assertEquals(602, SpreadSnap.target(602.9f, 5000f, fast, 603))
        assertEquals(0, SpreadSnap.target(0.2f, -5000f, fast, 603))
    }
}
