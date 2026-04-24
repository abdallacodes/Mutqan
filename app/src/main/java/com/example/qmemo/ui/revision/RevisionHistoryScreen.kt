package com.example.qmemo.ui.revision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.local.entity.RevisionLogEntity
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RevisionHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewModel<RevisionHistoryViewModel>(
        factory = RevisionHistoryViewModelFactory(context)
    )

    val groupedLogs   by viewModel.groupedLogs.collectAsState()
    val editState     by viewModel.editState.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()

    editState?.let { state ->
        EditRevisionDialog(
            editState    = state,
            onStartPage  = viewModel::onEditStartPage,
            onEndPage    = viewModel::onEditEndPage,
            onDifficulty = viewModel::onEditDifficulty,
            onDate       = viewModel::onEditDate,
            onSave       = viewModel::saveEdit,
            onCancel     = viewModel::cancelEdit
        )
    }

    pendingDelete?.let { log ->
        DeleteConfirmDialog(
            log       = log,
            onConfirm = viewModel::confirmDelete,
            onCancel  = viewModel::cancelDelete
        )
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
                    Column {
                        Text(
                            text          = stringResource(R.string.history_title),
                            style         = MaterialTheme.typography.titleLarge,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color         = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text          = stringResource(R.string.history_subtitle),
                            style         = MaterialTheme.typography.labelSmall,
                            color         = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        if (groupedLogs.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = stringResource(R.string.no_revisions_history),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                groupedLogs.forEach { group ->
                    stickyHeader(key = "h_${group.label}") {
                        MonthYearHeader(label = group.label)
                    }
                    items(group.logs, key = { "log_${it.id}" }) { log ->
                        SwipeToDismissLogItem(
                            log      = log,
                            onEdit   = { viewModel.startEdit(log) },
                            onDelete = { viewModel.requestDelete(log) }
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ── Month/Year sticky header ──────────────────────────────────────────────────

@Composable
private fun MonthYearHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text          = label,
            style         = MaterialTheme.typography.labelMedium,
            fontWeight    = FontWeight.Black,
            color         = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )
    }
}

// ── Swipeable log item ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissLogItem(
    log: RevisionLogEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        }
    )

    SwipeToDismissBox(
        state                    = dismissState,
        modifier                 = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val triggered = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (triggered) DifficultyCritical.copy(alpha = 0.85f)
                                else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (triggered) {
                    Icon(
                        imageVector        = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint               = Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) {
        HistoryLogItem(log = log, onClick = onEdit)
    }
}

// ── Single history log card ───────────────────────────────────────────────────

@Composable
private fun HistoryLogItem(log: RevisionLogEntity, onClick: () -> Unit) {
    val difficulty = Difficulty.fromId(log.difficulty)
    val diffColor  = when (difficulty) {
        Difficulty.SMOOTH    -> DifficultySmooth
        Difficulty.STRUGGLED -> DifficultyStruggled
        Difficulty.CRITICAL  -> DifficultyCritical
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(diffColor)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text          = historyDateLabel(log.dateMillis, log.timestamp),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text       = stringResource(R.string.pages_range, log.startPage, log.endPage),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape  = RoundedCornerShape(4.dp),
                color  = diffColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, diffColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text       = difficulty.localizedLabel(),
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = diffColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Delete confirmation dialog ────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    log: RevisionLogEntity,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(12.dp),
        title = {
            Text(
                text       = stringResource(R.string.delete_entry_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text  = stringResource(R.string.delete_entry_body, log.startPage, log.endPage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape   = RoundedCornerShape(8.dp),
                colors  = ButtonDefaults.buttonColors(
                    containerColor = DifficultyCritical,
                    contentColor   = Color.White
                )
            ) {
                Text(stringResource(R.string.btn_delete), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text  = stringResource(R.string.btn_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ── Edit revision dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRevisionDialog(
    editState: EditLogUiState,
    onStartPage: (String) -> Unit,
    onEndPage: (String) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onDate: (Long) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        HistoryDatePickerDialog(
            currentMillis = editState.selectedDateMillis,
            onDismiss     = { showDatePicker = false },
            onConfirm     = { millis -> onDate(millis); showDatePicker = false }
        )
        return
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape          = RoundedCornerShape(16.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text          = stringResource(R.string.edit_revision_title),
                    style         = MaterialTheme.typography.titleMedium,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color         = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value           = editState.startPage,
                        onValueChange   = onStartPage,
                        modifier        = Modifier.weight(1f),
                        label           = { Text(stringResource(R.string.label_start)) },
                        isError         = editState.startError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine      = true,
                        shape           = RoundedCornerShape(8.dp),
                        colors          = editFieldColors()
                    )
                    OutlinedTextField(
                        value           = editState.endPage,
                        onValueChange   = onEndPage,
                        modifier        = Modifier.weight(1f),
                        label           = { Text(stringResource(R.string.label_end)) },
                        isError         = editState.endError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine      = true,
                        shape           = RoundedCornerShape(8.dp),
                        colors          = editFieldColors()
                    )
                }

                EditDifficultyRow(
                    selected = editState.difficulty,
                    onSelect = onDifficulty
                )

                EditDateButton(
                    millis  = editState.selectedDateMillis,
                    onClick = { showDatePicker = true }
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text  = stringResource(R.string.btn_cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        shape   = RoundedCornerShape(8.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

// ── Edit dialog helpers ───────────────────────────────────────────────────────

@Composable
private fun EditDifficultyRow(selected: Difficulty, onSelect: (Difficulty) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Difficulty.entries.forEach { diff ->
            val isSelected = diff == selected
            val color      = when (diff) {
                Difficulty.SMOOTH    -> DifficultySmooth
                Difficulty.STRUGGLED -> DifficultyStruggled
                Difficulty.CRITICAL  -> DifficultyCritical
            }
            Surface(
                onClick  = { onSelect(diff) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(6.dp),
                color    = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
                border   = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) color else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text       = diff.localizedLabel(),
                    modifier   = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color      = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EditDateButton(millis: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(8.dp),
        color   = Color.Transparent,
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Default.CalendarToday,
                contentDescription = null,
                modifier           = Modifier.size(14.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = historyDateDisplay(millis, context),
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
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

// ── Date picker dialog (local to this screen) ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerDialog(
    currentMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentMillis
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(datePickerState.selectedDateMillis ?: currentMillis)
                }
            ) { Text(stringResource(R.string.btn_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private val historyUtcSdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).also {
    it.timeZone = TimeZone.getTimeZone("UTC")
}

private fun historyDateLabel(dateMillis: Long, fallbackTimestamp: Long): String {
    val millis = if (dateMillis > 0L) dateMillis else fallbackTimestamp
    return historyUtcSdf.format(Date(millis))
}

private fun historyDateDisplay(millis: Long, context: android.content.Context): String {
    if (millis == 0L) return context.getString(R.string.select_date_inline)
    return SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}
