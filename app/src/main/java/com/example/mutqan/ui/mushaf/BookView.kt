package com.example.mutqan.ui.mushaf

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.mutqan.quran.MUSHAF_PAGE_ASPECT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The book view of the one-page Mushaf: the page as one page of an open book lying on a desk, instead of paper filling
 * the whole screen (taller than any printed page). The paper keeps its printed shape. On its outer side lie the edges of
 * the pages beneath it, as deep as the part of the book on that side is thick (under a right-hand page the pages read,
 * under a left-hand page the pages to come), and the cover reaches a little past them. Along the bound edge the page
 * curves into the middle of the book, which runs on out of sight. The cover is green leather and the book lies on a
 * walnut desk under a soft pool of light: real materials, so it looks like a book and not like a drawing of one, with
 * nothing that shines or competes with the text.
 */

/** Where the parts of the book lie in a page area, in pixels: the paper, the edges beside it and the cover under both. */
internal data class BookLayout(val face: Rect, val edges: Rect, val cover: Rect)

internal object BookFrame {
    /** The desk left showing past the cover on the page's outer side. */
    val DESK = 5.dp
    /** How far the cover reaches past the pages. */
    val RIM = 4.dp
    /** The room kept for the edges of the pages beneath; they fill more of it the thicker the book is on that side. */
    val EDGES = 8.dp
    val EDGES_THINNEST = 1.5.dp
    /** The least desk above and below the book, on a screen too short for the page at its full width. */
    val DESK_ABOVE = 8.dp
    /** Leaves in the Mushaf: page 1 on the back of the first, then one for each even page and the odd page after it. */
    const val LEAVES = 303

    /** The leaves on [page]'s side of the open book: the ones read under a right-hand page, the ones to come under a left-hand page. */
    fun leavesBeside(page: Int): Int = if (page % 2 == 1) (page + 1) / 2 else LEAVES - page / 2

    /** The paper of a left-hand page: where every leaf lies before it turns. */
    fun sheet(width: Float, height: Float, density: Density): Rect = layout(2, width, height, density).face

    fun layout(page: Int, width: Float, height: Float, density: Density): BookLayout = with(density) {
        val middle = MIDDLE_WIDTH.toPx()
        // As wide as the screen allows after the book's outer side, or narrower when the screen is too short for that.
        var faceWidth = max(width - (DESK + RIM + EDGES).toPx(), middle + 1f)
        var faceHeight = (faceWidth - middle) / MUSHAF_PAGE_ASPECT
        val tallest = height - 2f * (RIM + DESK_ABOVE).toPx()
        if (faceHeight > tallest && tallest > 0f) {
            faceHeight = tallest
            faceWidth = faceHeight * MUSHAF_PAGE_ASPECT + middle
        }
        faceWidth = faceWidth.roundToInt().toFloat()
        faceHeight = faceHeight.roundToInt().toFloat()
        val top = ((height - faceHeight) / 2f).roundToInt().toFloat()

        // A right-hand (odd) page is bound on its left, so the bound edge stays at the edge of the screen it is bound on.
        val boundOnLeft = page % 2 == 1
        val face = if (boundOnLeft) Rect(0f, top, faceWidth, top + faceHeight)
                   else Rect(width - faceWidth, top, width, top + faceHeight)
        val thinnest = EDGES_THINNEST.toPx()
        val room = EDGES.toPx().roundToInt().toFloat()
        val depth = max(1f, (thinnest + (room - thinnest) * leavesBeside(page) / LEAVES).roundToInt().toFloat())
        val rim = RIM.toPx().roundToInt().toFloat()
        if (boundOnLeft) {
            BookLayout(
                face = face,
                edges = Rect(face.right, face.top, face.right + depth, face.bottom),
                cover = Rect(-rim, face.top - rim, face.right + room + rim, face.bottom + rim)
            )
        } else {
            BookLayout(
                face = face,
                edges = Rect(face.left - depth, face.top, face.left, face.bottom),
                cover = Rect(face.left - room - rim, face.top - rim, width + rim, face.bottom + rim)
            )
        }
    }
}

/**
 * How the book and the desk under it look, from the page colours of the reading theme; [density] sizes the grain. With
 * the reading lamp on ([lit]) the desk and the cover are those of the day whatever the page, for the lamp lights them
 * (the room around falls dark under the lamp's own light, see ReadingLamp).
 */
internal class BookLook(colors: MushafColors, paper: PaperLook, density: Float, lit: Boolean = false) {
    val page: Color = colors.bg
    val middle = BookMiddle(paper)

    /** Whether the desk and the cover are seen in dim light: at night, unless the reading lamp lights them. */
    val dark = colors.dark && !lit
    private val pageDark = colors.dark
    /** The wood's own colour, shown until its grain is ready. */
    private val deskPlain = DeskWood.tones(dark)[1]
    /** How far the desk falls into shade toward the corners and the bottom, away from the light over the book. */
    private val poolShade = if (dark) 0.45f else 0.32f

    /**
     * A binding of green leather, as many printed Mushafs have: a deep bottle green with a fine pebbled grain, a shade
     * darker toward the bottom (the light is above the reader) and along its edge, where the leather rounds over the
     * board out of the light. The walnut desk is warmer and lighter than the leather, so the book's edge shows against
     * it: on the first, dark desk a dark green was lost, and an even, lighter green looked painted. At night both are
     * seen in the same dim light, the leather still darker than the desk around it, so it shows as the book's edge and
     * not as a lit frame around the dark page.
     */
    val cover: Brush = ShaderBrush(
        ImageShader(LeatherGrain.image(if (dark) NIGHT_LEATHER else DAY_LEATHER, density), TileMode.Repeated, TileMode.Repeated)
    )
    val coverLight: Brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f))
    val coverEdge = Shadow(radius = 2.dp, color = Color.Black, alpha = if (dark) 0.5f else 0.6f)
    /**
     * The book's shadow on the desk, the light coming from above the reader. Tight, as under a book lying flat: on the
     * outer side it must fade within the bit of desk inside the page's own area, or it would show cut off there when the
     * book is seen smaller during a turn.
     */
    val castFar = Shadow(radius = 14.dp, color = Color.Black, spread = (-2).dp, offset = DpOffset(0.dp, 8.dp), alpha = if (dark) 0.75f else 0.50f)
    val castNear = Shadow(radius = 6.dp, color = Color.Black, offset = DpOffset(0.dp, 3.dp), alpha = if (dark) 0.50f else 0.35f)
    /** The pages' shadow on the cover, and the top page's on the edges beside it. */
    val castPages = Shadow(radius = 2.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = if (dark) 0.50f else 0.30f)

    private val shade = paper.paperShade
    // Paper seen edge on is a little darker than the page and lies in thin layers with darker lines between them. On a
    // dark page the layers are no lighter than the page: a lighter line there would read as a glow.
    private val edgeLayer = lerp(page, shade, if (pageDark) 0f else 0.09f)
    private val edgeLine = lerp(page, shade, if (pageDark) 0.5f else 0.38f)

    /**
     * The desk, filling the area: the walnut ([wood], once it is made; its plain colour until then, [shown] being how far
     * it has faded in), in a soft pool of light around the book.
     */
    fun DrawScope.drawDesk(wood: ImageBitmap?, shown: Float) {
        drawRect(deskPlain)
        if (wood != null && wood.width == size.width.roundToInt() && wood.height == size.height.roundToInt()) {
            drawImage(wood, alpha = shown)
        }
        drawRect(
            Brush.radialGradient(
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = poolShade),
                center = Offset(size.width / 2f, size.height * 0.42f),
                radius = size.height * 0.8f
            )
        )
    }

    /** The edges of the pages beneath, filling this area; the book's outer side is on the left when [outerOnLeft]. */
    fun DrawScope.drawEdges(outerOnLeft: Boolean) {
        val w = size.width
        val h = size.height
        drawRect(edgeLine)
        val layer = max(1f, 0.6.dp.toPx().roundToInt().toFloat())
        val line = max(1f, 0.3.dp.toPx().roundToInt().toFloat())
        // Counted from the page, so the layer just under it is always whole; not quite even, as real edges are not.
        var x = 0f
        var i = 0
        while (x < w) {
            val thick = min(layer + if (i % 3 == 1) 1f else 0f, w - x)
            drawRect(edgeLayer, topLeft = Offset(if (outerOnLeft) w - x - thick else x, 0f), size = Size(thick, h))
            x += thick + line
            i++
        }
        // In shade: deeper toward the outside, and just under the page lying on them.
        drawRect(
            Brush.horizontalGradient(
                0f to shade.copy(alpha = 0.40f),
                0.62f to shade.copy(alpha = 0.06f),
                1f to shade.copy(alpha = 0.34f),
                startX = if (outerOnLeft) 0f else w,
                endX = if (outerOnLeft) w else 0f
            )
        )
    }
}

/**
 * The desk of the book view, filling the area; drawn once behind all the pages, so it stays put while the book moves
 * and turns over it. The walnut is the size of the screen, so it is made off the main thread (a few tens of
 * milliseconds) and fades in; after that it is kept (see [DeskWood.image]).
 */
@Composable
internal fun BookDesk(look: BookLook, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val wood by produceState(DeskWood.cached(size.width, size.height, density, look.dark), size, look.dark, density) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@produceState
        value = DeskWood.cached(w, h, density, look.dark)
            ?: withContext(Dispatchers.Default) { DeskWood.image(w, h, density, look.dark) }
    }
    val shown by animateFloatAsState(if (wood != null) 1f else 0f, tween(DESK_FADE_MILLIS), label = "desk")
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .drawBehind { with(look) { drawDesk(wood, shown) } }
    )
}

private const val DESK_FADE_MILLIS = 180

/**
 * The walnut of the desk, made in pixels: its grain runs across the screen, as a table's does in front of the reader.
 * Across the grain lie soft light and dark bands and thin dark lines at uneven gaps; along it run fine fibres; and the
 * grain waves gently, a little differently here and there. The same every time (a fixed seed).
 */
internal object DeskWood {

    // By day a warm mid walnut, lighter than the leather of the cover. At night the same walnut in dim light (about 0.4
    // of the day's), so the desk is still there around the dark page without glaring; a first, nearly black night desk
    // left the dark page floating in nothing.
    private val DAY = listOf(Color(0xFF3E281A), Color(0xFF64442D), Color(0xFF845E40))
    private val NIGHT = listOf(Color(0xFF19100A), Color(0xFF281B12), Color(0xFF35261A))

    /** The dark lines of the grain, the wood itself, its light bands. */
    fun tones(dark: Boolean): List<Color> = if (dark) NIGHT else DAY

    // Sizes in dp.
    private const val GAP = 10f          // the mean gap between grain lines
    private const val BAND = 53f         // the light and dark bands
    private const val RIPPLE = 14f       // a finer play in them
    private const val FIBRE = 9f         // how long a fibre runs before it changes
    private const val WAVE1 = 2.7f
    private const val WAVE1_X = 261f
    private const val WAVE1_Y = 930f
    private const val WAVE2 = 1.9f
    private const val WAVE2_X = 684f
    private const val WAVE2_Y = 1727f
    private const val JITTER = 0.8f
    private const val JITTER_X = 23f
    private const val JITTER_Y = 19f
    private const val SEED = 7

    private class Made(val width: Int, val height: Int, val density: Float, val dark: Boolean, val image: ImageBitmap)

    @Volatile private var made: Made? = null

    /** The desk already made for this size and theme, if it was. */
    fun cached(width: Int, height: Int, density: Float, dark: Boolean): ImageBitmap? =
        made?.takeIf { it.width == width && it.height == height && it.density == density && it.dark == dark }?.image

    /** The desk for this size and theme, kept for the next time. Slow enough to belong off the main thread. */
    fun image(width: Int, height: Int, density: Float, dark: Boolean): ImageBitmap {
        cached(width, height, density, dark)?.let { return it }
        val bitmap = Bitmap.createBitmap(pixels(width, height, density, dark), width, height, Bitmap.Config.ARGB_8888)
        bitmap.prepareToDraw()
        val image = bitmap.asImageBitmap()
        made = Made(width, height, density, dark, image)
        return image
    }

    fun pixels(width: Int, height: Int, density: Float, dark: Boolean): IntArray {
        val out = IntArray(max(0, width) * max(0, height))
        if (width <= 0 || height <= 0) return out
        val tones = tones(dark)
        val random = Random(SEED)

        // Across the grain, a little longer than the screen is high, as the waves reach past its ends.
        val margin = ((WAVE1 + WAVE2 + JITTER) * density).toInt() + 2
        val n = height + 2 * margin + 2
        val bands = smoothNoise(n, BAND * density, random)
        val ripples = smoothNoise(n, RIPPLE * density, random)
        val profile = FloatArray(n) { 0.5f + 0.20f * bands[it] + 0.05f * ripples[it] }
        val gap = GAP * density
        var at = 0f
        while (at < n) {
            at += -ln(1f - random.nextFloat()) * gap
            val depth = 0.10f + 0.25f * random.nextFloat()
            val w = (0.38f + 0.95f * random.nextFloat()) * density
            val from = max(0, (at - 2.5f * w).toInt())
            val to = min(n - 1, (at + 2.5f * w).toInt() + 1)
            for (j in from..to) {
                val d = (j - at) / w
                profile[j] -= depth * exp(-d * d)
            }
        }

        // Fibres: random across the grain, changing smoothly along it.
        val cell = FIBRE * density
        val columns = (width / cell).toInt() + 3
        val fibres = Array(columns) {
            val raw = FloatArray(n) { 0.27f * gaussian(random) }
            FloatArray(n) { j -> (raw[max(0, j - 1)] + 2f * raw[j] + raw[min(n - 1, j + 1)]) / 4f }
        }

        // The waves: two slow ones, and a little jitter from place to place.
        val k1x = TWO_PI / (WAVE1_X * density)
        val k1y = TWO_PI / (WAVE1_Y * density)
        val k2x = TWO_PI / (WAVE2_X * density)
        val k2y = TWO_PI / (WAVE2_Y * density)
        val a1 = WAVE1 * density
        val a2 = WAVE2 * density
        val a3 = JITTER * density
        val s1x = FloatArray(width) { sin(k1x * it) }
        val c1x = FloatArray(width) { cos(k1x * it) }
        val s2x = FloatArray(width) { sin(k2x * it) }
        val c2x = FloatArray(width) { cos(k2x * it) }
        val jitterX = JITTER_X * density
        val jitterY = JITTER_Y * density
        val jx = (width / jitterX).toInt() + 2
        val jy = (height / jitterY).toInt() + 2
        val jitter = FloatArray(jx * jy) { random.nextFloat() * 2f - 1f }

        val lr = tones[0].red * 255f; val lg = tones[0].green * 255f; val lb = tones[0].blue * 255f
        val mr = tones[1].red * 255f; val mg = tones[1].green * 255f; val mb = tones[1].blue * 255f
        val hr = tones[2].red * 255f; val hg = tones[2].green * 255f; val hb = tones[2].blue * 255f

        for (y in 0 until height) {
            val s1y = sin(k1y * y + 1.3f)
            val c1y = cos(k1y * y + 1.3f)
            val s2y = sin(k2y * y + 0.4f)
            val c2y = cos(k2y * y + 0.4f)
            val gy = y / jitterY
            val j0 = gy.toInt()
            val fy = smooth(gy - j0)
            val row0 = j0 * jx
            val row1 = row0 + jx
            for (x in 0 until width) {
                val gx = x / jitterX
                val i0 = gx.toInt()
                val fx = smooth(gx - i0)
                val upper = jitter[row0 + i0] + (jitter[row0 + i0 + 1] - jitter[row0 + i0]) * fx
                val lower = jitter[row1 + i0] + (jitter[row1 + i0 + 1] - jitter[row1 + i0]) * fx
                // sin(a + b) and sin(a − b), from the sines and cosines of each part.
                val off = a1 * (s1x[x] * c1y + c1x[x] * s1y) + a2 * (s2x[x] * c2y - c2x[x] * s2y) +
                    a3 * (upper + (lower - upper) * fy)
                val across = y + margin + off
                val r = across.toInt()
                val p = profile[r] + (profile[r + 1] - profile[r]) * (across - r)
                val u = x / cell
                val c = u.toInt()
                val f0 = fibres[c][r]
                val fibre = f0 + (fibres[c + 1][r] - f0) * smooth(u - c)
                val g = (0.82f * p + 0.18f * (0.5f + fibre)).coerceIn(0f, 1f)
                val red: Float
                val green: Float
                val blue: Float
                if (g < 0.5f) {
                    val t = g * 2f
                    red = lr + (mr - lr) * t; green = lg + (mg - lg) * t; blue = lb + (mb - lb) * t
                } else {
                    val t = g * 2f - 1f
                    red = mr + (hr - mr) * t; green = mg + (hg - mg) * t; blue = mb + (hb - mb) * t
                }
                out[y * width + x] = argb(red, green, blue)
            }
        }
        return out
    }
}

/**
 * Pebbled leather in a seamless tile, lit from the top left: each little bump a shade lighter on the side facing the
 * light and darker on the other. Kept per colour and screen density.
 */
internal object LeatherGrain {

    /** How far the leather's light and dark go from its colour, either way. */
    const val RELIEF = 0.12f
    /** About how wide a pebble of the grain is, in dp. */
    private const val PEBBLE = 1.15f
    private const val SIDE_DP = 73f
    private const val SEED = 5

    private val images = HashMap<Pair<ULong, Float>, ImageBitmap>()

    fun image(base: Color, density: Float): ImageBitmap = images.getOrPut(base.value to density) {
        val side = max(16, (SIDE_DP * density).roundToInt())
        Bitmap.createBitmap(pixels(side, density, base), side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    fun pixels(side: Int, density: Float, base: Color): IntArray {
        val nodes = max(4, (side / (PEBBLE * density)).roundToInt())
        val random = Random(SEED)
        val grid = FloatArray(nodes * nodes) { gaussian(random) }
        val scale = nodes / side.toFloat()
        val height = FloatArray(side * side)
        for (y in 0 until side) {
            val v = y * scale
            val j = min(v.toInt(), nodes - 1)
            val j1 = (j + 1) % nodes
            val fv = smooth(v - j)
            for (x in 0 until side) {
                val u = x * scale
                val i = min(u.toInt(), nodes - 1)
                val i1 = (i + 1) % nodes
                val fu = smooth(u - i)
                val top = grid[j * nodes + i] + (grid[j * nodes + i1] - grid[j * nodes + i]) * fu
                val bottom = grid[j1 * nodes + i] + (grid[j1 * nodes + i1] - grid[j1 * nodes + i]) * fu
                height[y * side + x] = top + (bottom - top) * fv
            }
        }
        // Softened a little, wrapping round so the tile has no seams.
        fun at(a: FloatArray, x: Int, y: Int) = a[((y + side) % side) * side + (x + side) % side]
        val across = FloatArray(side * side) { val x = it % side; val y = it / side; (at(height, x - 1, y) + 2f * height[it] + at(height, x + 1, y)) / 4f }
        val soft = FloatArray(side * side) { val x = it % side; val y = it / side; (at(across, x, y - 1) + 2f * across[it] + at(across, x, y + 1)) / 4f }
        // Lit from the top left.
        val relief = FloatArray(side * side) { val x = it % side; val y = it / side; soft[it] - at(soft, x - 1, y - 1) }
        var sum = 0f
        for (r in relief) sum += r * r
        val spread = 2f * sqrt(sum / relief.size).coerceAtLeast(1e-6f)
        val red = base.red * 255f
        val green = base.green * 255f
        val blue = base.blue * 255f
        return IntArray(side * side) {
            val f = 1f + RELIEF * (relief[it] / spread).coerceIn(-1f, 1f)
            argb(red * f, green * f, blue * f)
        }
    }
}

/** A deep bottle green by day; at night the same leather in the desk's dim light. */
internal val DAY_LEATHER = Color(0xFF264637)
internal val NIGHT_LEATHER = Color(0xFF0F1C16)

private const val TWO_PI = (2.0 * PI).toFloat()

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

/** About normal, mean 0 and spread 1 (three uniforms added). */
private fun gaussian(random: Random): Float = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) * 2f

/** Values from −1 to 1 at every [step] pixels, eased between, [n] long. */
private fun smoothNoise(n: Int, step: Float, random: Random): FloatArray {
    val points = FloatArray((n / step).toInt() + 3) { random.nextFloat() * 2f - 1f }
    return FloatArray(n) {
        val t = it / step
        val k = t.toInt()
        points[k] + (points[k + 1] - points[k]) * smooth(t - k)
    }
}

private fun argb(red: Float, green: Float, blue: Float): Int =
    (0xFF shl 24) or
        ((red + 0.5f).toInt().coerceIn(0, 255) shl 16) or
        ((green + 0.5f).toInt().coerceIn(0, 255) shl 8) or
        (blue + 0.5f).toInt().coerceIn(0, 255)

/**
 * Page [page] of the one-page view as a page of the open book on the desk. [face] draws the page itself; the space for
 * the middle of the book along its bound edge is part of it (see [MIDDLE_WIDTH]).
 */
@Composable
internal fun BookPage(page: Int, look: BookLook, modifier: Modifier = Modifier, face: @Composable () -> Unit) {
    val boundOnLeft = page % 2 == 1
    // Only the corners on the outer side show; the bound side runs on out of sight.
    val coverShape = if (boundOnLeft) AbsoluteRoundedCornerShape(topRight = COVER_CORNER, bottomRight = COVER_CORNER)
                     else AbsoluteRoundedCornerShape(topLeft = COVER_CORNER, bottomLeft = COVER_CORNER)
    // The desk is not drawn here but once behind the pager (BookDesk): the book moves over it, and is seen smaller on it
    // while a leaf turns.
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(
                Modifier
                    .dropShadow(coverShape, look.castFar)
                    .dropShadow(coverShape, look.castNear)
                    .background(look.cover, coverShape)
                    .background(look.coverLight, coverShape)
                    .innerShadow(coverShape, look.coverEdge)
            )
            Box(
                Modifier
                    .dropShadow(RectangleShape, look.castPages)
                    .drawBehind { with(look) { drawEdges(outerOnLeft = !boundOnLeft) } }
            )
            Box(
                Modifier
                    .dropShadow(RectangleShape, look.castPages)
                    .background(look.page)
                    .drawWithContent {
                        drawContent()
                        with(look.middle) { drawMiddle(boundOnLeft) }
                    }
            ) { face() }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val book = BookFrame.layout(page, width.toFloat(), height.toFloat(), this)
        val parts = listOf(book.cover, book.edges, book.face)
        val placeables = measurables.mapIndexed { i, measurable ->
            val part = parts[i]
            measurable.measure(Constraints.fixed(part.width.roundToInt().coerceAtLeast(0), part.height.roundToInt().coerceAtLeast(0)))
        }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable -> placeable.place(parts[i].left.roundToInt(), parts[i].top.roundToInt()) }
        }
    }
}

private val COVER_CORNER = 3.dp
