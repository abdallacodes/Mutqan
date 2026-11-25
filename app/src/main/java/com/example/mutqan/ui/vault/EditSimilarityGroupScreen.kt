package com.example.mutqan.ui.vault

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSlider
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.QuickPeekBottomSheet
import com.example.mutqan.ui.components.QuickPeekTarget
import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.TonalButton
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Makes a mutashabihat group or changes one, in the look of the group's own screen: its description, its notes and how
 * well it is known on top; then its verses in the Mushaf's script, each with its own words in colour and a cross that
 * takes it out; then the verses that look like them, to tick and add. More verses are found with the app's verse search.
 * Nothing is written until Save, at the foot; leaving with changes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSimilarityGroupScreen(
    groupId: Int?,
    folderId: Int?,
    onBack: () -> Unit,
    onOpenMushaf: (Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: EditGroupViewModel = viewModel(
        key = "edit-group-${groupId ?: "new"}",
        factory = EditGroupViewModelFactory(context, groupId, folderId)
    )
    val form by viewModel.form.collectAsState()
    val members by viewModel.members.collectAsState()
    val lookAlikes by viewModel.lookAlikes.collectAsState()
    val changed by viewModel.changed.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()

    var searching by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var peek by remember { mutableStateOf<QuickPeekTarget?>(null) }
    var chosen by remember { mutableStateOf(emptySet<Int>()) }
    var shown by rememberSaveable { mutableIntStateOf(LOOK_ALIKES_STEP) }
    // A new group's description takes the typing once, when the editor opens (not again on coming back from the search).
    var named by rememberSaveable { mutableStateOf(groupId != null) }

    val inGroup = remember(members) { members.orEmpty().mapTo(HashSet()) { it.ref.verseId } }
    val canSave = viewModel.canSave(form, inGroup.size)
    // Chosen look-alikes that came into the group (or went from the list) are chosen no more.
    val offered = remember(lookAlikes) { lookAlikes?.shown.orEmpty().mapTo(HashSet()) { it.ayahId } }
    LaunchedEffect(offered) { if (chosen.any { it !in offered }) chosen = chosen.filterTo(HashSet()) { it in offered } }

    val removedNote = stringResource(R.string.group_verse_removed)
    val undo = stringResource(R.string.group_undo)
    fun added(count: Int) {
        Toast.makeText(context, context.resources.getQuantityString(R.plurals.group_similar_added, count, count), Toast.LENGTH_SHORT).show()
    }
    fun leave() {
        if (changed) leaving = true else onBack()
    }
    // A note still showing would wait out the search and show again on return: it goes now.
    fun openSearch() {
        snackbar.currentSnackbarData?.dismiss()
        searching = true
    }

    if (searching) {
        GroupVerseSearch(
            inGroup = inGroup,
            onAdd = { ids ->
                added(viewModel.add(ids))
                searching = false
            },
            onBack = { searching = false },
            onOpenMushaf = onOpenMushaf
        )
        return
    }

    BackHandler(enabled = changed) { leaving = true }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(if (groupId == null) R.string.new_group_title else R.string.edit_group_title),
                subtitle = members?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.count_ayahs, it.size, it.size) },
                onBack = { leave() }
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                AddChosenBar(
                    count = chosen.size,
                    onAdd = {
                        added(viewModel.add(chosen))
                        chosen = emptySet()
                    },
                    onClear = { chosen = emptySet() }
                )
            } else if (form != null) {
                SaveBar(
                    enabled = canSave,
                    reason = when {
                        canSave -> null
                        form?.description.isNullOrBlank() -> stringResource(R.string.group_save_needs_name)
                        else -> stringResource(R.string.hint_add_two_verses)
                    },
                    onSave = { viewModel.save(onBack) }
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                // In the colours of a raised card, like the rest of the page.
                Snackbar(
                    snackbarData = data,
                    shape = FolioTheme.shapes.card,
                    containerColor = FolioTheme.colors.folio,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val fields = form
        val verses = members
        if (fields == null || verses == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "fields") {
                GroupFields(
                    form = fields,
                    takeTyping = !named,
                    onTyping = { named = true },
                    onDescription = viewModel::onDescriptionChange,
                    onNotes = viewModel::onNotesChange,
                    onQuality = viewModel::onQualityChange
                )
            }

            // ── The group's verses ──
            item(key = "verses-head") {
                SectionHead(
                    title = stringResource(R.string.group_members_title),
                    actionLabel = stringResource(R.string.group_add_verses),
                    onAction = ::openSearch
                )
            }
            // With fewer than two verses, the line over Save says what is missing.
            if (verses.size >= EditGroupViewModel.MIN_VERSES) {
                item(key = "verses-hint") {
                    Text(
                        text = stringResource(R.string.group_own_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 4.dp)
                    )
                }
            }
            items(verses, key = { "m-${it.ref.verseId}" }) { member ->
                val ref = member.ref
                GroupVerseRow(
                    ref = ref,
                    own = member.own,
                    onClick = { peek = QuickPeekTarget(verseId = ref.verseId, surahId = ref.surahId, ayahNumber = ref.ayahNumber, pageNumber = ref.pageNumber) },
                    trailing = {
                        IconButton(onClick = {
                            viewModel.remove(ref.verseId)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                if (snackbar.showSnackbar(removedNote, actionLabel = undo, withDismissAction = false) == SnackbarResult.ActionPerformed) {
                                    viewModel.add(listOf(ref.verseId))
                                }
                            }
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_remove_from_group),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            if (verses.size < EditGroupViewModel.MIN_VERSES) {
                item(key = "find") {
                    TonalButton(
                        text = stringResource(R.string.group_find_verse),
                        onClick = ::openSearch,
                        icon = Icons.Filled.Search,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
                    )
                }
            }

            // ── The verses that look like them ──
            if (verses.isNotEmpty()) {
                item(key = "similar-head") { SectionHead(title = stringResource(R.string.group_similar_title)) }
                val found = lookAlikes
                item(key = "similar-hint") {
                    Text(
                        text = stringResource(
                            when {
                                found == null -> R.string.group_similar_loading
                                found.shown.isEmpty() -> R.string.group_similar_none
                                else -> R.string.group_similar_hint
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                    )
                }
                if (found != null) {
                    items(found.shown.take(shown), key = { "s-${it.ayahId}" }) { s ->
                        LookAlikeRow(
                            lookAlike = s,
                            on = s.ayahId in chosen,
                            onToggle = { chosen = if (s.ayahId in chosen) chosen - s.ayahId else chosen + s.ayahId },
                            onPeek = { peek = QuickPeekTarget(verseId = s.ayahId, surahId = s.surahId, ayahNumber = s.ayahNumber, pageNumber = s.page) }
                        )
                    }
                    val hidden = found.shown.size - shown
                    if (hidden > 0) {
                        item(key = "similar-more") {
                            PaperLink(
                                text = pluralStringResource(R.plurals.group_similar_show_more, minOf(hidden, LOOK_ALIKES_STEP), minOf(hidden, LOOK_ALIKES_STEP)),
                                onClick = { shown += LOOK_ALIKES_STEP },
                                modifier = Modifier.padding(horizontal = PaperSpace.Gutter - 12.dp, vertical = 4.dp)
                            )
                        }
                    } else if (found.more > 0) {
                        item(key = "similar-rest") {
                            Text(
                                text = pluralStringResource(R.plurals.group_similar_more, found.more, found.more),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (leaving) {
        PaperDialog(
            onDismissRequest = { leaving = false },
            title = { Text(stringResource(R.string.group_leave_title)) },
            text = { Text(stringResource(if (canSave) R.string.group_leave_text else R.string.group_leave_text_unsaveable)) },
            confirmButton = {
                if (canSave) PaperLink(text = stringResource(R.string.btn_save), onClick = { leaving = false; viewModel.save(onBack) })
                else PaperLink(text = stringResource(R.string.group_keep_editing), onClick = { leaving = false })
            },
            dismissButton = { PaperLink(text = stringResource(R.string.group_leave_discard), onClick = { leaving = false; onBack() }, soft = true) }
        )
    }

    peek?.let { target ->
        QuickPeekBottomSheet(target = target, onDismiss = { peek = null }, onOpenMushaf = onOpenMushaf)
    }
}

/** Look-alikes shown at first, and how many more each "show more" adds. */
private const val LOOK_ALIKES_STEP = 5

/**
 * The group's description and notes, and how well it is known (a slider). With [takeTyping] the description takes the
 * typing as it shows (a new group), then [onTyping] says it did.
 */
@Composable
private fun GroupFields(
    form: GroupForm,
    takeTyping: Boolean,
    onTyping: () -> Unit,
    onDescription: (String) -> Unit,
    onNotes: (String) -> Unit,
    onQuality: (Float) -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (takeTyping) {
            focus.requestFocus()
            onTyping()
        }
    }
    val masteryLabel = stringResource(R.string.section_mastery)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = form.description,
            onValueChange = onDescription,
            label = { Text(stringResource(R.string.section_group_desc)) },
            placeholder = { Text(stringResource(R.string.group_desc_placeholder)) },
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = onNotes,
            label = { Text(stringResource(R.string.section_memorization_notes)) },
            placeholder = { Text(stringResource(R.string.placeholder_memorization_notes)) },
            minLines = 3,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = masteryLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.percent_value, (form.quality * 100).roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            PaperSlider(
                value = form.quality,
                onValueChange = onQuality,
                valueRange = 0.1f..1f,
                steps = 8,
                modifier = Modifier.semantics { contentDescription = masteryLabel }
            )
        }
    }
}

/** At the foot: Save, and, while it cannot be pressed, the reason ([reason]). */
@Composable
private fun SaveBar(enabled: Boolean, reason: String?, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = PaperSpace.Gutter, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (reason != null) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PaperButton(text = stringResource(R.string.btn_save), onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
    }
}
