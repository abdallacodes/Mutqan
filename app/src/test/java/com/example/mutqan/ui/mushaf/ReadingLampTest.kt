package com.example.mutqan.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ReadingLampTest {

    // A phone screen, and the paper of a page of the book view on it.
    private val w = 1080f
    private val h = 2424f
    private val top = 359f
    private val bottom = 2065f

    private fun lightAt(x: Float, y: Float): Float {
        val dx = (x - w / 2f) / (ReadingLamp.WIDE * w)
        val dy = (y - ReadingLamp.MIDDLE * h) / (ReadingLamp.TALL * h)
        return ReadingLamp.light(sqrt(dx * dx + dy * dy))
    }

    @Test
    fun `the lamp lights the middle fully and falls off steadily into the dark of the room`() {
        assertEquals(1f, ReadingLamp.light(0f), 0.0001f)
        assertEquals(1f, ReadingLamp.light(0.3f), 0.0001f)
        var last = 1f
        for (i in 0..200) {
            val light = ReadingLamp.light(i / 100f)
            assertTrue(light <= last + 0.0001f)
            last = light
        }
        assertTrue(ReadingLamp.light(1f) <= 0.15f)
        assertTrue(ReadingLamp.light(1.5f) <= 0.06f)
        assertEquals(ReadingLamp.WARM, ReadingLamp.colorAt(0f))
    }

    @Test
    fun `the page stays readable under it, and the room around is dark`() {
        // The middle of the page and its sides are in full light, its top and bottom nearly so.
        assertTrue(lightAt(w / 2f, h / 2f) >= 0.99f)
        assertTrue(lightAt(0f, h / 2f) >= 0.9f)
        assertTrue(lightAt(w / 2f, top) >= 0.8f)
        assertTrue(lightAt(w / 2f, bottom) >= 0.8f)
        // Even its far corners keep a good part of the light.
        for (x in listOf(0f, w)) for (y in listOf(top, bottom)) assertTrue("corner $x,$y", lightAt(x, y) >= 0.4f)
        // The corners of the screen are dark.
        for (x in listOf(0f, w)) for (y in listOf(0f, h)) assertTrue("screen corner $x,$y", lightAt(x, y) <= 0.2f)
    }
}
