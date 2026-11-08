package com.example.mutqan.ui.mushaf

import com.example.mutqan.ui.components.FolioSheetInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.ui.components.FolioDivider
import com.example.mutqan.ui.components.FolioGroup
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperSwitch
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.FolioTheme

/**
 * What the Mushaf's "more" button opens: the less frequent things, in three groups (listening, studying, the page) instead
 * of one long list. What is done all the time while reading, the cover, the revision log and the two-page view, has its
 * own button in the bar. Things that are done (listen, saved, mark) close the sheet; settings stay open to be changed
 * together, except the page colours, which close it so the page can be seen in them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafMenuSheet(
    showGroupAyahs: Boolean,
    subjectTint: Boolean,
    revealSteps: Boolean,
    twoPage: Boolean,
    fillWidth: Boolean,
    rotateWithDevice: Boolean,
    theme: MushafTheme,
    onListen: () -> Unit,
    onRecitation: () -> Unit,
    onGroupAyahs: (Boolean) -> Unit,
    onSubjectTint: (Boolean) -> Unit,
    onRevealSteps: (Boolean) -> Unit,
    onSaved: () -> Unit,
    onMarkMemorised: () -> Unit,
    onFillWidth: (Boolean) -> Unit,
    onRotate: (Boolean) -> Unit,
    onTheme: (MushafTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = PaperSpace.Gutter)
                .padding(bottom = 16.dp)
        ) {
            Group(R.string.mushaf_menu_listen) {
                ActionRow(Icons.Filled.PlayArrow, stringResource(R.string.audio_listen_page), onListen)
                FolioDivider(inset = DIVIDER_INSET)
                ActionRow(Icons.Filled.Tune, stringResource(R.string.audio_sheet_title), onRecitation)
            }
            Group(R.string.mushaf_menu_study) {
                SwitchRow(Icons.Filled.Highlight, stringResource(R.string.mushaf_menu_group_ayahs), showGroupAyahs, onGroupAyahs)
                FolioDivider(inset = DIVIDER_INSET)
                SwitchRow(Icons.Filled.Palette, stringResource(R.string.mushaf_menu_subject_tint), subjectTint, onSubjectTint)
                FolioDivider(inset = DIVIDER_INSET)
                SwitchRow(Icons.Filled.LinearScale, stringResource(R.string.mushaf_menu_reveal_steps), revealSteps, onRevealSteps)
                FolioDivider(inset = DIVIDER_INSET)
                ActionRow(Icons.Filled.Bookmarks, stringResource(R.string.mushaf_cd_saved), onSaved)
                FolioDivider(inset = DIVIDER_INSET)
                ActionRow(Icons.Filled.Verified, stringResource(R.string.mushaf_mark_page_memorised), onMarkMemorised)
            }
            Group(R.string.mushaf_menu_page) {
                if (twoPage) {
                    SwitchRow(Icons.Filled.OpenInFull, stringResource(R.string.mushaf_menu_fill_width), fillWidth, onFillWidth)
                    FolioDivider(inset = DIVIDER_INSET)
                }
                SwitchRow(Icons.Filled.ScreenRotation, stringResource(R.string.mushaf_rotate), rotateWithDevice, onRotate)
                FolioDivider(inset = DIVIDER_INSET)
                ThemeChoice(theme, onTheme)
            }
        }
    }
}

private val DIVIDER_INSET = 56.dp

@Composable
private fun Group(label: Int, content: @Composable () -> Unit) {
    SectionLabel(stringResource(label), Modifier.padding(top = 16.dp, bottom = 8.dp))
    FolioGroup(Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}

/** Something to do: it closes the sheet. */
@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RowIcon(icon)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** A setting: the whole row is the switch, so it is announced with its name. */
@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RowIcon(icon)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        PaperSwitch(checked = checked, onCheckedChange = null)
    }
}

/** The page colours as small pages in those colours: pick the one to read on. */
@Composable
private fun ThemeChoice(selected: MushafTheme, onTheme: (MushafTheme) -> Unit) {
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.mushaf_page_colours),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MushafTheme.entries.forEach { option ->
                val page = option.colors(appIsDark)
                val isSelected = option == selected
                val shape = FolioTheme.shapes.control
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onTheme(option) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clip(shape)
                            .background(page.bg)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // A word in the page's own ink, so the colours are seen as text on paper.
                        Text(text = "قرآن", style = MaterialTheme.typography.titleMedium, color = page.ink)
                    }
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
