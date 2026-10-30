package com.example.mutqan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
fun PaperOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground) }
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

/** Spacing used between the parts of a page. */
object PaperSpace {
    val Gutter = 24.dp
    val Section = 28.dp
}
