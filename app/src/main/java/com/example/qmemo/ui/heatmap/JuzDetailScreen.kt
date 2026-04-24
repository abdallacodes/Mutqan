package com.example.qmemo.ui.heatmap

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.qmemo.ui.theme.AmiriFontFamily
import com.example.qmemo.ui.theme.DifficultyCritical
import com.example.qmemo.ui.theme.DifficultySmooth
import com.example.qmemo.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuzDetailScreen(
    juzId:  Int,
    onBack: () -> Unit
) {
    val context   = LocalContext.current
    val viewModel = viewModel<JuzDetailViewModel>(
        key     = "juz_$juzId",
        factory = JuzDetailViewModelFactory(context, juzId)
    )

    val pages        by viewModel.pagesWithSurahs.collectAsState()
    val surahRange   by viewModel.surahRange.collectAsState()
    val selectedPage by viewModel.selectedPage.collectAsState()
    val isArabic     = LocalConfiguration.current.locales[0].language == "ar"

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
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyVerticalGrid(
            columns               = GridCells.Fixed(4),
            modifier              = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding        = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                JuzHealthSummary(pages = pages)
            }

            if (pages.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
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

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (selectedPage != null) {
        QuickStatusDialog(
            selection = selectedPage!!,
            onDismiss = viewModel::dismissDialog,
            onSelect  = { diff -> viewModel.logPage(selectedPage!!.page, diff) }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 5.dp),
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
    selection: PageSelection,
    onDismiss: () -> Unit,
    onSelect:  (Int) -> Unit
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickStatusButton(
                    label       = stringResource(R.string.quick_status_smooth),
                    description = stringResource(R.string.quick_status_smooth_desc),
                    color       = DifficultySmooth,
                    onClick     = { onSelect(1) }
                )
                QuickStatusButton(
                    label       = stringResource(R.string.quick_status_struggled),
                    description = stringResource(R.string.quick_status_struggled_desc),
                    color       = DifficultyStruggled,
                    onClick     = { onSelect(2) }
                )
                QuickStatusButton(
                    label       = stringResource(R.string.quick_status_critical),
                    description = stringResource(R.string.quick_status_critical_desc),
                    color       = DifficultyCritical,
                    onClick     = { onSelect(3) }
                )
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

@Composable
private fun QuickStatusButton(
    label:       String,
    description: String,
    color:       Color,
    onClick:     () -> Unit
) {
    Button(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(8.dp),
        colors    = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor   = color
        ),
        border    = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text          = label,
                    style         = MaterialTheme.typography.labelMedium,
                    fontWeight    = FontWeight.Black,
                    color         = color,
                    letterSpacing = 1.sp
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = color.copy(alpha = 0.70f)
                )
            }
        }
    }
}
