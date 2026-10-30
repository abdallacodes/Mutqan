package com.example.mutqan.ui.coverage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import kotlinx.coroutines.launch

/** Where the user says which parts of the Quran they have memorised. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoverageScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: CoverageViewModel = viewModel(factory = CoverageViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddRangeDialog(
            onDismiss = { showAddDialog = false },
            onApply = viewModel::applyRange,
            onApplied = { showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.coverage_title),
                onBack = onDone
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = if (state.isSet) stringResource(R.string.coverage_summary, state.ayahCount, state.pageCount)
                        else stringResource(R.string.coverage_unset_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::setWholeQuran) { Text(stringResource(R.string.coverage_whole_quran)) }
                    OutlinedButton(onClick = viewModel::clear, enabled = state.isSet) { Text(stringResource(R.string.coverage_clear)) }
                }
            }
            item { SectionTitle(stringResource(R.string.coverage_juz_section)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..30).forEach { juz ->
                        FilterChip(
                            selected = juz in state.fullJuz,
                            onClick = { viewModel.toggleJuz(juz) },
                            label = { Text(juz.toString()) }
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.coverage_ranges_section)) }
            if (state.ranges.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.coverage_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.ranges, key = { it.range.first }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.coverage_range_label,
                            SurahData.nameOf(item.startSurah), item.startAyah,
                            SurahData.nameOf(item.endSurah), item.endAyah
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeRange(item.range) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coverage_cd_remove_range))
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.coverage_add_range))
                }
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.coverage_done), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRangeDialog(
    onDismiss: () -> Unit,
    onApply: suspend (RangeMode, String, String, String, String, Boolean) -> Boolean,
    onApplied: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(RangeMode.SURAH) }
    var from by remember { mutableStateOf("") }
    var fromAyah by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var toAyah by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun submit(remove: Boolean) {
        scope.launch {
            if (onApply(mode, from, fromAyah, to, toAyah, remove)) onApplied() else invalid = true
        }
    }

    val primaryLabel = stringResource(
        if (mode == RangeMode.PAGE) R.string.coverage_field_page else R.string.coverage_field_surah
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.coverage_add_range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RangeMode.SURAH to R.string.coverage_by_surah,
                        RangeMode.PAGE to R.string.coverage_by_page,
                        RangeMode.AYAH to R.string.coverage_by_ayah
                    ).forEach { (option, label) ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option; invalid = false },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
                Text(stringResource(R.string.coverage_from), style = MaterialTheme.typography.labelMedium)
                NumberRow(primaryLabel, from, { from = it; invalid = false }, mode == RangeMode.AYAH, fromAyah) {
                    fromAyah = it; invalid = false
                }
                Text(stringResource(R.string.coverage_to), style = MaterialTheme.typography.labelMedium)
                NumberRow(primaryLabel, to, { to = it; invalid = false }, mode == RangeMode.AYAH, toAyah) {
                    toAyah = it; invalid = false
                }
                if (invalid) {
                    Text(
                        text = stringResource(R.string.coverage_range_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { submit(remove = true) }) { Text(stringResource(R.string.coverage_btn_remove)) }
                TextButton(onClick = { submit(remove = false) }) { Text(stringResource(R.string.coverage_btn_add)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun NumberRow(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    withAyah: Boolean,
    ayahValue: String,
    onAyahValue: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValue(it.filter(Char::isDigit).take(3)) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        if (withAyah) {
            OutlinedTextField(
                value = ayahValue,
                onValueChange = { onAyahValue(it.filter(Char::isDigit).take(3)) },
                label = { Text(stringResource(R.string.coverage_field_ayah)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
