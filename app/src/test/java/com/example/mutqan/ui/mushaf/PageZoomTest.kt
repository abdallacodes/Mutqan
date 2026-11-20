package com.example.mutqan.ui.mushaf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.example.mutqan.quran.MUSHAF_PAGE_ASPECT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomTest {

    // A phone held upright, 1080 × 2424 pixels.
    private val area = Size(1080f, 2424f)

    /** Where a point of the page at [point] (in the page's own pixels) is on the screen. */
    private fun onScreen(point: Offset, scale: Float, shift: Offset) = point * scale + shift

    @Test
    fun `two fingers zoom about the point between them, which stays under them`() {
        val focus = Offset(700f, 900f)
        val (scale, shift) = PageZoom.pinch(1f, Offset.Zero, focus, 1.8f, Offset.Zero, area)
        assertEquals(1.8f, scale, 0.0001f)
        val point = focus  // at full size the page and the screen are one
        assertEquals(focus.x, onScreen(point, scale, shift).x, 0.01f)
        assertEquals(focus.y, onScreen(point, scale, shift).y, 0.01f)

        // Zoomed in further about another point, which stays put too.
        val focus2 = Offset(300f, 1500f)
        val point2 = (focus2 - shift) / scale
        val (scale2, shift2) = PageZoom.pinch(scale, shift, focus2, 1.5f, Offset.Zero, area)
        assertEquals(2.7f, scale2, 0.0001f)
        assertEquals(focus2.x, onScreen(point2, scale2, shift2).x, 0.01f)
        assertEquals(focus2.y, onScreen(point2, scale2, shift2).y, 0.01f)
    }

    @Test
    fun `the page is never smaller than the screen nor larger than the most, and never leaves its edges`() {
        val (out, outShift) = PageZoom.pinch(1.5f, Offset(-100f, -200f), Offset(500f, 500f), 0.2f, Offset.Zero, area)
        assertEquals(1f, out, 0f)
        assertEquals(Offset.Zero, outShift)
        val (most, _) = PageZoom.pinch(3f, Offset.Zero, Offset(500f, 500f), 10f, Offset.Zero, area)
        assertEquals(PageZoom.MAX, most, 0f)

        // Moved far past its corner, the page stops at it: its edges stay on the screen's edges.
        val shift = PageZoom.clamp(Offset(5000f, -90000f), 2f, area)
        assertEquals(0f, shift.x, 0f)
        assertEquals(area.height * (1f - 2f), shift.y, 0f)
        // At full size it cannot move at all.
        assertEquals(Offset.Zero, PageZoom.clamp(Offset(40f, -40f), 1f, area))
    }

    @Test
    fun `a double tap zooms in on the point tapped, and zoomed in, back out to the whole page`() {
        val at = Offset(540f, 1200f)
        val (scale, shift) = PageZoom.doubleTap(1f, Offset.Zero, at, area)
        assertEquals(PageZoom.DOUBLE_TAP, scale, 0f)
        assertEquals(at.x, onScreen(at, scale, shift).x, 0.01f)
        assertEquals(at.y, onScreen(at, scale, shift).y, 0.01f)

        // Near a corner too the point stays under the finger, and the page still covers the screen.
        val corner = Offset(10f, 10f)
        val (cornerScale, cornerShift) = PageZoom.doubleTap(1f, Offset.Zero, corner, area)
        assertEquals(corner.x, onScreen(corner, cornerScale, cornerShift).x, 0.01f)
        assertEquals(cornerShift, PageZoom.clamp(cornerShift, cornerScale, area))

        val (back, backShift) = PageZoom.doubleTap(scale, shift, at, area)
        assertEquals(1f, back, 0f)
        assertEquals(Offset.Zero, backShift)
    }

    @Test
    fun `filling the width, the open book takes the whole width it is given`() {
        // A phone held sideways: 2424 × 1080 pixels at 2.625 pixels a dp.
        val density = Density(2.625f)
        val width = 2424f
        for (w in listOf(width, width / 2f)) {
            val height = BookFrame.heightToFill(w, density)
            assertTrue(height > 1080)
            val face = BookFrame.layout(78, w, height.toFloat(), density).face
            val widest = BookFrame.layout(78, w, 100_000f, density).face
            // As wide as with all the height in the world, at the printed page's shape.
            assertEquals(widest.width, face.width, 0.5f)
            assertEquals(MUSHAF_PAGE_ASPECT, (face.width - 28f * 2.625f) / face.height, 0.003f)
        }
    }
}
