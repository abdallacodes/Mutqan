package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.ui.components.FrostedPanel
import com.example.mutqan.ui.components.GlassBackdrop

/** One of the tools in the capsule at the bottom of the page: a word under an icon, and what a screen reader says. */
internal class MushafTool(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val active: Boolean,
    val onClick: () -> Unit
)

/** A pane of the controls floating over the page: the page's own colour, frosted where the phone can blur, tinted where it cannot. */
@Composable
internal fun MushafGlass(
    backdrop: GlassBackdrop,
    colors: MushafColors,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    FrostedPanel(
        backdrop = backdrop,
        shape = shape,
        tint = colors.bg.copy(alpha = if (backdrop.blurs) 0.66f else 0.96f),
        sheen = Color.White.copy(alpha = if (colors.dark) 0.04f else 0.22f),
        rim = Brush.verticalGradient(listOf(colors.ink.copy(alpha = 0.20f), colors.ink.copy(alpha = 0.08f))),
        shadow = if (colors.dark) Color.Black else colors.ink,
        modifier = modifier,
        content = content
    )
}

/**
 * The capsule at the top: back, the name of the surah with the place under it (a tap opens the jump sheet), and the menu.
 * [placeLine] draws the line under the name, so the page's memory dots and text stay with the screen that knows them.
 */
@Composable
internal fun MushafTopCapsule(
    backdrop: GlassBackdrop,
    colors: MushafColors,
    title: String,
    onBack: () -> Unit,
    onTitle: () -> Unit,
    onMenu: () -> Unit,
    placeLine: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    MushafGlass(backdrop, colors, RoundedCornerShape(28.dp), modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(CAPSULE_BUTTON)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.ink
                )
            }
            // The arrow says the name is a button, and the height keeps it a full finger tall however little text there is.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onTitle)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                placeLine()
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(CAPSULE_BUTTON)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.mushaf_cd_menu),
                    tint = colors.ink
                )
            }
        }
    }
}

/** The quarter being read, with the subject the user gave it if any; a tap opens that juz's map. */
@Composable
internal fun MushafQuarterChip(
    backdrop: GlassBackdrop,
    colors: MushafColors,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MushafGlass(backdrop, colors, RoundedCornerShape(20.dp), modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val accent = accentOn(colors)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** The capsule at the bottom, in reach of the thumb: the tools used while reading, each with its name under it. */
@Composable
internal fun MushafToolCapsule(
    backdrop: GlassBackdrop,
    colors: MushafColors,
    tools: List<MushafTool>,
    modifier: Modifier = Modifier
) {
    MushafGlass(backdrop, colors, RoundedCornerShape(32.dp), modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            tools.forEach { tool ->
                val tint = if (tool.active) accentOn(colors) else colors.ink
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = tool.onClick)
                        .semantics { contentDescription = tool.description }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = tool.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The app's accent, lightened on a dark page so it stays readable on it. */
@Composable
private fun accentOn(colors: MushafColors): Color {
    val accent = MaterialTheme.colorScheme.primary
    return if (colors.dark) lerp(accent, colors.ink, 0.6f) else accent
}

private val CAPSULE_BUTTON = 44.dp
