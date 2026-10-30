package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.TopBarOverflowMenu
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** How many page-boundary rounds were done in the last seven days. */
class PracticeViewModel(study: StudyRepository) : ViewModel() {
    val boundariesThisWeek: StateFlow<Int> = study.exerciseCountsSince(System.currentTimeMillis() - WEEK_MS)
        .map { counts -> ExerciseType.PAGE_BOUNDARIES.sumOf { counts[it] ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

class PracticeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PracticeViewModel(StudyRepository.create(context)) as T
}

/** Every exercise in one list. Each entry opens its own screen. */
@Composable
fun PracticeScreen(
    onOpenBoundaries: () -> Unit,
    onRecite: (startPage: Int, endPage: Int) -> Unit,
    onRandomVerse: () -> Unit,
    onSimilarityTest: () -> Unit,
    onOpenVault: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModelFactory(context))
    val week by viewModel.boundariesThisWeek.collectAsState()
    var askRange by remember { mutableStateOf(false) }

    if (askRange) {
        PageRangeDialog(
            onConfirm = { start, end ->
                askRange = false
                onRecite(start, end)
            },
            onDismiss = { askRange = false }
        )
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.practice_title),
                subtitle = stringResource(R.string.practice_subtitle),
                actions = { TopBarOverflowMenu(onHelpClick = null, onSettingsClick = onSettingsClick) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            HubRow(
                title = stringResource(R.string.ex_boundaries_title),
                subtitle = stringResource(R.string.ex_boundaries_desc),
                note = if (week > 0) stringResource(R.string.ex_this_week, week) else null,
                onClick = onOpenBoundaries
            )
            HubRow(
                title = stringResource(R.string.ex_recite_title),
                subtitle = stringResource(R.string.ex_recite_desc),
                onClick = { askRange = true }
            )
            HubRow(
                title = stringResource(R.string.ex_random_title),
                subtitle = stringResource(R.string.ex_random_desc),
                onClick = onRandomVerse
            )
            HubRow(
                title = stringResource(R.string.ex_similarity_title),
                subtitle = stringResource(R.string.ex_similarity_desc),
                onClick = onSimilarityTest
            )
            HubRow(
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(R.string.ex_vault_desc),
                onClick = onOpenVault
            )
        }
    }
}

/** Two page numbers for the recite-from-memory exercise. */
@Composable
private fun PageRangeDialog(onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val s = start.toIntOrNull()
    val e = (end.toIntOrNull() ?: s)
    val valid = s != null && e != null && s in 1..604 && e in s..604
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ex_recite_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.label_start_page)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.label_end_page)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (valid) onConfirm(s!!, e!!) }, enabled = valid) { Text(stringResource(R.string.ex_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
