package com.example.mutqan.ui.heatmap.components

import com.example.mutqan.ui.components.FolioSheetInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.FolioDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.FolioTheme
import java.text.NumberFormat

/** A line of the verse list: the heading of a surah, or one of its verses. */
private sealed interface PickerRow {
    data class Surah(val id: Int) : PickerRow
    data class Verse(val ayah: QuranAyah) : PickerRow
}

/** The quarter's verses with a surah heading before the first of each surah. */
private fun pickerRows(ayahs: List<QuranAyah>): List<PickerRow> = buildList {
    var surah = -1
    for (ayah in ayahs) {
        if (ayah.surah != surah) {
            surah = ayah.surah
            add(PickerRow.Surah(surah))
        }
        add(PickerRow.Verse(ayah))
    }
}

/**
 * Adds a subject to a hizb quarter of the juz map, or changes one. A title field on top; below it the quarter's verses,
 * each with its opening words, to tap the one the subject starts at; Save stays at the bottom. A verse that already starts
 * another subject cannot be picked (a verse starts at most one): it is dimmed and says which subject it starts.
 *
 * @param taken start verse (global ayah id) to the title of the other subjects of this quarter.
 * @param editing the subject being changed, null for a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectEditorSheet(
    ayahs: List<QuranAyah>,
    taken: Map<Int, String>,
    editing: UserSubjectEntity?,
    onSave: (title: String, startAyahId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(editing?.subjectText.orEmpty()) }
    var selected by remember { mutableStateOf(editing?.startAyahId?.takeIf { id -> ayahs.any { it.id == id } }) }
    val rows = remember(ayahs) { pickerRows(ayahs) }
    // Editing opens with the subject's verse in view, one line below the top so its neighbours show.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember(rows) {
            val at = rows.indexOfFirst { it is PickerRow.Verse && it.ayah.id == selected }
            (at - 2).coerceAtLeast(0)
        }
    )
    val focus = LocalFocusManager.current
    val numbers = remember { NumberFormat.getInstance() }
    val chosen = ayahs.firstOrNull { it.id == selected }

    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = stringResource(if (editing == null) R.string.add_subject else R.string.edit_subjects),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.subject_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaperSpace.Gutter)
                    .padding(top = 16.dp)
            )
            SectionLabel(
                text = stringResource(R.string.subject_start_ayah_label),
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 16.dp)
            )
            Text(
                text = if (chosen == null) {
                    stringResource(R.string.subject_pick_start_hint)
                } else {
                    stringResource(
                        R.string.mushaf_subject_starts_at,
                        stringResource(R.string.saved_ref, SurahData.nameOf(chosen.surah), chosen.ayah)
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (chosen == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 4.dp, bottom = 8.dp)
            )
            FolioDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = PaperSpace.Gutter - 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = rows,
                    key = { row -> when (row) { is PickerRow.Surah -> "s${row.id}"; is PickerRow.Verse -> "v${row.ayah.id}" } }
                ) { row ->
                    when (row) {
                        is PickerRow.Surah -> SectionLabel(
                            text = "${row.id}. ${SurahData.nameOf(row.id)}",
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                        is PickerRow.Verse -> VerseRow(
                            ayah = row.ayah,
                            number = numbers.format(row.ayah.ayah),
                            selected = row.ayah.id == selected,
                            takenBy = taken[row.ayah.id],
                            onClick = {
                                focus.clearFocus()
                                selected = row.ayah.id
                            }
                        )
                    }
                }
            }
            FolioDivider()
            PaperButton(
                text = stringResource(R.string.btn_save),
                onClick = { chosen?.let { onSave(title.trim(), it.id) } },
                enabled = title.isNotBlank() && chosen != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaperSpace.Gutter)
                    .padding(top = 12.dp, bottom = 12.dp)
            )
        }
    }
}

/** One verse: its number, its opening words, and, if another subject starts there, that subject's title. */
@Composable
private fun VerseRow(ayah: QuranAyah, number: String, selected: Boolean, takenBy: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = FolioTheme.shapes.control
    val free = takenBy == null
    val ink = if (free) colors.onBackground else colors.onBackground.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) colors.primary else Color.Transparent, shape)
            .selectable(selected = selected, enabled = free, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else Color.Transparent)
                .border(1.dp, if (selected) colors.primary else colors.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else ink
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AyahText(ayahId = ayah.id, size = 20.sp, color = ink, firstWords = 8, maxLines = 2)
            if (takenBy != null) {
                Text(
                    text = stringResource(R.string.mushaf_subject_here, takenBy),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}
