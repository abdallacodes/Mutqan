package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.FolioSheetInsets
import com.example.mutqan.ui.components.GlassBackdrop
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace

/**
 * The way back after a jump, floating over the page: where the reader came from (a tap goes back there; with nothing
 * behind, where they went back from, to go forward again), a button for all the places, and one that tucks it away
 * into a small circle ([PlaceTrailDot]).
 */
@Composable
internal fun PlaceTrailPill(
    backdrop: GlassBackdrop,
    colors: MushafColors,
    trail: PlaceTrail,
    label: @Composable (Place) -> String,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onList: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previous = trail.previous
    val next = trail.next
    val accent = accentOn(colors)
    MushafGlass(backdrop, colors, RoundedCornerShape(TRAIL_HEIGHT / 2), modifier.widthIn(max = 340.dp)) {
        Row(modifier = Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val (text, icon, go) = when {
                previous != null -> Triple(stringResource(R.string.trail_back, label(previous)), Icons.AutoMirrored.Filled.Undo, onBack)
                next != null -> Triple(stringResource(R.string.trail_forward, label(next)), Icons.AutoMirrored.Filled.Redo, onForward)
                else -> Triple("", Icons.AutoMirrored.Filled.Undo, onBack)
            }
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = TRAIL_HEIGHT)
                    .clip(RoundedCornerShape(TRAIL_HEIGHT / 2))
                    .clickable(role = Role.Button, onClick = go)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.width(1.dp).height(22.dp).background(colors.muted.copy(alpha = 0.35f)))
            IconButton(onClick = onList, modifier = Modifier.size(TRAIL_HEIGHT)) {
                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.trail_cd_list, trail.places.size), tint = colors.ink)
            }
            IconButton(onClick = onHide, modifier = Modifier.size(TRAIL_HEIGHT)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.trail_cd_hide), tint = colors.muted)
            }
        }
    }
}

/** The way back tucked away: a small circle with the number of places; a tap brings the pill back. */
@Composable
internal fun PlaceTrailDot(
    backdrop: GlassBackdrop,
    colors: MushafColors,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.trail_cd_show, count)
    MushafGlass(backdrop, colors, CircleShape, modifier) {
        Box(
            modifier = Modifier
                .size(TRAIL_HEIGHT)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(16.dp)
                    .background(accentOn(colors), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = colors.bg,
                    maxLines = 1
                )
            }
        }
    }
}

/** Every place the reader jumped between, the latest first, the one they are at marked; a tap goes to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceTrailSheet(
    trail: PlaceTrail,
    label: @Composable (Place) -> String,
    onGo: (index: Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = PaperSpace.Gutter, end = PaperSpace.Gutter - 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                PaperLink(text = stringResource(R.string.trail_clear), onClick = onClear, soft = true)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(trail.places.indices.reversed().toList(), key = { it }) { i ->
                    val place = trail.places[i]
                    val here = i == trail.at
                    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !here, role = Role.Button) { onGo(i) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (here) Icons.Filled.Place else Icons.Filled.History,
                                contentDescription = null,
                                tint = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = label(place),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                    if (here) {
                                        Text(
                                            text = stringResource(R.string.trail_here),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (place.ayahId > 0) AyahText(ayahId = place.ayahId, size = 18.sp, firstWords = 8, maxLines = 1)
                            }
                        }
                        Hairline(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** The height of the way-back pill and circle: a finger's touch. */
private val TRAIL_HEIGHT = 44.dp
