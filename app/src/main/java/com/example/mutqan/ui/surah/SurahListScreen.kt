package com.example.mutqan.ui.surah

import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material.icons.filled.MenuBook
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
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.theme.AmiriFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahListScreen(
    onSurahClick: (Int) -> Unit,
    onOpenMushaf: (page: Int) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SurahListViewModel = viewModel(factory = SurahListViewModelFactory(context))
    val surahs by viewModel.surahs.collectAsState()
    
    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.surahs_title),
                subtitle = stringResource(R.string.surahs_subtitle),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                    }
                    TopBarOverflowMenu(
                        onHelpClick = { showHelpDialog = true },
                        onSettingsClick = onSettingsClick
                    )
                }
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
                        thickness = 1.dp
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            if (showHelpDialog) {
                com.example.mutqan.ui.components.HelpDialog(
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
                text     = "%d".format(item.info.id),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.width(36.dp)
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = item.info.getDisplayName(),
                    style    = MaterialTheme.typography.titleLarge,
                    color    = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                JuzBadge(juz = item.startJuz)

                if (item.groupCount > 0) {
                    GroupCountBadge(count = item.groupCount)
                }
            }
        }

        IconButton(
            onClick     = onOpenInMushaf,
            enabled     = item.startPage > 0,
            colors      = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
    Text(
        text  = stringResource(R.string.juz_badge, juz),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GroupCountBadge(count: Int) {
    Text(
        text       = if (count > 9) stringResource(R.string.group_count_max) else "$count",
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.tertiary
    )
}
