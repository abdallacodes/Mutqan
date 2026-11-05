package com.example.mutqan.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.theme.FolioTheme
import kotlin.math.cos
import kotlin.math.sin

// The surfaces of design system v2: the raised card, the hero card, the grouped list, and the star the app uses for numbers.

/**
 * A raised card on the page. With [onClick] the whole card is one button: it springs on press and TalkBack reads its
 * texts as one item. The shadow carries the elevation in the light versions, the lighter tone in the dark ones.
 */
@Composable
fun Folio(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FolioTheme.colors
    val shape = FolioTheme.shapes.card
    val source = remember { MutableInteractionSource() }
    var surface = if (onClick != null) modifier.pressScale(source) else modifier
    surface = surface
        .lifted(shape)
        .background(colors.folio, shape)
        .border(1.dp, if (colors.dark) colors.edge else colors.edge.copy(alpha = 0.7f), shape)
        .clip(shape)
    if (onClick != null) {
        surface = surface.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick
        )
    }
    Column(modifier = surface.padding(contentPadding), content = content)
}

/** Several rows in one [Folio]; put a [FolioDivider] between them. The rows bring their own padding. */
@Composable
fun FolioGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Folio(modifier = modifier, contentPadding = PaddingValues(0.dp), content = content)
}

/** A faint line between the rows of a [FolioGroup], starting [inset] after the edge so it does not touch the icons. */
@Composable
fun FolioDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier = modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/**
 * The one deep-colour card of a screen: a gold hairline frame inset from the edge, a faint lattice of eight-pointed
 * stars behind the content, and parchment text. Put the main thing of the screen on it, and a [HeroButton] if it has one.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FolioTheme.colors
    val shape = FolioTheme.shapes.hero
    val inset = 6.dp
    val source = remember { MutableInteractionSource() }
    var surface = if (onClick != null) modifier.pressScale(source) else modifier
    surface = surface
        .lifted(shape, Lift.Floating)
        .background(colors.hero, shape)
        .clip(shape)
        .drawBehind { starLattice(colors.heroAccent.copy(alpha = 0.10f)) }
    if (onClick != null) {
        surface = surface.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick
        )
    }
    CompositionLocalProvider(LocalContentColor provides colors.onHero) {
        Box(modifier = surface.padding(inset)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.heroAccent.copy(alpha = 0.55f), RoundedCornerShape(FolioHeroFrameRadius))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                content = content
            )
        }
    }
}

/** The hero shape (22) minus the frame's inset (6). */
private val FolioHeroFrameRadius = 16.dp

// ── The eight-pointed star: two squares, one turned a quarter of a right angle ──

/** Inner radius of the star as a fraction of its outer radius: where the two squares' edges cross. */
private const val StarInner = 0.7654f

private fun starPath(radius: Float, cx: Float = 0f, cy: Float = 0f): Path = Path().apply {
    for (k in 0 until 16) {
        val angle = Math.toRadians(-90.0 + k * 22.5)
        val r = if (k % 2 == 0) radius else radius * StarInner
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The star as a shape that fills the smaller side of whatever it clips. */
val EightPointStarShape = GenericShape { size, _ ->
    addPath(starPath(minOf(size.width, size.height) / 2f, size.width / 2f, size.height / 2f))
}

/** Outlined stars on a diamond lattice, drawn over the hero card's colour. */
private fun DrawScope.starLattice(color: Color) {
    val cell = 52.dp.toPx()
    val star = starPath(14.dp.toPx())
    val stroke = Stroke(width = 1.dp.toPx())
    var row = 0
    var y = 0f
    while (y < size.height + cell) {
        var x = if (row % 2 == 0) 0f else cell / 2f
        while (x < size.width + cell) {
            translate(x, y) { drawPath(star, color, style = stroke) }
            x += cell
        }
        row++
        y += cell / 2f
    }
}

/**
 * A number set in an eight-pointed star: a surah, a page, a juz. [onHero] switches to the gold-on-deep version for use
 * on a [HeroCard]. The star grows with the font size, so the number keeps its frame at large text.
 */
@Composable
fun StarMedallion(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onHero: Boolean = false
) {
    val colors = FolioTheme.colors
    val side = size.withFontScale()
    val fill = if (onHero) colors.heroAccent else colors.tonal
    val ink = if (onHero) colors.hero else colors.onTonal
    Box(
        modifier = modifier
            .size(side)
            .clip(EightPointStarShape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = ink,
            style = TextStyle(
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                lineHeight = (size.value * 0.36f).sp,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
            ),
            maxLines = 1
        )
    }
}

/** A rounded square in the tonal colour with an icon in it: the leading mark of a row or a tile. */
@Composable
fun IconMedallion(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = FolioTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(FolioTheme.shapes.medallion)
            .background(colors.tonal),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.onTonal, modifier = Modifier.size(size * 0.55f))
    }
}
