package com.example.mutqan.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.domain.TestType
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.quran.toCleanQuranicText
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.practice.Feedback
import com.example.mutqan.ui.practice.OptionState
import com.example.mutqan.ui.practice.QuizOption
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.components.OpenInMushaf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarityTestScreen(
    groupId: Int?,
    folderId: Int?,
    onBack: () -> Unit,
    onNavigateToGroup: (groupId: Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: SimilarityTestViewModel = viewModel(
        factory = SimilarityTestViewModelFactory(context, groupId, folderId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val source by viewModel.source.collectAsState()
    val type by viewModel.type.collectAsState()

    Scaffold(
        topBar = {
            Column {
                PaperHeader(
                    title = stringResource(R.string.vault_test_title),
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { viewModel.loadNextVerse() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                )
                if (viewModel.canChooseSource) {
                    PaperTabs(
                        labels = listOf(stringResource(R.string.test_source_mine), stringResource(R.string.test_source_ready)),
                        selectedIndex = source.ordinal,
                        onSelect = { viewModel.chooseSource(TestSource.entries[it]) },
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                ) {
                    listOf(
                        TestType.RECALL to R.string.test_type_recall,
                        TestType.FINISH to R.string.test_type_finish,
                        TestType.WHICH to R.string.test_type_which
                    ).forEach { (option, label) ->
                        PaperChip(
                            selected = type == option,
                            onClick = { viewModel.chooseType(option) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val q = uiState as? TestUiState.Question
            if (q != null && (q.isRevealed || q.type == TestType.RECALL)) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                    Hairline(Modifier.fillMaxWidth())
                    PaperButton(
                        text = stringResource(if (q.isRevealed) R.string.btn_next_verse else R.string.btn_show_ayah),
                        onClick = { if (q.isRevealed) viewModel.loadNextVerse() else viewModel.reveal() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is TestUiState.Loading -> PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                is TestUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.test_empty_scope),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                is TestUiState.NoGroups -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.test_empty_mine),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        PaperButton(
                            text = stringResource(R.string.test_use_ready),
                            onClick = { viewModel.chooseSource(TestSource.READY) }
                        )
                    }
                }
                is TestUiState.Error -> {
                    Text(
                        text = state.message.asString(LocalContext.current),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                is TestUiState.Question -> {
                    QuizContent(
                        state = state,
                        onPick = viewModel::pick,
                        onGroupClick = { onNavigateToGroup(state.result.groupId) }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun QuizContent(
    state: TestUiState.Question,
    onPick: (Int) -> Unit,
    onGroupClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isChoice = state.type != TestType.RECALL
    val verse = state.result.verse
    val surahName = SurahData.nameOf(verse.surah)
    val shared = scheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PaperSpace.Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // The reference is the answer when asking which verse it is.
        if (!(state.type == TestType.WHICH && !state.isRevealed)) {
            Text(
                text = listOf(
                    surahName,
                    stringResource(R.string.ayah_n, verse.ayah),
                    stringResource(R.string.label_page, verse.firstPage)
                ).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary
            )
        }

        when {
            // Ready-made groups mark the words the verse shares with its look-alikes.
            state.isRevealed && state.words.isNotEmpty() -> MushafText(
                span = MushafSpan.of(state.words),
                size = 28.sp,
                wordColors = state.words.filter { it.position in state.shared }.associate { it.id to shared }
            )
            state.isRevealed || state.type == TestType.WHICH -> AyahText(ayahId = verse.id, size = 28.sp)
            state.type == TestType.FINISH -> MushafText(span = state.lead, size = 28.sp)
            else -> AyahText(ayahId = verse.id, size = 28.sp, firstWords = 4)
        }
        if (state.isRevealed) OpenInMushaf(ayahId = verse.id)

        // The choices, for the "finish it" and "which verse" tests
        if (isChoice) {
            Text(
                text = if (state.type == TestType.FINISH) {
                    stringResource(R.string.test_q_finish, surahName, verse.ayah)
                } else stringResource(R.string.test_q_which),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onBackground
            )
            state.options.forEachIndexed { i, option ->
                val optionState = when {
                    state.picked == null -> OptionState.PLAIN
                    i == state.correct -> OptionState.CORRECT
                    i == state.picked -> OptionState.WRONG
                    else -> OptionState.DIMMED
                }
                QuizOption(
                    span = option,
                    state = optionState,
                    enabled = state.picked == null,
                    onClick = { onPick(i) },
                    verse = state.type == TestType.FINISH
                )
            }
            if (state.picked != null) Feedback(state.picked == state.correct)
        }

        if (state.source == TestSource.MINE && (!isChoice || state.isRevealed)) {
            Column {
                SectionLabel(stringResource(R.string.label_part_of_group))
                PaperLink(text = state.result.groupName, onClick = onGroupClick)
            }
        }

        // The lookalikes, for ready-made groups
        if (state.isRevealed && state.similar.isNotEmpty()) {
            OrnamentDivider()
            SectionLabel(stringResource(R.string.test_similar_title))
            state.similar.forEach { other ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(other.reference, style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                    MushafText(
                        span = MushafSpan.of(other.words),
                        size = 22.sp,
                        wordColors = other.words.filter { it.position in other.shared }.associate { it.id to shared }
                    )
                    OpenInMushaf(ayahId = other.ayahId, soft = true)
                }
            }
        }
    }
}
