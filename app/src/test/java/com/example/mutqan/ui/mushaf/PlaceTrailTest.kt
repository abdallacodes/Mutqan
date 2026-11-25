package com.example.mutqan.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTrailTest {
    private val a = Place(5, 40)
    private val b = Place(50, 300)
    private val c = Place(100, 700)

    @Test
    fun aJumpKeepsWhereTheReaderWas() {
        val t = PlaceTrail().jump(a, b)
        assertEquals(listOf(a, b), t.places)
        assertEquals(a, t.previous)
        assertNull(t.next)
    }

    @Test
    fun backAndForwardKeepThePlacesAfter() {
        val t = PlaceTrail().jump(a, b).jump(b, c)
        val back = t.back(c)
        assertEquals(b, back.places[back.at])
        assertEquals(c, back.next)
        val forward = back.forward(b)
        assertEquals(c, forward.places[forward.at])
    }

    @Test
    fun aNewJumpFromAnEarlierPlaceDropsThePlacesAfter() {
        val t = PlaceTrail().jump(a, b).jump(b, c).back(c).back(b)
        assertEquals(a, t.places[t.at])
        val d = Place(200, 1500)
        val jumped = t.jump(a, d)
        assertEquals(listOf(a, d), jumped.places)
    }

    @Test
    fun thePlaceLeftIsKeptAsTheReaderLeftIt() {
        // At b, the reader turned on to page 52 before jumping on.
        val turned = Place(52, 0)
        val t = PlaceTrail().jump(a, b).jump(turned, c)
        assertEquals(listOf(a, turned, c), t.places)
        // Going back from c after turning a page there too.
        val back = t.back(Place(101, 0))
        assertEquals(listOf(a, turned, Place(101, 0)), back.places)
        assertEquals(1, back.at)
    }

    @Test
    fun goingBackToWhereTheReaderAlreadyIsMergesThePlaces() {
        // Jumped from a to b, then turned back to a's page and verse by hand, then pressed back: one a, not two.
        val t = PlaceTrail().jump(a, b).back(a)
        assertEquals(listOf(a), t.places)
        assertTrue(t.isEmpty)
    }

    @Test
    fun aJumpToTheSamePlaceIsNoJump() {
        assertTrue(PlaceTrail().jump(a, a).isEmpty)
    }

    @Test
    fun onlyTheLatestPlacesAreKept() {
        var t = PlaceTrail()
        for (i in 1..40) t = t.jump(Place(i), Place(i + 1))
        assertEquals(PlaceTrail.MOST, t.places.size)
        assertEquals(Place(41), t.places.last())
        assertEquals(t.places.lastIndex, t.at)
    }

    @Test
    fun theTrailSurvivesBeingSaved() {
        val t = PlaceTrail().jump(a, b).jump(b, c).back(c)
        val saved = with(PlaceTrail.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(t) }!!
        assertEquals(t, PlaceTrail.Saver.restore(saved))
    }
}
