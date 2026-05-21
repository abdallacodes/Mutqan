package com.example.qmemo.ui.vault

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.ui.theme.AmiriFontFamily
import com.example.qmemo.util.toCleanQuranicText

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_test_title), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadNextVerse() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
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
                        text = state.message,
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
    val surahName = SurahData.nameOf(verse.surahId)
    val cleanText = verse.textArabic.toCleanQuranicText()
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
                        text = stringResource(R.string.ayah_n, verse.ayahNumber),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.label_page, verse.pageNumber),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2. Ayah Text
        Text(
            text = if (state.isRevealed) cleanText else snippet,
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        // 4. Actions
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
