package com.example.qmemo.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.entity.VerseEntity
import com.example.qmemo.ui.components.HelpDialog
import com.example.qmemo.ui.components.TopBarOverflowMenu
import com.example.qmemo.ui.components.QuickPeekBottomSheet
import com.example.qmemo.ui.components.QuickPeekTarget
import com.example.qmemo.ui.theme.AmiriFontFamily
import com.example.qmemo.ui.theme.DifficultyCritical

private val YellowAccent = Color(0xFFEDBB4A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseSearchScreen(
    onOpenMushaf: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: VerseSearchViewModel = viewModel(factory = VerseSearchViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    
    var showHelpDialog by remember { mutableStateOf(false) }
    var peekTarget by remember { mutableStateOf<QuickPeekTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_search),
                        fontWeight = FontWeight.Black
                    )
                },
                actions = {
                    TopBarOverflowMenu(
                        onHelpClick = { showHelpDialog = true },
                        onSettingsClick = onSettingsClick
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            // ── Search Field ─────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.placeholder_search_arabic)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = searchFieldColors()
            )

            // ── Filters ──────────────────────────────────────────────────
            SearchFiltersSection(
                selectedSurahId = uiState.filterSurahId,
                juzStart = uiState.filterJuzStart,
                juzEnd = uiState.filterJuzEnd,
                onSurahSelect = viewModel::onSurahFilterSelect,
                onJuzRangeChange = viewModel::onJuzRangeChange,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Random Ayah ──────────────────────────────────────────────
            RandomAyahSection(
                scope = uiState.randomScope,
                selectedJuz = uiState.randomJuz,
                selectedSurahId = uiState.randomSurahId,
                pageStart = uiState.randomPageStart,
                pageEnd = uiState.randomPageEnd,
                onScopeChange = viewModel::onRandomScopeChange,
                onJuzChange = viewModel::onRandomJuzChange,
                onSurahChange = viewModel::onRandomSurahChange,
                onPageRangeChange = viewModel::onRandomPageRangeChange,
                onGenerate = { 
                    viewModel.generateRandomAyah { verse ->
                        peekTarget = QuickPeekTarget(
                            verseId = verse.id,
                            surahId = verse.surahId,
                            ayahNumber = verse.ayahNumber,
                            pageNumber = verse.pageNumber
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Results ──────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.query.length >= 2) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(results, key = { it.id }) { verse ->
                            SearchResultCard(
                                verse = verse,
                                onPeek = {
                                    peekTarget = QuickPeekTarget(
                                        verseId = verse.id,
                                        surahId = verse.surahId,
                                        ayahNumber = verse.ayahNumber,
                                        pageNumber = verse.pageNumber
                                    )
                                },
                                onOpenMushaf = { onOpenMushaf(verse.pageNumber) }
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        if (showHelpDialog) {
            HelpDialog(
                title = stringResource(R.string.search_help_title),
                description = stringResource(R.string.search_help_desc),
                onDismiss = { showHelpDialog = false }
            )
        }

        peekTarget?.let { target ->
            QuickPeekBottomSheet(
                target = target,
                onDismiss = { peekTarget = null },
                onOpenMushaf = onOpenMushaf
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSection(
    selectedSurahId: Int?,
    juzStart: Int?,
    juzEnd: Int?,
    onSurahSelect: (Int?) -> Unit,
    onJuzRangeChange: (Int?, Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.label_filters), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) stringResource(R.string.label_hide) else stringResource(R.string.label_show),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    label = { Text(stringResource(R.string.label_filter_surah)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = surahExpanded) },
                    shape = RoundedCornerShape(8.dp),
                    colors = searchFieldColors()
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

            Spacer(Modifier.height(8.dp))

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
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = juzEnd?.toString() ?: "",
                    onValueChange = { val v = it.filter(Char::isDigit).toIntOrNull(); if (v == null || v in 1..30) onJuzRangeChange(juzStart, v) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.label_juz_end)) },
                    placeholder = { Text("30") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    colors = searchFieldColors()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RandomAyahSection(
    scope: RandomScope,
    selectedJuz: Int,
    selectedSurahId: Int,
    pageStart: Int,
    pageEnd: Int,
    onScopeChange: (RandomScope) -> Unit,
    onJuzChange: (Int) -> Unit,
    onSurahChange: (Int) -> Unit,
    onPageRangeChange: (Int, Int) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (expanded) YellowAccent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp), tint = YellowAccent)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.label_random_ayah),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = YellowAccent
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) stringResource(R.string.label_hide) else stringResource(R.string.label_show),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RandomScope.values().forEachIndexed { index, randomScope ->
                    SegmentedButton(
                        selected = scope == randomScope,
                        onClick = { onScopeChange(randomScope) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RandomScope.values().size),
                        label = {
                            Text(
                                text = when (randomScope) {
                                    RandomScope.JUZ -> stringResource(R.string.scope_juz)
                                    RandomScope.SURAH -> stringResource(R.string.scope_surah)
                                    RandomScope.PAGE_RANGE -> stringResource(R.string.scope_page_range)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (scope) {
                RandomScope.JUZ -> {
                    var juzExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = juzExpanded,
                        onExpandedChange = { juzExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "${stringResource(R.string.juz_label)} $selectedJuz",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                            label = { Text(stringResource(R.string.scope_juz)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = juzExpanded) },
                            shape = RoundedCornerShape(8.dp),
                            colors = searchFieldColors()
                        )
                        ExposedDropdownMenu(expanded = juzExpanded, onDismissRequest = { juzExpanded = false }) {
                            (1..30).forEach { juz ->
                                DropdownMenuItem(
                                    text = { Text("${stringResource(R.string.juz_label)} $juz") },
                                    onClick = { onJuzChange(juz); juzExpanded = false }
                                )
                            }
                        }
                    }
                }
                RandomScope.SURAH -> {
                    var surahExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = surahExpanded,
                        onExpandedChange = { surahExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "${selectedSurahId}. ${SurahData.nameOf(selectedSurahId)}",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                            label = { Text(stringResource(R.string.scope_surah)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = surahExpanded) },
                            shape = RoundedCornerShape(8.dp),
                            colors = searchFieldColors()
                        )
                        ExposedDropdownMenu(expanded = surahExpanded, onDismissRequest = { surahExpanded = false }) {
                            SurahData.ALL.forEach { info ->
                                DropdownMenuItem(
                                    text = { Text("${info.id}. ${info.getDisplayName()}") },
                                    onClick = { onSurahChange(info.id); surahExpanded = false }
                                )
                            }
                        }
                    }
                }
                RandomScope.PAGE_RANGE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pageStart.toString(),
                            onValueChange = { val v = it.filter(Char::isDigit).toIntOrNull(); if (v != null && v in 1..604) onPageRangeChange(v, pageEnd) else if (it.isEmpty()) onPageRangeChange(1, pageEnd) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.label_start_page)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            colors = searchFieldColors()
                        )
                        OutlinedTextField(
                            value = pageEnd.toString(),
                            onValueChange = { val v = it.filter(Char::isDigit).toIntOrNull(); if (v != null && v in 1..604) onPageRangeChange(pageStart, v) else if (it.isEmpty()) onPageRangeChange(pageStart, 604) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.label_end_page)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            colors = searchFieldColors()
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = YellowAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    stringResource(R.string.btn_generate_random),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    verse: VerseEntity,
    onPeek: () -> Unit,
    onOpenMushaf: () -> Unit
) {
    Surface(
        onClick = onPeek,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${verse.surahId}. ${SurahData.nameOf(verse.surahId)}  ·  ${verse.ayahNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = verse.textArabic,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Rtl,
                        fontFamily = AmiriFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(Modifier.width(8.dp))
            
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                IconButton(onClick = onOpenMushaf, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onPeek, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun searchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorBorderColor = DifficultyCritical
)
