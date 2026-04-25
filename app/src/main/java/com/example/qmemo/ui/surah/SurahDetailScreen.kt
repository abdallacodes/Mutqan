package com.example.qmemo.ui.surah

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
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.vault.MasterStrength

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
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text       = surahInfo?.getDisplayName()
                                             ?: stringResource(R.string.surah_fallback_title, surahId),
                            style      = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = if (isArabic) AmiriFontFamily else FontFamily.Default
                            ),
                            fontWeight = if (isArabic) FontWeight.SemiBold else FontWeight.Black,
                            color      = MaterialTheme.colorScheme.onBackground,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            stickyHeader {
                GroupsSectionHeader(count = groupsWithVerses.size)
            }

            if (groupsWithVerses.isEmpty()) {
                item {
                    EmptyGroupsHint(modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else {
                items(groupsWithVerses, key = { it.group.id }) { gwv ->
                    SurahGroupCard(
                        gwv         = gwv,
                        onClick     = { onGroupClick(gwv.group.id) },
                        modifier    = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Top section header ────────────────────────────────────────────────────────

@Composable
private fun GroupsSectionHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text          = stringResource(R.string.linked_groups_header),
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.Black,
                color         = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Text(
                text  = stringResource(R.string.linked_groups_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (count > 0) {
            Surface(
                shape  = RoundedCornerShape(4.dp),
                color  = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text       = "$count",
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Group card ────────────────────────────────────────────────────────────────

@Composable
private fun SurahGroupCard(
    gwv: GroupWithVerses,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strength    = MasterStrength.fromId(gwv.group.masterStrength)
    val primary     = MaterialTheme.colorScheme.primary
    val tertiary    = MaterialTheme.colorScheme.tertiary
    val stripeColor = if (gwv.hasExternal) tertiary else primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = stripeColor,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
        )

        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Text(
                    text       = gwv.group.description,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.weight(1f),
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                StrengthBadge(strength = strength)
            }

            if (gwv.group.memorizationNotes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text     = gwv.group.memorizationNotes,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaBadge(
                    text = stringResource(
                        R.string.group_meta_total_verses,
                        gwv.internalVerses.size + gwv.externalVerses.size
                    ),
                    borderColor = MaterialTheme.colorScheme.outline
                )
                MetaBadge(
                    text = stringResource(R.string.group_meta_in_surah, gwv.internalVerses.size),
                    borderColor = primary.copy(alpha = 0.5f)
                )
                MetaBadge(
                    text = stringResource(R.string.group_meta_other_surahs, gwv.externalVerses.size),
                    borderColor = tertiary.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String, borderColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Strength badge ────────────────────────────────────────────────────────────

private fun strengthBadgeColor(s: MasterStrength): Color = when (s) {
    MasterStrength.WEAK   -> com.example.qmemo.ui.theme.DifficultyCritical
    MasterStrength.STABLE -> com.example.qmemo.ui.theme.DifficultyStruggled
    MasterStrength.SOLID  -> com.example.qmemo.ui.theme.DifficultySmooth
}

@Composable
private fun StrengthBadge(strength: MasterStrength) {
    val color = strengthBadgeColor(strength)
    Surface(
        shape  = RoundedCornerShape(4.dp),
        color  = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text       = strength.localizedLabel(),
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyGroupsHint(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = stringResource(R.string.no_groups_for_surah),
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
