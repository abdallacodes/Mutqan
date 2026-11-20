package com.example.mutqan.ui.mushaf

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BookMaterialsTest {

    private val density = 2.625f

    private fun luma(argb: Int): Float =
        0.299f * ((argb shr 16) and 0xFF) + 0.587f * ((argb shr 8) and 0xFF) + 0.114f * (argb and 0xFF)

    private fun luma(color: Color): Float = 0.299f * color.red * 255f + 0.587f * color.green * 255f + 0.114f * color.blue * 255f

    @Test
    fun `the desk is the same walnut every time`() {
        assertArrayEquals(DeskWood.pixels(120, 200, density, dark = false), DeskWood.pixels(120, 200, density, dark = false))
    }

    @Test
    fun `its grain runs across the screen`() {
        val w = 300
        val h = 300
        val wood = DeskWood.pixels(w, h, density, dark = false)
        var along = 0f
        var across = 0f
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val here = luma(wood[y * w + x])
            along += abs(luma(wood[y * w + x + 1]) - here)
            across += abs(luma(wood[(y + 1) * w + x]) - here)
        }
        assertTrue("along $along, across $across", along * 2f < across)
    }

    @Test
    fun `the walnut is lighter than the leather, so the cover shows on it, by day and in the dim light of night`() {
        val day = DeskWood.pixels(200, 300, density, dark = false)
        val dayWood = day.map { luma(it) }.average().toFloat()
        assertTrue("wood $dayWood, leather ${luma(DAY_LEATHER)}", dayWood > luma(DAY_LEATHER) + 5f)
        val night = DeskWood.pixels(200, 300, density, dark = true)
        val nightWood = night.map { luma(it) }.average().toFloat()
        // At night the leather is darker than the desk around it, so it is the book's edge and not a lit frame.
        assertTrue("wood $nightWood, leather ${luma(NIGHT_LEATHER)}", nightWood > luma(NIGHT_LEATHER) + 3f)
        // The desk is still there at night, but dim: well under half as light as by day, and darker than the night page,
        // so the page stands out from it.
        assertTrue(nightWood < dayWood / 2f)
        assertTrue(nightWood < luma(MushafTheme.NIGHT.colors(appIsDark = true).bg))
    }

    @Test
    fun `the leather keeps close to its colour and tiles without a seam`() {
        val side = 96
        val tile = LeatherGrain.pixels(side, density, DAY_LEATHER)
        val base = luma(DAY_LEATHER)
        for (p in tile) assertTrue(abs(luma(p) / base - 1f) <= LeatherGrain.RELIEF + 0.02f)
        // From the last column to the first is no bigger a step than between any two neighbours.
        var inside = 0f
        var seam = 0f
        for (y in 0 until side) {
            for (x in 0 until side - 1) inside += abs(luma(tile[y * side + x + 1]) - luma(tile[y * side + x]))
            seam += abs(luma(tile[y * side]) - luma(tile[y * side + side - 1]))
        }
        assertTrue("seam ${seam / side}, inside ${inside / (side * (side - 1))}", seam / side < 2f * inside / (side * (side - 1)))
    }
}
