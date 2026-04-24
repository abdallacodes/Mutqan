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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.qmemo.data.local.dao.MemberVerseRef
import com.example.qmemo.ui.components.QuickPeekBottomSheet
import com.example.qmemo.ui.components.QuickPeekTarget
import com.example.qmemo.ui.components.localizedLabel
import com.example.qmemo.ui.vault.MasterStrength

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SurahDetailScreen(
    surahId: Int,
    onBack: () -> Unit,
    onGroupClick: (groupId: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SurahDetailViewModel = viewModel(
        factory = SurahDetailViewModelFactory(context, surahId)
    )

    val surahInfo        = viewModel.surahInfo
    val groupsWithVerses by viewModel.groupsWithVerses.collectAsState()
    val isArabic         = LocalConfiguration.current.locales[0].language == "ar"

    var quickPeekTarget by remember { mutableStateOf<QuickPeekTarget?>(null) }

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
                        onVersePeek = { quickPeekTarget = it },
                        modifier    = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    quickPeekTarget?.let { target ->
        QuickPeekBottomSheet(
            target    = target,
            onDismiss = { quickPeekTarget = null }
        )
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
    onVersePeek: (QuickPeekTarget) -> Unit,
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

            if (gwv.internalVerses.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                InlineSubHeader(label = stringResource(R.string.section_in_this_surah), accent = primary)
                Spacer(Modifier.height(4.dp))
                gwv.internalVerses.forEach { verse ->
                    InlineVerseRow(
                        verse  = verse,
                        accent = primary,
                        onPeek = { onVersePeek(QuickPeekTarget(verse.verseId, verse.surahId, verse.ayahNumber)) }
                    )
                }
            }

            if (gwv.externalVerses.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                InlineSubHeader(label = stringResource(R.string.section_other_surahs), accent = tertiary)
                Spacer(Modifier.height(4.dp))
                gwv.externalVerses.forEach { verse ->
                    InlineExternalVerseRow(
                        verse  = verse,
                        onPeek = { onVersePeek(QuickPeekTarget(verse.verseId, verse.surahId, verse.ayahNumber)) }
                    )
                }
            }
        }
    }
}

// ── Inline sub-section label ──────────────────────────────────────────────────

@Composable
private fun InlineSubHeader(label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(accent, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text          = label,
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Bold,
            color         = accent,
            letterSpacing = 1.sp
        )
    }
}

// ── Internal verse row ────────────────────────────────────────────────────────

@Composable
private fun InlineVerseRow(
    verse: MemberVerseRef,
    accent: Color,
    onPeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPeek)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = stringResource(R.string.ayah_n, verse.ayahNumber),
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text  = stringResource(R.string.page_n, verse.pageNumber),
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.8f)
        )
    }
}

// ── External verse row ────────────────────────────────────────────────────────

@Composable
private fun InlineExternalVerseRow(
    verse: MemberVerseRef,
    onPeek: () -> Unit
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val tertiary  = MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPeek)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "${verse.surahId}. ${SurahData.nameOf(verse.surahId)}",
                style      = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = if (isArabic) AmiriFontFamily else FontFamily.Default
                ),
                fontWeight    = FontWeight.SemiBold,
                color         = tertiary,
                letterSpacing = 0.3.sp
            )
            Text(
                text       = stringResource(R.string.ayah_n, verse.ayahNumber),
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text  = stringResource(R.string.page_n, verse.pageNumber),
            style = MaterialTheme.typography.labelSmall,
            color = tertiary.copy(alpha = 0.8f)
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
