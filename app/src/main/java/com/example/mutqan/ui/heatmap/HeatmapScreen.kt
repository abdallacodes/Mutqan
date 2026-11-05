package com.example.mutqan.ui.heatmap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperSlider
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.ui.components.HelpDialog
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.theme.*
import kotlin.math.roundToInt
import com.example.mutqan.ui.components.Folio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onJuzClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: HeatmapViewModel = viewModel(factory = HeatmapViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.brain_title),
                onBack = onBack,
                actions = {
                    TopBarOverflowMenu(
                        onHelpClick = { showHelp = true },
                        onSettingsClick = onSettingsClick
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Mode Toggle ───────────────────────────────────
                item(span = { GridItemSpan(2) }) {
                    PaperTabs(
                        labels = listOf(
                            stringResource(R.string.mode_memory_health),
                            stringResource(R.string.mode_structural_flow)
                        ),
                        selectedIndex = if (uiState.isStructuralMode) 1 else 0,
                        onSelect = { viewModel.toggleStructuralMode(it == 1) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // ── Global Stats ───────────────────────────────────
                if (!uiState.isStructuralMode) {
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                value    = "${uiState.stats.revisionDebt}",
                                label    = stringResource(R.string.stat_revision_debt),
                                sublabel = stringResource(R.string.stat_debt_sublabel),
                                valueColor = if (uiState.stats.revisionDebt > 5) DifficultyCritical else DifficultySmooth
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                value    = stringResource(R.string.percent_value, (uiState.stats.stabilityIndex * 100).roundToInt()),
                                label    = stringResource(R.string.stat_stability_index),
                                sublabel = pluralStringResource(R.plurals.stat_pages_tracked, uiState.stats.trackedPages, uiState.stats.trackedPages),
                                valueColor = healthTextColor(uiState.stats.stabilityIndex)
                            )
                        }
                    }

                    // ── Forecast Slider ─────────────────────────────────
                    item(span = { GridItemSpan(2) }) {
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.stat_forecast_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        pluralStringResource(R.plurals.count_days, uiState.forecastDays, uiState.forecastDays),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                PaperSlider(
                                    value = uiState.forecastDays.toFloat(),
                                    onValueChange = { viewModel.onForecastChange(it.roundToInt()) },
                                    valueRange = 0f..30f,
                                    steps = 30
                                )
                            }
                        }
                    }
                }

                // ── Legend ──────────────────────────────────────────
                if (!uiState.isStructuralMode) {
                    item(span = { GridItemSpan(2) }) {
                        HeatmapLegend()
                    }
                }

                // ── Juz Cards ───────────────────────────────────────
                if (uiState.juzSummaries.isEmpty()) {
                    items(30) { i -> JuzCardSkeleton(i + 1) }
                } else {
                    items(uiState.juzSummaries, key = { it.juzId }) { juz ->
                        JuzCard(
                            juz     = juz,
                            isStructural = uiState.isStructuralMode,
                            onClick = { onJuzClick(juz.juzId) }
                        )
                    }
                }
            }

            if (showHelp) {
                HelpDialog(
                    title = stringResource(R.string.help_brain_title),
                    description = stringResource(R.string.help_brain_desc),
                    onDismiss = { showHelp = false }
                )
            }
        }
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    sublabel: String,
    valueColor: Color
) {
    Folio(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(
            text     = value,
            style    = MaterialTheme.typography.displaySmall,
            color    = valueColor,
            maxLines = 1
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text  = sublabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Wraps instead of squeezing the last item when the text is large.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapLegend() {
    FlowRow(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(4.dp)
    ) {
        LegendDot(color = DifficultySmooth,    label = stringResource(R.string.legend_fresh))
        LegendDot(color = DifficultyStruggled, label = stringResource(R.string.legend_fading))
        LegendDot(color = DifficultyCritical,  label = stringResource(R.string.legend_critical))
        LegendDot(color = MaterialTheme.colorScheme.outlineVariant, label = stringResource(R.string.legend_untracked), border = true)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, border: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun JuzCard(
    juz: JuzSummary, 
    isStructural: Boolean = false,
    onClick: () -> Unit
) {
    val healthScore  = (juz.healthPercent / 100f).coerceIn(0f, 1f)
    val healthClr    = juz.healthTone?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor  = MaterialTheme.colorScheme.outlineVariant
    val trackedLabel = "${juz.trackedCount}/${juz.totalPages}${stringResource(R.string.pages_short_suffix)}"
    val healthValue = stringResource(R.string.juz_meta_overall_health_short, juz.healthPercent)

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = FolioTheme.colors.folio,
        border   = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isStructural) 80.dp else 132.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.juz_label)} ${juz.juzId}",
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )
                if (!isStructural) {
                    Column(
                        modifier = Modifier.width(64.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = healthValue,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.juz_meta_overall_health_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            if (!isStructural) {
                Spacer(Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress   = { healthScore },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color      = healthClr,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeCap  = StrokeCap.Butt
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = trackedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun JuzCardSkeleton(juzId: Int) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = FolioTheme.colors.folio,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text          = "${stringResource(R.string.juz_label)} $juzId",
                    modifier      = Modifier.weight(1f),
                    style         = MaterialTheme.typography.titleSmall,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight    = FontWeight.Bold
                )
                Text(
                    text  = stringResource(R.string.juz_meta_overall_health_short, 0),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            )
        }
    }
}
