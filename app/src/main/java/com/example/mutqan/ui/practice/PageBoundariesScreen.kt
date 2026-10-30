package com.example.mutqan.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.components.EmptyStateCard
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.AmiriFontFamily

/**
 * Know the edges of each page. Three ways to ask: from the page number, from the page's first
 * verse, or from the verses either side of it. The user reveals the answer and says how it went.
 */
@Composable
fun PageBoundariesScreen(onBack: () -> Unit, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val viewModel: PageBoundariesViewModel = viewModel(factory = PageBoundariesViewModelFactory(context))
    val state by viewModel.state.collectAsState()
    val variants = ExerciseType.PAGE_BOUNDARIES

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.ex_boundaries_title),
                subtitle = if (state.done > 0) stringResource(R.string.ex_done_session, state.done) else null,
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperTabs(
                labels = listOf(
                    stringResource(R.string.ex_variant_page),
                    stringResource(R.string.ex_variant_start),
                    stringResource(R.string.ex_variant_neighbours)
                ),
                selectedIndex = variants.indexOf(state.variant),
                onSelect = { viewModel.setVariant(variants[it]) }
            )

            // ── Which pages ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.hasCoverage) {
                    PaperChip(
                        selected = state.memorisedOnly,
                        onClick = { viewModel.setMemorisedOnly(!state.memorisedOnly) },
                        label = { Text(stringResource(R.string.ex_scope_memorised)) }
                    )
                }
                Spacer(Modifier.weight(1f))
                PaperLink(text = "−", onClick = { viewModel.changeJuz(-1) })
                Text(
                    text = if (state.juz == 0) stringResource(R.string.ex_juz_all) else stringResource(R.string.plan_item_juz, state.juz),
                    style = MaterialTheme.typography.labelLarge
                )
                PaperLink(text = "+", onClick = { viewModel.changeJuz(1) })
            }
            Hairline()

            val round = state.round
            if (state.noPages || round == null) {
                if (state.noPages) {
                    EmptyStateCard(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.ex_no_pages_title),
                        description = stringResource(R.string.ex_no_pages_body),
                        modifier = Modifier.height(280.dp)
                    )
                }
                return@Column
            }

            // ── The question ──
            when (state.variant) {
                ExerciseType.PAGE_EDGES -> {
                    SectionLabel(stringResource(R.string.ex_ask_edges))
                    Text(
                        text = stringResource(R.string.dialog_page_title, round.page),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                ExerciseType.FIRST_TO_LAST -> {
                    SectionLabel(stringResource(R.string.ex_ask_last))
                    VerseBlock(stringResource(R.string.ex_first_verse), round.first)
                }

                ExerciseType.NEIGHBOURS -> {
                    SectionLabel(stringResource(R.string.ex_ask_between))
                    round.previousLast?.let { VerseBlock(stringResource(R.string.ex_previous_ends), it) }
                    round.nextFirst?.let { VerseBlock(stringResource(R.string.ex_next_starts), it) }
                }

                // The quiz types have their own screen and are never a page-boundaries variant.
                else -> {}
            }

            if (!state.revealed) {
                Spacer(Modifier.height(8.dp))
                PaperButton(
                    text = stringResource(R.string.recall_btn_reveal),
                    onClick = viewModel::reveal,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OrnamentDivider()
                SectionLabel(stringResource(R.string.dialog_page_title, round.page))
                VerseBlock(stringResource(R.string.ex_first_verse), round.first)
                VerseBlock(stringResource(R.string.ex_last_verse), round.last)
                PaperLink(stringResource(R.string.home_btn_open_mushaf), onClick = { onOpenMushaf(round.page) })

                SectionLabel(stringResource(R.string.ex_how_did_it_go))
                PaperButton(
                    text = stringResource(R.string.ex_knew),
                    onClick = { viewModel.finish(RecallOutcome.INDEPENDENT) },
                    modifier = Modifier.fillMaxWidth()
                )
                PaperOutlinedButton(
                    text = stringResource(R.string.ex_hint),
                    onClick = { viewModel.finish(RecallOutcome.HINTED) },
                    modifier = Modifier.fillMaxWidth()
                )
                PaperOutlinedButton(
                    text = stringResource(R.string.ex_missed),
                    onClick = { viewModel.finish(RecallOutcome.UNABLE) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A verse with a small caption and its reference. */
@Composable
private fun VerseBlock(caption: String, ref: AyahRef) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$caption · ${SurahData.nameOf(ref.surah)} ${ref.ayah}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = ref.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDirection = TextDirection.Rtl,
                fontFamily = AmiriFontFamily,
                fontSize = 24.sp,
                lineHeight = 46.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
