package com.example.mutqan.ui.lists

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.ListPassage
import com.example.mutqan.domain.VerseListDetail
import com.example.mutqan.domain.VerseLists
import com.example.mutqan.domain.VerseListsRepository
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperStepper
import com.example.mutqan.ui.components.FolioSheetInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** A list as its screen sees it: [loaded] is false until it is read; then a null [list] means it is gone. */
data class ListScreenState(val loaded: Boolean = false, val list: VerseListDetail? = null)

class VerseListViewModel(context: Context, private val listId: Int) : ViewModel() {
    private val repo = VerseListsRepository.create(context.applicationContext)

    val state: StateFlow<ListScreenState> = repo.list(listId).map { ListScreenState(true, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListScreenState())

    /** The names of the other lists, which this one cannot be renamed to. */
    val takenKeys: StateFlow<Set<String>> = repo.lists()
        .map { all -> all.filter { it.id != listId }.mapTo(HashSet()) { VerseLists.nameKey(it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun edit(name: String, note: String) {
        viewModelScope.launch { repo.edit(listId, name, note) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(listId)
            onDone()
        }
    }

    fun editPassage(old: ListPassage, start: Int, end: Int, note: String) {
        viewModelScope.launch { repo.editPassage(listId, old, start, end, note) }
    }

    fun remove(passage: ListPassage) {
        viewModelScope.launch { repo.remove(listId, passage.start, passage.end) }
    }
}

class VerseListViewModelFactory(private val context: Context, private val listId: Int) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = VerseListViewModel(context, listId) as T
}

/** Words of a passage shown in the list; the Mushaf shows the rest. */
private const val PASSAGE_WORDS = 40

/**
 * One of the user's lists: its note, a way to add verses (Search, picking), and each passage in the Mushaf's script
 * with where it is and the user's note. A passage opens the Mushaf on its first verse; its pencil changes its verses
 * or note, or takes it out. The menu renames, shares the verses as text, or deletes the list.
 */
@Composable
fun VerseListScreen(
    listId: Int,
    onBack: () -> Unit,
    onOpenMushaf: (page: Int, ayahId: Int) -> Unit,
    onAddVerses: (listId: Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: VerseListViewModel = viewModel(key = "verse-list-$listId", factory = VerseListViewModelFactory(context, listId))
    val state by viewModel.state.collectAsState()
    val takenKeys by viewModel.takenKeys.collectAsState()
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var passage by remember { mutableStateOf<ListPassage?>(null) }
    val deletedNote = stringResource(R.string.list_deleted)

    // Deleted (here or by a sync): nothing is left to show.
    LaunchedEffect(state) { if (state.loaded && state.list == null) onBack() }
    val list = state.list

    Scaffold(
        topBar = {
            PaperHeader(
                title = list?.name.orEmpty(),
                subtitle = list?.takeIf { it.passages.isNotEmpty() }?.let {
                    stringResource(
                        R.string.list_summary,
                        pluralStringResource(R.plurals.count_ayahs, it.verses, it.verses),
                        pluralStringResource(R.plurals.list_passages, it.passages.size, it.passages.size)
                    )
                },
                onBack = onBack,
                actions = {
                    if (list != null) {
                        ListMenu(
                            canShare = list.passages.isNotEmpty(),
                            onEdit = { editing = true },
                            onShare = { scope.launch { share(context, list) } },
                            onDelete = { deleting = true }
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (list == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (list.note.isNotBlank()) {
                item(key = "note") {
                    Text(
                        text = list.note,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 4.dp)
                    )
                }
            }
            item(key = "add") {
                PaperOutlinedButton(
                    text = stringResource(R.string.list_add_verses),
                    onClick = { onAddVerses(list.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                )
            }
            if (list.passages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
                    )
                }
            }
            item(key = "top-rule") { Hairline(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) }
            items(list.passages, key = { "${it.start}-${it.end}" }) { p ->
                val page = index.firstPageOf(p.start)
                PassageRow(
                    passage = p,
                    page = page,
                    onOpen = { onOpenMushaf(page, p.start) },
                    onEdit = { passage = p }
                )
            }
        }
    }

    if (editing && list != null) {
        ListNameDialog(
            title = stringResource(R.string.list_edit_title),
            initialName = list.name,
            initialNote = list.note,
            takenKeys = takenKeys,
            onDismiss = { editing = false },
            onSave = { name, note ->
                editing = false
                viewModel.edit(name, note)
            }
        )
    }

    if (deleting && list != null) {
        PaperDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.list_delete_title)) },
            text = { Text(stringResource(R.string.list_delete_body, list.name)) },
            confirmButton = {
                PaperLink(text = stringResource(R.string.btn_delete), onClick = {
                    deleting = false
                    viewModel.delete { Toast.makeText(context, deletedNote, Toast.LENGTH_SHORT).show() }
                })
            },
            dismissButton = { PaperLink(text = stringResource(R.string.btn_cancel), onClick = { deleting = false }, soft = true) }
        )
    }

    passage?.let { p ->
        PassageSheet(
            passage = p,
            onSave = { start, end, note ->
                passage = null
                viewModel.editPassage(p, start, end, note)
            },
            onRemove = {
                passage = null
                viewModel.remove(p)
            },
            onDismiss = { passage = null }
        )
    }
}

@Composable
private fun ListMenu(canShare: Boolean, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = MaterialTheme.colorScheme.onBackground)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.list_menu_edit)) }, onClick = { open = false; onEdit() })
            if (canShare) DropdownMenuItem(text = { Text(stringResource(R.string.list_menu_share)) }, onClick = { open = false; onShare() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.list_menu_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

/** A passage: where it is and its page, its words, and the user's note; the pencil edits it. */
@Composable
private fun PassageRow(passage: ListPassage, page: Int, onOpen: () -> Unit, onEdit: () -> Unit) {
    val openLabel = stringResource(R.string.list_open_page, page)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = passageRef(passage.start, passage.end) + " · " + stringResource(R.string.page_n, page),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                PassageText(passage.start, passage.end, most = PASSAGE_WORDS)
                if (passage.note.isNotBlank()) {
                    Text(
                        text = passage.note,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.list_passage_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/**
 * Changes a passage: the verse it starts and ends at (within the surahs it is in), and its note; or takes it out of
 * the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassageSheet(
    passage: ListPassage,
    onSave: (start: Int, end: Int, note: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    var start by remember(passage) { mutableIntStateOf(passage.start) }
    var end by remember(passage) { mutableIntStateOf(passage.end) }
    var note by remember(passage) { mutableStateOf(passage.note) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = FolioSheetInsets,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = passageRef(start, end), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            VerseStepper(
                label = stringResource(R.string.list_passage_from),
                ayah = index.ayahOf(start),
                // Within the surah it starts in, and not after the end.
                min = 1,
                max = if (index.surahOf(start) == index.surahOf(end)) index.ayahOf(end) else index.ayahOf(lastOfSurah(index, start)),
                onChange = { start = firstOfSurah(index, start) + it - 1 }
            )
            VerseStepper(
                label = stringResource(R.string.list_passage_to),
                ayah = index.ayahOf(end),
                min = if (index.surahOf(start) == index.surahOf(end)) index.ayahOf(start) else 1,
                max = index.ayahOf(lastOfSurah(index, end)),
                onChange = { end = firstOfSurah(index, end) + it - 1 }
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.list_note_label)) },
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth()
            )
            PaperButton(
                text = stringResource(R.string.btn_save),
                onClick = { onSave(start, end, note) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            PaperLink(text = stringResource(R.string.list_passage_remove), onClick = onRemove, soft = true)
        }
    }
}

@Composable
private fun VerseStepper(label: String, ayah: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        PaperStepper(value = ayah, onChange = onChange, min = min, max = max)
    }
}

/** Sends the list's verses as text: its name and note, then each passage with its verse numbers, where it is, and the note. */
private suspend fun share(context: Context, list: VerseListDetail) {
    val text = withContext(Dispatchers.IO) {
        val quran = QuranRepository.getInstance(context)
        val numbers = NumberFormat.getInstance(context.resources.configuration.locales[0])
        buildString {
            append(list.name)
            if (list.note.isNotBlank()) append('\n').append(list.note)
            for (p in list.passages) {
                val verses = (p.start..p.end).mapNotNull { quran.ayahById(it) }
                val body = if (verses.size == 1) verses[0].text
                else verses.joinToString(" ") { it.text + " (" + numbers.format(it.ayah.toLong()) + ")" }
                append("\n\n").append(context.getString(R.string.list_share_passage, body, passageRef(context, p.start, p.end)))
                if (p.note.isNotBlank()) append('\n').append(p.note)
            }
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, list.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.list_share_title)))
}
