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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupDetailScreen(
    groupId: Int,
    currentSurahId: Int,
    onBack: () -> Unit,
    onOpenMushaf: (Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: GroupDetailViewModel = viewModel(
        factory = GroupDetailViewModelFactory(context, groupId, currentSurahId)
    )

    val group         by viewModel.group.collectAsState()
    val contextVerses by viewModel.contextVerses.collectAsState()
    val clashVerses   by viewModel.clashVerses.collectAsState()

    val isUnifiedVaultView = currentSurahId <= 0
    val currentSurahName = if (isUnifiedVaultView) "" else SurahData.nameOf(currentSurahId)

    var quickPeekTarget by remember { mutableStateOf<QuickPeekTarget?>(null) }

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
                            text          = group?.description ?: stringResource(R.string.group_detail_fallback_title),
                            style         = MaterialTheme.typography.titleMedium,
                            fontWeight    = FontWeight.Black,
                            color         = MaterialTheme.colorScheme.onBackground,
                            maxLines      = 1,
                            overflow      = TextOverflow.Ellipsis
                        )
                        Text(
                            text          = if (isUnifiedVaultView) {
                                stringResource(R.string.mutashabihat_breadcrumb_all)
                            } else {
                                stringResource(R.string.mutashabihat_breadcrumb, currentSurahName)
                            },
                            style         = MaterialTheme.typography.labelSmall,
                            color         = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
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

            val memorizationNotes = group?.memorizationNotes.orEmpty()
            if (memorizationNotes.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape    = RoundedCornerShape(8.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text       = stringResource(R.string.group_notes_detail_label),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = memorizationNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (isUnifiedVaultView) {
                val allVerses = (contextVerses + clashVerses).sortedWith(
                    compareBy<MemberVerseRef> { it.surahId }.thenBy { it.ayahNumber }
                )

                stickyHeader {
                    DetailSectionHeader(
                        label = stringResource(R.string.section_all_linked_verses),
                        subtitle = stringResource(R.string.section_all_linked_verses_subtitle),
                        accent = MaterialTheme.colorScheme.primary,
                        count = allVerses.size
                    )
                }

                if (allVerses.isEmpty()) {
                    item {
                        DetailEmptyHint(
                            message = stringResource(R.string.no_linked_verses),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(allVerses, key = { "all_${it.verseId}" }) { ref ->
                        VerseRefCard(
                            ref = ref,
                            accentColor = MaterialTheme.colorScheme.primary,
                            showSurahName = true,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onClick = {
                                quickPeekTarget = QuickPeekTarget(
                                    verseId = ref.verseId,
                                    surahId = ref.surahId,
                                    ayahNumber = ref.ayahNumber,
                                    pageNumber = ref.pageNumber
                                )
                            }
                        )
                    }
                }
            } else {
                stickyHeader {
                    DetailSectionHeader(
                        label    = stringResource(R.string.section_context),
                        subtitle = stringResource(R.string.section_context_subtitle, currentSurahName),
                        accent   = MaterialTheme.colorScheme.primary,
                        count    = contextVerses.size
                    )
                }

                if (contextVerses.isEmpty()) {
                    item {
                        DetailEmptyHint(
                            message  = stringResource(R.string.no_context_verses),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(contextVerses, key = { "ctx_${it.verseId}" }) { ref ->
                        VerseRefCard(
                            ref           = ref,
                            accentColor   = MaterialTheme.colorScheme.primary,
                            showSurahName = false,
                            modifier      = Modifier.padding(horizontal = 16.dp),
                            onClick       = {
                                quickPeekTarget = QuickPeekTarget(
                                    verseId    = ref.verseId,
                                    surahId    = ref.surahId,
                                    ayahNumber = ref.ayahNumber,
                                    pageNumber = ref.pageNumber
                                )
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }

                stickyHeader {
                    DetailSectionHeader(
                        label    = stringResource(R.string.section_clashes),
                        subtitle = stringResource(R.string.section_clashes_subtitle),
                        accent   = MaterialTheme.colorScheme.tertiary,
                        count    = clashVerses.size
                    )
                }

                if (clashVerses.isEmpty()) {
                    item {
                        DetailEmptyHint(
                            message  = stringResource(R.string.no_clash_verses),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(clashVerses, key = { "cls_${it.verseId}" }) { ref ->
                        VerseRefCard(
                            ref           = ref,
                            accentColor   = MaterialTheme.colorScheme.tertiary,
                            showSurahName = true,
                            modifier      = Modifier.padding(horizontal = 16.dp),
                            onClick       = {
                                quickPeekTarget = QuickPeekTarget(
                                    verseId    = ref.verseId,
                                    surahId    = ref.surahId,
                                    ayahNumber = ref.ayahNumber,
                                    pageNumber = ref.pageNumber
                                )
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    quickPeekTarget?.let { target ->
        QuickPeekBottomSheet(
            target    = target,
            onDismiss = { quickPeekTarget = null },
            onOpenMushaf = onOpenMushaf
        )
    }
}

// ── Verse reference card ──────────────────────────────────────────────────────

@Composable
private fun VerseRefCard(
    ref: MemberVerseRef,
    accentColor: Color,
    showSurahName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

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
                    color = accentColor,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showSurahName) {
                    Text(
                        text          = SurahData.nameOf(ref.surahId),
                        style         = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = if (isArabic) AmiriFontFamily else FontFamily.Default
                        ),
                        color         = accentColor,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text       = stringResource(R.string.ayah_n, ref.ayahNumber),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape  = RoundedCornerShape(4.dp),
                color  = accentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text       = stringResource(R.string.page_n, ref.pageNumber),
                    modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    color      = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Sticky section header ─────────────────────────────────────────────────────

@Composable
private fun DetailSectionHeader(
    label: String,
    subtitle: String,
    accent: Color,
    count: Int
) {
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
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text          = label,
                style         = MaterialTheme.typography.labelMedium,
                fontWeight    = FontWeight.Black,
                color         = accent,
                letterSpacing = 2.sp
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (count > 0) {
            Surface(
                shape  = RoundedCornerShape(4.dp),
                color  = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
            ) {
                Text(
                    text       = "$count",
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color      = accent
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun DetailEmptyHint(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = message,
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
