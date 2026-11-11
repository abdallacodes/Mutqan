package com.example.mutqan.ui.practice

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.mutqan.ui.components.FolioSheetInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.ChoiceQuestion
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.FocusPages
import com.example.mutqan.domain.OrderQuestion
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.PracticeUsage
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.QuizQuestion
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.WhereQuestion
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.HeroButton
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.IndexRow
import com.example.mutqan.ui.components.LocalDockInset
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.components.PageGlyphKind
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.components.StarMedallion
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.navigation.OnTabReselected
import com.example.mutqan.ui.navigation.Routes
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
private const val USAGE_MS = 60L * 24 * 60 * 60 * 1000

/** The pages a focused set would be about: how many, and in which recall state. */
@Immutable
data class FocusSummary(val state: PassageState, val pages: Int)

/** What the Practice list needs: the choice of pages, what was practised, the focus suggestion and today's first question. */
class PracticeViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository,
    private val prefs: UserPreferencesRepository,
    private val appContext: Context
) : ViewModel() {

    /** Which pages the questions are drawn from. */
    val scope: StateFlow<PracticeScope> = prefs.practiceScope
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeScope())

    fun setScope(scope: PracticeScope) {
        viewModelScope.launch {
            prefs.setPracticeScope(scope)
            // The first question shown on the card was drawn from the old choice.
            refreshDaily(force = true)
        }
    }

    /** What the current choice gives, for the picker: how many pages, and how many at each level. */
    val preview: StateFlow<PracticeScope.Preview?> =
        combine(scope, study.coveredPages(), study.revisionLogs(), study.selfRatedStrength()) { choice, covered, logs, rated ->
            choice.preview(covered, PassageStates.compute(logs), rated, logs)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val countsThisWeek = study.exerciseCountsSince(System.currentTimeMillis() - WEEK_MS)

    /** How many page-boundary rounds were done in the last seven days. */
    val boundariesThisWeek: StateFlow<Int> = countsThisWeek
        .map { counts -> ExerciseType.PAGE_BOUNDARIES.sumOf { counts[it] ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** How many quiz questions were answered in the last seven days. */
    val quizThisWeek: StateFlow<Int> = countsThisWeek
        .map { counts -> (ExerciseType.QUIZ + ExerciseType.PAGE_QUIZ).sumOf { counts[it] ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Answers per kind of exercise over the last two months, to tell which are practised most. */
    val usage: StateFlow<Map<ExerciseType, Int>> = study.exerciseCountsSince(System.currentTimeMillis() - USAGE_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The weakest pages worth a set of their own; null when there are none. */
    val focus: StateFlow<FocusSummary?> = combine(study.coveredPages(), study.revisionLogs()) { covered, logs ->
        FocusPages.pick(covered, PassageStates.compute(logs))?.let { FocusSummary(it.state, it.pages.size) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _daily = MutableStateFlow<QuizQuestion?>(null)

    /** The first question of today's set, as the quiz will ask it; null while it is being built or when none can be. */
    val daily: StateFlow<QuizQuestion?> = _daily.asStateFlow()
    private var dailyJob: Job? = null

    /**
     * Builds the first question of a new daily set and holds it for the quiz. Does nothing while a question is still
     * held (the card still shows it); a [force] builds anew, after the choice of pages changed.
     */
    fun refreshDaily(force: Boolean = false) {
        if (!force) {
            val held = DailyPreview.peek()
            if (held != null) {
                // Still held from before: show it again (this screen may be new), do not draw another.
                if (_daily.value == null) _daily.value = held.first
                return
            }
        }
        dailyJob?.cancel()
        DailyPreview.clear()
        _daily.value = null
        dailyJob = viewModelScope.launch(Dispatchers.Default) {
            val picker = QuizPicker.create(appContext, study, quran)
            val schedule = (ExerciseType.QUIZ + ExerciseType.QUIZ).shuffled().take(QuizMode.DAILY_SIZE)
            val first = (listOf(schedule.first()) + ExerciseType.QUIZ.filter { it != schedule.first() }.shuffled())
                .firstNotNullOfOrNull { picker.build(it, hard = false) }
                ?: return@launch
            DailyPreview.hold(DailyPreview.Held(listOf(first.type) + schedule.drop(1), first))
            _daily.value = first
        }
    }
}

class PracticeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PracticeViewModel(
        StudyRepository.create(context),
        QuranRepository.getInstance(context),
        UserPreferencesRepository(context.applicationContext),
        context.applicationContext
    ) as T
}

/** One exercise in the index: its page drawing, its texts, and the types its answers are recorded under. */
private class Entry(
    val glyph: PageGlyphKind,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val types: List<ExerciseType>,
    val onClick: () -> Unit,
    /** Rounds done this week, shown as a small note when above zero. */
    val week: Int = 0
)

/**
 * Practice. The choice of pages comes first, since every question is drawn from it, then the day's set on the one deep
 * card, showing the question it would open with. Below, an index: what to do given the data, the exercises practised
 * most, and all the others behind one row; then the tools.
 */
@Composable
fun PracticeScreen(
    onOpenBoundaries: () -> Unit,
    onOpenPageEdges: () -> Unit,
    onOpenPageQuiz: (end: Boolean) -> Unit,
    onOpenJuz: (Int) -> Unit,
    onOpenQuiz: (QuizMode) -> Unit,
    onRandomVerse: () -> Unit,
    onSimilarityTest: () -> Unit,
    onOpenVault: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModelFactory(context))
    val week by viewModel.boundariesThisWeek.collectAsState()
    val quizWeek by viewModel.quizThisWeek.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val focus by viewModel.focus.collectAsState()
    val daily by viewModel.daily.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    var pickingJuz by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    OnTabReselected(Routes.PRACTICE) { scroll.animateScrollTo(0) }
    LaunchedEffect(Unit) { viewModel.refreshDaily() }

    // The catalogue. The order inside a group is the order in the list; the first of the list is what a new reader sees as "most practised".
    val where = Entry(PageGlyphKind.WHERE, R.string.ex_where_title, R.string.ex_where_desc, listOf(ExerciseType.QUIZ_WHERE), { onOpenQuiz(QuizMode.WHERE) })
    val next = Entry(PageGlyphKind.NEXT, R.string.ex_next_title, R.string.ex_next_desc, listOf(ExerciseType.QUIZ_NEXT), { onOpenQuiz(QuizMode.NEXT) })
    val ending = Entry(PageGlyphKind.ENDING, R.string.ex_ending_title, R.string.ex_ending_desc, listOf(ExerciseType.QUIZ_ENDING), { onOpenQuiz(QuizMode.ENDING) })
    val finish = Entry(PageGlyphKind.FINISH, R.string.ex_finish_title, R.string.ex_finish_desc, listOf(ExerciseType.QUIZ_FINISH), { onOpenQuiz(QuizMode.FINISH) })
    val order = Entry(PageGlyphKind.ORDER, R.string.ex_order_title, R.string.ex_order_desc, listOf(ExerciseType.QUIZ_ORDER), { onOpenQuiz(QuizMode.ORDER) })
    val pageStart = Entry(PageGlyphKind.PAGE_START, R.string.ex_pstart_title, R.string.ex_pstart_desc, listOf(ExerciseType.QUIZ_PAGE_START), { onOpenPageQuiz(false) })
    val pageEnd = Entry(PageGlyphKind.PAGE_END, R.string.ex_pend_title, R.string.ex_pend_desc, listOf(ExerciseType.QUIZ_PAGE_END), { onOpenPageQuiz(true) })
    val boundaries = Entry(PageGlyphKind.BOUNDARIES, R.string.ex_boundaries_title, R.string.ex_boundaries_desc, ExerciseType.FROM_PAGE, onOpenBoundaries, week)
    val edges = Entry(PageGlyphKind.EDGES, R.string.ex_edges_title, R.string.ex_edges_desc, ExerciseType.FROM_VERSE, onOpenPageEdges)
    val similarity = Entry(PageGlyphKind.SIMILAR, R.string.ex_similarity_title, R.string.ex_similarity_desc, emptyList(), onSimilarityTest)

    val groups = listOf(
        R.string.practice_group_questions to listOf(ending, next, finish, order),
        R.string.practice_group_place to listOf(where, pageStart, pageEnd, boundaries, edges),
        R.string.practice_group_similar to listOf(similarity)
    )
    val ranked = listOf(where, next, ending, finish, order, pageStart, pageEnd, boundaries, edges)
    val mostPractised = PracticeUsage.mostPractised(usage, ranked.map { it.types }, 2).map { ranked[it] }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.practice_title),
                subtitle = stringResource(R.string.practice_subtitle),
                actions = { TopBarOverflowMenu(onHelpClick = null, onSettingsClick = onSettingsClick) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scroll).padding(bottom = LocalDockInset.current)) {
            ScopeLink(scope = scope, preview = preview, onClick = { choosing = true })
            DailyHero(
                question = daily,
                note = if (quizWeek > 0) stringResource(R.string.ex_this_week, quizWeek) else null,
                onStart = { onOpenQuiz(QuizMode.DAILY) }
            )

            focus?.let { f ->
                SectionHead(stringResource(R.string.practice_section_suggested))
                IndexRow(
                    title = stringResource(if (f.state == PassageState.NEEDS_ATTENTION) R.string.practice_focus_weak_title else R.string.practice_focus_dev_title),
                    description = stringResource(R.string.practice_focus_desc, pluralStringResource(R.plurals.count_pages, f.pages, f.pages)),
                    glyph = PageGlyphKind.WEAK,
                    pill = stringResource(R.string.practice_start),
                    onClick = { onOpenQuiz(QuizMode.FOCUS) }
                )
            }

            SectionHead(stringResource(R.string.practice_section_most))
            mostPractised.forEach { EntryRow(it) }

            val total = groups.sumOf { it.second.size }
            IndexRow(
                title = stringResource(R.string.practice_all_title),
                description = stringResource(R.string.practice_all_desc, total, groups.size),
                leading = { CountRing(total) },
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                emphasis = true,
                onClick = { showAll = true }
            )

            SectionHead(stringResource(R.string.practice_section_tools))
            IndexRow(
                title = stringResource(R.string.ex_juzmap_title),
                description = stringResource(R.string.ex_juzmap_desc),
                glyph = PageGlyphKind.JUZ,
                onClick = { pickingJuz = true }
            )
            IndexRow(
                title = stringResource(R.string.vault_title),
                description = stringResource(R.string.ex_vault_desc),
                glyph = PageGlyphKind.VAULT,
                onClick = onOpenVault
            )
            IndexRow(
                title = stringResource(R.string.ex_random_title),
                description = stringResource(R.string.ex_random_desc),
                glyph = PageGlyphKind.RANDOM,
                onClick = onRandomVerse
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // The whole catalogue opens in a sheet, not in the list: rows appearing in the middle of the screen pushed the tools
    // below them out of sight.
    if (showAll) {
        AllExercisesSheet(groups = groups, onDismiss = { showAll = false })
    }

    if (pickingJuz) {
        JuzPickerSheet(
            onPick = { juz ->
                pickingJuz = false
                onOpenJuz(juz)
            },
            onDismiss = { pickingJuz = false }
        )
    }

    if (choosing) {
        ScopeSheet(
            scope = scope,
            preview = preview,
            onChange = viewModel::setScope,
            onDismiss = { choosing = false }
        )
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit = entry.onClick) {
    IndexRow(
        title = stringResource(entry.title),
        description = stringResource(entry.desc),
        glyph = entry.glyph,
        note = if (entry.week > 0) stringResource(R.string.ex_this_week, entry.week) else null,
        onClick = onClick
    )
}

/** Every exercise, in its group: the sheet that opens from the "all exercises" row. A pick closes it and starts that exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllExercisesSheet(groups: List<Pair<Int, List<Entry>>>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.practice_all_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
            )
            groups.forEach { (label, entries) ->
                SectionHead(stringResource(label))
                entries.forEach { entry -> EntryRow(entry, onClick = { onDismiss(); entry.onClick() }) }
            }
        }
    }
}

/** "Questions from: all my memorised pages · 604 pages", as a link that opens the choice. */
@Composable
private fun ScopeLink(scope: PracticeScope, preview: PracticeScope.Preview?, onClick: () -> Unit) {
    val summary = scopeSummary(scope)
    val pages = preview?.let { pluralStringResource(R.plurals.scope_span_pages, it.total, it.total) }
    val empty = preview != null && preview.total == 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.scope_change), role = Role.Button, onClick = onClick)
            .padding(horizontal = PaperSpace.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.scope_row_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = listOfNotNull(summary, pages).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
            color = if (empty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * What to do first: the day's set on the screen's one deep card, with the question it opens with, set as the quiz
 * will show it, and a gold button that starts it.
 */
@Composable
private fun DailyHero(question: QuizQuestion?, note: String?, onStart: () -> Unit) {
    HeroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StarMedallion(text = "10", onHero = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.practice_daily_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.practice_daily_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Column(modifier = Modifier.animateContentSize()) {
            if (question != null) QuestionPreview(question)
        }
        Spacer(Modifier.height(14.dp))
        HeroButton(text = stringResource(R.string.practice_start), onClick = onStart, pill = true, modifier = Modifier.fillMaxWidth())
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = FolioTheme.colors.heroAccent,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** The first question of the set: the verse it is about between two gold lines, and what it asks. */
@Composable
private fun QuestionPreview(question: QuizQuestion) {
    val accent = FolioTheme.colors.heroAccent
    val span = when (question) {
        is ChoiceQuestion -> question.prompt
        is OrderQuestion -> question.shuffled.firstOrNull() ?: MushafSpan.Empty
        is WhereQuestion -> question.text
    }
    val titleRes = when (question.type) {
        ExerciseType.QUIZ_ENDING -> R.string.ex_ending_title
        ExerciseType.QUIZ_NEXT -> R.string.ex_next_title
        ExerciseType.QUIZ_FINISH -> R.string.ex_finish_title
        ExerciseType.QUIZ_ORDER -> R.string.ex_order_title
        else -> R.string.ex_where_title
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .drawBehind {
                val line = accent.copy(alpha = 0.35f)
                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MushafText(
            span = span,
            size = 24.sp,
            color = FolioTheme.colors.onHero,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.practice_first_question, stringResource(titleRes)),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** A number in a thin ring, where the other rows have a page drawing: how many exercises are behind the row. */
@Composable
private fun CountRing(count: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(44.dp, 44.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily, fontSize = 18.sp, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
