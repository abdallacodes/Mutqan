package com.example.mutqan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.theme.FolioTheme

/**
 * The app's building blocks, in the look of design system v2 ("Illuminated folio"). The Paper* names stay so that
 * every screen already uses them; new code can also use the Folio components next to this file.
 */

/** The ayah-end mark (۝) in gold, centred: a quiet pause between parts of a page. Lines on both sides were dropped. */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "۝",
            style = TextStyle(fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/** A faint rule as wide as the space it is given. Use it only where rows of one list need a line between them. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
}

/** A small quiet label above a group, in the soft ink colour. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The main action: filled with the action colour, rounded, with a spring on press. */
@Composable
fun PaperButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PrimaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** A secondary action: filled with the tonal colour. */
@Composable
fun PaperOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TonalButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** A text action in the action colour (or, [soft], in the quiet ink colour). Not underlined: the colour and weight say it can be pressed. */
@Composable
fun PaperLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, soft: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = FolioTheme.shapes.control,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (soft) FontWeight.Medium else FontWeight.SemiBold),
            color = if (soft) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * A page heading: the title in display type, an optional quiet line under it and actions at the end. [onBack] adds a
 * back arrow at the start for sub-screens. [compact] is for a task screen whose own content (a question) is the
 * focal point: the title is set small. With large text the title steps down so it never takes a quarter of the screen.
 */
@Composable
fun PaperHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    titleGlyph: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val titleStyle = when {
        compact -> MaterialTheme.typography.titleMedium
        largeText -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.headlineMedium
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = if (onBack == null) PaperSpace.Gutter else 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (titleGlyph != null) {
                Box(Modifier.semantics(mergeDescendants = true) { contentDescription = title }) { titleGlyph() }
            } else {
                Text(
                    text = title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions()
    }
}

/** Two or three choices as one segmented pill: the current one is raised like a card, the others sit flat in the tonal track. */
@Composable
fun PaperTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = FolioTheme.colors
    val pill = FolioTheme.shapes.pill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .background(colors.tonal.copy(alpha = 0.55f), pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val container by animateColorAsState(if (selected) colors.folio else Color.Transparent, label = "tabFill")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .then(if (selected) Modifier.lifted(pill) else Modifier)
                    .background(container, pill)
                    .clip(pill)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** A choice that can be on or off, as a pill: filled and checked when on, outlined when off. */
@Composable
fun PaperChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterPill(selected = selected, onClick = onClick, modifier = modifier, label = label)
}

/** A slider with a rounded track, the filled part in the action colour and a round handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = FolioTheme.colors.tonal
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        thumb = {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(22.dp).background(primary, CircleShape).border(3.dp, FolioTheme.colors.folio, CircleShape))
            }
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span == 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(track, FolioTheme.shapes.pill)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(primary, FolioTheme.shapes.pill))
            }
        }
    )
}

/** A compact tonal action for rows in a list. */
@Composable
fun PaperSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FolioTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = FolioTheme.shapes.control,
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.tonal, contentColor = colors.onTonal)
    ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) }
}

/** A thick rounded bar with the finished part drawn over it, animated. */
@Composable
fun PaperProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    PageStrip(fraction = fraction, modifier = modifier, height = 8.dp, color = color)
}

/** One of several exclusive options on its own card: a ring, a name and an optional sentence. The chosen card is outlined in the action colour. */
@Composable
fun PaperChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val colors = FolioTheme.colors
    val shape = FolioTheme.shapes.control
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) colors.tonal.copy(alpha = 0.6f) else colors.folio, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(modifier = Modifier.size(12.dp).background(primary, CircleShape))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A number with a minus button before it and a plus button after it, always in that order. With [onValueClick]
 * the number itself can be tapped, to type a value.
 */
@Composable
fun PaperStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    onValueClick: (() -> Unit)? = null
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.plan_btn_decrease), enabled = value > min) { onChange(value - 1) }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .then(if (onValueClick != null) Modifier.clickable(role = Role.Button, onClick = onValueClick) else Modifier)
            )
            StepButton("+", stringResource(R.string.plan_btn_increase), enabled = value < max) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = FolioTheme.colors
    val shape = FolioTheme.shapes.control
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (enabled) colors.tonal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) colors.onTonal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

/**
 * A question or a small form on a rounded raised sheet. It takes the slots of
 * [androidx.compose.material3.AlertDialog], so the two are interchangeable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaperDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Surface(
            shape = RoundedCornerShapeDialog,
            color = FolioTheme.colors.folio,
            // A dialog is its own window: without this the content colour falls back to black, and any Text that sets its
            // own style (so does not take the colour given below) is black on the dark folio.
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, FolioTheme.colors.edge.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                if (title != null) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface)) { title() }
                    Spacer(Modifier.height(16.dp))
                }
                if (text != null) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { text() }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

private val RoundedCornerShapeDialog = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

/**
 * An on/off control: a pill track with a round thumb that slides to the "on" end. Beside a label, pass
 * null for [onCheckedChange] and make the whole row `toggleable`, so the switch is announced with its name.
 */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val track by animateColorAsState(if (checked) primary else FolioTheme.colors.folio, label = "switchTrack")
    val thumb by animateColorAsState(if (checked) MaterialTheme.colorScheme.onPrimary else outline, label = "switchThumb")
    val bias by animateFloatAsState(if (checked) 1f else -1f, label = "switchThumbPosition")
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onCheckedChange != null) Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .background(track, CircleShape)
                .border(2.dp, if (checked) primary else outline, CircleShape)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = bias, verticalBias = 0f))
                    .size(16.dp)
                    .background(thumb, CircleShape)
            )
        }
    }
}

/** Spacing used between the parts of a page. */
object PaperSpace {
    val Gutter = 20.dp
    val Section = 24.dp
}
