package com.example.mutqan.ui.mushaf

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

/*
 * The reading lamp: the Mushaf as if read in a dark room by a warm reading light. A pool of warm light lies over the
 * page, its middle a little above the page's, so the page is lit nearly to its corners and the room past it falls away
 * into a warm dark. It is one layer of light over everything the page area draws (the page, and in the book view the
 * book and the desk), multiplying it as light does: so it suits every page colour (on the night page the print takes the
 * lamp's colour) and every view, and the bars over it stay as they are.
 */

internal object ReadingLamp {

    /** The lamp's light: a warm white, as from a bulb of about 3000 K to eyes used to it. */
    val WARM = Color(0xFFFFE0B6)
    /** The dark of the room, warm too: what little light reaches it is the lamp's. */
    val ROOM = Color(0xFF180F09)

    /**
     * How wide and tall the pool is, as shares of the area's width and height (upright; on a screen held sideways, where
     * the whole open book lies wide, the two change places), and how far down its middle lies.
     */
    const val WIDE = 0.95f
    const val TALL = 0.56f
    const val MIDDLE = 0.47f
    /** How far the light is drawn, in pool sizes (1 being the pool's edge); past it the room is dark. */
    const val REACH = 1.2f

    // How much of the lamp's light reaches each distance from the pool's middle: all of it over the middle of the page,
    // a little less toward the page's corners, then quickly little, past the page.
    private val AT = floatArrayOf(0f, 0.35f, 0.55f, 0.72f, 0.86f, 1f, REACH)
    private val LIGHT = floatArrayOf(1f, 1f, 0.93f, 0.78f, 0.45f, 0.14f, 0.05f)

    /** How much of the lamp's light reaches a point [distance] from the pool's middle, 1 being the pool's edge. */
    fun light(distance: Float): Float {
        val d = distance.coerceAtLeast(0f)
        for (i in 1 until AT.size) {
            if (d <= AT[i]) {
                val t = (d - AT[i - 1]) / (AT[i] - AT[i - 1])
                return LIGHT[i - 1] + (LIGHT[i] - LIGHT[i - 1]) * t
            }
        }
        return LIGHT.last()
    }

    /** The light's colour [distance] from the pool's middle: the lamp's warm white, fading into the dark of the room. */
    fun colorAt(distance: Float): Color {
        val k = light(distance)
        return Color(
            red = ROOM.red + (WARM.red - ROOM.red) * k,
            green = ROOM.green + (WARM.green - ROOM.green) * k,
            blue = ROOM.blue + (WARM.blue - ROOM.blue) * k
        )
    }

    /** The light as gradient stops over a radius of [REACH] pool sizes. */
    fun stops(): Array<Pair<Float, Color>> = Array(AT.size) { AT[it] / REACH to colorAt(AT[it]) }
}

/** Lays the reading lamp's light over this area's drawing (see [ReadingLamp]). */
internal fun Modifier.readingLamp(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val upright = h >= w
    val wide = (if (upright) ReadingLamp.WIDE else ReadingLamp.TALL) * w
    val tallness = (if (upright) ReadingLamp.TALL else ReadingLamp.WIDE) * h
    val squeeze = if (wide > 0f) tallness / wide else 1f
    val middle = Offset(w / 2f, ReadingLamp.MIDDLE * h)
    val light = Brush.radialGradient(*ReadingLamp.stops(), center = middle, radius = wide * ReadingLamp.REACH)
    // A round pool squeezed to the pool's height; the rect is the area, as it lies before the squeeze.
    val top = middle.y - middle.y / squeeze
    val tall = h / squeeze
    onDrawWithContent {
        drawContent()
        withTransform({ scale(1f, squeeze, pivot = middle) }) {
            // Multiplied in, as light falls on things: Modulate is Android's multiply on every version.
            drawRect(light, topLeft = Offset(0f, top), size = Size(w, tall), blendMode = BlendMode.Modulate)
        }
    }
}
