package com.example.qmemo.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.dao.MemberVerseRef
import com.example.qmemo.data.local.entity.VerseEntity
import com.example.qmemo.ui.components.QuickPeekBottomSheet
import com.example.qmemo.ui.components.QuickPeekTarget
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.theme.AmiriFontFamily
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSimilarityGroupScreen(
    groupId: Int?,
    folderId: Int?,
    onBack: () -> Unit,
    onOpenMushaf: (Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: EditGroupViewModel = viewModel(factory = EditGroupViewModelFactory(context))

    LaunchedEffect(groupId, folderId) {
        if (groupId != null) {
            viewModel.loadGroup(groupId)
        } else {
            viewModel.setInitialFolder(folderId)
        }
    }

    val uiState          by viewModel.uiState.collectAsState()
    val members          by viewModel.members.collectAsState()
    val searchResults    by viewModel.textSearchResults.collectAsState()
    val snackbarHost      = remember { SnackbarHostState() }

    // Peek state — non-null means the sheet is open for that verse
    var peekTarget by remember { mutableStateOf<QuickPeekTarget?>(null) }

    // TopAppBar save is unlocked when description + 2 linked verses are present
    val canSave = uiState.description.isNotBlank() && members.size >= 2
    // Add button is unlocked when a surah is selected and ayah is filled
    val canAdd  = uiState.selectedSurahId != null && uiState.ayahInput.isNotBlank()
    // Show search results below the search field when query ≥ 2 chars
    val showSearchResults = uiState.textSearchQuery.length >= 2

    val addResult = uiState.addVerseResult
    LaunchedEffect(addResult) {
        val msg = when (addResult) {
            is AddVerseResult.InvalidRef          -> context.getString(R.string.snackbar_no_verse)
            is AddVerseResult.AlreadyMember       -> context.getString(R.string.snackbar_already_member)
            is AddVerseResult.DescriptionRequired -> context.getString(R.string.snackbar_desc_required)
            is AddVerseResult.Added               -> "Verse added"
            else -> null
        }
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            viewModel.clearAddResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Text(
                        text          = if (groupId == null) stringResource(R.string.new_group_title)
                                        else stringResource(R.string.edit_group_title),
                        style         = MaterialTheme.typography.titleMedium,
                        fontWeight    = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color         = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick  = { viewModel.saveGroup(); onBack() },
                        enabled  = canSave
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = stringResource(R.string.cd_save_group),
                            tint = if (canSave) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Group description ──────────────────────────────────────────────
            item {
                SectionLabel(stringResource(R.string.section_group_desc))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text(stringResource(R.string.group_desc_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine    = false,
                    maxLines      = 3,
                    shape         = RoundedCornerShape(8.dp),
                    colors        = fieldColors()
                )
            }

            // ── Memorization notes ─────────────────────────────────────────────
            item {
                SectionLabel(stringResource(R.string.section_memorization_notes))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = uiState.memorizationNotes,
                    onValueChange = viewModel::onMemorizationNotesChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = {
                        Text(
                            stringResource(R.string.placeholder_memorization_notes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine    = false,
                    minLines      = 3,
                    maxLines      = 8,
                    shape         = RoundedCornerShape(8.dp),
                    colors        = fieldColors()
                )
            }

            // ── Mastery strength ───────────────────────────────────────────────
            item {
                SectionLabel(stringResource(R.string.section_mastery))
                Spacer(Modifier.height(6.dp))
                EditQualitySlider(
                    quality = uiState.masterQuality,
                    onSelect = viewModel::onQualityChange
                )
            }

            // ── Divider + Link a Verse section ─────────────────────────────────
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(R.string.section_link_verse))
                Spacer(Modifier.height(6.dp))
            }

            // ── Surah dropdown + Ayah + Add ────────────────────────────────────
            item {
                VersePicker(
                    selectedSurahId   = uiState.selectedSurahId,
                    surahFilterQuery  = uiState.surahFilterQuery,
                    ayahInput         = uiState.ayahInput,
                    textSearchQuery   = uiState.textSearchQuery,
                    isAyahError       = uiState.addVerseResult is AddVerseResult.InvalidRef,
                    canAdd            = canAdd,
                    onSurahFilterChange = viewModel::onSurahFilterChange,
                    onSurahSelected   = viewModel::onSurahSelected,
                    onAyahChange      = viewModel::onAyahInputChange,
                    onTextSearchChange = viewModel::onTextSearchChange,
                    onAdd             = viewModel::addVerse
                )
            }

            // ── Search Filters (Surah & Juz) ───────────────────────────────────
            item {
                SearchFiltersSection(
                    selectedSurahId = uiState.searchFilterSurahId,
                    juzStart = uiState.searchFilterJuzStart,
                    juzEnd = uiState.searchFilterJuzEnd,
                    onSurahSelect = viewModel::onSearchFilterSurahSelected,
                    onJuzRangeChange = viewModel::onSearchFilterJuzRangeChange
                )
            }

            // ── Arabic text search results ─────────────────────────────────────
            if (showSearchResults) {
                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            "No results found with current filters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(searchResults, key = { "search_${it.id}" }) { verse ->
                        val isAlreadyMember = members.any { it.verseId == verse.id }
                        SearchResultRow(
                            verse  = verse,
                            isAdded = isAlreadyMember,
                            onAdd = { viewModel.onVerseSearchAdd(verse) },
                            onPeek = {
                                peekTarget = QuickPeekTarget(
                                    verseId = verse.id,
                                    surahId = verse.surahId,
                                    ayahNumber = verse.ayahNumber,
                                    pageNumber = verse.pageNumber
                                )
                            }
                        )
                    }
                }
            }

            // ── 2-verse validation hint ────────────────────────────────────────
            item {
                val hintText  = if (members.size >= 2)
                    stringResource(R.string.hint_group_ready)
                else
                    stringResource(R.string.hint_add_two_verses)
                val hintColor = if (members.size >= 2) DifficultySmooth
                                else MaterialTheme.colorScheme.onSurfaceVariant

                Text(
                    text      = hintText,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = hintColor,
                    modifier  = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            // ── Linked verses list ─────────────────────────────────────────────
            if (members.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel(stringResource(R.string.linked_verses_header, members.size))
                    Spacer(Modifier.height(6.dp))
                }
                items(members, key = { it.verseId }) { member ->
                    MemberRow(
                        member   = member,
                        onRemove = { viewModel.removeMember(member) },
                        onPeek   = {
                            peekTarget = QuickPeekTarget(
                                verseId     = member.verseId,
                                surahId     = member.surahId,
                                ayahNumber  = member.ayahNumber,
                                pageNumber  = member.pageNumber
                            )
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Quick Peek sheet ───────────────────────────────────────────────────────
    peekTarget?.let { target ->
        QuickPeekBottomSheet(
            target    = target,
            onDismiss = { peekTarget = null },
            onOpenMushaf = onOpenMushaf
        )
    }
}

// ── Search Filters Section ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSection(
    selectedSurahId: Int?,
    juzStart: Int?,
    juzEnd: Int?,
    onSurahSelect: (Int?) -> Unit,
    onJuzRangeChange: (Int?, Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Track active mode explicitly (0=Surah, 1=Juz Range)
    var filterMode by remember { 
        mutableIntStateOf(if (juzStart != null || juzEnd != null) 1 else 0) 
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp)
        ) {
            Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("SEARCH FILTERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "HIDE" else "SHOW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))

            // ── Mode Toggle ──────────────────────────────────────────────────
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                SegmentedButton(
                    selected = filterMode == 0,
                    onClick = { 
                        filterMode = 0
                        onSurahSelect(null) 
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Surah", style = MaterialTheme.typography.labelLarge)
                }
                SegmentedButton(
                    selected = filterMode == 1,
                    onClick = { 
                        filterMode = 1
                        onJuzRangeChange(null, null) 
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Juz Range", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (filterMode == 0) {
                // Surah Filter
                var surahExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = surahExpanded,
                    onExpandedChange = { surahExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSurahId?.let { "${it}. ${SurahData.nameOf(it)}" } ?: stringResource(R.string.all_surahs),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        label = { Text(stringResource(R.string.label_filter_surah)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = surahExpanded) },
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors()
                    )
                    ExposedDropdownMenu(expanded = surahExpanded, onDismissRequest = { surahExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all_surahs)) },
                            onClick = { onSurahSelect(null); surahExpanded = false }
                        )
                        SurahData.ALL.forEach { info ->
                            DropdownMenuItem(
                                text = { Text("${info.id}. ${info.getDisplayName()}") },
                                onClick = { onSurahSelect(info.id); surahExpanded = false }
                            )
                        }
                    }
                }
            } else {
                // Juz Range
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = juzStart?.toString() ?: "",
                        onValueChange = { val v = it.filter(Char::isDigit).toIntOrNull(); if (v == null || v in 1..30) onJuzRangeChange(v, juzEnd) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.label_juz_start)) },
                        placeholder = { Text("1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = juzEnd?.toString() ?: "",
                        onValueChange = { val v = it.filter(Char::isDigit).toIntOrNull(); if (v == null || v in 1..30) onJuzRangeChange(juzStart, v) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.label_juz_end)) },
                        placeholder = { Text("30") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors()
                    )
                }
            }
        }
    }
}

// ── Verse picker (Surah dropdown + Ayah + Add + Text search) ──────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersePicker(
    selectedSurahId: Int?,
    surahFilterQuery: String,
    ayahInput: String,
    textSearchQuery: String,
    isAyahError: Boolean,
    canAdd: Boolean,
    onSurahFilterChange: (String) -> Unit,
    onSurahSelected: (Int) -> Unit,
    onAyahChange: (String) -> Unit,
    onTextSearchChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // ── Row 1: Surah dropdown (full width) ─────────────────────────────────
        var surahExpanded by remember { mutableStateOf(false) }

        val surahDisplayText = when {
            surahFilterQuery.isNotEmpty() -> surahFilterQuery
            selectedSurahId != null ->
                SurahData.getById(selectedSurahId)
                    ?.let { "${it.id}. ${it.getDisplayName()}" } ?: ""
            else -> ""
        }

        val filteredSurahs = remember(surahFilterQuery) {
            if (surahFilterQuery.isBlank()) SurahData.ALL
            else SurahData.ALL.filter { info ->
                info.getDisplayName().contains(surahFilterQuery, ignoreCase = true) ||
                info.nameArabic.contains(surahFilterQuery) ||
                info.id.toString() == surahFilterQuery.trim()
            }
        }

        ExposedDropdownMenuBox(
            expanded          = surahExpanded,
            onExpandedChange  = { surahExpanded = it }
        ) {
            OutlinedTextField(
                value         = surahDisplayText,
                onValueChange = {
                    onSurahFilterChange(it)
                    surahExpanded = true
                },
                modifier      = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                label         = { Text(stringResource(R.string.label_surah)) },
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = surahExpanded) },
                singleLine    = true,
                shape         = RoundedCornerShape(8.dp),
                colors        = fieldColors()
            )
            ExposedDropdownMenu(
                expanded          = surahExpanded,
                onDismissRequest  = { surahExpanded = false }
            ) {
                filteredSurahs.forEach { info ->
                    DropdownMenuItem(
                        text    = { Text("${info.id}. ${info.getDisplayName()}") },
                        onClick = {
                            onSurahSelected(info.id)
                            surahExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // ── Row 2: Ayah number field + Add button ──────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value           = ayahInput,
                onValueChange   = onAyahChange,
                modifier        = Modifier.weight(1f),
                label           = { Text(stringResource(R.string.label_ayah)) },
                placeholder     = { Text(stringResource(R.string.placeholder_ayah_range), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                isError         = isAyahError,
                supportingText  = if (isAyahError) {
                    { Text(stringResource(R.string.error_invalid_ref), color = DifficultyCritical, style = MaterialTheme.typography.labelSmall) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine      = true,
                shape           = RoundedCornerShape(8.dp),
                colors          = fieldColors()
            )
            Button(
                onClick  = onAdd,
                modifier = Modifier
                    .height(56.dp)
                    .width(80.dp),
                shape    = RoundedCornerShape(8.dp),
                enabled  = canAdd,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    contentColor           = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    stringResource(R.string.btn_add),
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontSize      = 12.sp
                )
            }
        }

        // ── Row 3: Arabic text search field ────────────────────────────────────
        Spacer(Modifier.height(2.dp))
        SectionLabel(stringResource(R.string.section_search_by_text))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = textSearchQuery,
            onValueChange = onTextSearchChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = {
                Text(
                    stringResource(R.string.placeholder_search_arabic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon   = {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(18.dp)
                )
            },
            singleLine    = true,
            shape         = RoundedCornerShape(8.dp),
            colors        = fieldColors()
        )
    }
}

// ── Search result row ─────────────────────────────────────────────────────────

@Composable
private fun SearchResultRow(
    verse: VerseEntity,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onPeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onPeek) // Peek on whole row tap
            .then(
                Modifier.padding(0.dp) // keep border visible
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Text(
                text       = "${verse.surahId}. ${SurahData.nameOf(verse.surahId)}  ·  ${verse.ayahNumber}",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = verse.textArabic.let { t ->
                    if (t.length > 80) t.take(80) + "…" else t
                },
                style    = MaterialTheme.typography.bodySmall.copy(
                    textDirection = TextDirection.Rtl,
                    fontFamily    = AmiriFontFamily,
                    lineHeight    = 20.sp
                ),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
        
        IconButton(onClick = onPeek) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onAdd, enabled = !isAdded) {
            Icon(
                imageVector        = Icons.Default.Check,
                contentDescription = stringResource(R.string.btn_add),
                tint               = if (isAdded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ── Member row ────────────────────────────────────────────────────────────────

@Composable
private fun MemberRow(
    member: MemberVerseRef,
    onRemove: () -> Unit,
    onPeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text       = "${member.surahId}. ${SurahData.nameOf(member.surahId)}  ·  ${stringResource(R.string.ayah_n, member.ayahNumber)}",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = stringResource(R.string.label_page, member.pageNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Peek button — opens Quick Peek sheet for this verse
        IconButton(onClick = onPeek) {
            Icon(
                imageVector        = Icons.Default.Visibility,
                contentDescription = stringResource(R.string.cd_peek_verse),
                tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier           = Modifier.size(18.dp)
            )
        }
        // Delete button
        IconButton(onClick = onRemove) {
            Icon(
                imageVector        = Icons.Default.Delete,
                contentDescription = stringResource(R.string.cd_delete_verse),
                tint               = MaterialTheme.colorScheme.error,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ── Strength segmented control ────────────────────────────────────────────────

@Composable
private fun EditQualitySlider(quality: Float, onSelect: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.section_mastery),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(quality * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = quality,
            onValueChange = onSelect,
            valueRange = 0.1f..1f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelSmall,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
    focusedLabelColor       = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor             = MaterialTheme.colorScheme.primary,
    focusedTextColor        = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
    errorBorderColor        = DifficultyCritical,
    errorLabelColor         = DifficultyCritical,
    errorTextColor          = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor   = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor     = MaterialTheme.colorScheme.surface
)

private fun strengthColor(s: MasterStrength): Color = when (s) {
    MasterStrength.WEAK   -> DifficultyCritical
    MasterStrength.STABLE -> DifficultyStruggled
    MasterStrength.SOLID  -> DifficultySmooth
}
