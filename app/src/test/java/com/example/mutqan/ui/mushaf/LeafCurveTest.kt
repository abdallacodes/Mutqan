package com.example.mutqan.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class LeafCurveTest {

    private val spine = 1080f
    private val length = 1035f

    @Test
    fun `the leaf lies flat on the left page before the turn and on the right page after it`() {
        val before = LeafCurve.section(0f, spine, length)
        val after = LeafCurve.section(1f, spine, length)
        for (p in before) assertEquals(0f, p.y, 0.01f)
        for (p in after) assertEquals(0f, p.y, 0.01f)
        assertEquals(spine - length, before.last().x, 0.5f)
        assertEquals(spine + length, after.last().x, 0.5f)
    }

    @Test
    fun `halfway it stands on its bound edge, bent, its free edge already past upright`() {
        val boundEdge = LeafCurve.angle(0f, 0.5f)
        val freeEdge = LeafCurve.angle(1f, 0.5f)
        assertTrue(boundEdge < PI / 2)
        assertTrue(freeEdge > PI / 2)
        assertEquals(PI / 2, ((boundEdge + freeEdge) / 2).toDouble(), 0.001)
        // High off the book: most of the leaf's length is height.
        assertTrue(LeafCurve.section(0.5f, spine, length).last().y > length * 0.8f)
    }

    @Test
    fun `as the turn starts the free edge rises while the part by the binding still lies flat`() {
        assertEquals(0f, LeafCurve.angle(0f, 0.15f), 0.0001f)
        assertTrue(LeafCurve.angle(1f, 0.15f) > 0.3f)
    }

    @Test
    fun `the leaf never goes below the pages, is never torn, and turns further toward its free edge`() {
        for (step in 0..20) {
            val t = step / 20f
            val section = LeafCurve.section(t, spine, length)
            var last = -1f
            for (i in 0..LeafCurve.STRIPS) {
                val theta = LeafCurve.angle(i / LeafCurve.STRIPS.toFloat(), t)
                assertTrue(theta >= 0f && theta <= PI.toFloat())
                assertTrue(theta >= last - 0.0001f)
                last = theta
            }
            for (p in section) assertTrue(p.y >= -0.01f)
            // Its length along the paper stays the page's width.
            var along = 0f
            for (i in 1 until section.size) along += (section[i] - section[i - 1]).getDistance()
            assertEquals(length, along, 0.5f)
        }
    }

    @Test
    fun `a strip seen on edge or twisted through it is not given the page`() {
        val a = androidx.compose.ui.geometry.Offset(500f, 300f)
        // A proper strip, a little narrower at the far end.
        assertTrue(LeafCurve.seenFlat(a, androidx.compose.ui.geometry.Offset(530f, 290f), androidx.compose.ui.geometry.Offset(530f, 2110f), androidx.compose.ui.geometry.Offset(500f, 2100f)))
        // On edge: no width.
        assertTrue(!LeafCurve.seenFlat(a, androidx.compose.ui.geometry.Offset(500.2f, 290f), androidx.compose.ui.geometry.Offset(500.3f, 2110f), androidx.compose.ui.geometry.Offset(500f, 2100f)))
        // Twisted: the top goes right, the bottom left.
        assertTrue(!LeafCurve.seenFlat(a, androidx.compose.ui.geometry.Offset(520f, 290f), androidx.compose.ui.geometry.Offset(480f, 2110f), androidx.compose.ui.geometry.Offset(500f, 2100f)))
    }

    @Test
    fun `held low, the bottom corner goes first`() {
        assertTrue(LeafCurve.twist(0.3f, grip = 0.9f) > 0f)
        assertTrue(LeafCurve.twist(0.3f, grip = 0.1f) < 0f)
        assertEquals(0f, LeafCurve.twist(0f, grip = 0.9f), 0.0001f)
        assertEquals(0f, LeafCurve.twist(1f, grip = 0.9f), 0.0001f)
    }

    @Test
    fun `what lies on the book is seen where it is, and what is raised comes nearer`() {
        val onBook = LeafCurve.project(300f, 400f, 0f, cx = 540f, cy = 1212f, eye = 3780f)
        assertEquals(300f, onBook.x, 0.001f)
        assertEquals(400f, onBook.y, 0.001f)
        val raised = LeafCurve.project(300f, 400f, 900f, cx = 540f, cy = 1212f, eye = 3780f)
        assertTrue(raised.x < 300f && raised.y < 400f)
        // Lying flat the leaf is not shaded; on edge it is, the most.
        assertEquals(0f, LeafCurve.shade(0f, 0.4f), 0.0001f)
        assertEquals(0.4f, LeafCurve.shade((PI / 2).toFloat(), 0.4f), 0.0001f)
        assertTrue(abs(LeafCurve.shade(PI.toFloat(), 0.4f)) < 0.0001f)
    }
}
