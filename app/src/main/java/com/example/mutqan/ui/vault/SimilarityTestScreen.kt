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
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.quran.toCleanQuranicText

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
                        onSelect = { viewModel.chooseSource(TestSource.entries[it]) }
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
                is TestUiState.Loading -> CircularProgressIndicator()
                is TestUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.test_empty_scope),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
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
                        onReveal = viewModel::reveal,
                        onNext = viewModel::loadNextVerse,
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
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onGroupClick: () -> Unit
) {
    val result = state.result
    val verse = result.verse
    val surahName = SurahData.nameOf(verse.surah)
    val cleanText = verse.text.toCleanQuranicText()
    val words = cleanText.split(" ")
    val snippet = words.take(4).joinToString(" ") + " ..."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Meta Info
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = surahName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.ayah_n, verse.ayah),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.label_page, verse.firstPage),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2. Ayah Text
        val shared = MaterialTheme.colorScheme.primary
        val revealedText = if (state.isRevealed && state.words.isNotEmpty()) sharedWords(state.words, state.shared, shared) else null
        Text(
            text = revealedText ?: androidx.compose.ui.text.AnnotatedString(if (state.isRevealed) cleanText else snippet),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = AmiriFontFamily,
                fontSize = 28.sp,
                lineHeight = 44.sp,
                textDirection = TextDirection.Rtl
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // 3. Group Info
        if (state.source == TestSource.MINE) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.label_part_of_group),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = result.groupName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clickable { onGroupClick() }
                    .padding(4.dp)
            )
        }

        // 4. The lookalikes, for ready-made groups
        if (state.isRevealed && state.similar.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.test_similar_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.similar.forEach { other ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(other.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = sharedWords(other.words, other.shared, shared),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 38.sp, textDirection = TextDirection.Rtl
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 5. Actions
        if (!state.isRevealed) {
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_show_ayah))
            }
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_next_verse))
            }
        }
    }
}

/** [words] as one line, the ones at the 1-based positions in [shared] set in [color] and bold. */
private fun sharedWords(words: List<String>, shared: Set<Int>, color: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        words.forEachIndexed { i, word ->
            if (i > 0) append(" ")
            if (i + 1 in shared) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(word) }
            } else {
                append(word)
            }
        }
    }
