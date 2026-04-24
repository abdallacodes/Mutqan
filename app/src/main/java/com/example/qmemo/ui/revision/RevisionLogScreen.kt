package com.example.qmemo.ui.revision

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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

// ── Screen entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionLogScreen(
    onViewHistory: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: RevisionViewModel = viewModel(factory = RevisionViewModelFactory(context))

    val uiState    by viewModel.uiState.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialogWrapper(
            currentSelectedMillis = uiState.selectedDateMillis,
            onDismiss             = { showDatePicker = false },
            onConfirm             = { millis ->
                viewModel.onDateChange(millis)
                showDatePicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text          = stringResource(R.string.journal_title),
                            style         = MaterialTheme.typography.titleLarge,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color         = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text          = stringResource(R.string.journal_subtitle),
                            style         = MaterialTheme.typography.labelSmall,
                            color         = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector        = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {

            Spacer(modifier = Modifier.height(8.dp))

            PageRangeInputRow(
                startPage     = uiState.startPage,
                endPage       = uiState.endPage,
                startError    = uiState.startPageError,
                endError      = uiState.endPageError,
                onStartChange = viewModel::onStartPageChange,
                onEndChange   = viewModel::onEndPageChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            DifficultySelector(
                selected = uiState.difficulty,
                onSelect = viewModel::onDifficultyChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            DateSelectorButton(
                selectedDateMillis = uiState.selectedDateMillis,
                onClick            = { showDatePicker = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = viewModel::logRevision,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text          = stringResource(R.string.btn_log_revision),
                    style         = MaterialTheme.typography.titleSmall,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text          = stringResource(R.string.recent_revisions),
                    style         = MaterialTheme.typography.labelMedium,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier      = Modifier.weight(1f)
                )
                TextButton(onClick = onViewHistory) {
                    Text(
                        text          = stringResource(R.string.view_all),
                        style         = MaterialTheme.typography.labelSmall,
                        color         = MaterialTheme.colorScheme.primary,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (recentLogs.isEmpty()) {
                    item { EmptyHistoryHint() }
                } else {
                    items(recentLogs, key = { it.id }) { log ->
                        RevisionLogItem(log)
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Date picker dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogWrapper(
    currentSelectedMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentSelectedMillis
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = datePickerState.selectedDateMillis ?: currentSelectedMillis
                    onConfirm(selected)
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

// ── Date selector button ──────────────────────────────────────────────────────

@Composable
private fun DateSelectorButton(
    selectedDateMillis: Long,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape  = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector        = Icons.Default.CalendarToday,
            contentDescription = null,
            modifier           = Modifier.size(16.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = formatDateMillisDisplay(selectedDateMillis, context),
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Page range inputs ─────────────────────────────────────────────────────────

@Composable
private fun PageRangeInputRow(
    startPage: String,
    endPage: String,
    startError: Boolean,
    endError: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor        = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor      = MaterialTheme.colorScheme.outline,
        focusedLabelColor         = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor               = MaterialTheme.colorScheme.primary,
        focusedTextColor          = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor        = MaterialTheme.colorScheme.onSurface,
        errorBorderColor          = DifficultyCritical,
        errorLabelColor           = DifficultyCritical,
        errorTextColor            = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor     = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor   = MaterialTheme.colorScheme.surface,
        errorContainerColor       = MaterialTheme.colorScheme.surface
    )

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value         = startPage,
            onValueChange = onStartChange,
            modifier      = Modifier.weight(1f),
            label         = { Text(stringResource(R.string.label_start_page)) },
            placeholder   = { Text(stringResource(R.string.placeholder_start_page), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError       = startError,
            supportingText = if (startError) {
                { Text(stringResource(R.string.error_page_range), color = DifficultyCritical) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine    = true,
            colors        = fieldColors,
            shape         = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value         = endPage,
            onValueChange = onEndChange,
            modifier      = Modifier.weight(1f),
            label         = { Text(stringResource(R.string.label_end_page)) },
            placeholder   = { Text(stringResource(R.string.placeholder_end_page), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError       = endError,
            supportingText = if (endError) {
                { Text(stringResource(R.string.error_end_before_start), color = DifficultyCritical) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine    = true,
            colors        = fieldColors,
            shape         = RoundedCornerShape(8.dp)
        )
    }
}

// ── Difficulty selector ───────────────────────────────────────────────────────

private val Difficulty.color: Color
    get() = when (this) {
        Difficulty.SMOOTH    -> DifficultySmooth
        Difficulty.STRUGGLED -> DifficultyStruggled
        Difficulty.CRITICAL  -> DifficultyCritical
    }

@Composable
private fun DifficultySelector(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Difficulty.entries.forEach { diff ->
            val isSelected = diff == selected
            val diffColor  = diff.color
            OutlinedButton(
                onClick  = { onSelect(diff) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) diffColor.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor   = if (isSelected) diffColor else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) diffColor else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text       = diff.localizedLabel(),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines   = 1
                )
            }
        }
    }
}

// ── History items ─────────────────────────────────────────────────────────────

@Composable
private fun RevisionLogItem(log: RevisionLogEntity) {
    val context    = LocalContext.current
    val difficulty = Difficulty.fromId(log.difficulty)
    val diffColor  = difficulty.color

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    text       = formatLogDate(log.dateMillis, log.timestamp, context),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
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

@Composable
private fun EmptyHistoryHint() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = stringResource(R.string.no_revisions_hint),
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

// ── Date formatting helpers ───────────────────────────────────────────────────

private val utcSdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).also {
    it.timeZone = TimeZone.getTimeZone("UTC")
}

private fun formatDateMillisDisplay(millis: Long, context: Context): String {
    if (millis == 0L) return context.getString(R.string.select_date)
    return SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}

private fun formatLogDate(dateMillis: Long, fallbackTimestamp: Long, context: Context): String {
    val millis = if (dateMillis > 0L) dateMillis else fallbackTimestamp
    return if (dateMillis > 0L) {
        utcSdf.format(Date(millis))
    } else {
        formatRelativeTime(fallbackTimestamp, context)
    }
}

private fun formatRelativeTime(timestamp: Long, context: Context): String {
    val diff    = System.currentTimeMillis() - timestamp
    val seconds = diff / 1_000
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours   / 24

    return when {
        seconds < 60 -> context.getString(R.string.just_now)
        minutes < 60 -> context.getString(R.string.x_min_ago, minutes)
        hours   < 24 -> context.getString(R.string.x_hr_ago, hours)
        days    < 7  -> context.getString(R.string.x_day_ago, days)
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
