package com.example.qmemo.ui.heatmap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.ui.components.HelpDialog
import com.example.qmemo.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onJuzClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HeatmapViewModel = viewModel(factory = HeatmapViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCoachmark by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.brain_title),
                        fontWeight = FontWeight.Black
                    )
                },
                actions = {
                    IconButton(onClick = { showCoachmark = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                // ── Global Stats ───────────────────────────────────
                item(span = { GridItemSpan(2) }) {
                    val trackedSum = uiState.juzSummaries.sumOf { it.trackedCount }
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
                            value    = "${(uiState.stats.stabilityIndex * 100).roundToInt()}%",
                            label    = stringResource(R.string.stat_stability_index),
                            sublabel = stringResource(R.string.stat_pages_tracked, trackedSum),
                            valueColor = healthColor(uiState.stats.stabilityIndex)
                        )
                    }
                }

                // ── Legend ──────────────────────────────────────────
                item(span = { GridItemSpan(2) }) {
                    HeatmapLegend()
                }

                // ── Juz Cards ───────────────────────────────────────
                if (uiState.juzSummaries.isEmpty()) {
                    items(30) { i -> JuzCardSkeleton(i + 1) }
                } else {
                    items(uiState.juzSummaries, key = { it.juzId }) { juz ->
                        JuzCard(
                            juz     = juz,
                            onClick = { onJuzClick(juz.juzId) }
                        )
                    }
                }
            }

            if (showCoachmark) {
                HelpDialog(
                    title = stringResource(R.string.brain_how_it_works),
                    description = stringResource(R.string.brain_how_it_works_intro),
                    onDismiss = { showCoachmark = false }
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
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(8.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier            = Modifier
                .heightIn(min = 108.dp)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color      = valueColor,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text          = label,
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Bold,
                color         = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                textAlign     = TextAlign.Center
            )
            Text(
                text      = sublabel,
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Legend ────────────────────────────────────────────────────────────────────

@Composable
private fun HeatmapLegend() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
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
                .then(
                    if (border) Modifier.background(color)
                    else Modifier
                )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Juz Card ──────────────────────────────────────────────────────────────────

@Composable
internal fun JuzCard(juz: JuzSummary, onClick: () -> Unit) {
    val healthScore  = (juz.healthPercent / 100f).coerceIn(0f, 1f)
    val healthClr    = juz.healthTone?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor  = juz.borderTone?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
    val trackedLabel = "${juz.trackedCount}/${juz.totalPages}${stringResource(R.string.pages_short_suffix)}"
    val healthValue = stringResource(R.string.juz_meta_overall_health_short, juz.healthPercent)

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )
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

            Spacer(Modifier.height(4.dp))

            LinearProgressIndicator(
                progress   = { healthScore },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color      = healthClr,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                strokeCap  = StrokeCap.Round
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

@Composable
private fun JuzCardSkeleton(juzId: Int) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
