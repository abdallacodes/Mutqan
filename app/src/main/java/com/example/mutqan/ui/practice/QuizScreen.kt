package com.example.mutqan.ui.practice

import androidx.compose.runtime.remember
import com.example.mutqan.data.UserPreferencesRepository

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.example.mutqan.quran.PaperLoading
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.ChoiceQuestion
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.OrderQuestion
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.QuizRef
import com.example.mutqan.domain.WhereQuestion
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.components.OpenInMushaf
import com.example.mutqan.domain.EndingKind
import androidx.compose.ui.draw.clip
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.rememberHaptics
import com.example.mutqan.ui.theme.FolioTheme

/** One sitting of questions: a single kind in a row, or a daily set of ten mixed ones. */
@Composable
fun QuizScreen(mode: QuizMode, onBack: () -> Unit, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val viewModel: QuizViewModel = viewModel(key = "quiz-${mode.route}", factory = QuizViewModelFactory(context, mode))
    val state by viewModel.state.collectAsState()
    val scope by remember { UserPreferencesRepository(context.applicationContext).practiceScope }.collectAsState(initial = null)

    val title = stringResource(
        when (mode) {
            QuizMode.ENDING -> R.string.ex_ending_title
            QuizMode.NEXT -> R.string.ex_next_title
            QuizMode.FINISH -> R.string.ex_finish_title
            QuizMode.ORDER -> R.string.ex_order_title
            QuizMode.WHERE -> R.string.ex_where_title
            QuizMode.DAILY -> R.string.practice_daily_title
        }
    )
    val subtitle = when {
        state.finished || state.question == null -> null
        state.isDaily -> stringResource(R.string.quiz_progress, minOf(state.asked + if (state.answered) 0 else 1, QuizMode.DAILY_SIZE), QuizMode.DAILY_SIZE)
        state.asked > 0 -> stringResource(R.string.quiz_score, state.right, state.asked)
        else -> null
    }

    Scaffold(
        topBar = { PaperHeader(title = title, subtitle = subtitle, onBack = onBack, compact = true) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Which pages the questions come from, when that is not simply all of them.
            scope?.takeIf { !it.isAll && !state.loading && !state.finished }?.let { ScopeLine(it, onChange = onBack) }
            val q = state.question
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.finished -> Summary(state, onAgain = viewModel::restart, onBack = onBack, onOpenMushaf = onOpenMushaf)
                q == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.quiz_empty_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        stringResource(if (state.scopeEmpty) R.string.quiz_scope_empty else R.string.quiz_empty_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PaperOutlinedButton(stringResource(R.string.quiz_try_again), onClick = viewModel::restart)
                }
                else -> {
                    if (mode == QuizMode.NEXT) {
                        Row {
                            PaperChip(selected = state.hard, onClick = viewModel::toggleHard, label = { Text(stringResource(R.string.quiz_harder)) })
                        }
                    }
                    when (q) {
                        is ChoiceQuestion -> ChoiceBody(q, state, viewModel::chooseOption)
                        is OrderQuestion -> OrderBody(q, state, viewModel::tapOrder, viewModel::clearOrder)
                        is WhereQuestion -> WhereBody(q, state, viewModel::chooseSurah)
                    }
                    if (state.answered) {
                        Feedback(state.correct)
                        PaperButton(
                            text = stringResource(if (state.isDaily && state.asked >= QuizMode.DAILY_SIZE) R.string.quiz_see_results else R.string.quiz_next),
                            onClick = viewModel::next
                        )
                        // The verse number is an extra: it is there to answer or to leave.
                        if (q is WhereQuestion && state.correct) WhereBonus(q, state, viewModel::chooseAyah)
                    }
                }
            }
        }
    }
}

/** What the reader is asked to do: the first thing to read on the page, so it is set large. */
@Composable
private fun QuestionText(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun refLabel(ref: QuizRef): String = stringResource(R.string.quiz_ref, SurahData.nameOf(ref.surah), ref.ayah)

/** A verse (or part of one) as the Mushaf prints it. */
@Composable
internal fun VerseText(span: MushafSpan, size: Int = 30, muted: Boolean = false) {
    MushafText(
        span = span,
        size = size.sp,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    )
}

internal enum class OptionState { PLAIN, CORRECT, WRONG, DIMMED }

/** One answer to press. After the question is answered it shows whether it was right, wrong or neither. */
@Composable
internal fun QuizOption(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit, badge: Int? = null) {
    QuizOption(MushafSpan.label(text), state, enabled, onClick, verse = false, badge = badge)
}

/** [verse] says the span is Quran text, drawn like the Mushaf; otherwise it is a plain label. */
@Composable
internal fun QuizOption(
    span: MushafSpan,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    verse: Boolean = true,
    badge: Int? = null
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (state) {
        OptionState.CORRECT -> scheme.primary
        OptionState.WRONG -> scheme.error
        else -> scheme.outlineVariant
    }
    val fill = when (state) {
        OptionState.CORRECT -> scheme.primary.copy(alpha = 0.10f)
        OptionState.WRONG -> scheme.error.copy(alpha = 0.10f)
        else -> FolioTheme.colors.folio
    }
    val shape = FolioTheme.shapes.control
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, shape)
            .border(if (state == OptionState.CORRECT || state == OptionState.WRONG) 2.dp else 1.dp, accent, shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = { haptics.tick(); onClick() })
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (badge != null) {
            Box(Modifier.size(28.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("$badge", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (verse) {
                VerseText(span, size = 26, muted = state == OptionState.DIMMED)
            } else {
                Text(
                    text = span.plain,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state == OptionState.DIMMED) scheme.onSurfaceVariant else scheme.onBackground
                )
            }
        }
        when (state) {
            OptionState.CORRECT -> Icon(Icons.Default.Check, contentDescription = null, tint = scheme.primary)
            OptionState.WRONG -> Icon(Icons.Default.Close, contentDescription = null, tint = scheme.error)
            else -> {}
        }
    }
}

@Composable
private fun instruction(type: ExerciseType, kind: EndingKind? = null): String = stringResource(
    when (type) {
        ExerciseType.QUIZ_ENDING -> R.string.quiz_q_ending
        ExerciseType.QUIZ_NEXT -> R.string.quiz_q_next
        ExerciseType.QUIZ_FINISH -> if (kind == EndingKind.LOOKALIKE) R.string.quiz_q_finish_lookalike else R.string.quiz_q_finish_closing
        ExerciseType.QUIZ_ORDER -> R.string.quiz_q_order
        ExerciseType.QUIZ_TOPIC -> R.string.quiz_q_topic
        else -> R.string.quiz_q_where_surah
    }
)

@Composable
private fun ChoiceBody(q: ChoiceQuestion, state: QuizUiState, onPick: (Int) -> Unit) {
    QuestionText(instruction(q.type, q.kind))
    if (q.showRef) {
        Text(refLabel(q.subject), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    VerseText(q.prompt)
    q.options.forEachIndexed { i, option ->
        val optionState = when {
            !state.answered -> OptionState.PLAIN
            i == q.correct -> OptionState.CORRECT
            i == state.pickedOption -> OptionState.WRONG
            else -> OptionState.DIMMED
        }
        QuizOption(option, optionState, enabled = !state.answered, onClick = { onPick(i) }, verse = q.optionsAreVerses)
    }
    if (state.answered) {
        OrnamentDivider()
        SectionLabel(stringResource(R.string.quiz_answer_was))
        Text(refLabel(q.revealRef), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        VerseText(q.revealed, size = 28)
        OpenInMushaf(ayahId = q.revealRef.id)
        q.note?.let {
            SectionLabel(stringResource(R.string.quiz_topics_of_verse))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun OrderBody(q: OrderQuestion, state: QuizUiState, onTap: (Int) -> Unit, onClear: () -> Unit) {
    QuestionText(instruction(q.type))
    Text(
        text = stringResource(R.string.quiz_in_surah, SurahData.nameOf(q.subject.surah)),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    q.shuffled.forEachIndexed { i, span ->
        val tapped = state.orderTaps.indexOf(i)
        val optionState = when {
            !state.answered -> OptionState.PLAIN
            state.orderTaps.getOrNull(q.correctOrder.indexOf(i)) == i -> OptionState.CORRECT
            else -> OptionState.WRONG
        }
        QuizOption(
            span = span,
            state = optionState,
            enabled = !state.answered && tapped < 0,
            onClick = { onTap(i) },
            badge = if (tapped >= 0) tapped + 1 else null
        )
    }
    if (!state.answered && state.orderTaps.isNotEmpty()) {
        PaperLink(stringResource(R.string.quiz_clear), onClick = onClear, soft = true)
    }
    if (state.answered) {
        OrnamentDivider()
        SectionLabel(stringResource(R.string.quiz_right_order))
        q.correctOrder.forEachIndexed { k, shuffledIndex ->
            Text("${q.ayahNumbers[k]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            VerseText(q.shuffled[shuffledIndex], size = 26)
            OpenInMushaf(ayahId = q.ayahIds[k], soft = true)
        }
    }
}

@Composable
private fun WhereBody(q: WhereQuestion, state: QuizUiState, onSurah: (Int) -> Unit) {
    QuestionText(stringResource(R.string.quiz_q_where_surah))
    VerseText(q.text)
    q.surahOptions.forEach { id ->
        val optionState = when {
            !state.answered -> OptionState.PLAIN
            id == q.subject.surah -> OptionState.CORRECT
            id == state.pickedSurah -> OptionState.WRONG
            else -> OptionState.DIMMED
        }
        QuizOption(SurahData.nameOf(id), optionState, enabled = !state.answered, onClick = { onSurah(id) })
    }
    if (state.answered) {
        OrnamentDivider()
        SectionLabel(stringResource(R.string.quiz_answer_was))
        // While the optional verse number is still open, the answer shows the surah only.
        val numberOpen = state.correct && state.pickedAyah == null
        Text(
            text = if (numberOpen) SurahData.nameOf(q.subject.surah) else refLabel(q.subject),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        OpenInMushaf(ayahId = q.subject.id)
    }
}

/** The extra question after the surah was named: which verse number. Nothing depends on it. */
@Composable
private fun WhereBonus(q: WhereQuestion, state: QuizUiState, onAyah: (Int) -> Unit) {
    OrnamentDivider()
    SectionLabel(stringResource(R.string.quiz_bonus_ayah))
    q.ayahOptions.forEach { n ->
        val optionState = when {
            state.pickedAyah == null -> OptionState.PLAIN
            n == q.subject.ayah -> OptionState.CORRECT
            n == state.pickedAyah -> OptionState.WRONG
            else -> OptionState.DIMMED
        }
        QuizOption("$n", optionState, enabled = state.pickedAyah == null, onClick = { onAyah(n) })
    }
}

@Composable
internal fun Feedback(correct: Boolean) {
    Text(
        text = stringResource(if (correct) R.string.quiz_correct else R.string.quiz_wrong),
        style = MaterialTheme.typography.headlineSmall,
        color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

/** The end of a daily set: the score, and the pages that went wrong, each a link into the Mushaf. */
@Composable
private fun Summary(state: QuizUiState, onAgain: () -> Unit, onBack: () -> Unit, onOpenMushaf: (Int) -> Unit) {
    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.quiz_done_title), style = MaterialTheme.typography.displaySmall)
        Text(
            text = stringResource(R.string.quiz_score, state.right, state.asked),
            style = MaterialTheme.typography.headlineMedium,
            color = FolioTheme.colors.heroAccent
        )
    }
    if (state.missedPages.isEmpty()) {
        Text(stringResource(R.string.quiz_all_right), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        SectionLabel(stringResource(R.string.quiz_done_pages))
        state.missedPages.sorted().forEach { page ->
            PaperLink(stringResource(R.string.quiz_open_page, page), onClick = { onOpenMushaf(page) })
        }
    }
    Spacer(Modifier.height(8.dp))
    PaperButton(stringResource(R.string.quiz_again), onClick = onAgain, modifier = Modifier.fillMaxWidth())
    PaperOutlinedButton(stringResource(R.string.back), onClick = onBack, modifier = Modifier.fillMaxWidth())
}
