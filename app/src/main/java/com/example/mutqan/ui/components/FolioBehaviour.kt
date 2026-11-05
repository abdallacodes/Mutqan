package com.example.mutqan.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mutqan.ui.theme.FolioTheme

/** How far a pressed control shrinks. */
private const val PressedScale = 0.97f

/** Shrinks the element a little while [source] is pressed, and springs back. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = PressedScale): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Something to press: a ripple inside the element's bounds (clip it to its shape first) and a small spring on
 * press. Announced as a button unless [role] says otherwise.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null
): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressScale(source).clickable(
        interactionSource = source,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

/** How high a shadow lifts the element: a card resting on the page, or something floating over it (hero card, dock). */
enum class Lift(val elevation: Dp) { Raised(6.dp), Floating(14.dp) }

/**
 * A soft shadow tinted with the palette's deep colour. In the dark versions nothing is drawn: there the lighter tone
 * of the surface carries the elevation.
 */
@Composable
fun Modifier.lifted(shape: Shape, lift: Lift = Lift.Raised): Modifier {
    val colors = FolioTheme.colors
    if (colors.dark) return this
    return shadow(
        elevation = lift.elevation,
        shape = shape,
        clip = false,
        ambientColor = colors.shadow.copy(alpha = 0.18f),
        spotColor = colors.shadow.copy(alpha = 0.32f)
    )
}

/** Scales a size that holds text with the user's font size, up to [max] times, so a number keeps its frame at large text. */
@Composable
fun Dp.withFontScale(max: Float = 1.6f): Dp = this * LocalDensity.current.fontScale.coerceIn(1f, max)

/** The three haptic cues of the app. Never the only signal: each goes with something visible or audible. */
@Stable
class Haptics internal constructor(private val feedback: HapticFeedback) {
    /** A revision logged, an answer right. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** An answer wrong, an action refused. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A step between choices (chips, tabs). */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
