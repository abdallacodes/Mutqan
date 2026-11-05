package com.example.mutqan.ui.components

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.Palette
import com.example.mutqan.ui.theme.paletteSwatch

private fun Palette.nameRes() = when (this) {
    Palette.PAPER -> R.string.palette_paper
    Palette.LAPIS -> R.string.palette_lapis
    Palette.DUNE -> R.string.palette_dune
    Palette.OLIVE -> R.string.palette_olive
    Palette.PLUM -> R.string.palette_plum
    Palette.GRAPHITE -> R.string.palette_graphite
}

/**
 * One tile per palette, drawn in the version (light or dark) the app is showing now. Two rows of three: six tiles do
 * not fit one row on a phone, and a scrolling row hid the last one off-screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PalettePicker(selected: Palette, dark: Boolean, onSelect: (Palette) -> Unit) {
    FlowRow(
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup()
    ) {
        Palette.entries.forEach { palette ->
            val (bg, primary, accent) = paletteSwatch(palette, dark)
            val isSelected = palette == selected
            val shape = RoundedCornerShape(4.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(palette) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(shape)
                        .background(bg)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape
                        )
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(primary))
                        Box(Modifier.size(16.dp).clip(CircleShape).background(accent))
                    }
                }
                Text(
                    text = stringResource(palette.nameRes()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
