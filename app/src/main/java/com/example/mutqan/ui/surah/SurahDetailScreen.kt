package com.example.mutqan.ui.surah

import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.IconMedallion
import com.example.mutqan.ui.components.Folio
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import kotlin.math.roundToInt
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.vault.MasterStrength

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SurahDetailScreen(
    surahId: Int,
    onBack: () -> Unit,
    onGroupClick: (groupId: Int) -> Unit = {},
    onOpenMushaf: (page: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SurahDetailViewModel = viewModel(
        factory = SurahDetailViewModelFactory(context, surahId)
    )

    val surahInfo         = viewModel.surahInfo
    val groupsWithVerses by viewModel.groupsWithVerses.collectAsState()
    val mushafStartPage by viewModel.mushafStartPage.collectAsState()
    val isArabic         = LocalConfiguration.current.locales[0].language == "ar"

    Scaffold(
        topBar = {
            PaperHeader(
                title = surahInfo?.getDisplayName() ?: stringResource(R.string.surah_fallback_title, surahId),
                onBack = onBack,
                actions = {
                    val page = mushafStartPage
                    IconButton(
                        onClick  = { if (page != null && page > 0) onOpenMushaf(page) },
                        enabled  = page != null && page > 0
                    ) {
                        Icon(
                            imageVector        = Icons.Default.MenuBook,
                            contentDescription = stringResource(R.string.open_in_mushaf),
                            tint               = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "head") {
                SectionHead(
                    title = stringResource(R.string.linked_groups_header) +
                        if (groupsWithVerses.isNotEmpty()) " (${groupsWithVerses.size})" else "",
                    first = true
                )
            }
            item(key = "hint") {
                Text(
                    text     = stringResource(if (groupsWithVerses.isEmpty()) R.string.no_groups_for_surah else R.string.linked_groups_hint),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                )
            }
            items(groupsWithVerses, key = { it.group.id }) { gwv ->
                SurahGroupCard(gwv = gwv, onClick = { onGroupClick(gwv.group.id) })
            }
        }
    }
}

// ── Group card ────────────────────────────────────────────────────────────────

/**
 * A group with verses in this surah, in the look of the app's hub rows: its name, its notes' first lines, how many of
 * its verses are here and elsewhere, and how well it is mastered.
 */
@Composable
private fun SurahGroupCard(gwv: GroupWithVerses, onClick: () -> Unit) {
    val quality = gwv.group.masterQuality
    val healthColor = when {
        quality >= 0.70f -> DifficultySmooth
        quality >= 0.40f -> DifficultyStruggled
        else          -> DifficultyCritical
    }
    val total = gwv.internalVerses.size + gwv.externalVerses.size
    val where = pluralStringResource(R.plurals.count_ayahs, total, total) + " · " +
        stringResource(R.string.group_meta_in_surah, gwv.internalVerses.size) + " · " +
        stringResource(R.string.group_meta_other_surahs, gwv.externalVerses.size)
    Folio(
        modifier       = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 5.dp),
        onClick        = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconMedallion(Icons.Outlined.Layers)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text     = gwv.group.description,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (gwv.group.memorizationNotes.isNotBlank()) {
                    Text(
                        text     = gwv.group.memorizationNotes,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text  = where,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text       = stringResource(R.string.percent_value, (quality * 100).roundToInt()),
                style      = MaterialTheme.typography.labelMedium,
                color      = healthColor,
                fontWeight = FontWeight.Bold
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
