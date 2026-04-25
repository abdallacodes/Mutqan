package com.example.qmemo.ui.surah

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qmemo.R
import com.example.qmemo.ui.theme.AmiriFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahListScreen(
    onSurahClick: (Int) -> Unit,
    onOpenMushaf: (page: Int) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SurahListViewModel = viewModel(factory = SurahListViewModelFactory(context))
    val surahs by viewModel.surahs.collectAsState()
    
    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text          = stringResource(R.string.surahs_title),
                            style         = MaterialTheme.typography.titleLarge,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color         = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text          = stringResource(R.string.surahs_subtitle),
                            style         = MaterialTheme.typography.labelSmall,
                            color         = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector        = Icons.Default.Info,
                            contentDescription = "Help",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(surahs, key = { it.info.id }) { item ->
                    SurahRow(
                        item           = item,
                        onClick        = { onSurahClick(item.info.id) },
                        onOpenInMushaf = {
                            if (item.startPage > 0) onOpenMushaf(item.startPage)
                        }
                    )
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            if (showHelpDialog) {
                com.example.qmemo.ui.components.HelpDialog(
                    title = stringResource(R.string.help_surahs_title),
                    description = stringResource(R.string.help_surahs_desc),
                    onDismiss = { showHelpDialog = false }
                )
            }
        }
    }
}

@Composable
private fun SurahRow(
    item: SurahListItem,
    onClick: () -> Unit,
    onOpenInMushaf: () -> Unit
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    onClick = onClick,
                    role    = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple()
                )
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "%3d".format(item.info.id),
                style      = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                modifier   = Modifier.width(36.dp)
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = item.info.getDisplayName(),
                    style      = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = if (isArabic) AmiriFontFamily else FontFamily.Default
                    ),
                    fontWeight = if (isArabic) FontWeight.SemiBold else FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                JuzBadge(juz = item.startJuz)

                if (item.groupCount > 0) {
                    GroupCountBadge(count = item.groupCount)
                } else {
                    Spacer(Modifier.size(18.dp))
                }
            }
        }

        IconButton(
            onClick     = onOpenInMushaf,
            enabled     = item.startPage > 0,
            colors      = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector        = Icons.Default.MenuBook,
                contentDescription = stringResource(R.string.open_in_mushaf),
                modifier           = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun JuzBadge(juz: Int) {
    if (juz == 0) return
    Surface(
        shape  = RoundedCornerShape(4.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text     = stringResource(R.string.juz_badge, juz),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupCountBadge(count: Int) {
    Surface(
        shape  = CircleShape,
        color  = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Box(
            modifier         = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = if (count > 9) stringResource(R.string.group_count_max) else "$count",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.primary,
                fontSize   = 9.sp
            )
        }
    }
}
