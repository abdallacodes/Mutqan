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
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.qmemo.ui.components.EmptyStateCard
import com.example.qmemo.ui.components.HelpDialog
import com.example.qmemo.ui.components.TopBarOverflowMenu
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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
    var showHelpDialog by remember { mutableStateOf(false) }

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
                    TopBarOverflowMenu(
                        onHelpClick = { showHelpDialog = true },
                        onSettingsClick = onSettingsClick
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {
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

                ManualQualitySection(
                    quality = uiState.manualQuality,
                    onQualityChange = viewModel::onManualQualityChange
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
                        item {
                            EmptyStateCard(
                                icon        = Icons.Default.EventNote,
                                title       = stringResource(R.string.empty_history_title),
                                description = stringResource(R.string.empty_history_body),
                                modifier    = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(recentLogs, key = { it.id }) { log ->
                            RevisionLogItem(log)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            if (showHelpDialog) {
                HelpDialog(
                    title = stringResource(R.string.help_journal_title),
                    description = stringResource(R.string.help_journal_desc),
                    onDismiss = { showHelpDialog = false }
                )
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

// ── Manual Quality Section ──────────────────────────────────────────

@Composable
private fun ManualQualitySection(
    quality: Float,
    onQualityChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_starting_quality),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(quality * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(8.dp))

        Slider(
            value = quality,
            onValueChange = onQualityChange,
            valueRange = 0.1f..1f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ── History items ─────────────────────────────────────────────────────────────

@Composable
private fun RevisionLogItem(log: RevisionLogEntity) {
    val context    = LocalContext.current
    val quality    = log.manualStability
    val healthClr = healthColor(quality)

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
                color  = healthClr.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, healthClr.copy(alpha = 0.4f))
            ) {
                Text(
                    text       = "${(quality * 100).roundToInt()}%",
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = healthClr,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Add this helper if it doesn't exist or use the one from JuzDetailScreen
@Composable
private fun healthColor(score: Float): Color = when {
    score >= 0.70f -> Color(0xFF4CAF50) // Green
    score >= 0.40f -> Color(0xFFFFC107) // Amber
    else          -> Color(0xFFF44336) // Red
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
