package com.example.qmemo.ui.heatmap

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.local.entity.UserSubjectEntity
import com.example.qmemo.ui.heatmap.components.AnchorCard
import com.example.qmemo.ui.heatmap.components.AyahPreviewBottomSheet
import com.example.qmemo.ui.heatmap.components.PeekDetailBottomSheet
import com.example.qmemo.ui.heatmap.components.SubjectEditorDialog
import com.example.qmemo.ui.theme.AmiriFontFamily
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuzDetailScreen(
    juzId:       Int,
    onBack:      () -> Unit,
    onPageOpen:  (Int) -> Unit = {}
) {
    val context   = LocalContext.current
    val viewModel = viewModel<JuzDetailViewModel>(
        key     = "juz_$juzId",
        factory = JuzDetailViewModelFactory(context, juzId)
    )

    val pages            by viewModel.pagesWithSurahs.collectAsState()
    val timelineItems    by viewModel.timelineItems.collectAsState()
    val isStructural     by viewModel.isStructuralMode.collectAsState()
    val surahRange       by viewModel.surahRange.collectAsState()
    val selectedPage     by viewModel.selectedPage.collectAsState()
    val peekData         by viewModel.peekData.collectAsState()
    val ayahPreview      by viewModel.ayahPreview.collectAsState()
    val isArabic         = LocalConfiguration.current.locales[0].language == "ar"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportSubjects(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importSubjects(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SharingEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is SharingEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    var editingUnitId by remember { mutableStateOf<Int?>(null) }
    var editingSubject by remember { mutableStateOf<UserSubjectEntity?>(null) }
    var editingAyahs by remember { mutableStateOf<List<com.example.qmemo.data.local.entity.VerseEntity>>(emptyList()) }
    var subjectToDelete by remember { mutableStateOf<UserSubjectEntity?>(null) }

    val subtitle = surahRange?.let { range ->
        val from = "${range.firstSurah}. ${SurahData.nameOf(range.firstSurah)}"
        if (range.firstSurah == range.lastSurah) from
        else "$from – ${range.lastSurah}. ${SurahData.nameOf(range.lastSurah)}"
    } ?: stringResource(R.string.loading_subtitle)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint               = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text          = stringResource(R.string.juz_title, juzId),
                            style         = MaterialTheme.typography.titleLarge,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color         = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text          = subtitle,
                            style         = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = if (isArabic) AmiriFontFamily else FontFamily.Default
                            ),
                            color         = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.3.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    com.example.qmemo.ui.components.TopBarOverflowMenu(
                        onHelpClick = { /* TODO */ },
                        onSettingsClick = {},
                        extraItems = { dismiss ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_export_subjects)) },
                                onClick = {
                                    dismiss()
                                    exportLauncher.launch("juz_${juzId}_subjects.json")
                                },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_import_subjects)) },
                                onClick = {
                                    dismiss()
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                            )
                        }
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = isStructural,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "mode_transition"
            ) { structural ->
                if (structural) {
                    // ── Structural Flow View (Anchor Cards) ─────────────────
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(timelineItems) { cardData ->
                            AnchorCard(
                                data = cardData,
                                onAddSubject = { 
                                    editingUnitId = cardData.unit.id 
                                    editingAyahs = cardData.ayahsInQuarter
                                    editingSubject = null
                                },
                                onEditSubject = { subject ->
                                    editingUnitId = cardData.unit.id
                                    editingAyahs = cardData.ayahsInQuarter
                                    editingSubject = subject
                                },
                                onDeleteSubject = { subject ->
                                    subjectToDelete = subject
                                },
                                onSubjectClick = { subject ->
                                    viewModel.showAyahPreview(subject.startAyahId)
                                },
                                onPageClick = { pageNum ->
                                    viewModel.onPageTap(pageNum, "")
                                }
                            )
                        }
                    }
                } else {
                    // ── Memory Health View ──────────────────────────────────
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(4),
                        modifier              = Modifier.fillMaxSize(),
                        contentPadding        = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp)
                    ) {
                        item(span = { GridItemSpan(4) }) {
                            JuzHealthSummary(pages = pages)
                        }

                        if (pages.isEmpty()) {
                            item(span = { GridItemSpan(4) }) {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text  = stringResource(R.string.loading_pages),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(pages, key = { it.stability.page }) { pwS ->
                                PageTile(
                                    pwS     = pwS,
                                    onClick = { viewModel.onPageTap(pwS.stability.page, pwS.surahLabel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Sheets / Dialogs ───────────────────────────────────────────────────

    selectedPage?.let { selection ->
        QuickStatusDialog(
            selection    = selection,
            onDismiss    = viewModel::dismissDialog,
            onSelect     = { quality -> viewModel.logPage(selection.page, quality) },
            onViewInMushaf = { page -> viewModel.dismissDialog(); onPageOpen(page) }
        )
    }

    peekData?.let { data ->
        PeekDetailBottomSheet(
            pageNumber = data.pageNumber,
            startSnippet = data.startSnippet,
            endSnippet = data.endSnippet,
            onOpenInMushaf = { viewModel.dismissPeek(); onPageOpen(data.pageNumber) },
            onDismiss = { viewModel.dismissPeek() }
        )
    }

    ayahPreview?.let { verse ->
        AyahPreviewBottomSheet(
            verse = verse,
            onOpenInMushaf = { viewModel.dismissAyahPreview(); onPageOpen(verse.pageNumber) },
            onShare = { /* TODO */ },
            onDismiss = { viewModel.dismissAyahPreview() }
        )
    }

    editingUnitId?.let { unitId ->
        SubjectEditorDialog(
            ayahs = editingAyahs,
            initialText = editingSubject?.subjectText ?: "",
            initialAyahId = editingSubject?.startAyahId,
            onSave = { text, startAyahId ->
                val current = editingSubject
                if (current == null) {
                    viewModel.addSubject(unitId, text, startAyahId)
                } else {
                    viewModel.updateSubject(current, text, startAyahId)
                }
                editingUnitId = null
                editingSubject = null
            },
            onDismiss = { 
                editingUnitId = null
                editingSubject = null
            }
        )
    }

    subjectToDelete?.let { subject ->
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text(stringResource(R.string.btn_delete)) },
            text = { Text(stringResource(R.string.delete_subject_confirmation)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubject(subject)
                        subjectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

// ── Juz health summary ────────────────────────────────────────────────────────

@Composable
private fun JuzHealthSummary(pages: List<PageWithSurahs>) {
    if (pages.isEmpty()) return

    val stabilities = pages.map { it.stability }
    val tracked     = stabilities.filter { it.isTracked }
    val avg         = if (tracked.isEmpty()) 0f
                      else tracked.map { it.score }.average().toFloat()
    val healthClr   = if (tracked.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                      else healthColor(avg)
    val critical    = tracked.count { it.score < 0.25f }
    val fading      = tracked.count { it.score in 0.25f..0.50f }

    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text          = stringResource(R.string.juz_health),
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Bold,
                        color         = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text       = if (tracked.isEmpty()) stringResource(R.string.no_revisions_juz)
                                     else stringResource(
                                         R.string.pages_tracked_summary,
                                         (avg * 100).roundToInt(),
                                         tracked.size,
                                         stabilities.size
                                     ),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color      = healthClr,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (critical > 0 || fading > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (critical > 0) {
                            Text(
                                text  = stringResource(R.string.count_critical, critical),
                                style = MaterialTheme.typography.labelSmall,
                                color = DifficultyCritical
                            )
                        }
                        if (fading > 0) {
                            Text(
                                text  = stringResource(R.string.count_fading, fading),
                                style = MaterialTheme.typography.labelSmall,
                                color = DifficultyStruggled
                            )
                        }
                    }
                }
            }

            if (tracked.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress          = { avg },
                    modifier          = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color             = healthClr,
                    trackColor        = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    gapSize           = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
}

// ── Page tile ─────────────────────────────────────────────────────────────────

@Composable
private fun PageTile(pwS: PageWithSurahs, onClick: () -> Unit) {
    val ps        = pwS.stability
    val bgColor   = pageColor(ps)
    val textColor = adaptiveTextColor(bgColor)
    val dimColor  = textColor.copy(alpha = if (ps.isTracked) 0.65f else 0.40f)

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(8.dp),
        color    = bgColor,
        border   = BorderStroke(
            width = 1.dp,
            color = if (ps.isTracked) textColor.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text      = pwS.surahLabel,
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color     = dimColor,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 8.sp,
                modifier  = Modifier.fillMaxWidth()
            )
            Text(
                text       = "${ps.page}",
                style      = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Black,
                color      = textColor,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
            Text(
                text      = if (ps.isTracked) "${(ps.score * 100).roundToInt()}%"
                            else stringResource(R.string.untracked_dash),
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color     = dimColor,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Quick-status dialog ───────────────────────────────────────────────────────

@Composable
private fun QuickStatusDialog(
    selection:      PageSelection,
    onDismiss:      () -> Unit,
    onSelect:       (Float) -> Unit,
    onViewInMushaf: (Int) -> Unit = {}
) {
    var manualQuality by remember { mutableFloatStateOf(0.9f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(12.dp),
        title = {
            Column {
                Text(
                    text          = stringResource(R.string.dialog_page_title, selection.page),
                    style         = MaterialTheme.typography.titleMedium,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 2.sp
                )
                if (selection.surahLabel.isNotBlank()) {
                    Text(
                        text  = selection.surahLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_manual_quality),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(manualQuality * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))

                    Slider(
                        value = manualQuality,
                        onValueChange = { manualQuality = it },
                        valueRange = 0.1f..1f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { onSelect(manualQuality) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_save),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )

                TextButton(
                    onClick  = { onViewInMushaf(selection.page) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text   = stringResource(R.string.open_in_mushaf),
                        style  = MaterialTheme.typography.labelMedium,
                        color  = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text  = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
