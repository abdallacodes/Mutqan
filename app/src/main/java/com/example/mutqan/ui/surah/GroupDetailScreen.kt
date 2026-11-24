package com.example.mutqan.ui.surah

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.dao.MemberVerseRef
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.QuickPeekBottomSheet
import com.example.mutqan.ui.components.QuickPeekTarget
import com.example.mutqan.ui.components.SectionHead
import kotlinx.coroutines.launch

/**
 * One of the user's mutashabihat groups: its notes, its verses in the Mushaf's script with the words they share in
 * colour (split into this surah and the others when opened from a surah), and the verses that look like them and are
 * not in it yet, from the ready lists and from verses that share many words with them, to choose and add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: Int,
    currentSurahId: Int,
    onBack: () -> Unit,
    onOpenMushaf: (Int) -> Unit,
    onEditGroup: ((Int) -> Unit)? = null,
    onStartTest: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: GroupDetailViewModel = viewModel(
        key = "group-$groupId-$currentSurahId",
        factory = GroupDetailViewModelFactory(context, groupId, currentSurahId)
    )
    val group by viewModel.group.collectAsState()
    val members by viewModel.members.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val scope = rememberCoroutineScope()

    val inSurah = currentSurahId > 0
    var peek by remember { mutableStateOf<QuickPeekTarget?>(null) }
    var chosen by remember { mutableStateOf(emptySet<Int>()) }
    // Chosen ones that came into the group (or went from the list) are chosen no more.
    val offered = remember(suggestions) { suggestions?.shown.orEmpty().map { it.ayahId }.toSet() }
    LaunchedEffect(offered) { if (chosen.any { it !in offered }) chosen = chosen.filterTo(HashSet()) { it in offered } }

    val inSurahTitle = if (inSurah) stringResource(R.string.group_in_surah, SurahData.nameOf(currentSurahId)) else ""
    val otherSurahsTitle = stringResource(R.string.group_in_other_surahs)
    val membersTitle = stringResource(R.string.group_members_title)
    val emptyTexts = listOf(
        stringResource(if (inSurah) R.string.no_context_verses else R.string.no_linked_verses),
        stringResource(R.string.no_clash_verses)
    )

    fun peekAt(ayahId: Int, surahId: Int, ayah: Int, page: Int) {
        peek = QuickPeekTarget(verseId = ayahId, surahId = surahId, ayahNumber = ayah, pageNumber = page)
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = group?.description ?: stringResource(R.string.group_detail_fallback_title),
                subtitle = members?.let { pluralStringResource(R.plurals.count_ayahs, it.size, it.size) },
                onBack = onBack,
                actions = {
                    if (onStartTest != null && (members?.size ?: 0) >= 2) {
                        IconButton(onClick = { onStartTest(groupId) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_start_test), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (onEditGroup != null) {
                        IconButton(onClick = { onEditGroup(groupId) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit_group), tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                AddBar(
                    count = chosen.size,
                    onAdd = {
                        val picked = chosen.toList()
                        scope.launch {
                            val added = viewModel.add(picked)
                            chosen = emptySet()
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(R.plurals.group_similar_added, added, added),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onClear = { chosen = emptySet() }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            val notes = group?.memorizationNotes.orEmpty()
            if (notes.isNotBlank()) {
                item(key = "notes") {
                    Folio(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.group_notes_detail_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val list = members
            if (list != null) {
                val parts = if (inSurah) {
                    listOf(
                        inSurahTitle to list.filter { it.ref.surahId == currentSurahId },
                        otherSurahsTitle to list.filter { it.ref.surahId != currentSurahId }
                    )
                } else {
                    listOf(membersTitle to list)
                }
                parts.forEachIndexed { i, (title, verses) ->
                    item(key = "head-$i") { SectionHead(title = title, first = i == 0 && notes.isBlank()) }
                    if (verses.isEmpty()) {
                        item(key = "empty-$i") {
                            Text(
                                text = emptyTexts[i],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
                            )
                        }
                    }
                    items(verses, key = { "m-${it.ref.verseId}" }) { member ->
                        MemberRow(member.ref, member.shared) {
                            peekAt(member.ref.verseId, member.ref.surahId, member.ref.ayahNumber, member.ref.pageNumber)
                        }
                    }
                }
            }

            item(key = "similar-head") { SectionHead(title = stringResource(R.string.group_similar_title)) }
            val found = suggestions
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
                items(found.shown, key = { "s-${it.ayahId}" }) { s ->
                    SuggestionRow(
                        suggestion = s,
                        on = s.ayahId in chosen,
                        onToggle = { chosen = if (s.ayahId in chosen) chosen - s.ayahId else chosen + s.ayahId },
                        onPeek = { peekAt(s.ayahId, s.surahId, s.ayahNumber, s.page) }
                    )
                }
                if (found.more > 0) {
                    item(key = "similar-more") {
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

    peek?.let { target ->
        QuickPeekBottomSheet(target = target, onDismiss = { peek = null }, onOpenMushaf = onOpenMushaf)
    }
}

/** Where a verse is: its surah and number, and its page. */
@Composable
private fun verseRef(surahId: Int, ayah: Int, page: Int): String =
    stringResource(R.string.saved_ref, SurahData.nameOf(surahId), ayah) + " · " + stringResource(R.string.page_n, page)

/** A verse of the group: where it is, and its words with those the other verses share in colour; tapping shows it. */
@Composable
private fun MemberRow(ref: MemberVerseRef, shared: List<IntRange>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = verseRef(ref.surahId, ref.ayahNumber, ref.pageNumber),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary
            )
            AyahText(ayahId = ref.verseId, size = 22.sp, marked = shared, markColor = MaterialTheme.colorScheme.primary)
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/** A look-alike not in the group: chosen or not (the row is the switch), the words it shares in colour, and an eye to see it. */
@Composable
private fun SuggestionRow(suggestion: GroupDetailViewModel.Suggestion, on: Boolean, onToggle: () -> Unit, onPeek: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp).size(24.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = verseRef(suggestion.surahId, suggestion.ayahNumber, suggestion.page),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                AyahText(
                    ayahId = suggestion.ayahId,
                    size = 22.sp,
                    color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    marked = suggestion.shared,
                    markColor = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onPeek) {
                Icon(Icons.Outlined.Visibility, contentDescription = stringResource(R.string.cd_preview_verse), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/** At the foot while look-alikes are chosen: the button that adds them, and a link that clears the choice. */
@Composable
private fun AddBar(count: Int, onAdd: () -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = PaperSpace.Gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperButton(
                text = pluralStringResource(R.plurals.group_similar_add, count, count),
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            )
            PaperLink(text = stringResource(R.string.group_similar_clear), onClick = onClear, soft = true)
        }
    }
}
