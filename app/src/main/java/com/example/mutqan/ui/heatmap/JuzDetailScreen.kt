package com.example.mutqan.ui.heatmap

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.QuarterMap
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.heatmap.components.AyahPreviewBottomSheet
import com.example.mutqan.ui.heatmap.components.QuarterCard
import com.example.mutqan.ui.heatmap.components.QuarterJointSheet
import com.example.mutqan.ui.heatmap.components.QuarterStrip
import com.example.mutqan.ui.heatmap.components.SubjectEditorSheet
import com.example.mutqan.ui.revision.OutcomePicker
import com.example.mutqan.ui.revision.OutcomePreview
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * The map of one juz: a strip of its eight hizb quarters coloured by how well each is held, with the weakest ringed, and
 * a compact card per quarter that opens to its pages, the user's subjects and a test of the joint to the next quarter.
 */
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
    val quarters         by viewModel.quarters.collectAsState()
    val surahRange       by viewModel.surahRange.collectAsState()
    val selectedPage     by viewModel.selectedPage.collectAsState()
    val ayahPreview      by viewModel.ayahPreview.collectAsState()
    val joint            by viewModel.joint.collectAsState()

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
                is SharingEvent.Success -> Toast.makeText(context, event.message.asString(context), Toast.LENGTH_SHORT).show()
                is SharingEvent.Error -> Toast.makeText(context, event.message.asString(context), Toast.LENGTH_LONG).show()
            }
        }
    }

    var editingUnitId by remember { mutableStateOf<Int?>(null) }
    var editingSubject by remember { mutableStateOf<UserSubjectEntity?>(null) }
    var subjectToDelete by remember { mutableStateOf<UserSubjectEntity?>(null) }

    // One quarter open at a time (by its number 1..240), so the list stays short.
    var openQuarter by rememberSaveable { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val weakest = remember(quarters) { QuarterMap.weakest(quarters.map { it.health }) }
    fun focus(index: Int) {
        val q = quarters.getOrNull(index) ?: return
        openQuarter = q.card.unit.number
        scope.launch { listState.animateScrollToItem(index + 1) } // the map is item 0
    }

    val subtitle = surahRange?.let { range ->
        val from = "${range.first}. ${SurahData.nameOf(range.first)}"
        if (range.first == range.last) from
        else "$from – ${range.last}. ${SurahData.nameOf(range.last)}"
    } ?: stringResource(R.string.loading_subtitle)

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.juz_title, juzId),
                subtitle = subtitle,
                onBack = onBack,
                actions = {
                    com.example.mutqan.ui.components.TopBarOverflowMenu(
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
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            item(key = "map") {
                JuzMap(
                    pages = pages,
                    quarters = quarters,
                    weakest = weakest,
                    onSelect = ::focus
                )
            }

            if (quarters.isEmpty()) {
                item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.loading_pages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(quarters, key = { _, q -> q.card.unit.number }) { _, q ->
                val unit = q.card.unit
                QuarterCard(
                    data = q,
                    expanded = openQuarter == unit.number,
                    onToggle = { openQuarter = if (openQuarter == unit.number) null else unit.number },
                    onAddSubject = {
                        editingUnitId = unit.number
                        editingSubject = null
                    },
                    onEditSubject = { subject ->
                        editingUnitId = unit.number
                        editingSubject = subject
                    },
                    onDeleteSubject = { subject -> subjectToDelete = subject },
                    onSubjectClick = { subject -> viewModel.showAyahPreview(subject.startAyahId) },
                    onPageClick = { page ->
                        viewModel.onPageTap(page.stability.page, page.surahLabel, page.state)
                    },
                    onTestJoint = if (unit.number < LAST_QUARTER) ({ viewModel.startJoint(unit) }) else null
                )
            }
        }
    }

    // ── Sheets / Dialogs ───────────────────────────────────────────────────

    selectedPage?.let { selection ->
        QuickStatusDialog(
            selection    = selection,
            onDismiss    = viewModel::dismissDialog,
            onSelect     = { outcome, tags -> viewModel.logPage(selection.page, outcome, tags) },
            onViewInMushaf = { page -> viewModel.dismissDialog(); onPageOpen(page) }
        )
    }

    joint?.let { test ->
        QuarterJointSheet(
            test = test,
            onFinish = viewModel::finishJoint,
            onDismiss = viewModel::dismissJoint
        )
    }

    ayahPreview?.let { verse ->
        AyahPreviewBottomSheet(
            verse = verse,
            onOpenInMushaf = { viewModel.dismissAyahPreview(); onPageOpen(verse.firstPage) },
            onShare = { /* TODO */ },
            onDismiss = { viewModel.dismissAyahPreview() }
        )
    }

    editingUnitId?.let { unitNumber ->
        val card = quarters.firstOrNull { it.card.unit.number == unitNumber }?.card
        if (card != null) {
            val editing = editingSubject
            SubjectEditorSheet(
                ayahs = card.ayahsInQuarter,
                // The other subjects of this quarter: their verses cannot start this one as well.
                taken = card.subjects
                    .filter { it.entity.id != editing?.id }
                    .associate { it.entity.startAyahId to it.entity.subjectText },
                editing = editing,
                onSave = { text, startAyahId ->
                    viewModel.saveSubject(text, startAyahId, editing)
                    editingUnitId = null
                    editingSubject = null
                },
                onDismiss = {
                    editingUnitId = null
                    editingSubject = null
                }
            )
        }
    }

    subjectToDelete?.let { subject ->
        PaperDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text(stringResource(R.string.btn_delete)) },
            text = { Text(stringResource(R.string.delete_subject_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubject(subject)
                        subjectToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
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

/** The 240th and last quarter of the Quran: nothing follows it, so it has no joint to test. */
private const val LAST_QUARTER = 240

// ── The map: the juz at a glance ─────────────────────────────────────────────

@Composable
private fun JuzMap(
    pages: List<PageWithSurahs>,
    quarters: List<QuarterCardData>,
    weakest: Int?,
    onSelect: (Int) -> Unit
) {
    val tracked = pages.map { it.stability }.filter { it.isTracked }
    val average = if (tracked.isEmpty()) 0f else tracked.map { it.score }.average().toFloat()
    val worst = weakest?.let { quarters.getOrNull(it) }

    Folio(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                SectionLabel(stringResource(R.string.juz_health))
                Text(
                    text = if (tracked.isEmpty()) stringResource(R.string.no_revisions_juz)
                    else stringResource(R.string.pages_tracked_summary, (average * 100).roundToInt(), tracked.size, pages.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (tracked.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else healthTextColor(average)
                )
            }

            if (quarters.isNotEmpty()) {
                QuarterStrip(quarters = quarters, weakest = weakest, onSelect = onSelect)
            }

            if (worst != null && weakest != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.map_weakest,
                            worst.card.unit.quarterInJuz,
                            ((worst.health.average ?: 0f) * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PaperLink(stringResource(R.string.map_weakest_go), onClick = { onSelect(weakest) })
                }
            } else if (quarters.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Quick-status dialog ───────────────────────────────────────────────────────

@Composable
private fun QuickStatusDialog(
    selection:      PageSelection,
    onDismiss:      () -> Unit,
    onSelect:       (RecallOutcome, Set<MistakeTag>) -> Unit,
    onViewInMushaf: (Int) -> Unit = {}
) {
    var outcome by remember { mutableStateOf(RecallOutcome.INDEPENDENT) }
    var tags by remember { mutableStateOf(emptySet<MistakeTag>()) }

    PaperDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text          = stringResource(R.string.dialog_page_title, selection.page),
                    style         = MaterialTheme.typography.titleMedium,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.onSurface,
                )
                if (selection.surahLabel.isNotBlank()) {
                    Text(
                        text  = selection.surahLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text  = stringResource(R.string.state_evidence_label, selection.state.localizedLabel()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text  = stringResource(R.string.state_self_reported),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    OutcomePicker(
                        outcome = outcome,
                        tags = tags,
                        onOutcomeChange = { outcome = it },
                        onTagsChange = { tags = it },
                        preview = OutcomePreview(selection.page..selection.page)
                    )

                    Spacer(Modifier.height(12.dp))

                    PaperButton(
                        text = stringResource(R.string.btn_save),
                        onClick = { onSelect(outcome, tags) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
