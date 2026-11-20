package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Turning the leaves of a Mushaf in the one-page view.
 *
 * A Mushaf is bound on the right. Open, it shows an odd page on the right and the even page after it on the left; a
 * leaf carries an even page on its front and the odd page after it on its back. So going from a right-hand page to the
 * left-hand one beside it turns nothing: the eye moves across the open book (the pager's own slide does that). Only
 * going between a left-hand page and the page after it turns a leaf. Going on, the leaf lifts at its free edge on the
 * left, the next page's ayahs show on its back as it goes over, the next left-hand page appears beneath it, and the view
 * follows the leaf so that it lands in front of the reader. Going back from a right-hand page, that leaf is lifted at its
 * free edge on the right and laid back over to the left: the same turn seen in a mirror.
 *
 * The pager still does all the work (the drag, the fling, settling, a turn asked for by the recitation, TalkBack's page
 * actions); only the drawing changes. During a turn each page taking part draws its own share: the leaf's two sides, the
 * page beneath it, and, in the book view (where the desk and the edges of the book show around the paper), the page the
 * leaf comes down on.
 */

/** The geometry of a leaf turning, in screen coordinates, from the turn's progress and how high the page is held. */
internal object LeafTurn {

    /** How much a grip at the top or the bottom tilts the fold as the turn starts (1 would start it at the corner). */
    const val LEAN = 0.9f

    /**
     * The parts pages play in a turn, with the order they lie in: the leaf's back on top (it is folded over its front),
     * then its front, the page beneath the leaf, and at the bottom the page the leaf comes down on.
     */
    enum class Role(val z: Float) { FRONT(2f), BACK(3f), BENEATH(1f), LANDING(0f) }

    /**
     * The part a page plays in a turn under way, how far the turn has gone (0..1, counted from the page it began on),
     * where the pager has the page, and whether the turn goes back (begun on the right-hand page, so it is drawn mirrored).
     */
    data class Part(val role: Role, val progress: Float, val position: Float, val backward: Boolean = false)

    /**
     * The part pager page [page] (0 = page 1) plays when the pager is at [scroll] (current page plus offset), or null
     * when it is in no turn: at rest, or moving across the open book from a right-hand page to the left-hand one. [from]
     * is the pager page the turn began on (the pager's settled page): begun on the right-hand page, the turn goes back.
     * The page the leaf comes down on only takes part when [landing] asks for it.
     */
    fun partOf(page: Int, scroll: Float, from: Int = Int.MIN_VALUE, landing: Boolean = false): Part? {
        // The pager page of the leaf's front, if a leaf is turning.
        val leaf = floor(scroll).toInt()
        val t = scroll - leaf
        if (leaf < 0 || (leaf + 1) % 2 != 0 || t <= 0f) return null
        val backward = from > leaf
        val role = when (page - leaf) {
            0 -> if (backward) Role.BACK else Role.FRONT
            1 -> if (backward) Role.FRONT else Role.BACK
            2 -> if (backward) Role.LANDING else Role.BENEATH
            -1 -> if (backward) Role.BENEATH else Role.LANDING
            else -> return null
        }
        if (role == Role.LANDING && !landing) return null
        return Part(role, if (backward) 1f - t else t, page - scroll, backward)
    }

    /**
     * In the two-page view (pages half the pager wide, at rest only on a spread: an even pager page, the right-hand page,
     * with the left-hand one beside it), the pager's [scroll] as the one-page view's, for [partOf]: the two pages from one
     * spread to the next are one leaf turning, from the left-hand page of the first to the right-hand page of the next.
     */
    fun spreadScroll(scroll: Float): Float {
        val spread = floor(scroll / 2f)
        return 2f * spread + 1f + (scroll - 2f * spread) / 2f
    }

    /** Whether going from pager page [page] to the next turns a leaf (from an even page) rather than crossing the open book. */
    fun turnsLeafAfter(page: Int): Boolean = (page + 1) % 2 == 0

    /**
     * How far the fold has come from the leaf's free edge, 0..1 of the leaf's width, at [progress]. Half as fast as the
     * turn at first, so the edge stays under a finger that took it by the edge; halfway through the turn the leaf stands
     * up (the fold is in the middle), which is also where the pager decides between finishing and falling back.
     */
    fun fold(progress: Float): Float {
        val f = progress.coerceIn(0f, 1f)
        return f - f * (f - 0.5f) * (f - 1f)
    }

    /**
     * How large the book is drawn at [progress] of a turn, [depth] being how much smaller it is halfway through: as a
     * leaf is turned the eye takes in the whole book, then comes back to the page.
     */
    fun zoom(progress: Float, depth: Float): Float = 1f - depth * sin(PI * progress.coerceIn(0f, 1f)).toFloat()

    /** The tilt of the fold from the grip: low lifts the bottom corner first, high the top one; gone by the middle. */
    fun lean(progress: Float, grip: Float): Float =
        (grip.coerceIn(0f, 1f) - 0.5f) * 2f * LEAN * max(0f, 1f - 2f * progress.coerceIn(0f, 1f))

    /**
     * How far the view has moved with the leaf. Still while the leaf rises; once it is past standing the view follows
     * it, so it lands in front of the reader (with the paper filling the screen, nothing of the page it falls on, the
     * right-hand page before it, comes into view).
     */
    fun leafLeft(progress: Float, width: Float): Float {
        val f = progress.coerceIn(0f, 1f)
        return if (f <= 0.5f) 0f else width - 2f * fold(f) * width
    }

    /**
     * How far the view has moved in the book view, where the book is seen smaller during a turn: smoothly from the page
     * the turn began on to the page it ends on, centred on the binding halfway, so the leaf is seen going over from one
     * page to the other.
     */
    fun bookCamera(progress: Float, width: Float): Float {
        val f = progress.coerceIn(0f, 1f)
        return -width * f * f * (3f - 2f * f)
    }

    /**
     * A turn at [progress] in a page area [width] wide, the leaf lying in [sheet] before it turns (a left-hand page,
     * bound on the right edge of the area): the fold line where it crosses the leaf's top and bottom. With
     * [smoothCamera] the view moves as in the book view ([bookCamera]); otherwise it follows the leaf ([leafLeft]).
     */
    fun scene(progress: Float, width: Float, sheet: Rect, grip: Float, smoothCamera: Boolean = false): Scene {
        val left = if (smoothCamera) bookCamera(progress, width) else leafLeft(progress, width)
        val reach = fold(progress) * sheet.width
        val lean = lean(progress, grip)
        val edge = left + sheet.left
        return Scene(left, Offset(edge + reach * (1f - lean), sheet.top), Offset(edge + reach * (1f + lean), sheet.bottom), width, sheet)
    }

    /** A turn of a leaf that fills the page area. */
    fun scene(progress: Float, width: Float, height: Float, grip: Float): Scene =
        scene(progress, width, Rect(0f, 0f, width, height), grip)

    /** The mirror image across the line through [a] and [b]: where a point of the folded part lands. */
    fun reflection(a: Offset, b: Offset): Matrix {
        val d = b - a
        val length = d.getDistance()
        val m = Matrix()
        if (length == 0f) return m
        val ux = d.x / length
        val uy = d.y / length
        val r00 = 2f * ux * ux - 1f
        val r01 = 2f * ux * uy
        val r11 = 2f * uy * uy - 1f
        m.values[Matrix.ScaleX] = r00
        m.values[Matrix.SkewX] = r01
        m.values[Matrix.SkewY] = r01
        m.values[Matrix.ScaleY] = r11
        m.values[Matrix.TranslateX] = a.x - (r00 * a.x + r01 * a.y)
        m.values[Matrix.TranslateY] = a.y - (r01 * a.x + r11 * a.y)
        return m
    }

    /** A turn at one moment: how far the view has moved, the fold line, the page area's width and the leaf's place at rest. */
    class Scene(val leafLeft: Float, val foldTop: Offset, val foldBottom: Offset, val width: Float, sheet: Rect) {
        val mirror: Matrix = reflection(foldTop, foldBottom)

        /** Where the leaf lies now, moved with the view, before it is folded. */
        val leaf: Rect = sheet.translate(leafLeft, 0f)

        /**
         * Where the back of the leaf draws its page: a point of that page lies behind the front point mirrored across
         * the page (the back page's bound edge is behind the front page's), and the fold then lays it over.
         */
        val back: Matrix = Matrix().also { m ->
            val v = mirror.values
            val r00 = v[Matrix.ScaleX]
            val r01 = v[Matrix.SkewX]
            val r11 = v[Matrix.ScaleY]
            val c = leafLeft + width
            m.values[Matrix.ScaleX] = -r00
            m.values[Matrix.SkewX] = r01
            m.values[Matrix.TranslateX] = r00 * c + v[Matrix.TranslateX]
            m.values[Matrix.SkewY] = -r01
            m.values[Matrix.ScaleY] = r11
            m.values[Matrix.TranslateY] = r01 * c + v[Matrix.TranslateY]
        }
    }
}

/** The colours a turn is drawn with, from the page colours of the reading theme ([page] is the paper's own colour). */
internal class PaperLook(val dark: Boolean, val page: Color = if (dark) Color(0xFF282521) else Color(0xFFE8E0D2)) {
    val shadow: Float = if (dark) 0.60f else 0.30f
    val edgeShadow: Float = if (dark) 0.45f else 0.18f
    val crease: Float = if (dark) 0.45f else 0.22f
    /** How much darker a leaf looks standing on edge to the light from above than lying flat (book view). */
    val tilt: Float = if (dark) 0.55f else 0.40f
    /** The tone paper takes in shade: warm on a light page, black on a dark one. */
    val paperShade: Color = if (dark) Color.Black else Color(0xFF2E2216)

    // The middle of the book (see BookMiddle), in the same warm shade.
    val middleShade: Color = paperShade
    /** How dark it is deep in the crease. */
    val middleCrease: Float = if (dark) 0.85f else 0.50f
}

/**
 * Draws pager page [page] as part of a leaf when a turn is under way. [state] is the pager's, [grip] the height of the
 * finger (0 top..1 bottom, read while drawing, so a turn follows it without recomposing). With [book], the page is drawn
 * as a page of a book on a desk ([BookPage]): only the paper turns, the book stays where it lies, and while the leaf
 * goes over the view draws back until the whole open book is in sight, then comes in on the new page (the desk behind
 * the pager stays as it is). With [spread] (the two-page view, see BookSpread) that whole open book is what is always in
 * sight, two pages side by side, and the leaf turns over it with the view staying where it is.
 */
internal fun Modifier.leafTurn(
    state: PagerState,
    page: Int,
    grip: () -> Float,
    look: PaperLook,
    book: Boolean = false,
    spread: Boolean = false
): Modifier = this
    // The pages of a turn lie in the order of their parts, which depends on the way the turn goes.
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0, zIndex = state.partOf(page, book, spread)?.role?.z ?: 0f)
        }
    }
    .graphicsLayer {
        // The pages of a turn are drawn in screen coordinates; any other page stays where the pager put it (out of
        // sight at rest), so it cannot take a tap meant for the page in view. With two pages side by side, from the
        // screen's left edge, where the left-hand page lies (the pager has the page it is on a page further right).
        val part = state.partOf(page, book, spread)
        translationX = when {
            part == null -> 0f
            spread -> (page - state.scroll - 1f) * size.width
            else -> part.position * size.width
        }
    }
    .drawWithCache {
        // In the book view the leaf is drawn from a recording of its page, in thin strips each seen in perspective.
        val leaf = obtainGraphicsLayer()
        onDrawWithContent {
            val part = state.partOf(page, book, spread)
            if (part == null) {
                drawContent()
            } else {
                val sheet = if (book) BookFrame.sheet(size.width, size.height, this) else Rect(Offset.Zero, size)
                if (book && (part.role == LeafTurn.Role.FRONT || part.role == LeafTurn.Role.BACK)) {
                    leaf.record {
                        // Going back, the turn is drawn mirrored (see drawPart), so the page is mirrored once more here.
                        if (part.backward) withTransform({ scale(-1f, 1f, pivot = center) }) { this@onDrawWithContent.drawContent() }
                        else this@onDrawWithContent.drawContent()
                    }
                }
                drawPart(part, grip(), look, sheet, book, leaf, spread)
            }
        }
    }

/**
 * How much smaller the book is seen halfway through a turn in the book view: a little under half its size, so the whole
 * open book is in view, both pages and a little desk on each side, and the leaf can be seen going over.
 */
internal const val BOOK_TURN_ZOOM = 0.52f

private val PagerState.scroll: Float get() = currentPage + currentPageOffsetFraction

private fun PagerState.partOf(page: Int, book: Boolean, spread: Boolean): LeafTurn.Part? =
    LeafTurn.partOf(page, if (spread) LeafTurn.spreadScroll(scroll) else scroll, settledPage, landing = book)

private fun ContentDrawScope.drawPart(
    part: LeafTurn.Part, grip: Float, look: PaperLook, sheet: Rect, book: Boolean, leaf: GraphicsLayer, spread: Boolean
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    // The screen is the frame: one page fills it, or two side by side, each this page's width, from its left edge.
    val frame = if (spread) 2f * w else w
    val middle = Offset(frame / 2f, h / 2f)
    // With both pages in sight there is nothing to draw back for.
    val zoom = if (spread) 1f else LeafTurn.zoom(part.progress, if (book) BOOK_TURN_ZOOM else 0f)
    // Inside the frame the book may be seen smaller, with more of it in view (past the binding, the page the leaf comes
    // down on) and the desk around.
    clipRect(0f, 0f, frame, h) {
        withTransform({ scale(zoom, zoom, pivot = middle) }) {
            // Going back, the same turn is seen in a mirror about the binding: the right-hand page is lifted at its free
            // edge on the right and laid over to the left. Each page is mirrored once more about its own middle as it is
            // drawn, so its text reads the right way (with one page the two middles are the same).
            val own = Offset(w / 2f, h / 2f)
            val mirrored: () -> Unit = { withTransform({ scale(-1f, 1f, pivot = own) }) { this@drawPart.drawContent() } }
            val plain: () -> Unit = { this@drawPart.drawContent() }
            val page = if (part.backward) mirrored else plain
            withTransform({ if (part.backward) scale(-1f, 1f, pivot = middle) }) {
                if (book) drawLeaf3D(part, grip, look, sheet, leaf, page, fixed = spread, frame = frame)
                else drawLeaf(part, grip, look, sheet, page)
            }
        }
    }
}

/*
 * The leaf in the book view, turned in three dimensions. It is hinged at the binding and bends as paper does: the edge
 * held by the finger goes first and the part near the binding comes up last, so halfway through it stands curved, its
 * edge already past upright. Seen from above with the eye a little way off, the raised part comes nearer and looks larger;
 * the side facing up shows (the page turned, then the page on its back), darker the more it stands on edge to the light;
 * and it shades the pages under it. It is drawn in thin upright strips, each a slice of the page set in perspective.
 */

/** The shape of a leaf being turned in the book view, in pure numbers so it can be checked without a screen. */
internal object LeafCurve {

    /** How many upright strips the leaf is drawn in. */
    const val STRIPS = 32
    /** How much further the free edge is turned than the bound edge halfway through, in radians: the bend of the paper. */
    const val SPREAD = 1.1f
    /** How far, in radians, the corner held by the finger goes ahead of the other corner at most. */
    const val TWIST = 0.35f
    /** How high the eye is above the book, in page widths: lower makes the raised leaf come nearer. */
    const val EYE = 3.5f

    private val PI_F = PI.toFloat()

    /**
     * How far the leaf is turned at [u] (0 its bound edge, 1 its free edge) at [progress] of the turn, with [twist] added:
     * 0 lying on the left-hand page, π lying on the right-hand page. Never below either page.
     */
    fun angle(u: Float, progress: Float, twist: Float = 0f): Float {
        val t = progress.coerceIn(0f, 1f)
        val mean = PI_F * t * t * (3f - 2f * t) + twist
        val spread = SPREAD * sin(PI_F * t)
        return (mean + spread * (u - 0.5f)).coerceIn(0f, PI_F)
    }

    /** How much further the bottom corner is turned than the top one, from how low the finger holds the page ([grip]). */
    fun twist(progress: Float, grip: Float): Float =
        TWIST * (2f * grip.coerceIn(0f, 1f) - 1f) * sin(PI_F * progress.coerceIn(0f, 1f))

    /**
     * Points along the leaf from its bound edge, at [spine], to its free edge, [length] away along the paper: x on the
     * book and height above it, [STRIPS] + 1 of them.
     */
    fun section(progress: Float, spine: Float, length: Float, twist: Float = 0f): List<Offset> {
        val step = length / STRIPS
        var x = spine
        var z = 0f
        val points = ArrayList<Offset>(STRIPS + 1)
        points.add(Offset(x, z))
        for (i in 0 until STRIPS) {
            val theta = angle((i + 0.5f) / STRIPS, progress, twist)
            x -= step * cos(theta)
            z += step * sin(theta)
            points.add(Offset(x, z))
        }
        return points
    }

    /** How much darker a part of the leaf turned [theta] looks: none lying flat, [most] standing on edge. */
    fun shade(theta: Float, most: Float): Float = most * (1f - abs(cos(theta)))

    /** Where a point [height] above the book at ([x], [y]) is seen, by an eye [eye] above ([cx], [cy]). */
    fun project(x: Float, y: Float, height: Float, cx: Float, cy: Float, eye: Float): Offset {
        val k = eye / max(eye - height, 1f)
        return Offset(cx + (x - cx) * k, cy + (y - cy) * k)
    }

    /**
     * Whether a strip seen at corners [a], [b], [c], [d] (in order round it) shows its face: it is a proper, convex shape
     * at least [least] pixels wide at top and bottom. On edge, or twisted through edge-on so its top and bottom face
     * different ways, it does not.
     */
    fun seenFlat(a: Offset, b: Offset, c: Offset, d: Offset, least: Float = 0.75f): Boolean {
        if (abs(b.x - a.x) < least || abs(c.x - d.x) < least) return false
        fun turn(o: Offset, p: Offset, q: Offset) = (p.x - o.x) * (q.y - p.y) - (p.y - o.y) * (q.x - p.x)
        val t1 = turn(a, b, c)
        val t2 = turn(b, c, d)
        val t3 = turn(c, d, a)
        val t4 = turn(d, a, b)
        return (t1 > 0f && t2 > 0f && t3 > 0f && t4 > 0f) || (t1 < 0f && t2 < 0f && t3 < 0f && t4 < 0f)
    }
}

/** How far, in pixels, each strip of the leaf reaches over the next, so no seam shows between them. */
private const val STRIP_OVERLAP = 1.5f

/**
 * A page's share of a turn in the book view, the leaf lying in [sheet] (a left-hand page) before it turns. [leaf] holds
 * the recording of this page, for the leaf's two sides; [page] draws this page as it lies, for the pages under the leaf.
 */
private fun DrawScope.drawLeaf3D(
    part: LeafTurn.Part, grip: Float, look: PaperLook, sheet: Rect, leaf: GraphicsLayer, page: () -> Unit, fixed: Boolean, frame: Float
) {
    val w = size.width
    val t = part.progress
    // The view moves smoothly from the page the turn began on to the next; with both pages in sight ([fixed]) it stays.
    val left = if (fixed) 0f else LeafTurn.bookCamera(t, w)
    val lift = sin(PI * t.coerceIn(0f, 1f)).toFloat()
    when (part.role) {
        LeafTurn.Role.BENEATH -> {
            translate(left = left) { page() }
            // The leaf shades the page it is lifting off, most just past where it still lies on it.
            val section = LeafCurve.section(t, sheet.right, sheet.width)
            val rising = section.indexOfFirst { it.y > 0.5f }.takeIf { it >= 0 } ?: return
            val foot = section[max(rising - 1, 0)].x + left
            val reach = (30.dp.toPx() + 150.dp.toPx() * lift)
            clipRect(sheet.left + left, sheet.top, foot, sheet.bottom) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to look.paperShade.copy(alpha = look.shadow * lift),
                        1f to Color.Transparent,
                        startX = foot,
                        endX = foot - reach
                    )
                )
            }
        }

        LeafTurn.Role.LANDING -> {
            translate(left = left + w) { page() }
            // Once the leaf is over this page it shades it, from the binding out.
            val section = LeafCurve.section(t, sheet.right, sheet.width)
            val over = ((section.maxOf { it.x } - sheet.right) / sheet.width).coerceIn(0f, 1f)
            if (over <= 0f) return
            val spine = sheet.right + left
            val reach = (30.dp.toPx() + 150.dp.toPx() * lift)
            clipRect(spine, sheet.top, spine + sheet.width, sheet.bottom) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to look.paperShade.copy(alpha = look.shadow * lift * min(1f, over * 2f)),
                        1f to Color.Transparent,
                        startX = spine,
                        endX = spine + reach
                    )
                )
            }
        }

        LeafTurn.Role.FRONT, LeafTurn.Role.BACK ->
            drawLeafStrips(t, grip, look, sheet, leaf, front = part.role == LeafTurn.Role.FRONT, left, frame)
    }
}

/**
 * The strips of the leaf showing one side: the page turned ([front]: its side facing up while the leaf is less than
 * upright) or the page on its back. Drawn from the bound edge out, which is from lower to higher, so a part of the leaf
 * bending over another is drawn over it; the front's strips are always nearer the binding than the back's.
 */
private fun DrawScope.drawLeafStrips(
    t: Float, grip: Float, look: PaperLook, sheet: Rect, leaf: GraphicsLayer, front: Boolean, left: Float, frame: Float
) {
    val w = size.width
    val h = size.height
    val twist = LeafCurve.twist(t, grip)
    val spine = sheet.right
    val length = sheet.width
    val top = LeafCurve.section(t, spine, length, -twist / 2f)
    val bottom = LeafCurve.section(t, spine, length, twist / 2f)
    val eye = LeafCurve.EYE * w
    // The eye is over the middle of the screen.
    val cx = frame / 2f
    val cy = h / 2f
    val n = LeafCurve.STRIPS
    val toScreen = android.graphics.Matrix()
    val matrix = Matrix()
    val src = FloatArray(8)
    val dst = FloatArray(8)
    for (i in 0 until n) {
        val u = (i + 0.5f) / n
        val theta = (LeafCurve.angle(u, t, -twist / 2f) + LeafCurve.angle(u, t, twist / 2f)) / 2f
        if ((cos(theta) > 0f) != front) continue
        val a = LeafCurve.project(top[i].x + left, sheet.top, top[i].y, cx, cy, eye)
        val b = LeafCurve.project(top[i + 1].x + left, sheet.top, top[i + 1].y, cx, cy, eye)
        val c = LeafCurve.project(bottom[i + 1].x + left, sheet.bottom, bottom[i + 1].y, cx, cy, eye)
        val d = LeafCurve.project(bottom[i].x + left, sheet.bottom, bottom[i].y, cx, cy, eye)
        // The strip on the page itself: the page turned has its bound edge on its right, the page on its back on its left.
        val s0 = length * i / n
        val s1 = length * (i + 1) / n
        val near = if (front) spine - s0 else s0
        val far = if (front) spine - s1 else s1
        src[0] = near; src[1] = sheet.top; src[2] = far; src[3] = sheet.top
        src[4] = far; src[5] = sheet.bottom; src[6] = near; src[7] = sheet.bottom
        dst[0] = a.x; dst[1] = a.y; dst[2] = b.x; dst[3] = b.y
        dst[4] = c.x; dst[5] = c.y; dst[6] = d.x; dst[7] = d.y
        val darkness = LeafCurve.shade(theta, look.tilt)
        // A strip seen on edge (or twisted through it, top and bottom facing different ways) cannot carry the page: set in
        // perspective it would be stretched out of all bounds. It is too thin to read anyway, so it is plain paper.
        if (!LeafCurve.seenFlat(a, b, c, d) || !toScreen.setPolyToPoly(src, 0, dst, 0, 4)) {
            val edge = Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            drawPath(edge, color = lerp(look.page, look.paperShade, darkness))
            continue
        }
        matrix.setFrom(toScreen)
        // Each strip reaches a little over the next one out, which covers it; the last stops at the page's edge.
        val reach = if (i < n - 1) STRIP_OVERLAP else 0f
        val clipLeft = if (front) far - reach else near
        val clipRight = if (front) near else far + reach
        withTransform({ transform(matrix) }) {
            clipRect(clipLeft, sheet.top, clipRight, sheet.bottom) {
                drawLayer(leaf)
                if (darkness > 0.002f) drawRect(look.paperShade.copy(alpha = darkness))
            }
        }
    }
}

/**
 * A page's share of a turn going on with the page filling the screen, the leaf lying in [sheet] before it turns, folded
 * flat over itself; [page] draws the page.
 */
private fun DrawScope.drawLeaf(part: LeafTurn.Part, grip: Float, look: PaperLook, sheet: Rect, page: () -> Unit) {
    val w = size.width
    val h = size.height
    val scene = LeafTurn.scene(part.progress, w, sheet, grip)
    val leaf = scene.leaf
    val a = scene.foldTop
    val b = scene.foldBottom
    val along = (b - a) / (b - a).getDistance()
    // Across the fold, toward the binding.
    val across = Offset(along.y, -along.x)
    val far = (w + h) * 4f
    val farA = a - along * far
    val farB = b + along * far
    val middle = (a + b) / 2f
    // How far the leaf is off the page beneath: nothing as the turn starts and ends, most when it stands up.
    val lift = sin(PI * part.progress.coerceIn(0f, 1f)).toFloat()

    when (part.role) {
        // Where the leaf comes down, past the binding: it shows as the view follows the leaf, with the desk around it.
        LeafTurn.Role.LANDING -> translate(left = scene.leafLeft + w) { page() }

        // Everything the flat part of the leaf does not cover: the paper the leaf has left, and in the book view the
        // edges, the cover and the desk. It reaches a little under the leaf, so the two soft edges at the fold leave no
        // pale seam.
        LeafTurn.Role.BENEATH -> clipPath(flatPart(leaf, farA, farB, across, far, SEAM), clipOp = ClipOp.Difference) {
            translate(left = scene.leafLeft) { page() }
            // The leaf shades the paper along the fold, as far under the leaf as the page beneath reaches.
            clipRect(leaf.left, leaf.top, leaf.right, leaf.bottom) {
                clipPath(halfPlane(farA + across * SEAM, farB + across * SEAM, across * -far)) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(look.paperShade.copy(alpha = look.shadow * lift), Color.Transparent),
                            start = middle,
                            end = middle - across * (24.dp.toPx() + 40.dp.toPx() * lift)
                        )
                    )
                }
            }
        }

        LeafTurn.Role.FRONT -> clipPath(flatPart(leaf, farA, farB, across, far, 0f)) {
            translate(left = scene.leafLeft) { page() }
            // The folded part's shadow on the part still flat, just past its edge (the back covers the rest).
            val edgeTop = scene.mirror.map(Offset(leaf.left, leaf.top))
            val edgeBottom = scene.mirror.map(Offset(leaf.left, leaf.bottom))
            val outward = scene.mirror.map(Offset(leaf.left - 1f, leaf.top)) - edgeTop
            val edgeMiddle = (edgeTop + edgeBottom) / 2f
            drawRect(
                Brush.linearGradient(
                    listOf(look.paperShade.copy(alpha = look.edgeShadow * lift), Color.Transparent),
                    start = edgeMiddle,
                    end = edgeMiddle + outward * (6.dp.toPx() + 14.dp.toPx() * lift)
                )
            )
        }

        LeafTurn.Role.BACK -> {
            // Where the folded part lies: the leaf's outline mirrored across the fold, on the binding's side of it.
            val corners = listOf(
                Offset(leaf.left, leaf.top), Offset(leaf.right, leaf.top),
                Offset(leaf.right, leaf.bottom), Offset(leaf.left, leaf.bottom)
            ).map { scene.mirror.map(it) }
            val flap = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                for (i in 1..3) lineTo(corners[i].x, corners[i].y)
                close()
            }
            // It reaches a hair past the fold, so its soft edge lies over the shaded page beneath and not over the
            // flat front, which would show through as a pale line.
            clipPath(halfPlane(farA - across * SEAM, farB - across * SEAM, across * far)) {
                clipPath(flap) {
                    withTransform({ transform(scene.back) }) { page() }
                    // Shading across it, as matte paper takes it: darkest where it bends at the fold, fading
                    // smoothly, and a little dark again toward its edge. No highlight: a highlight reads as gloss.
                    val folded = max(middle.x - leaf.left, 1f)
                    drawRect(
                        Brush.linearGradient(
                            0f to look.paperShade.copy(alpha = look.crease),
                            0.08f to look.paperShade.copy(alpha = look.crease * 0.55f),
                            0.22f to look.paperShade.copy(alpha = look.crease * 0.18f),
                            0.40f to Color.Transparent,
                            1f to look.paperShade.copy(alpha = look.crease * 0.35f),
                            start = middle,
                            end = middle + across * folded
                        )
                    )
                }
            }
        }
    }
}

/** How far, in pixels, the page beneath reaches under the leaf at the fold. */
private const val SEAM = 2f

/** The half of the plane on one side of the line through [a] and [b], reaching out by [out]. */
private fun halfPlane(a: Offset, b: Offset, out: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(b.x + out.x, b.y + out.y)
    lineTo(a.x + out.x, a.y + out.y)
    close()
}

/** The part of [leaf] still lying flat, on the binding's side of the fold, its fold edge moved [inset] toward the binding. */
private fun flatPart(leaf: Rect, farA: Offset, farB: Offset, across: Offset, far: Float, inset: Float): Path {
    val side = halfPlane(farA + across * inset, farB + across * inset, across * far)
    val whole = Path().apply { addRect(leaf) }
    return Path().apply { op(whole, side, PathOperation.Intersect) }
}

/** The width the middle of the book takes beside a page when it is shown; the page's text keeps clear of it. */
internal val MIDDLE_WIDTH = 28.dp

/**
 * The middle of the open book at a page's bound edge, so the one-page view always shows which side of the book the page
 * is on: the paper curving down into the binding, darkest at the edge of the screen, where the crease is. Moving across
 * to the facing page, the two pages meet at their bound edges and their curves make one valley.
 *
 * Nothing is drawn past the crease: a strip of the facing page rising out of it was tried, and at a few pixels wide it
 * read as a glowing line, at rest and while moving.
 */
internal class BookMiddle(private val look: PaperLook) {

    fun DrawScope.drawMiddle(boundOnLeft: Boolean) {
        val reach = MIDDLE_CURVE.toPx()
        // Matte paper bending away from the light darkens slowly at first and quickly near the binding, with no
        // highlight anywhere (a highlight is what made it read as metal). The shade is sampled along that curve so it
        // runs smoothly into the crease.
        val stops = Array(CURVE_SAMPLES + 1) { i ->
            val u = i / CURVE_SAMPLES.toFloat()
            u to look.middleShade.copy(alpha = look.middleCrease * (1f - u).pow(CURVE_POWER))
        }
        val edge = if (boundOnLeft) 0f else size.width
        val inner = if (boundOnLeft) reach else size.width - reach
        drawRect(
            brush = Brush.horizontalGradient(*stops, startX = edge, endX = inner),
            topLeft = Offset(minOf(edge, inner), 0f),
            size = Size(reach, size.height)
        )
    }
}

/** How far from the crease the page starts to bend; wider than the space kept clear, as the text near it darkens a little. */
private val MIDDLE_CURVE = 56.dp
/** How steeply the bend darkens toward the crease: slowly at first, then quickly. */
private const val CURVE_POWER = 2.6f
private const val CURVE_SAMPLES = 14
