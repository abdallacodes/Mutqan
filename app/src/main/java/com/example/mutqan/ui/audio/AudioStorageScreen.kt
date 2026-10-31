package com.example.mutqan.ui.audio

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.audio.RecitationDownloads
import com.example.mutqan.domain.audio.RecitationPlayer
import com.example.mutqan.domain.audio.Reciter
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the recitation takes on the device: the downloads, reciter by reciter and surah by surah, and the streaming cache. */
@Composable
fun AudioStorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads = remember(context) { RecitationDownloads.get(context) }
    val state by downloads.state.collectAsState()

    var cacheBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { RecitationPlayer.cacheBytes(context) } }

    // Kept surahs by reciter, each reciter's surahs in order.
    val byReciter = remember(state.sizes, state.downloaded) {
        Reciter.entries.mapNotNull { reciter ->
            val surahs = state.downloaded
                .mapNotNull { key -> key.substringAfter(':').toIntOrNull()?.takeIf { key == RecitationDownloads.key(reciter, it) } }
                .sorted()
            if (surahs.isEmpty()) null else reciter to surahs
        }
    }
    val total = state.sizes.values.sum()

    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingSurah by remember { mutableStateOf<Pair<Reciter, Int>?>(null) }
    var deletingReciter by remember { mutableStateOf<Reciter?>(null) }
    var clearingCache by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.audio_storage_title),
                subtitle = stringResource(R.string.audio_storage_subtitle),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)) {
                    SectionLabel(stringResource(R.string.audio_downloads))
                    Text(
                        text = Formatter.formatShortFileSize(context, total),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (byReciter.isEmpty()) {
                        Text(
                            text = stringResource(R.string.audio_storage_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Hairline()
            }

            byReciter.forEach { (reciter, surahs) ->
                val open = expanded == reciter.id
                val reciterBytes = surahs.sumOf { state.sizes[RecitationDownloads.key(reciter, it)] ?: 0L }
                item(key = "reciter-${reciter.id}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { expanded = if (open) null else reciter.id }
                            .padding(start = PaperSpace.Gutter, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(reciter.nameRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.audio_storage_count, surahs.size) + " · " +
                                    Formatter.formatShortFileSize(context, reciterBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deletingReciter = reciter }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.audio_delete_confirm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    Hairline()
                }
                if (open) {
                    items(surahs.size, key = { "${reciter.id}-${surahs[it]}" }) { i ->
                        val surah = surahs[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = PaperSpace.Gutter + 12.dp, end = 4.dp)
                        ) {
                            Text(
                                text = SurahData.nameOf(surah),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = Formatter.formatShortFileSize(context, state.sizes[RecitationDownloads.key(reciter, surah)] ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { deletingSurah = reciter to surah }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.audio_delete_confirm),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item(key = "end-${reciter.id}") { Hairline() }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 20.dp)) {
                    SectionLabel(stringResource(R.string.audio_storage_cache))
                    Text(
                        text = Formatter.formatShortFileSize(context, cacheBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.audio_storage_cache_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (cacheBytes > 0) {
                        PaperLink(
                            text = stringResource(R.string.audio_storage_clear_cache),
                            onClick = { clearingCache = true },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    deletingSurah?.let { (reciter, surah) ->
        DeleteDownloadDialog(
            surahName = SurahData.nameOf(surah),
            reciterName = stringResource(reciter.nameRes),
            onConfirm = { downloads.delete(reciter, surah); deletingSurah = null },
            onDismiss = { deletingSurah = null }
        )
    }
    deletingReciter?.let { reciter ->
        val size = Formatter.formatShortFileSize(
            context,
            byReciter.firstOrNull { it.first == reciter }?.second.orEmpty().sumOf { state.sizes[RecitationDownloads.key(reciter, it)] ?: 0L }
        )
        ConfirmDialog(
            title = stringResource(R.string.audio_storage_reciter_title, stringResource(reciter.nameRes)),
            body = stringResource(R.string.audio_storage_reciter_body, size),
            onConfirm = { downloads.deleteReciter(reciter); deletingReciter = null },
            onDismiss = { deletingReciter = null }
        )
    }
    if (clearingCache) {
        ConfirmDialog(
            title = stringResource(R.string.audio_storage_clear_cache),
            body = stringResource(R.string.audio_storage_clear_cache_body),
            onConfirm = {
                clearingCache = false
                scope.launch {
                    withContext(Dispatchers.IO) { RecitationPlayer.clearCache(context) }
                    cacheBytes = withContext(Dispatchers.IO) { RecitationPlayer.cacheBytes(context) }
                }
            },
            onDismiss = { clearingCache = false }
        )
    }
}
