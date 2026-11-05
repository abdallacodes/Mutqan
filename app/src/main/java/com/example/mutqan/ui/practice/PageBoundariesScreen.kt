package com.example.mutqan.ui.practice

import androidx.compose.runtime.remember
import com.example.mutqan.data.UserPreferencesRepository

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.OpenInMushaf

/**
 * Know the edges of each page. Starting from a page: its number, its first verse, or the verses either side of it.
 * Starting from a verse ([fromVerse]): say the first, or the last, verse of its page. The numbers are never asked for;
 * they are shown with the answer for anyone who wants to check them too. The user reveals the answer and says how it went.
 */
@Composable
fun PageBoundariesScreen(fromVerse: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PageBoundariesViewModel = viewModel(
        key = "boundaries-$fromVerse",
        factory = PageBoundariesViewModelFactory(context, fromVerse)
    )
    val state by viewModel.state.collectAsState()
    val scope by remember { UserPreferencesRepository(context.applicationContext).practiceScope }.collectAsState(initial = null)
    val variants = if (fromVerse) ExerciseType.FROM_VERSE else ExerciseType.FROM_PAGE

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(if (fromVerse) R.string.ex_edges_title else R.string.ex_boundaries_title),
                subtitle = if (state.done > 0) stringResource(R.string.ex_done_session, state.done) else null,
                onBack = onBack,
                compact = true
            )
        },
        // What to do next stays in reach: reveal the answer, then say how it went.
        bottomBar = {
            if (!state.noPages && state.round != null) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                    Hairline(Modifier.fillMaxWidth())
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.revealed) {
                            PaperButton(
                                text = stringResource(R.string.recall_btn_reveal),
                                onClick = viewModel::reveal,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            SectionLabel(stringResource(R.string.ex_how_did_it_go))
                            PaperButton(
                                text = stringResource(R.string.ex_knew),
                                onClick = { viewModel.finish(RecallOutcome.INDEPENDENT) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PaperOutlinedButton(
                                    text = stringResource(R.string.ex_hint),
                                    onClick = { viewModel.finish(RecallOutcome.HINTED) },
                                    modifier = Modifier.weight(1f)
                                )
                                PaperOutlinedButton(
                                    text = stringResource(R.string.ex_missed),
                                    onClick = { viewModel.finish(RecallOutcome.UNABLE) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperTabs(
                labels = if (fromVerse) listOf(
                    stringResource(R.string.ex_variant_ayah_first),
                    stringResource(R.string.ex_variant_ayah_last)
                ) else listOf(
                    stringResource(R.string.ex_variant_page),
                    stringResource(R.string.ex_variant_start),
                    stringResource(R.string.ex_variant_neighbours)
                ),
                selectedIndex = variants.indexOf(state.variant),
                onSelect = { viewModel.setVariant(variants[it]) }
            )

            // ── Which pages ──
            scope?.takeIf { !it.isAll }?.let { ScopeLine(it, onChange = onBack) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.hasCoverage) {
                    // The chip's rule fills the width it is given, so it is given only the width of its own label.
                    PaperChip(
                        selected = state.memorisedOnly,
                        onClick = { viewModel.setMemorisedOnly(!state.memorisedOnly) },
                        label = { Text(stringResource(R.string.ex_scope_memorised)) },
                        modifier = Modifier.width(IntrinsicSize.Max)
                    )
                }
                Spacer(Modifier.weight(1f))
                PaperLink(text = "−", onClick = { viewModel.changeJuz(-1) })
                Text(
                    text = if (state.juz == 0) stringResource(R.string.ex_juz_all) else stringResource(R.string.plan_item_juz, state.juz),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                PaperLink(text = "+", onClick = { viewModel.changeJuz(1) })
            }
            Hairline(Modifier.fillMaxWidth())

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
                    Text(stringResource(R.string.ex_ask_edges), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        text = stringResource(R.string.dialog_page_title, round.page),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                ExerciseType.FIRST_TO_LAST -> {
                    Text(stringResource(R.string.ex_ask_last), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    VerseBlock(stringResource(R.string.ex_first_verse), round.first)
                }

                ExerciseType.NEIGHBOURS -> {
                    Text(stringResource(R.string.ex_ask_between), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    round.previousLast?.let { VerseBlock(stringResource(R.string.ex_previous_ends), it) }
                    round.nextFirst?.let { VerseBlock(stringResource(R.string.ex_next_starts), it) }
                }

                ExerciseType.AYAH_PAGE_START, ExerciseType.AYAH_PAGE_END -> {
                    Text(
                        text = stringResource(if (state.variant == ExerciseType.AYAH_PAGE_START) R.string.ex_ask_page_start else R.string.ex_ask_page_end),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.ex_bonus_numbers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    round.prompt?.let { AyahText(ayahId = it.id, size = 30.sp) }
                }

                // The quiz types have their own screen and are never a page-boundaries variant.
                else -> {}
            }

            // ── The answer ──
            if (state.revealed) {
                OrnamentDivider()
                SectionLabel(stringResource(R.string.dialog_page_title, round.page))
                when (state.variant) {
                    ExerciseType.AYAH_PAGE_START -> {
                        round.prompt?.let { Text(stringResource(R.string.ex_shown_verse, SurahData.nameOf(it.surah), it.ayah), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        VerseBlock(stringResource(R.string.ex_first_verse), round.first)
                    }
                    ExerciseType.AYAH_PAGE_END -> {
                        round.prompt?.let { Text(stringResource(R.string.ex_shown_verse, SurahData.nameOf(it.surah), it.ayah), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        VerseBlock(stringResource(R.string.ex_last_verse), round.last)
                    }
                    else -> {
                        VerseBlock(stringResource(R.string.ex_first_verse), round.first)
                        VerseBlock(stringResource(R.string.ex_last_verse), round.last)
                    }
                }
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
        AyahText(ayahId = ref.id, size = 24.sp)
        // Only shown with the answer: opening the Mushaf from the question would give it away.
        OpenInMushaf(ayahId = ref.id)
    }
}
