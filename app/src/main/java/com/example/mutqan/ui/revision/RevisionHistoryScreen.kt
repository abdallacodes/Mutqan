package com.example.mutqan.ui.revision

import android.text.format.DateFormat as AndroidDateFormat
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.RevisionLog
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.theme.FolioTheme

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
            onOutcome    = viewModel::onEditOutcome,
            onTags       = viewModel::onEditTags,
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
            PaperHeader(
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_subtitle),
                onBack = onBack
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
        )
    }
}

// ── Swipeable log item ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissLogItem(
    log: RevisionLog,
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
                        shape = RoundedCornerShape(14.dp)
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
private fun HistoryLogItem(log: RevisionLog, onClick: () -> Unit) {
    val healthClr = log.outcome.color()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(healthClr)
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
                shape  = RoundedCornerShape(12.dp),
                color  = healthClr.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, healthClr.copy(alpha = 0.4f))
            ) {
                Text(
                    text       = log.outcome.localizedLabel(),
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = healthClr,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Delete confirmation dialog ────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    log: RevisionLog,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    PaperDialog(
        onDismissRequest = onCancel,
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
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
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
    onOutcome: (RecallOutcome) -> Unit,
    onTags: (Set<MistakeTag>) -> Unit,
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
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text          = stringResource(R.string.edit_revision_title),
                    style         = MaterialTheme.typography.titleMedium,
                    fontWeight    = FontWeight.Black,
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
                        shape           = RoundedCornerShape(14.dp),
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
                        shape           = RoundedCornerShape(14.dp),
                        colors          = editFieldColors()
                    )
                }

                OutcomePicker(
                    outcome = editState.outcome,
                    tags = editState.mistakeTags,
                    onOutcomeChange = onOutcome,
                    onTagsChange = onTags
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
                    PaperButton(text = stringResource(R.string.btn_save), onClick = onSave)
                }
            }
        }
    }
}

// ── Edit dialog helpers ───────────────────────────────────────────────────────

@Composable
private fun EditDateButton(millis: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(14.dp),
        color   = FolioTheme.colors.folio,
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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

// Made per use so a change of language shows at once.
private fun historyUtcSdf() = SimpleDateFormat(AndroidDateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMd"), Locale.getDefault()).also {
    it.timeZone = TimeZone.getTimeZone("UTC")
}

private fun historyDateLabel(dateMillis: Long, fallbackTimestamp: Long): String {
    val millis = if (dateMillis > 0L) dateMillis else fallbackTimestamp
    return historyUtcSdf().format(Date(millis))
}

private fun historyDateDisplay(millis: Long, context: android.content.Context): String {
    if (millis == 0L) return context.getString(R.string.select_date_inline)
    return SimpleDateFormat(AndroidDateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMEd"), Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}
