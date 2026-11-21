package com.example.mutqan.ui.lists

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.ListPassage
import com.example.mutqan.domain.VerseLists
import com.example.mutqan.domain.VerseListSummary
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.FolioSheetInsets
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperStepper
import com.example.mutqan.ui.theme.FolioTheme

/**
 * The row on a verse's sheet that leads to the user's lists: "Add to a list", or the lists the verse is already in.
 */
@Composable
fun ListsRow(names: List<String>, onClick: () -> Unit) {
    val shape = FolioTheme.shapes.control
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(
            text = if (names.isEmpty()) stringResource(R.string.mushaf_lists_add)
            else stringResource(R.string.mushaf_lists_in, names.joinToString(stringResource(R.string.scope_list_separator))),
            style = MaterialTheme.typography.bodyLarge,
            color = if (names.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (names.isEmpty()) Icons.Filled.Add else Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Puts the verse pressed ([ayahId]), or it and the verses after it up to one chosen, into the user's lists, or takes it
 * out of them. Each list shows whether it holds all of those verses; tapping it switches that. A new list can be
 * started here with the verses in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    ayahId: Int,
    lists: List<VerseListSummary>,
    passages: Map<Int, List<ListPassage>>,
    onToggle: (list: VerseListSummary, start: Int, end: Int, add: Boolean) -> Unit,
    onCreate: (name: String, note: String, start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    val first = remember(ayahId) { firstOfSurah(index, ayahId) }
    val lastAyah = remember(ayahId) { index.ayahOf(lastOfSurah(index, ayahId)) }
    val fromAyah = index.ayahOf(ayahId)
    var upTo by remember(ayahId) { mutableIntStateOf(fromAyah) }
    val end = first + upTo - 1
    var naming by remember { mutableStateOf(false) }
    val takenKeys = remember(lists) { lists.mapTo(HashSet()) { VerseLists.nameKey(it.name) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = FolioSheetInsets,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter)) {
                Text(stringResource(R.string.list_add_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(passageRef(ayahId, end), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
            if (lastAyah > fromAyah) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.list_add_up_to),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PaperStepper(value = upTo, onChange = { upTo = it }, min = fromAyah, max = lastAyah)
                }
            }
            Hairline(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                item(key = "new") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { naming = true }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = PaperSpace.Gutter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.lists_new), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(lists, key = { it.id }) { list ->
                    val holds = VerseLists.containsAll(passages[list.id].orEmpty(), ayahId, end)
                    ListChoice(list, holds, onToggle = { onToggle(list, ayahId, end, !holds) })
                }
            }
            PaperButton(
                text = stringResource(R.string.scope_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)
            )
        }
    }

    if (naming) {
        ListNameDialog(
            title = stringResource(R.string.lists_new),
            initialName = "",
            initialNote = "",
            takenKeys = takenKeys,
            onDismiss = { naming = false },
            onSave = { name, note ->
                naming = false
                onCreate(name, note, ayahId, end)
            }
        )
    }
}

@Composable
private fun ListChoice(list: VerseListSummary, holds: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (holds) R.string.list_in_cd else R.string.list_not_in_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = holds, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = state; contentDescription = list.name }
            .heightIn(min = 56.dp)
            .padding(horizontal = PaperSpace.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (holds) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (holds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(list.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.count_ayahs, list.verses, list.verses),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
