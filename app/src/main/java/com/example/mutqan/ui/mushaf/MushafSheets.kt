package com.example.mutqan.ui.mushaf

import com.example.mutqan.ui.components.FolioSheetInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranSurah
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.revision.OutcomePicker
import com.example.mutqan.ui.revision.OutcomePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.mutqan.ui.topics.ThemesList
import com.example.mutqan.ui.topics.themesAvailable

private const val TOTAL_PAGES = 604

/** Jump to a surah, a juz, a page number or a passage by what it is about. [onGo] gets the page to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafJumpSheet(quran: QuranRepository, currentPage: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val themes = themesAvailable()
    val data by produceState<Pair<List<QuranSurah>, Map<Int, List<Int>>>?>(null) {
        value = withContext(Dispatchers.IO) { quran.surahs() to quran.pagesByJuz() }
    }

    ModalBottomSheet(contentWindowInsets = FolioSheetInsets, onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.mushaf_go_to),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            PaperTabs(
                labels = listOfNotNull(
                    stringResource(R.string.mushaf_tab_surah),
                    stringResource(R.string.mushaf_tab_juz),
                    stringResource(R.string.mushaf_tab_page),
                    if (themes) stringResource(R.string.mushaf_tab_themes) else null
                ),
                selectedIndex = tab,
                onSelect = { tab = it }
            )
            when (tab) {
                0 -> LazyColumn(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                    items(data?.first.orEmpty(), key = { it.id }) { surah ->
                        HubRow(
                            title = SurahData.nameOf(surah.id),
                            subtitle = stringResource(
                                R.string.mushaf_surah_row,
                                pluralStringResource(R.plurals.count_ayahs, surah.versesCount, surah.versesCount),
                                surah.firstPage
                            ),
                            onClick = { onGo(surah.firstPage) }
                        )
                    }
                }
                1 -> LazyColumn(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                    items(data?.second?.entries?.sortedBy { it.key }.orEmpty(), key = { it.key }) { (juz, pages) ->
                        HubRow(
                            title = stringResource(R.string.mushaf_juz_row, juz),
                            subtitle = stringResource(R.string.dialog_page_title, pages.first()),
                            onClick = { onGo(pages.first()) }
                        )
                    }
                }
                3 -> ThemesList(onGo = onGo)
                else -> {
                    var text by rememberSaveable { mutableStateOf(currentPage.toString()) }
                    val page = text.toIntOrNull()?.takeIf { it in 1..TOTAL_PAGES }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.mushaf_page_field)) },
                            singleLine = true,
                            isError = text.isNotEmpty() && page == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        PaperButton(
                            text = stringResource(R.string.mushaf_go),
                            onClick = { page?.let(onGo) },
                            enabled = page != null,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Says how reciting a page went and logs it as a revision of that page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushafRevisedSheet(page: Int, onSave: (RecallOutcome, Set<MistakeTag>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var outcome by remember { mutableStateOf(RecallOutcome.INDEPENDENT) }
    var tags by remember { mutableStateOf(emptySet<MistakeTag>()) }

    ModalBottomSheet(contentWindowInsets = FolioSheetInsets, onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.mushaf_revised_title, page),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutcomePicker(
                outcome = outcome,
                tags = tags,
                onOutcomeChange = { outcome = it },
                onTagsChange = { tags = it },
                preview = OutcomePreview(page..page)
            )
            PaperButton(
                text = stringResource(R.string.btn_save),
                onClick = { onSave(outcome, if (outcome.isRecallAttempt) tags else emptySet()) },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
        }
    }
}
