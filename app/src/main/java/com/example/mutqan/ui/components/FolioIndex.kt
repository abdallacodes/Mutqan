package com.example.mutqan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.ui.theme.FolioTheme

// The index look of the Practice and Progress tabs: a heading with a star and a leader line, rows that read like the
// entries of a book's contents (a title in Amiri, a line under it, and a small page drawing instead of a stock icon),
// and the page drawings themselves. Cards stay for the one hero of a screen.

/**
 * The head of a part of a screen: its title in display type, a small gold eight-pointed star, and a thin leader line
 * running to the end of the row, where an optional [actionLabel] link can sit.
 */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PaperSpace.Gutter)
            .padding(top = if (first) 8.dp else 32.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Box(Modifier.size(11.dp).clip(EightPointStarShape).background(MaterialTheme.colorScheme.tertiary))
        if (actionLabel != null && onAction != null) {
            // The link is measured before the weighted line, so it keeps its room and the line takes what is left.
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onAction),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

/**
 * One entry of an index: an optional page drawing ([glyph]), the title in Amiri, a quiet line under it, and at the end
 * a [value], a [pill] that says what pressing does (the whole row is the button), a [trailingIcon], or a chevron. [leading]
 * replaces the page drawing with something else; [emphasis] sets the title and icon in the action colour. A faint line closes the row.
 */
@Composable
fun IndexRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    glyph: PageGlyphKind? = null,
    value: String? = null,
    pill: String? = null,
    note: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    emphasis: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (glyph != null) PageGlyph(glyph) else leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
                    color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pill != null) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, FolioTheme.shapes.pill)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pill,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (trailingIcon != null) {
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Hairline()
    }
}

// ── The page drawings ───────────────────────────────────────────────────────

/** What a page drawing shows: which lines of a Mushaf page the exercise is about. */
enum class PageGlyphKind {
    /** Nothing marked. */
    PLAIN,

    /** Several lines marked: the weak pages. */
    WEAK,

    /** One line, and a dot above it: where is this verse. */
    WHERE,

    /** A line, and the start of the line after it. */
    NEXT,

    /** A line whose end is missing. */
    ENDING,

    /** A line whose start is given and whose rest is missing. */
    FINISH,

    /** Lines in a mixed-up order. */
    ORDER,

    /** The first line of the page. */
    PAGE_START,

    /** The last line of the page. */
    PAGE_END,

    /** The first and the last lines of the page. */
    BOUNDARIES,

    /** A given line, and the first and last lines of its page. */
    EDGES,

    /** Two lines that look alike. */
    SIMILAR,

    /** A grid: the quarters of a juz. */
    JUZ,

    /** A page with a ribbon: saved verses. */
    VAULT,

    /** Scattered marks: any verse. */
    RANDOM
}

private enum class Ink { MUTED, GREEN, GOLD }

/** One mark on a line: from [from] to [to] as a share of the line's width, counted from where reading starts. */
private class Mark(val line: Int, val from: Float, val to: Float, val ink: Ink)

private const val GLYPH_LINES = 9

private fun marksOf(kind: PageGlyphKind): List<Mark> = when (kind) {
    PageGlyphKind.PLAIN, PageGlyphKind.JUZ -> emptyList()
    PageGlyphKind.WEAK -> listOf(Mark(2, 0f, 1f, Ink.GOLD), Mark(4, 0f, 0.7f, Ink.GOLD), Mark(6, 0f, 0.9f, Ink.GOLD))
    PageGlyphKind.WHERE -> listOf(Mark(4, 0f, 0.76f, Ink.MUTED), Mark(4, 0.76f, 1f, Ink.GREEN))
    PageGlyphKind.NEXT -> listOf(Mark(2, 0f, 1f, Ink.GREEN), Mark(3, 0f, 0.7f, Ink.GOLD))
    PageGlyphKind.ENDING -> listOf(Mark(4, 0f, 0.62f, Ink.MUTED), Mark(4, 0.62f, 1f, Ink.GOLD))
    PageGlyphKind.FINISH -> listOf(Mark(4, 0f, 0.4f, Ink.GREEN), Mark(4, 0.4f, 1f, Ink.GOLD))
    PageGlyphKind.ORDER -> listOf(Mark(2, 0f, 0.55f, Ink.GREEN), Mark(3, 0f, 1f, Ink.GOLD), Mark(4, 0f, 0.75f, Ink.GREEN))
    PageGlyphKind.PAGE_START -> listOf(Mark(0, 0f, 1f, Ink.GREEN))
    PageGlyphKind.PAGE_END -> listOf(Mark(8, 0f, 0.6f, Ink.GREEN))
    PageGlyphKind.BOUNDARIES -> listOf(Mark(0, 0f, 1f, Ink.GREEN), Mark(8, 0f, 0.6f, Ink.GREEN))
    PageGlyphKind.EDGES -> listOf(Mark(0, 0f, 0.55f, Ink.GREEN), Mark(4, 0f, 1f, Ink.GOLD), Mark(8, 0f, 0.6f, Ink.GREEN))
    PageGlyphKind.SIMILAR -> listOf(
        Mark(3, 0f, 0.8f, Ink.GOLD), Mark(3, 0.8f, 1f, Ink.GREEN),
        Mark(5, 0f, 0.8f, Ink.GOLD), Mark(5, 0.8f, 1f, Ink.MUTED)
    )
    PageGlyphKind.VAULT -> emptyList()
    PageGlyphKind.RANDOM -> listOf(
        Mark(1, 0.5f, 0.9f, Ink.GOLD), Mark(3, 0.1f, 0.4f, Ink.GOLD),
        Mark(5, 0.6f, 1f, Ink.GOLD), Mark(7, 0.2f, 0.6f, Ink.GOLD)
    )
}

/**
 * A small drawing of a Mushaf page: lines of text, with the ones an exercise is about marked in colour. It replaces a
 * stock icon, so that each exercise has a picture of its own question. It is decoration: the row's title says the same.
 */
@Composable
fun PageGlyph(kind: PageGlyphKind, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = FolioTheme.colors
    val muted = scheme.outlineVariant
    val green = scheme.primary
    val gold = scheme.tertiary
    Canvas(modifier = modifier.size(36.dp, 48.dp).clearAndSetSemantics { }) {
        drawPage(kind, colors.folio, colors.edge, muted, green, gold)
    }
}

private fun DrawScope.drawPage(kind: PageGlyphKind, paper: Color, edge: Color, muted: Color, green: Color, gold: Color) {
    val w = size.width
    val h = size.height
    val rtl = layoutDirection == LayoutDirection.Rtl
    val corner = CornerRadius(3.dp.toPx())
    drawRoundRect(paper, size = size, cornerRadius = corner)
    drawRoundRect(edge, size = size, cornerRadius = corner, style = Stroke(1.dp.toPx()))

    val padX = w * 0.15f
    val padY = h * 0.13f
    val full = w - 2 * padX
    val lineHeight = 2.dp.toPx()
    val step = (h - 2 * padY - lineHeight) / (GLYPH_LINES - 1)
    fun top(line: Int) = padY + line * step
    /** A bar from [from] to [to] of the line's width, measured from the side reading starts: the right when Arabic. */
    fun bar(line: Int, from: Float, to: Float, color: Color) {
        val x = padX + from * full
        val width = (to - from) * full
        drawRoundRect(
            color,
            topLeft = Offset(if (rtl) w - x - width else x, top(line)),
            size = Size(width, lineHeight),
            cornerRadius = CornerRadius(lineHeight)
        )
    }
    fun ink(ink: Ink) = when (ink) {
        Ink.MUTED -> muted
        Ink.GREEN -> green
        Ink.GOLD -> gold
    }

    if (kind == PageGlyphKind.JUZ) {
        // Two columns of three: the quarters of a juz, a few of them marked.
        val gap = 3.dp.toPx()
        val cellW = (full - gap) / 2f
        val cellH = (h - 2 * padY - 2 * gap) / 3f
        val inks = listOf(Ink.GREEN, Ink.MUTED, Ink.GOLD, Ink.MUTED, Ink.MUTED, Ink.GREEN)
        for (i in 0 until 6) {
            val x = padX + (i % 2) * (cellW + gap)
            drawRoundRect(
                ink(inks[i]),
                topLeft = Offset(if (rtl) w - x - cellW else x, padY + (i / 2) * (cellH + gap)),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        return
    }

    val marked = marksOf(kind)
    for (line in 0 until GLYPH_LINES) {
        val own = marked.filter { it.line == line }
        if (own.isEmpty()) bar(line, 0f, if (line == GLYPH_LINES - 1) 0.6f else 1f, muted)
        else own.forEach { bar(it.line, it.from, it.to, ink(it.ink)) }
    }
    if (kind == PageGlyphKind.WHERE) {
        // The dot above the marked line: this verse, somewhere on the page.
        val r = lineHeight * 1.5f
        val x = padX + full * 0.05f + r
        drawCircle(green, radius = r, center = Offset(if (rtl) w - x else x, top(4) - lineHeight * 2.2f))
    }
    if (kind == PageGlyphKind.VAULT) {
        // A ribbon hanging from the top edge, on the side the page is held.
        val ribbonW = w * 0.2f
        val x = if (rtl) w - padX - ribbonW else padX
        val path = Path().apply {
            moveTo(x, 0f)
            lineTo(x + ribbonW, 0f)
            lineTo(x + ribbonW, padY * 1.9f)
            lineTo(x + ribbonW / 2f, padY * 1.45f)
            lineTo(x, padY * 1.9f)
            close()
        }
        drawPath(path, gold)
    }
}
