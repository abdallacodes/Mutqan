package com.example.mutqan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.ui.theme.FolioTheme

// Buttons and pills of design system v2. Every one is a filled shape (or an unmistakable text action), at least 48dp
// tall to touch, and springs a little on press.

@Composable
private fun FolioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    container: Color,
    content: Color,
    raised: Boolean,
    pill: Boolean
) {
    val source = remember { MutableInteractionSource() }
    val shape = if (pill) FolioTheme.shapes.pill else FolioTheme.shapes.control
    Button(
        onClick = onClick,
        modifier = modifier
            .pressScale(source)
            .then(if (raised && enabled) Modifier.lifted(shape) else Modifier)
            .heightIn(min = 50.dp),
        enabled = enabled,
        shape = shape,
        interactionSource = source,
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** The main action of a screen: filled with the action colour. [pill] makes it fully round, for the one action a tab leads with. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    pill: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    FolioButton(text, onClick, modifier, enabled, icon, colors.primary, colors.onPrimary, raised = true, pill = pill)
}

/** A second action beside the main one, or the main one where the page already has a stronger mark: filled with the tonal colour. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val colors = FolioTheme.colors
    FolioButton(text, onClick, modifier, enabled, icon, colors.tonal, colors.onTonal, raised = false, pill = false)
}

/** The action on a [HeroCard]: gold, with the hero colour as its label. */
@Composable
fun HeroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    pill: Boolean = false
) {
    val colors = FolioTheme.colors
    FolioButton(text, onClick, modifier, enabled, icon, colors.heroAccent, colors.hero, raised = false, pill = pill)
}

/**
 * A quiet action in the action colour, with a chevron when it leads to another screen. Not underlined: the colour,
 * the weight and the chevron are what say it can be pressed.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chevron: Boolean = false
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = FolioTheme.shapes.control,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * A filter that is on or off, as a round-ended pill: filled with the tonal colour and marked with a check when on,
 * outlined when off. The touch area is 48dp tall even though the pill is slimmer. Announced as a check box.
 */
@Composable
fun FilterPill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = true,
    label: @Composable () -> Unit
) {
    val colors = FolioTheme.colors
    val source = remember { MutableInteractionSource() }
    val container by animateColorAsState(if (selected) colors.tonal else colors.folio, label = "pillFill")
    val content = if (selected) colors.onTonal else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp, minWidth = 48.dp)
            .toggleable(value = selected, interactionSource = source, indication = null, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .pressScale(source)
                .heightIn(min = 38.dp)
                .clip(FolioTheme.shapes.pill)
                .background(container)
                .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, FolioTheme.shapes.pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected && showCheck) Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            androidx.compose.material3.ProvideTextStyle(
                MaterialTheme.typography.labelLarge.copy(color = content, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            ) { label() }
        }
    }
}

/** Shows what a screen is limited to ("All pages", "Juz 30") and opens the choice: a tonal pill with a down chevron. Announced as a button. */
@Composable
fun ScopePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FolioTheme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp, minWidth = 48.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .pressScale(source)
                .heightIn(min = 38.dp)
                .clip(FolioTheme.shapes.pill)
                .background(colors.tonal)
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onTonal)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.onTonal, modifier = Modifier.size(20.dp))
        }
    }
}
