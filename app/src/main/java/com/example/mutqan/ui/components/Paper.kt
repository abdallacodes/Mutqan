package com.example.mutqan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import com.example.mutqan.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.ui.theme.AmiriFontFamily
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize

/** The app's own building blocks: a printed page, not stacked cards. */

/** A thin rule with the ayah-end mark (۝) in the middle, used to separate parts of a page. */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Hairline(Modifier.weight(1f))
        Text(
            text = "۝",
            style = TextStyle(fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.tertiary
        )
        Hairline(Modifier.weight(1f))
    }
}

/** A one-pixel rule in the page's rule colour. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outline
    Box(modifier = modifier.height(1.dp).drawBehind {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    })
}

/** A small quiet label above a group, in the soft ink colour. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The main action: a flat, square-cornered block of the action colour. */
@Composable
fun PaperButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)) }
}

/** A secondary action: the same block outlined by a rule. */
@Composable
fun PaperOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/** A text action, underlined like a link in a printed page. */
@Composable
fun PaperLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, soft: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (soft) FontWeight.Normal else FontWeight.Medium,
                textDecoration = if (soft) TextDecoration.None else TextDecoration.Underline
            ),
            color = if (soft) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * A page heading: the title in display type, an optional quiet line under it, actions at the end
 * and an ornament divider below. [onBack] adds a back arrow at the start for sub-screens.
 */
@Composable
fun PaperHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack == null) PaperSpace.Gutter else 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
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
        OrnamentDivider(Modifier.padding(horizontal = PaperSpace.Gutter))
    }
}

/** Two or three choices set as words with a rule under the current one, instead of a segmented block. */
@Composable
fun PaperTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Tab, onClick = { onSelect(i) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

/** A choice that can be on or off: plain words, with a rule under them when chosen. Replaces filter chips. */
@Composable
fun PaperChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ProvideTextStyle(
            MaterialTheme.typography.labelLarge.copy(
                color = color,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        ) { label() }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(if (selected) 2.dp else 1.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** A thin slider: a hairline track, the filled part in the action colour, a small round handle. */
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
    val rule = MaterialTheme.colorScheme.outline
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        thumb = {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(14.dp).background(primary, CircleShape))
            }
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span == 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(rule)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(primary))
            }
        }
    )
}

/** A compact outlined action for rows in a list, in the action colour so it reads as something to press. */
@Composable
fun PaperSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
}

/** A hairline track with the finished part drawn over it in the action colour. */
@Composable
fun PaperProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Box(modifier = modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

/** One of several exclusive options: a ring, a name and an optional sentence, with a rule under it. */
@Composable
fun PaperChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 14.dp),
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
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
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
        Hairline()
    }
}

/**
 * A number with a square minus button before it and a plus button after it, always in that order. With [onValueClick]
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
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = color) }
}

/**
 * A question or a small form on a flat, square-cornered sheet with a rule round it. It takes the slots of
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
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                if (title != null) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground)) { title() }
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

/** An on/off control: a square-cornered track with a square thumb that slides to the "on" end. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val rule = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .border(1.dp, if (checked) primary else rule, RoundedCornerShape(4.dp))
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(16.dp)
                    .background(if (checked) primary else rule, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Spacing used between the parts of a page. */
object PaperSpace {
    val Gutter = 24.dp
    val Section = 28.dp
}
