package com.example.mutqan.ui.mushaf

import androidx.compose.ui.unit.Density
import com.example.mutqan.quran.MUSHAF_PAGE_ASPECT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFrameTest {

    // A phone 1080 × 2424 pixels at 2.625 pixels a dp.
    private val density = Density(2.625f)
    private val width = 1080f
    private val height = 2424f
    private val middle = 28f * 2.625f

    private fun layout(page: Int, w: Float = width, h: Float = height) = BookFrame.layout(page, w, h, density)

    @Test
    fun `the paper keeps the printed page's shape and the book sits in the middle of the screen`() {
        val face = layout(78).face
        assertEquals(MUSHAF_PAGE_ASPECT, (face.width - middle) / face.height, 0.002f)
        assertEquals(height - face.bottom, face.top, 1f)
        // Much shorter than the screen: that was the trouble with paper filling it.
        assertTrue(face.height < height * 0.8f)
    }

    @Test
    fun `a page's bound edge runs to the edge of the screen it is bound on, the book's outer side opposite`() {
        val left = layout(78)  // a left-hand page, bound on its right
        assertEquals(width, left.face.right, 0.01f)
        assertEquals(left.face.left, left.edges.right, 0.01f)
        assertTrue(left.cover.left < left.edges.left && left.cover.left > 0f)
        assertTrue(left.cover.right > width)

        val right = layout(79)  // a right-hand page, bound on its left
        assertEquals(0f, right.face.left, 0.01f)
        assertEquals(right.face.right, right.edges.left, 0.01f)
        assertTrue(right.cover.right > right.edges.right && right.cover.right < width)
        assertTrue(right.cover.left < 0f)
    }

    @Test
    fun `the cover reaches past the paper above and below it`() {
        val book = layout(78)
        assertTrue(book.cover.top < book.face.top)
        assertTrue(book.cover.bottom > book.face.bottom)
    }

    @Test
    fun `the edges are as deep as the book is thick on that side`() {
        assertEquals(1, BookFrame.leavesBeside(1))
        assertEquals(302, BookFrame.leavesBeside(2))
        assertEquals(302, BookFrame.leavesBeside(603))
        assertEquals(1, BookFrame.leavesBeside(604))
        // Under a right-hand page lie the pages read; under a left-hand page the pages to come.
        assertTrue(layout(3).edges.width < layout(301).edges.width)
        assertTrue(layout(301).edges.width < layout(601).edges.width)
        assertTrue(layout(4).edges.width > layout(600).edges.width)
        assertTrue(layout(604).edges.width >= 1f)
        assertTrue(layout(2).edges.width <= 8f * 2.625f + 0.5f)
    }

    @Test
    fun `every leaf lies where a left-hand page's paper is`() {
        val sheet = BookFrame.sheet(width, height, density)
        for (page in listOf(2, 78, 300, 604)) assertEquals(layout(page).face, sheet)
    }

    @Test
    fun `on a screen too short for the page at full width the paper fits its height and stays bound at the edge`() {
        val short = 1500f
        val book = layout(78, h = short)
        assertTrue(book.cover.top >= 0f && book.cover.bottom <= short)
        assertEquals(width, book.face.right, 0.01f)
        assertEquals(MUSHAF_PAGE_ASPECT, (book.face.width - middle) / book.face.height, 0.002f)
    }
}
