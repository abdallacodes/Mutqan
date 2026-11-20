package com.example.mutqan.ui.mushaf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeafTurnTest {

    private val width = 1080f
    private val height = 2400f

    // Pager page n shows Mushaf page n + 1.

    @Test
    fun `going from a right-hand page to the left-hand one beside it turns nothing`() {
        // Between page 1 and page 2 (one open spread).
        assertNull(LeafTurn.partOf(page = 0, scroll = 0.3f))
        assertNull(LeafTurn.partOf(page = 1, scroll = 0.3f))
        assertTrue(!LeafTurn.turnsLeafAfter(0))
    }

    @Test
    fun `going from a left-hand page to the next turns its leaf, with the next page on its back`() {
        // Between page 2 and page 3: the leaf holds page 2 on its front and page 3 on its back; page 4 lies beneath.
        assertEquals(LeafTurn.Role.FRONT, LeafTurn.partOf(page = 1, scroll = 1.3f)?.role)
        assertEquals(LeafTurn.Role.BACK, LeafTurn.partOf(page = 2, scroll = 1.3f)?.role)
        assertEquals(LeafTurn.Role.BENEATH, LeafTurn.partOf(page = 3, scroll = 1.3f)?.role)
        assertEquals(0.3f, LeafTurn.partOf(page = 1, scroll = 1.3f)!!.progress, 0.0001f)
        assertEquals(0.3f, LeafTurn.partOf(page = 2, scroll = 1.3f)!!.progress, 0.0001f)
        assertEquals(0.3f, LeafTurn.partOf(page = 3, scroll = 1.3f)!!.progress, 0.0001f)
        // The page before takes no part.
        assertNull(LeafTurn.partOf(page = 0, scroll = 1.3f))
        assertTrue(LeafTurn.turnsLeafAfter(1))
    }

    @Test
    fun `going back from a right-hand page lifts that page, with the page before it on its back`() {
        // On page 3, going back to page 2: page 3 is the leaf's front now, page 2 is on its back, page 1 lies beneath.
        val from = 2
        assertEquals(LeafTurn.Role.FRONT, LeafTurn.partOf(page = 2, scroll = 1.3f, from = from)?.role)
        assertEquals(LeafTurn.Role.BACK, LeafTurn.partOf(page = 1, scroll = 1.3f, from = from)?.role)
        assertEquals(LeafTurn.Role.BENEATH, LeafTurn.partOf(page = 0, scroll = 1.3f, from = from)?.role)
        // The turn is counted from the page it began on: 30% of the way from page 2 is 70% of the way back from page 3.
        assertEquals(0.7f, LeafTurn.partOf(page = 2, scroll = 1.3f, from = from)!!.progress, 0.0001f)
        assertTrue(LeafTurn.partOf(page = 2, scroll = 1.3f, from = from)!!.backward)
        // Page 4 is what the leaf comes down on; it only takes part when asked for.
        assertNull(LeafTurn.partOf(page = 3, scroll = 1.3f, from = from))
        assertEquals(LeafTurn.Role.LANDING, LeafTurn.partOf(page = 3, scroll = 1.3f, from = from, landing = true)?.role)
    }

    @Test
    fun `going on, the leaf comes down on the right-hand page before it`() {
        assertNull(LeafTurn.partOf(page = 0, scroll = 1.3f, from = 1))
        assertEquals(LeafTurn.Role.LANDING, LeafTurn.partOf(page = 0, scroll = 1.3f, from = 1, landing = true)?.role)
        assertTrue(!LeafTurn.partOf(page = 1, scroll = 1.3f, from = 1)!!.backward)
    }

    @Test
    fun `the back of the leaf lies on top, then its front, then the page beneath, then the one it comes down on`() {
        val z = LeafTurn.Role.entries.sortedByDescending { it.z }
        assertEquals(listOf(LeafTurn.Role.BACK, LeafTurn.Role.FRONT, LeafTurn.Role.BENEATH, LeafTurn.Role.LANDING), z)
    }

    @Test
    fun `in the book view the leaf is only the paper and lands as the right-hand page's paper`() {
        // A left-hand page's paper: the book's outer side takes the first 45px, the paper runs on to the bound edge.
        val sheet = Rect(45f, 300f, width, 2100f)
        val landed = LeafTurn.scene(1f, width, sheet, grip = 0.5f)
        // The free edge comes down where a right-hand page's outer edge is: the paper's width from the bound edge.
        val freeEdge = landed.mirror.map(Offset(landed.leaf.left, 1200f))
        assertEquals(sheet.width, freeEdge.x, 0.5f)
        // And the page on its back lands where it is read.
        val point = Offset(500f, 800f)
        val drawn = landed.back.map(point)
        assertEquals(point.x, drawn.x, 0.5f)
        assertEquals(point.y, drawn.y, 0.5f)
        // The fold runs from the paper's top to its bottom.
        val rising = LeafTurn.scene(0.2f, width, sheet, grip = 0.9f)
        assertEquals(sheet.top, rising.foldTop.y, 0.01f)
        assertEquals(sheet.bottom, rising.foldBottom.y, 0.01f)
    }

    @Test
    fun `the book is seen smallest halfway through a turn and at its size when the turn starts and ends`() {
        assertEquals(1f, LeafTurn.zoom(0f, 0.18f), 0.0001f)
        assertEquals(1f, LeafTurn.zoom(1f, 0.18f), 0.0001f)
        assertEquals(0.82f, LeafTurn.zoom(0.5f, 0.18f), 0.0001f)
        // The same on the way out and on the way back, and never larger than the book.
        assertEquals(LeafTurn.zoom(0.2f, 0.18f), LeafTurn.zoom(0.8f, 0.18f), 0.0001f)
        for (i in 0..100) assertTrue(LeafTurn.zoom(i / 100f, 0.18f) <= 1f)
        // Without depth, nothing changes.
        assertEquals(1f, LeafTurn.zoom(0.5f, 0f), 0.0001f)
    }

    @Test
    fun `in the book view the view goes smoothly from page to page, and halfway the whole open book is in sight`() {
        assertEquals(0f, LeafTurn.bookCamera(0f, width), 0.01f)
        assertEquals(-width / 2f, LeafTurn.bookCamera(0.5f, width), 0.01f)
        assertEquals(-width, LeafTurn.bookCamera(1f, width), 0.01f)
        var last = 1f
        for (i in 0..100) {
            val x = LeafTurn.bookCamera(i / 100f, width)
            assertTrue(x <= last)
            last = x
        }
        // Halfway, both pages (the left one from 0 to the width, the right one on to twice the width) fit on the screen
        // with the book seen at its smallest.
        val zoom = LeafTurn.zoom(0.5f, 0.52f)
        val camera = LeafTurn.bookCamera(0.5f, width)
        fun onScreen(bookX: Float) = width / 2f + (camera + bookX - width / 2f) * zoom
        assertTrue(onScreen(0f) > 0f)
        assertTrue(onScreen(2f * width) < width)
    }

    @Test
    fun `at rest no page takes part in a turn`() {
        for (page in 0..5) assertNull(LeafTurn.partOf(page, scroll = 2f))
    }

    @Test
    fun `the fold reaches the middle as the pager reaches its turning point, and only moves one way`() {
        assertEquals(0f, LeafTurn.fold(0f), 0.0001f)
        assertEquals(0.5f, LeafTurn.fold(0.5f), 0.0001f)
        assertEquals(1f, LeafTurn.fold(1f), 0.0001f)
        var last = -1f
        for (i in 0..100) {
            val fold = LeafTurn.fold(i / 100f)
            assertTrue(fold >= last)
            last = fold
        }
        // At first half as fast as the turn, so the free edge stays under the finger.
        assertEquals(0.01f, LeafTurn.fold(0.02f), 0.001f)
    }

    @Test
    fun `the view stays while the leaf rises and follows it after`() {
        assertEquals(0f, LeafTurn.leafLeft(0.3f, width), 0.01f)
        assertEquals(0f, LeafTurn.leafLeft(0.5f, width), 0.01f)
        assertEquals(-width, LeafTurn.leafLeft(1f, width), 0.01f)
        assertTrue(LeafTurn.leafLeft(0.75f, width) < 0f)
    }

    @Test
    fun `once past standing the leaf always reaches the right edge, so the page it falls on never shows`() {
        for (i in 50..100) {
            val scene = LeafTurn.scene(i / 100f, width, height, grip = 0.9f)
            val freeEdge = scene.mirror.map(Offset(scene.leafLeft, height / 2f))
            assertEquals("at ${i / 100f}", width, freeEdge.x, 0.5f)
        }
    }

    @Test
    fun `the page on the back lands readable, in the place it is read in`() {
        for (progress in listOf(0.5f, 0.75f, 1f)) {
            val scene = LeafTurn.scene(progress, width, height, grip = 0.5f)
            for (point in listOf(Offset(width * 0.8f, 100f), Offset(width * 0.95f, 2000f))) {
                if (progress < 1f && point.x < (scene.foldTop.x + scene.foldBottom.x) / 2f) continue
                val drawn = scene.back.map(point)
                assertEquals(point.x, drawn.x, 0.5f)
                assertEquals(point.y, drawn.y, 0.5f)
            }
        }
    }

    @Test
    fun `a page held low lifts at the bottom corner first, held high at the top one`() {
        val low = LeafTurn.scene(0.2f, width, height, grip = 0.95f)
        val high = LeafTurn.scene(0.2f, width, height, grip = 0.05f)
        assertTrue(low.foldBottom.x > low.foldTop.x)
        assertTrue(high.foldTop.x > high.foldBottom.x)
        // By the middle of the turn the leaf stands straight.
        val standing = LeafTurn.scene(0.5f, width, height, grip = 0.95f)
        assertEquals(standing.foldTop.x, standing.foldBottom.x, 0.01f)
    }

    @Test
    fun `the mirror leaves the fold in place, and folding twice is no fold`() {
        val scene = LeafTurn.scene(0.3f, width, height, grip = 0.8f)
        assertClose(scene.foldTop, scene.mirror.map(scene.foldTop))
        assertClose(scene.foldBottom, scene.mirror.map(scene.foldBottom))
        val point = Offset(50f, 700f)
        assertClose(point, scene.mirror.map(scene.mirror.map(point)))
    }

    private fun assertClose(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
    }
}
