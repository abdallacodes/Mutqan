package com.example.mutqan.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.ScopePlace
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.FolioSheetInsets
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperStepper
import com.example.mutqan.ui.components.PaperTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pages of every surah and every juz, read once for the pickers. */
private class MushafParts(val surahs: Map<Int, IntRange>, val juz: Map<Int, IntRange>, val lastPage: Int)

/** A place in a few words: "البقرة، آل عمران", "الجزء 30", "الصفحات 1–20", or how many when there are many. */
@Composable
fun placeLabel(place: ScopePlace): String {
    val separator = stringResource(R.string.scope_list_separator)
    val ids = place.ids.sorted()
    return when (place.kind) {
        ScopePlace.Kind.SURAHS ->
            if (ids.size <= 2) ids.joinToString(separator) { SurahData.nameOf(it) }
            else pluralStringResource(R.plurals.place_label_surahs, ids.size, ids.size)
        ScopePlace.Kind.JUZ -> when {
            ids.size == 1 -> stringResource(R.string.place_label_juz, ids[0])
            ids.size <= 3 -> stringResource(R.string.place_label_juz_list, ids.joinToString(separator))
            else -> pluralStringResource(R.plurals.place_label_juzs, ids.size, ids.size)
        }
        ScopePlace.Kind.PAGES -> {
            val first = ids.first()
            val last = ids.last()
            if (first == last) stringResource(R.string.place_label_page, first) else stringResource(R.string.place_label_pages, first, last)
        }
    }
}

/**
 * Where the questions come from in the Mushaf: chosen surahs, chosen juz, or a run of pages (one kind at a time), or
 * anywhere. [onApply] gets the choice (null for anywhere).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceSheet(place: ScopePlace?, onApply: (ScopePlace?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val parts by produceState<MushafParts?>(null) {
        value = withContext(Dispatchers.IO) {
            val quran = QuranRepository.getInstance(context)
            val juz = quran.pagesByJuz().mapValues { (_, pages) -> pages.first()..pages.last() }
            MushafParts(quran.pagesBySurah(), juz, juz.values.maxOfOrNull { it.last } ?: 604)
        }
    }
    var tab by remember { mutableIntStateOf(((place?.kind?.code ?: 1) - 1).coerceIn(0, 2)) }
    var surahs by remember { mutableStateOf(if (place?.kind == ScopePlace.Kind.SURAHS) place.ids else emptySet()) }
    var juz by remember { mutableStateOf(if (place?.kind == ScopePlace.Kind.JUZ) place.ids else emptySet()) }
    var from by remember { mutableIntStateOf(if (place?.kind == ScopePlace.Kind.PAGES) place.ids.min() else 1) }
    var to by remember { mutableIntStateOf(if (place?.kind == ScopePlace.Kind.PAGES) place.ids.max() else 20) }
    var typing by remember { mutableStateOf<Int?>(null) }

    /** The choice as it stands on the open tab; null when nothing is chosen there. */
    fun current(p: MushafParts): ScopePlace? = when (tab) {
        0 -> surahs.takeIf { it.isNotEmpty() }?.let { ids ->
            ScopePlace(ScopePlace.Kind.SURAHS, ids, ids.flatMapTo(HashSet()) { p.surahs[it]?.toList().orEmpty() })
        }
        1 -> juz.takeIf { it.isNotEmpty() }?.let { ids ->
            ScopePlace(ScopePlace.Kind.JUZ, ids, ids.flatMapTo(HashSet()) { p.juz[it]?.toList().orEmpty() })
        }
        else -> (from..to).toSet().let { pages -> ScopePlace(ScopePlace.Kind.PAGES, pages, pages) }
    }

    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = PaperSpace.Gutter)) {
            Text(
                text = stringResource(R.string.place_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            PaperTabs(
                labels = listOf(stringResource(R.string.place_tab_surahs), stringResource(R.string.place_tab_juz), stringResource(R.string.place_tab_pages)),
                selectedIndex = tab,
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(8.dp))
        }
        val p = parts
        if (p == null) {
            Spacer(Modifier.height(240.dp))
        } else when (tab) {
            0 -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(p.surahs.keys.sorted(), key = { it }) { id ->
                    val on = id in surahs
                    val pages = p.surahs.getValue(id)
                    Column(Modifier.padding(horizontal = PaperSpace.Gutter)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = on, role = Role.Checkbox) { surahs = if (on) surahs - id else surahs + id }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "$id. ${SurahData.nameOf(id)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (pages.first == pages.last) stringResource(R.string.place_label_page, pages.first)
                                else stringResource(R.string.place_label_pages, pages.first, pages.last),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Hairline(Modifier.fillMaxWidth())
                    }
                }
            }
            1 -> FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (id in p.juz.keys.sorted()) {
                    val on = id in juz
                    PaperChip(
                        selected = on,
                        onClick = { juz = if (on) juz - id else juz + id },
                        label = { Text(id.toString()) },
                        modifier = Modifier.width(56.dp),
                        showCheck = false
                    )
                }
            }
            else -> Column(
                Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.place_from_page), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PaperStepper(
                        value = from,
                        onChange = { from = it.coerceIn(1, p.lastPage); if (to < from) to = from },
                        min = 1,
                        max = p.lastPage,
                        onValueClick = { typing = 0 }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.place_to_page), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PaperStepper(
                        value = to,
                        onChange = { to = it.coerceIn(from, p.lastPage) },
                        min = from,
                        max = p.lastPage,
                        onValueClick = { typing = 1 }
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 12.dp, bottom = 24.dp)) {
            val chosen = p?.let { current(it) }
            Text(
                text = if (chosen == null) stringResource(R.string.place_choose_some)
                else placeLabel(chosen) + " · " + pluralStringResource(R.plurals.scope_span_pages, chosen.pages.size, chosen.pages.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            PaperButton(
                text = stringResource(R.string.scope_done),
                onClick = { chosen?.let(onApply) },
                enabled = chosen != null,
                modifier = Modifier.fillMaxWidth()
            )
            if (place != null) {
                PaperLink(
                    text = stringResource(R.string.place_anywhere),
                    onClick = { onApply(null) },
                    soft = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    typing?.let { which ->
        val last = parts?.lastPage ?: 604
        NumberDialog(
            initial = if (which == 0) from else to,
            onDone = { n ->
                typing = null
                if (n != null) {
                    if (which == 0) {
                        from = n.coerceIn(1, last)
                        if (to < from) to = from
                    } else {
                        to = n.coerceIn(from, last)
                    }
                }
            }
        )
    }
}
