package com.example.mutqan.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The game's own colours: a night sky over a town, and lanterns in jewel tones with gold frames. */
internal object GameColors {
    val SkyTop = Color(0xFF060A1C)
    val SkyMid = Color(0xFF15163A)
    val SkyLow = Color(0xFF39204A)
    val Horizon = Color(0xFFD9824A)
    val Moon = Color(0xFFFFE9AE)
    val Gold = Color(0xFFF5CC6E)
    val GoldDeep = Color(0xFFB0802A)
    val Window = Color(0xFFFFF4D6)
    val Ink = Color(0xFF2A1906)
    val Town = Color(0xFF0E0818)
    val TownLight = Color(0xFFFFC56B)
    val Red = Color(0xFFFF5964)
    val Calm = Color(0xFF6FB7FF)
    val Panel = Color(0xEB120C26)

    /** A golden lantern's body. */
    val Golden = Color(0xFFE9B23C)

    /** The sky near dawn, toward which the night turns as the levels go up. */
    private val DawnTop = Color(0xFF1C2758)
    private val DawnMid = Color(0xFF3D4384)
    private val DawnLow = Color(0xFF9C5C88)
    private val DawnHorizon = Color(0xFFFFB57E)

    /** The sky's four colours, from top to horizon, [dawn] of the way (0 to 1) from night to dawn. */
    fun sky(dawn: Float): List<Color> {
        val d = dawn.coerceIn(0f, 1f)
        return listOf(lerp(SkyTop, DawnTop, d), lerp(SkyMid, DawnMid, d), lerp(SkyLow, DawnLow, d), lerp(Horizon, DawnHorizon, d))
    }

    /** Emerald, ruby, sapphire, amber, amethyst. */
    val hues = listOf(
        Color(0xFF13A06E),
        Color(0xFFD03E57),
        Color(0xFF2F73DF),
        Color(0xFFE59822),
        Color(0xFF904FD6)
    )

    fun hue(index: Int): Color = hues[Math.floorMod(index, hues.size)]
}

private class Star(val x: Float, val y: Float, val radius: Float, val phase: Float, val speed: Float)

/**
 * The sky: deep blue to a warm glow over the town, stars that twinkle with [time] (seconds), and a crescent at [moon]
 * (fractions of the width and height). With [dawn] (0 to 1) the night turns toward dawn and the stars fade.
 */
@Composable
internal fun NightSky(time: () -> Float, modifier: Modifier = Modifier, moon: Offset = Offset(0.16f, 0.15f), dawn: Float = 0f) {
    val stars = remember {
        val random = Random(7)
        List(80) { Star(random.nextFloat(), random.nextFloat() * 0.78f, 0.5f + random.nextFloat() * 1.5f, random.nextFloat() * 6.28f, 0.5f + random.nextFloat() * 2f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time()
        val sky = GameColors.sky(dawn)
        drawRect(
            Brush.verticalGradient(
                0f to sky[0],
                0.5f to sky[1],
                0.84f to sky[2],
                1f to sky[3]
            )
        )
        val unit = size.minDimension / 400f
        val starLight = 1f - 0.65f * dawn.coerceIn(0f, 1f)
        for (s in stars) {
            val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * s.speed + s.phase))
            drawCircle(Color.White.copy(alpha = a * 0.9f * starLight), radius = s.radius * unit, center = Offset(s.x * size.width, s.y * size.height))
        }
        val r = 24f * unit
        val c = Offset(size.width * moon.x, size.height * moon.y)
        drawCircle(
            Brush.radialGradient(listOf(GameColors.Moon.copy(alpha = 0.28f), Color.Transparent), center = c, radius = r * 3.4f),
            radius = r * 3.4f,
            center = c
        )
        val disc = Path().apply { addOval(Rect(c, r)) }
        val cut = Path().apply { addOval(Rect(c + Offset(r * 0.48f, -r * 0.22f), r * 0.92f)) }
        drawPath(Path().apply { op(disc, cut, PathOperation.Difference) }, GameColors.Moon)
    }
}

/** A town at the bottom of the sky: domes, minarets and houses against the glow, with lit windows. */
@Composable
internal fun Skyline(modifier: Modifier = Modifier) {
    val windows = remember {
        val random = Random(11)
        List(26) { Offset(random.nextFloat(), 0.62f + random.nextFloat() * 0.3f) }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val town = GameColors.Town
        fun house(x0: Float, x1: Float, top: Float) = drawRect(town, Offset(x0 * w, top * h), Size((x1 - x0) * w, h - top * h))
        fun dome(cx: Float, radius: Float, base: Float) {
            val rx = radius * w
            val baseY = base * h
            drawRect(town, Offset(cx * w - rx, baseY), Size(rx * 2, h - baseY))
            val path = Path().apply {
                moveTo(cx * w - rx, baseY)
                cubicTo(cx * w - rx, baseY - rx * 1.05f, cx * w - rx * 0.2f, baseY - rx * 1.25f, cx * w, baseY - rx * 1.55f)
                cubicTo(cx * w + rx * 0.2f, baseY - rx * 1.25f, cx * w + rx, baseY - rx * 1.05f, cx * w + rx, baseY)
                close()
            }
            drawPath(path, town)
            drawLine(town, Offset(cx * w, baseY - rx * 1.5f), Offset(cx * w, baseY - rx * 1.85f), strokeWidth = rx * 0.06f + 1f)
            drawCircle(town, radius = rx * 0.07f + 1f, center = Offset(cx * w, baseY - rx * 1.9f))
        }
        fun minaret(cx: Float, width: Float, top: Float) {
            val half = width * w / 2
            val topY = top * h
            drawRect(town, Offset(cx * w - half, topY), Size(half * 2, h - topY))
            // Balcony, and the pointed cap.
            drawRect(town, Offset(cx * w - half * 1.6f, topY + (h - topY) * 0.18f), Size(half * 3.2f, half * 0.8f))
            val cap = Path().apply {
                moveTo(cx * w - half * 1.1f, topY)
                lineTo(cx * w, topY - half * 4.2f)
                lineTo(cx * w + half * 1.1f, topY)
                close()
            }
            drawPath(cap, town)
        }
        house(0f, 0.11f, 0.58f)
        dome(0.19f, 0.075f, 0.56f)
        minaret(0.3f, 0.026f, 0.16f)
        house(0.33f, 0.36f, 0.62f)
        dome(0.47f, 0.13f, 0.5f)
        minaret(0.64f, 0.028f, 0.1f)
        house(0.66f, 0.78f, 0.6f)
        dome(0.85f, 0.085f, 0.57f)
        minaret(0.95f, 0.024f, 0.22f)
        house(0.97f, 1f, 0.55f)
        for (window in windows) {
            drawRoundRect(
                GameColors.TownLight.copy(alpha = 0.8f),
                topLeft = Offset(window.x * w, window.y * h),
                size = Size(w * 0.008f + 2f, h * 0.035f),
                cornerRadius = CornerRadius(w * 0.004f)
            )
        }
    }
}

/**
 * A lantern filling this draw area: a soft glow in its colour, a ring to hang it by, a gold cap, a bellied body in
 * [body] with ribs, a warm window where its word sits, a foot and a tassel. [lit] dims it toward dark (a broken one).
 */
internal fun DrawScope.drawLantern(
    body: Color,
    lit: Float = 1f,
    glow: Float = 1f,
    topLeft: Offset = Offset.Zero,
    area: Size = size,
    alpha: Float = 1f
) {
    if (alpha <= 0f) return
    if (alpha >= 1f) {
        lanternAt(body, lit, glow, topLeft, area)
        return
    }
    // See-through: drawn in a layer as wide as its glow.
    val bounds = Rect(topLeft.x - area.width * 0.45f, topLeft.y - area.height * 0.2f, topLeft.x + area.width * 1.45f, topLeft.y + area.height * 1.15f)
    drawContext.canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
    lanternAt(body, lit, glow, topLeft, area)
    drawContext.canvas.restore()
}

/** Little four-pointed glints turning around a golden lantern filling this draw area, at [time] seconds. */
internal fun DrawScope.drawTwinkles(time: Float) {
    val cx = size.width / 2f
    val cy = size.height * 0.52f
    for (i in 0 until 4) {
        val angle = time * 0.9f + i * (PI.toFloat() / 2f)
        val r = size.width * (0.62f + 0.06f * sin(time * 2.3f + i))
        val c = Offset(cx + cos(angle) * r, cy + sin(angle) * r * 0.9f)
        val a = 0.45f + 0.55f * (0.5f + 0.5f * sin(time * 5f + i * 1.7f))
        val arm = size.width * 0.07f * a
        val glint = Color(0xFFFFF3C4).copy(alpha = a)
        drawLine(glint, c - Offset(arm, 0f), c + Offset(arm, 0f), strokeWidth = size.width * 0.018f)
        drawLine(glint, c - Offset(0f, arm), c + Offset(0f, arm), strokeWidth = size.width * 0.018f)
        drawCircle(glint, radius = size.width * 0.018f, center = c)
    }
}

private fun DrawScope.lanternAt(body: Color, lit: Float, glow: Float, topLeft: Offset, area: Size) = translate(topLeft.x, topLeft.y) {
    val w = area.width
    val h = area.height
    val cx = w / 2f
    val gold = lerp(GameColors.GoldDeep, GameColors.Gold, lit)
    val tone = lerp(Color(0xFF2A2430), body, lit)

    val glowCenter = Offset(cx, h * 0.52f)
    drawCircle(
        Brush.radialGradient(listOf(tone.copy(alpha = 0.55f * glow * lit), Color.Transparent), center = glowCenter, radius = w * 0.85f),
        radius = w * 0.85f,
        center = glowCenter
    )
    val stroke = (w * 0.018f).coerceAtLeast(1.5f)
    // The ring and its cord.
    drawCircle(gold, radius = w * 0.045f, center = Offset(cx, h * 0.035f), style = Stroke(stroke))
    drawLine(gold, Offset(cx, h * 0.065f), Offset(cx, h * 0.09f), strokeWidth = stroke)

    val capTop = h * 0.09f
    val capBottom = h * 0.17f
    val cap = Path().apply {
        moveTo(cx - w * 0.13f, capTop)
        lineTo(cx + w * 0.13f, capTop)
        lineTo(cx + w * 0.3f, capBottom)
        lineTo(cx - w * 0.3f, capBottom)
        close()
    }
    drawPath(cap, Brush.verticalGradient(listOf(gold, GameColors.GoldDeep), startY = capTop, endY = capBottom))

    val top = capBottom
    val bottom = h * 0.83f
    val mid = (top + bottom) / 2f
    val endHalf = w * 0.3f
    val belly = w * 0.5f
    val shell = Path().apply {
        moveTo(cx - endHalf, top)
        quadraticTo(cx - belly, mid, cx - endHalf, bottom)
        lineTo(cx + endHalf, bottom)
        quadraticTo(cx + belly, mid, cx + endHalf, top)
        close()
    }
    drawPath(
        shell,
        Brush.horizontalGradient(
            listOf(lerp(tone, Color.Black, 0.45f), tone, lerp(tone, Color.White, 0.18f), tone, lerp(tone, Color.Black, 0.45f)),
            startX = cx - belly,
            endX = cx + belly
        )
    )
    // Two ribs following the belly.
    for (side in listOf(-1f, 1f)) {
        val rib = Path().apply {
            moveTo(cx + side * endHalf * 0.55f, top)
            quadraticTo(cx + side * belly * 0.86f, mid, cx + side * endHalf * 0.55f, bottom)
        }
        drawPath(rib, gold.copy(alpha = 0.55f), style = Stroke(stroke * 0.7f))
    }
    drawPath(shell, gold, style = Stroke(stroke))

    // The window, lit from inside.
    val win = Rect(cx - w * 0.36f, top + h * 0.075f, cx + w * 0.36f, bottom - h * 0.075f)
    val window = lerp(Color(0xFF6D6670), GameColors.Window, lit)
    drawRoundRect(
        Brush.radialGradient(
            listOf(window, lerp(window, tone, 0.22f)),
            center = win.center,
            radius = win.width * 0.8f
        ),
        topLeft = win.topLeft,
        size = win.size,
        cornerRadius = CornerRadius(w * 0.12f)
    )
    drawRoundRect(gold.copy(alpha = 0.75f), topLeft = win.topLeft, size = win.size, cornerRadius = CornerRadius(w * 0.12f), style = Stroke(stroke * 0.7f))

    val foot = Path().apply {
        moveTo(cx - w * 0.3f, bottom)
        lineTo(cx + w * 0.3f, bottom)
        lineTo(cx + w * 0.14f, h * 0.9f)
        lineTo(cx - w * 0.14f, h * 0.9f)
        close()
    }
    drawPath(foot, Brush.verticalGradient(listOf(GameColors.GoldDeep, gold), startY = bottom, endY = h * 0.9f))
    drawLine(gold, Offset(cx, h * 0.9f), Offset(cx, h * 0.955f), strokeWidth = stroke)
    val tassel = Path().apply {
        moveTo(cx, h * 0.95f)
        lineTo(cx + w * 0.035f, h * 0.975f)
        lineTo(cx, h)
        lineTo(cx - w * 0.035f, h * 0.975f)
        close()
    }
    drawPath(tassel, gold)
}

/** A spark of a burst: flies out, slows, falls a little and fades. */
internal class Spark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float,
    val life: Float
) {
    var age = 0f
}

/** Sparks and the points that float up from a lantern, in pixels; the screen moves them every frame. */
internal class Effects {
    val sparks = ArrayList<Spark>()

    /** Floating words: text, colour, where, age. */
    class Floater(val text: String, val color: Color, var x: Float, var y: Float, val big: Boolean) {
        var age = 0f
    }

    val floaters = ArrayList<Floater>()

    /** The last flash of red (a mistake) and of gold (a verse), 1 fading to 0. */
    var redFlash = 0f
    var goldFlash = 0f

    /** The shake of the whole sky after a mistake, 1 fading to 0. */
    var shake = 0f

    fun burst(x: Float, y: Float, color: Color, count: Int, speed: Float, random: Random = Random.Default) {
        repeat(count) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val v = speed * (0.35f + random.nextFloat() * 0.75f)
            sparks += Spark(
                x, y, cos(angle) * v, sin(angle) * v - speed * 0.25f,
                if (random.nextFloat() < 0.35f) GameColors.Gold else color,
                radius = 2f + random.nextFloat() * 4f,
                life = 0.55f + random.nextFloat() * 0.6f
            )
        }
    }

    fun float(text: String, color: Color, x: Float, y: Float, big: Boolean = false) {
        floaters += Floater(text, color, x, y, big)
    }

    fun update(dt: Float, gravity: Float) {
        val sparkIterator = sparks.iterator()
        while (sparkIterator.hasNext()) {
            val s = sparkIterator.next()
            s.age += dt
            if (s.age >= s.life) {
                sparkIterator.remove()
                continue
            }
            s.vx *= (1f - 1.6f * dt).coerceAtLeast(0f)
            s.vy = s.vy * (1f - 1.6f * dt).coerceAtLeast(0f) + gravity * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
        val floaterIterator = floaters.iterator()
        while (floaterIterator.hasNext()) {
            val f = floaterIterator.next()
            f.age += dt
            f.y -= dt * 60f
            if (f.age > FLOAT_LIFE) floaterIterator.remove()
        }
        redFlash = (redFlash - dt * 2.2f).coerceAtLeast(0f)
        goldFlash = (goldFlash - dt * 1.4f).coerceAtLeast(0f)
        shake = (shake - dt * 3f).coerceAtLeast(0f)
    }

    companion object {
        const val FLOAT_LIFE = 1.1f
    }
}
