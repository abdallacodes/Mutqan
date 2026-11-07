package com.example.mutqan.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.mutqan.ui.components.PaperChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.SavedKind
import com.example.mutqan.domain.WeakTag
import com.example.mutqan.ui.components.EmptyStateCard
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.components.priorityLabel
import com.example.mutqan.ui.theme.AmiriFontFamily
import androidx.compose.ui.draw.alpha
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.AyahText
import androidx.compose.foundation.layout.PaddingValues
import com.example.mutqan.ui.components.Folio

/** Every bookmark and weak passage the user saved, filterable by type and tag. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavedPassagesScreen(onBack: () -> Unit, onOpenMushaf: (Int) -> Unit, initialKind: SavedKind? = null) {
    val context = LocalContext.current
    val viewModel: SavedPassagesViewModel = viewModel(factory = SavedPassagesViewModelFactory(context, initialKind))
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<SavedItem?>(null) }

    editing?.let { item ->
        SavePassageSheet(
            reference = referenceOf(item),
            initial = item.passage,
            onSave = { kind, tags, priority, note ->
                viewModel.save(item.passage.copy(kind = kind, tags = tags, priority = priority, note = note))
                editing = null
            },
            onDelete = {
                viewModel.delete(item.passage.id)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.saved_title),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PaperSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperChip(
                    selected = state.kindFilter == null,
                    onClick = { viewModel.setKindFilter(null) },
                    label = { Text(stringResource(R.string.saved_filter_all)) }
                )
                SavedKind.entries.forEach { kind ->
                    PaperChip(
                        selected = state.kindFilter == kind,
                        onClick = { viewModel.setKindFilter(kind) },
                        label = { Text(kind.localizedLabel()) }
                    )
                }
            }
            if (state.kindFilter != SavedKind.BOOKMARK) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeakTag.entries.forEach { tag ->
                        PaperChip(
                            selected = tag in state.tagFilter,
                            onClick = { viewModel.toggleTagFilter(tag) },
                            label = { Text(tag.localizedLabel()) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                if (state.isLoaded && state.items.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Bookmarks,
                            title = stringResource(R.string.saved_title),
                            description = stringResource(R.string.saved_empty),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(state.items, key = { it.passage.id }) { item ->
                    SavedCard(
                        item = item,
                        onOpen = { onOpenMushaf(item.page) },
                        onEdit = { editing = item },
                        onToggleSolid = { viewModel.toggleSolid(item.passage) }
                    )
                }
            }
        }
    }
}

@Composable
private fun referenceOf(item: SavedItem): String {
    val start = stringResource(R.string.saved_ref, SurahData.nameOf(item.startSurah), item.startAyah)
    val base = if (item.endSurah == item.startSurah && item.endAyah == item.startAyah) start
    else stringResource(
        R.string.saved_ref_range,
        SurahData.nameOf(item.startSurah), item.startAyah,
        SurahData.nameOf(item.endSurah), item.endAyah
    )
    return if (item.wordPosition > 0) base + " · " + stringResource(R.string.saved_word_position, item.wordPosition) else base
}

/** One saved passage, set as an entry on the page: its reference, the verse, what it is tagged with, and what can be done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedCard(
    item: SavedItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleSolid: () -> Unit
) {
    val passage = item.passage
    val solid = passage.isResolved
    val tags = passage.tags.map { it.localizedLabel() }
    Folio(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).alpha(if (solid) 0.6f else 1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = referenceOf(item),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (solid) stringResource(R.string.saved_solid_badge)
                    else passage.kind.localizedLabel() + " · " + priorityLabel(passage.priority),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AyahText(ayahId = passage.startAyahId, size = 22.sp, firstWords = SNIPPET_WORDS, maxLines = 3)
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (passage.note.isNotBlank()) {
                Text(
                    text = passage.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaperLink(text = stringResource(R.string.open_in_mushaf), onClick = onOpen)
                PaperLink(text = stringResource(R.string.peek_edit_saved), onClick = onEdit, soft = true)
                if (passage.kind == SavedKind.WEAK) {
                    PaperLink(
                        text = stringResource(if (solid) R.string.saved_reopen else R.string.saved_mark_solid),
                        onClick = onToggleSolid,
                        soft = true
                    )
                }
            }
        }
    }
}

/** How much of the first verse of a saved passage is shown in the list. */
private const val SNIPPET_WORDS = 10
