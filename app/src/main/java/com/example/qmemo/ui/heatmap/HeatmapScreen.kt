package com.example.qmemo.ui.heatmap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.pow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.domain.PageStability
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt

// ── Colour helpers (internal so JuzDetailScreen can share them) ───────────────

internal fun pageColor(ps: PageStability): Color {
    if (!ps.isTracked) return Color(0xFF1E2020)
    return healthColor(ps.score)
}

internal fun healthColor(score: Float): Color {
    if (score <= 0f) return Color(0xFF1E2020)
    val hue = score.coerceIn(0f, 1f) * 120f
    return Color.hsv(hue, saturation = 0.82f, value = 0.76f)
}

internal fun adaptiveTextColor(bgColor: Color): Color {
    fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val lum = 0.2126 * lin(bgColor.red.toDouble()) +
              0.7152 * lin(bgColor.green.toDouble()) +
              0.0722 * lin(bgColor.blue.toDouble())
    return if (lum > 0.179) Color(0xFF0D0D0D) else Color.White
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onJuzClick: (Int) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context   = LocalContext.current
    val viewModel = viewModel<HeatmapViewModel>(factory = HeatmapViewModelFactory(context))

    val state        by viewModel.heatmapState.collectAsState()
    val juzSummaries by viewModel.juzSummaries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text          = stringResource(R.string.brain_title),
                            style         = MaterialTheme.typography.titleLarge,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color         = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text          = stringResource(R.string.brain_subtitle),
                            style         = MaterialTheme.typography.labelSmall,
                            color         = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
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

        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            modifier              = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding        = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {

            item(span = { GridItemSpan(maxLineSpan) }) {
                DashboardHeader(stats = state.stats)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                HeatmapLegend()
            }

            if (juzSummaries.isEmpty()) {
                items(30) { idx ->
                    JuzCardSkeleton(juzId = idx + 1)
                }
            } else {
                items(juzSummaries, key = { it.juzId }) { juz ->
                    JuzCard(juz = juz, onClick = { onJuzClick(juz.juzId) })
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Dashboard header ──────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(stats: DashboardStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                modifier   = Modifier.weight(1f),
                value      = "${stats.revisionDebt}",
                label      = stringResource(R.string.stat_revision_debt),
                sublabel   = stringResource(R.string.stat_debt_sublabel),
                valueColor = when {
                    stats.revisionDebt == 0 -> DifficultySmooth
                    stats.revisionDebt < 30 -> DifficultyStruggled
                    else                    -> DifficultyCritical
                }
            )
            StatCard(
                modifier   = Modifier.weight(1f),
                value      = "${(stats.stabilityIndex * 100).roundToInt()}%",
                label      = stringResource(R.string.stat_stability_index),
                sublabel   = stringResource(R.string.stat_pages_tracked, stats.trackedPages),
                valueColor = when {
                    stats.stabilityIndex >= 0.7f -> DifficultySmooth
                    stats.stabilityIndex >= 0.4f -> DifficultyStruggled
                    else                         -> DifficultyCritical
                }
            )
            StatCard(
                modifier   = Modifier.weight(1f),
                value      = "${stats.criticalCount}",
                label      = stringResource(R.string.stat_critical_label),
                sublabel   = stringResource(R.string.stat_critical_sublabel),
                valueColor = if (stats.criticalCount == 0) DifficultySmooth else DifficultyCritical
            )
        }
    }
}

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
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
        LegendDot(color = Color(0xFF1E2020),   label = stringResource(R.string.legend_untracked), border = true)
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
    val healthScore  = juz.healthPercent / 100f
    val healthClr    = if (juz.trackedCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
                       else healthColor(healthScore)
    val borderColor  = if (juz.trackedCount == 0) MaterialTheme.colorScheme.outline
                       else healthClr.copy(alpha = 0.45f)

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text          = stringResource(R.string.juz_label),
                        style         = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color         = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text       = "${juz.juzId}",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text       = if (juz.trackedCount == 0) stringResource(R.string.untracked_dash)
                                     else "${juz.healthPercent}%",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color      = healthClr,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text  = "${juz.trackedCount}/${juz.totalPages}${stringResource(R.string.pages_short_suffix)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (juz.trackedCount > 0) {
                LinearProgressIndicator(
                    progress      = { healthScore },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color         = healthClr,
                    trackColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    gapSize       = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(8.dp))
            }

            JuzMiniMap(pages = juz.pageStabilities)
        }
    }
}

@Composable
private fun JuzCardSkeleton(juzId: Int) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text          = stringResource(R.string.juz_label),
                        style         = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color         = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text       = "$juzId",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text  = stringResource(R.string.untracked_dash),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ── Mini-Map ──────────────────────────────────────────────────────────────────

@Composable
internal fun JuzMiniMap(pages: List<PageStability>, squareSize: Int = 8) {
    val cols    = 5
    val sqDp    = squareSize.dp
    val chunked = pages.chunked(cols)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        chunked.forEach { rowPages ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowPages.forEach { ps ->
                    Box(
                        modifier = Modifier
                            .size(sqDp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(pageColor(ps))
                    )
                }
                repeat(cols - rowPages.size) {
                    Spacer(Modifier.size(sqDp))
                }
            }
        }
    }
}
