package com.example.mutqan.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.VerseListSummary
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperSpace

/**
 * The third tab of the Quran page: the user's own lists of verses, the most recently changed first, under a row that
 * starts a new one. Before the first list, what lists are for and a few names to start with. [lists] is null while
 * they are read.
 */
fun LazyListScope.listsTab(lists: List<VerseListSummary>?, onOpen: (Int) -> Unit, onNew: (suggested: String) -> Unit) {
    if (lists == null) return
    if (lists.isEmpty()) {
        item(key = "lists-intro") { ListsIntro(onNew) }
        return
    }
    item(key = "lists-new") {
        HubRow(
            title = stringResource(R.string.lists_new),
            subtitle = null,
            onClick = { onNew("") },
            compact = true,
            icon = Icons.Filled.Add
        )
    }
    items(lists, key = { "list-${it.id}" }) { list ->
        HubRow(
            title = list.name,
            subtitle = list.note.lineSequence().firstOrNull { it.isNotBlank() },
            note = pluralStringResource(R.plurals.count_ayahs, list.verses, list.verses),
            onClick = { onOpen(list.id) },
            compact = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListsIntro(onNew: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.lists_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.lists_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PaperButton(text = stringResource(R.string.lists_new), onClick = { onNew("") }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.lists_examples),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (res in listOf(R.string.lists_example_1, R.string.lists_example_2, R.string.lists_example_3)) {
                val name = stringResource(res)
                PaperChip(selected = false, onClick = { onNew(name) }, label = { Text(name) }, showCheck = false)
            }
        }
    }
}
