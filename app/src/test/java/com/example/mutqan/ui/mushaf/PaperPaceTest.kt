package com.example.mutqan.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperPaceTest {

    private val page = 1080f

    @Test
    fun `a whole page takes the full time, a part of it less, and even a little never flashes by`() {
        assertEquals(PaperPace.FULL_MILLIS, PaperPace.durationMillis(page, page))
        assertEquals(PaperPace.FULL_MILLIS, PaperPace.durationMillis(-page, page))
        val half = PaperPace.durationMillis(page / 2f, page)
        assertTrue(half in (PaperPace.MIN_MILLIS + 1) until PaperPace.FULL_MILLIS)
        assertEquals(PaperPace.MIN_MILLIS, PaperPace.durationMillis(5f, page))
        assertEquals(0L, PaperPace.durationMillis(0f, page))
    }

    @Test
    fun `however hard the flick, the page goes no faster than paper`() {
        val seconds = PaperPace.durationMillis(page * 0.8f, page) / 1000f
        // A flick at 20 000 pixels a second.
        val slope = PaperPace.startSlope(page * 0.8f, 20_000f, seconds)
        assertEquals(PaperPace.MAX_START, slope, 0.0001f)
        var fastest = 0f
        for (i in 0..100) fastest = maxOf(fastest, PaperPace.velocity(i / 100f, page * 0.8f, slope, seconds))
        // Never more than about one and a half pages a second (some 1 650 pixels a second for 80% of a page).
        assertTrue("fastest $fastest", fastest <= 1.6f * page)
        // Let go halfway, the rest takes well over half a second.
        assertTrue(PaperPace.durationMillis(page / 2f, page) >= 600L)
    }

    @Test
    fun `it goes on from the finger's speed and comes to rest at the page, without going past it`() {
        val distance = -page * 0.7f  // going back
        val seconds = PaperPace.durationMillis(distance, page) / 1000f
        // A slow finger is carried on at its own speed.
        val slope = PaperPace.startSlope(distance, -500f, seconds)
        assertEquals(-500f, PaperPace.velocity(0f, distance, slope, seconds), 0.5f)
        assertEquals(0f, PaperPace.velocity(1f, distance, slope, seconds), 0.0001f)
        assertEquals(0f, PaperPace.position(0f, distance, slope), 0.0001f)
        assertEquals(distance, PaperPace.position(1f, distance, slope), 0.0001f)
        for (start in listOf(0f, 1f, PaperPace.MAX_START)) {
            var last = 0f
            for (i in 0..100) {
                val x = PaperPace.position(i / 100f, distance, start)
                assertTrue(x <= last + 0.0001f)  // always toward the page, never back
                assertTrue(x >= distance - 0.0001f)  // never past it
                last = x
            }
        }
    }

    @Test
    fun `it comes to rest softly, so the book seen smaller does not rush back to its size at the end`() {
        for (slope in listOf(0f, 0.5f, PaperPace.MAX_START)) {
            val seconds = PaperPace.durationMillis(page * 0.3f, page) / 1000f
            val average = page * 0.3f / seconds
            // Near the end it is barely moving, and over its last fifth it goes only a few hundredths of the way.
            assertTrue(PaperPace.velocity(0.95f, page * 0.3f, slope, seconds) < 0.05f * average)
            assertTrue(page * 0.3f - PaperPace.position(0.8f, page * 0.3f, slope) < 0.03f * page * 0.3f)
        }
        // Let go a third of the way into a turn: the page falls back, and the book seen smaller grows back with it. Over
        // the last quarter of the time it grows by under 3% (an even slow-down to a stop left about 8% for that quarter).
        val back = -page * 0.3f
        val seconds = PaperPace.durationMillis(back, page) / 1000f
        fun zoomAt(u: Float) = LeafTurn.zoom(0.3f + PaperPace.position(u, back, 0f) / page, BOOK_TURN_ZOOM)
        assertEquals(1f, zoomAt(1f), 0.0001f)
        assertTrue("last quarter ${1f - zoomAt(0.75f)}", 1f - zoomAt(0.75f) < 0.03f)
        // And the whole of it takes over half a second.
        assertTrue(seconds > 0.5f)
    }

    @Test
    fun `a finger going the other way gives no push, and a turn the app makes starts and ends gently`() {
        assertEquals(0f, PaperPace.startSlope(page, -3000f, 0.65f), 0.0001f)
        // From rest (the recitation turning the page): slow, faster, slow.
        val start = PaperPace.velocity(0f, page, 0f, 0.65f)
        val middle = PaperPace.velocity(0.5f, page, 0f, 0.65f)
        assertEquals(0f, start, 0.0001f)
        assertTrue(middle > 0f)
    }
}
