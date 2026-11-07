package com.example.mutqan.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.PageEdgeQuestion
import com.example.mutqan.domain.PageOrder
import com.example.mutqan.domain.QuizRef
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OpenInMushaf
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.components.SectionLabel

/**
 * The page-edge quiz, which has no end: a page number and the verse that starts (or ends) it, among five verses cut to
 * their first (or last) six words, or, with the choices off, recalled and then shown. [end] says which edge it opens on;
 * the tabs switch it. The page can be typed, stepped, or left to move on by itself in order, at random or not at all.
 */
@Composable
fun PageQuizScreen(end: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PageQuizViewModel = viewModel(key = "page-quiz-$end", factory = PageQuizViewModelFactory(context, end))
    val state by viewModel.state.collectAsState()
    val q = state.question

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(if (state.end) R.string.ex_pend_title else R.string.ex_pstart_title),
                subtitle = if (state.asked > 0) stringResource(R.string.quiz_score, state.right, state.asked) else null,
                onBack = onBack,
                compact = true
            )
        },
        // What to do next stays in reach: skip or go on, or reveal the answer and say how it went.
        bottomBar = {
            if (q != null) {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                    Hairline(Modifier.fillMaxWidth())
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when {
                            state.withOptions && state.answered ->
                                PaperButton(stringResource(R.string.quiz_next), onClick = viewModel::next, modifier = Modifier.fillMaxWidth())

                            state.withOptions ->
                                PaperOutlinedButton(stringResource(R.string.pq_skip), onClick = viewModel::next, modifier = Modifier.fillMaxWidth())

                            !state.revealed ->
                                PaperButton(stringResource(R.string.recall_btn_reveal), onClick = viewModel::reveal, modifier = Modifier.fillMaxWidth())

                            else -> {
                                SectionLabel(stringResource(R.string.ex_how_did_it_go))
                                PaperButton(
                                    text = stringResource(R.string.ex_knew),
                                    onClick = { viewModel.rate(RecallOutcome.INDEPENDENT) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PaperOutlinedButton(
                                        text = stringResource(R.string.ex_hint),
                                        onClick = { viewModel.rate(RecallOutcome.HINTED) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    PaperOutlinedButton(
                                        text = stringResource(R.string.ex_missed),
                                        onClick = { viewModel.rate(RecallOutcome.UNABLE) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
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
                .padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperTabs(
                labels = listOf(stringResource(R.string.ex_first_verse), stringResource(R.string.ex_last_verse)),
                selectedIndex = if (state.end) 1 else 0,
                onSelect = { viewModel.setEnd(it == 1) }
            )
            PageField(page = state.page, onStep = viewModel::step, onGo = viewModel::goTo)
            PaperTabs(
                labels = listOf(
                    stringResource(R.string.pq_order_forward),
                    stringResource(R.string.pq_order_random),
                    stringResource(R.string.pq_order_stay)
                ),
                selectedIndex = state.order.ordinal,
                onSelect = { viewModel.setOrder(PageOrder.entries[it]) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                PaperChip(
                    selected = state.withOptions,
                    onClick = { viewModel.setWithOptions(!state.withOptions) },
                    label = { Text(stringResource(R.string.pq_choices)) },
                    modifier = Modifier.width(IntrinsicSize.Max)
                )
            }
            Hairline(Modifier.fillMaxWidth())

            when {
                q != null -> Question(q, state, viewModel::pick)
                state.loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> PaperOutlinedButton(stringResource(R.string.quiz_try_again), onClick = viewModel::retry)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The page to ask about: typed (and confirmed), or stepped one page at a time. */
@Composable
private fun PageField(page: Int, onStep: (Int) -> Unit, onGo: (Int) -> Unit) {
    var text by remember(page) { mutableStateOf(page.toString()) }
    val go = { text.toIntOrNull()?.let(onGo) ?: Unit }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.pq_page), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        PaperLink(text = "−", onClick = { onStep(-1) })
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            modifier = Modifier.width(88.dp)
        )
        PaperLink(text = "+", onClick = { onStep(1) })
        PaperLink(text = stringResource(R.string.pq_go), onClick = { go() })
    }
}

@Composable
private fun refLabel(ref: QuizRef): String = stringResource(R.string.quiz_ref, SurahData.nameOf(ref.surah), ref.ayah)

/** The question: the page, what is asked, and either the five choices or the button that shows the answer. */
@Composable
private fun Question(q: PageEdgeQuestion, state: PageQuizUiState, onPick: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.dialog_page_title, q.page),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
    Text(
        text = stringResource(
            when {
                state.withOptions && q.end -> R.string.pq_q_end
                state.withOptions -> R.string.pq_q_start
                q.end -> R.string.pq_recall_end
                else -> R.string.pq_recall_start
            }
        ),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground
    )

    if (state.withOptions) {
        q.options.forEachIndexed { i, option ->
            val optionState = when {
                !state.answered -> OptionState.PLAIN
                i == q.correct -> OptionState.CORRECT
                i == state.picked -> OptionState.WRONG
                else -> OptionState.DIMMED
            }
            QuizOption(option, optionState, enabled = !state.answered && !state.loading, onClick = { onPick(i) })
        }
        if (state.answered) {
            Feedback(state.correct)
            // What a wrong pick really was: knowing where it is is how the mix-up is cured.
            state.picked?.takeIf { !state.correct }?.let { picked ->
                val ref = q.refs[picked]
                Text(
                    text = stringResource(R.string.pq_you_chose, refLabel(ref), ref.page),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (state.answered || state.revealed) {
        OrnamentDivider()
        SectionLabel(stringResource(R.string.quiz_answer_was))
        Text(refLabel(q.answer), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        VerseText(q.answerText, size = 28)
        OpenInMushaf(ayahId = q.answer.id)
    }
}
