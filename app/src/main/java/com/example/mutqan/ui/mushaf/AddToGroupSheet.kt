package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.dao.GroupWithCount
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.vault.MasterStrength

/** More groups than this and the list gets a search field. */
private const val SEARCH_FROM = 6

/**
 * Where an ayah goes in the user's mutashabihat groups: tap a group to put the ayah in it, or start a new group with
 * it. [memberOf] are the groups that already hold the ayah (shown, not tappable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGroupSheet(
    surah: Int,
    ayah: Int,
    groups: List<GroupWithCount>,
    memberOf: Set<Int>,
    onPick: (GroupWithCount) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(groups.isEmpty()) }
    var newName by rememberSaveable { mutableStateOf("") }
    val shown = remember(groups, query) {
        val q = query.trim()
        if (q.isEmpty()) groups else groups.filter { it.group.description.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.add_to_group_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                text = stringResource(R.string.peek_group_ayah_ref, SurahData.nameOf(surah), ayah),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            if (creating) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.add_to_group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PaperButton(
                            text = stringResource(R.string.add_to_group_create),
                            onClick = { onCreate(newName.trim()) },
                            enabled = newName.isNotBlank()
                        )
                        if (groups.isNotEmpty()) {
                            PaperLink(stringResource(R.string.btn_cancel), onClick = { creating = false }, soft = true)
                        }
                    }
                }
            } else {
                HubRow(
                    title = stringResource(R.string.add_to_group_new),
                    subtitle = null,
                    icon = Icons.Default.Add,
                    compact = true,
                    onClick = { creating = true }
                )
            }

            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.add_to_group_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                if (groups.size >= SEARCH_FROM) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.add_to_group_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown, key = { it.group.id }) { entry ->
                        val already = entry.group.id in memberOf
                        HubRow(
                            title = entry.group.description,
                            subtitle = stringResource(
                                R.string.add_to_group_row,
                                pluralStringResource(R.plurals.count_ayahs, entry.memberCount, entry.memberCount),
                                MasterStrength.fromQuality(entry.group.masterQuality).localizedLabel()
                            ),
                            note = if (already) stringResource(R.string.add_to_group_already) else null,
                            icon = if (already) Icons.Default.Check else null,
                            compact = true,
                            onClick = { if (!already) onPick(entry) }
                        )
                    }
                }
            }
        }
    }
}
