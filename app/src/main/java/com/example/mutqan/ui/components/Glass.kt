package com.example.mutqan.ui.components

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** The room a tab screen leaves at its bottom for the floating dock, so its last row can scroll clear of the glass. */
val LocalDockInset = compositionLocalOf { 0.dp }

/**
 * What a panel of glass looks through: the screen behind it, recorded once, then drawn again inside the panel and
 * blurred. Blur needs Android 12; before it the panel is simply tinted so much that nothing needs to show through.
 */
@Stable
class GlassBackdrop internal constructor(internal val source: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)

    val blurs: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val source = rememberGraphicsLayer()
    return remember(source) { GlassBackdrop(source) }
}

/** Marks the content a [FrostedPanel] sees through. [background] is painted first so the blur has a colour to mix with. */
fun Modifier.glassSource(backdrop: GlassBackdrop, background: Color): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.source.record {
            drawRect(background)
            this@drawWithContent.drawContent()
        }
        drawLayer(backdrop.source)
    }

/** A pane of frosted glass: the blurred screen behind it, a tint, a soft sheen across the top and a bright rim. */
@Composable
fun FrostedPanel(
    backdrop: GlassBackdrop,
    shape: Shape,
    tint: Color,
    sheen: Color,
    rim: Brush,
    modifier: Modifier = Modifier,
    blur: Dp = 12.dp,
    shadow: Color = Color.Black,
    content: @Composable BoxScope.() -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Each pane blurs in a layer of its own: panes sharing one layer would all show whichever was recorded last.
    val frost = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { blur.toPx() }
    SideEffect {
        frost.renderEffect = if (backdrop.blurs) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = shadow.copy(alpha = 0.18f),
                spotColor = shadow.copy(alpha = 0.30f)
            )
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                if (backdrop.blurs) {
                    frost.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                        translate(left = backdrop.origin.x - origin.x, top = backdrop.origin.y - origin.y) {
                            drawLayer(backdrop.source)
                        }
                    }
                    drawLayer(frost)
                }
                drawRect(tint)
                drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = size.height * 0.65f))
            }
            .border(1.dp, rim, shape),
        content = content
    )
}

/**
 * The glass of every floating control, so they all look like the dock: its colour laid thin over the blurred screen
 * behind ([cover] of it; nearly all where the phone cannot blur), a soft white sheen across the top and a bright white
 * rim, both dimmer on a dark colour.
 */
object GlassLook {
    /** How much of the tint covers the blur on the app's own screens and over a light page. */
    const val COVER = 0.50f

    fun tint(base: Color, blurs: Boolean, cover: Float = COVER): Color = base.copy(alpha = if (blurs) cover else 0.96f)

    fun sheen(dark: Boolean): Color = Color.White.copy(alpha = if (dark) 0.07f else 0.34f)

    fun rim(dark: Boolean): Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.95f), Color.White.copy(alpha = if (dark) 0.06f else 0.30f))
    )
}
