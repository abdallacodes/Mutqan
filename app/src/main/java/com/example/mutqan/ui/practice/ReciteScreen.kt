package com.example.mutqan.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.EmptyStateCard
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OpenInMushaf
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import com.example.mutqan.domain.time.TimeKind
import com.example.mutqan.ui.time.TrackTime

/**
 * Continue from here: the classic test of a hafiz, a verse from anywhere in what they know, and they go on from it.
 * The verses after it are revealed one at a time to check against, then the user says how it went.
 */
@Composable
fun ReciteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    TrackTime(TimeKind.PRACTICE)
    val viewModel: ReciteViewModel = viewModel(factory = ReciteViewModelFactory(context))
    val state by viewModel.state.collectAsState()
    val scope by remember { UserPreferencesRepository(context.applicationContext).practiceScope }.collectAsState(initial = null)
    val round = state.round
    val scroll = rememberScrollState()
    // What was just revealed is brought into view above the buttons. Its words are read and drawn a moment after the
    // tap, so the end is followed as the page grows, for a short while.
    LaunchedEffect(state.revealed) {
        if (state.revealed > 0) withTimeoutOrNull(FOLLOW_MS) { snapshotFlow { scroll.maxValue }.collectLatest { scroll.animateScrollTo(it) } }
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.ex_recite_title),
                subtitle = if (state.done > 0) stringResource(R.string.ex_done_session, state.done) else null,
                onBack = onBack,
                compact = true
            )
        },
        // What to do next stays in reach: reveal, then say how it went.
        bottomBar = {
            if (round != null) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                    Hairline(Modifier.fillMaxWidth())
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.revealed == 0) {
                            PaperButton(
                                text = stringResource(R.string.ex_recite_reveal),
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
                .verticalScroll(scroll)
                .padding(horizontal = PaperSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Which pages ──
            scope?.takeIf { !it.isAll }?.let { ScopeLine(it, onChange = onBack) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

            when {
                state.noPages -> EmptyStateCard(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.ex_no_pages_title),
                    description = stringResource(R.string.ex_recite_no_pages),
                    modifier = Modifier.height(280.dp)
                )
                round == null -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    // ── The verse to go on from ──
                    Text(
                        text = stringResource(R.string.ex_recite_ask),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    VerseCaption(round.start, page = round.page)
                    AyahText(ayahId = round.start.id, size = 28.sp)

                    // ── What follows, as far as revealed ──
                    if (state.revealed > 0) {
                        OrnamentDivider()
                        round.following.take(state.revealed).forEach { ref ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                VerseCaption(ref)
                                AyahText(ayahId = ref.id, size = 26.sp)
                            }
                        }
                        if (state.revealed < round.following.size) {
                            PaperLink(text = stringResource(R.string.ex_recite_more), onClick = viewModel::reveal, soft = true)
                        }
                        // Only with the answer: the page would give it away.
                        OpenInMushaf(ayahId = round.start.id)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** How long the screen follows a revealed verse into view. */
private const val FOLLOW_MS = 1_500L

/** "Al-Baqarah 255", and the page when given. */
@Composable
private fun VerseCaption(ref: AyahRef, page: Int? = null) {
    val name = stringResource(R.string.quiz_ref, SurahData.nameOf(ref.surah), ref.ayah)
    Text(
        text = if (page != null) "$name · ${stringResource(R.string.page_n, page)}" else name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary
    )
}
