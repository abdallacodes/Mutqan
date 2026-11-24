package com.example.mutqan.ui.practice

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.mutqan.domain.DailySchedule
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.OrderQuestion
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.PracticeSkills
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.QuizQuestion
import com.example.mutqan.domain.RecencyUnit
import com.example.mutqan.domain.Skill
import com.example.mutqan.domain.SkillScore
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.WhereQuestion
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.FolioSheetInsets
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.HeroButton
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.IndexRow
import com.example.mutqan.ui.components.LocalDockInset
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.components.PageGlyph
import com.example.mutqan.ui.components.PageGlyphKind
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.StarMedallion
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.components.pressScale
import com.example.mutqan.ui.navigation.OnTabReselected
import com.example.mutqan.ui.navigation.Routes
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mutqan.ui.game.LanternsCard

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
private const val SKILL_WINDOW_MS = PracticeSkills.DAYS * 24L * 60 * 60 * 1000

/** What the Practice screen needs: the choice of pages, how each skill went, the pages missed lately and today's first question. */
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

    /** What the current choice gives: how many pages, and how many at each level. */
    val preview: StateFlow<PracticeScope.Preview?> =
        combine(scope, study.coveredPages(), study.revisionLogs(), study.selfRatedStrength()) { choice, covered, logs, rated ->
            choice.preview(covered, PassageStates.compute(logs), rated, logs)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Answers given in the last seven days, by exercise type, for the notes on the exercises. */
    val week: StateFlow<Map<ExerciseType, Int>> = study.exerciseCountsSince(System.currentTimeMillis() - WEEK_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val outcomes = study.exerciseOutcomesSince(System.currentTimeMillis() - SKILL_WINDOW_MS)

    /** How each skill went in the last 30 days. */
    val skills: StateFlow<Map<Skill, SkillScore>> = outcomes.map { PracticeSkills.scores(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The pages whose quiz questions went wrong in the last 30 days, most missed first. */
    val missed: StateFlow<List<Int>> = study.quizMissesSince(System.currentTimeMillis() - SKILL_WINDOW_MS)
        .map { misses -> misses.map { it.page } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _daily = MutableStateFlow<QuizQuestion?>(null)

    /** The first question of today's set, as the quiz will ask it; null while it is being built or when none can be. */
    val daily: StateFlow<QuizQuestion?> = _daily.asStateFlow()
    private var dailyJob: Job? = null

    /**
     * Builds the first question of a new daily set and holds it for the quiz. Does nothing while a question is still
     * held (the card still shows it); a [force] builds anew, after the choice of pages changed. The set leans to the
     * weakest skill (see [DailySchedule]).
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
            val weakest = PracticeSkills.weakest(PracticeSkills.scores(outcomes.first()))
            val picker = QuizPicker.create(appContext, study, quran)
            val schedule = DailySchedule.make(weakest)
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

/** One exercise: its page drawing, its texts, the types its answers are recorded under, and how to open it. */
@Immutable
private class Entry(
    val glyph: PageGlyphKind,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val types: List<ExerciseType>,
    val onClick: () -> Unit
)

/** A skill's exercises at its two levels: chosen answers the app checks, and recited ones the user rates. */
private class SkillExercises(val choose: List<Entry>, val recite: List<Entry>)

/** The choices of pages offered as one tap; anything else made in the sheet shows as "custom". */
internal enum class ScopePreset(@StringRes val label: Int, val scope: PracticeScope) {
    ALL(R.string.practice_chip_all, PracticeScope()),
    TODAY(R.string.practice_chip_today, PracticeScope(recentAmount = 1, recentUnit = RecencyUnit.DAYS)),
    WEEK(R.string.practice_chip_week, PracticeScope(recentAmount = 1, recentUnit = RecencyUnit.WEEKS)),
    WEAK(R.string.practice_chip_weak, PracticeScope(tiers = setOf(PlanTier.WEAK)))
}

/**
 * Practice. Which pages the questions come from, as one-tap choices; the day's set on the one deep card, showing the
 * question it opens with; then the three skills, each with how the last month went, the weakest marked; the pages
 * missed lately; and the tools.
 */
@Composable
fun PracticeScreen(
    onPlayGame: () -> Unit = {},
    onOpenBoundaries: () -> Unit,
    onOpenPageEdges: () -> Unit,
    onOpenPageQuiz: (end: Boolean) -> Unit,
    onOpenJuz: (Int) -> Unit,
    onOpenQuiz: (QuizMode) -> Unit,
    onRecite: () -> Unit,
    onSimilarityTest: () -> Unit,
    onOpenVault: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModelFactory(context))
    val week by viewModel.week.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val skills by viewModel.skills.collectAsState()
    val missed by viewModel.missed.collectAsState()
    val daily by viewModel.daily.collectAsState()
    val gameBest by remember(context) { UserPreferencesRepository(context.applicationContext).lanternsMasteryBest }.collectAsState(initial = 0)
    val gameStars by remember(context) { UserPreferencesRepository(context.applicationContext).lanternsStars }.collectAsState(initial = emptyList())
    var choosing by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }
    var pickingJuz by remember { mutableStateOf(false) }
    var openSkill by remember { mutableStateOf<Skill?>(null) }
    val scroll = rememberScrollState()
    OnTabReselected(Routes.PRACTICE) { scroll.animateScrollTo(0) }
    LaunchedEffect(Unit) { viewModel.refreshDaily() }

    // The exercises of each skill.
    val exercises = remember(onOpenQuiz, onOpenPageQuiz, onOpenBoundaries, onOpenPageEdges, onRecite, onSimilarityTest) {
        mapOf(
            Skill.LINKS to SkillExercises(
                choose = listOf(
                    Entry(PageGlyphKind.ORDER, R.string.practice_skill_set_title, R.string.practice_set_links_desc, Skill.LINKS.quizTypes) { onOpenQuiz(QuizMode.LINKS) },
                    Entry(PageGlyphKind.NEXT, R.string.ex_next_title, R.string.ex_next_desc, listOf(ExerciseType.QUIZ_NEXT)) { onOpenQuiz(QuizMode.NEXT) },
                    Entry(PageGlyphKind.ORDER, R.string.ex_order_title, R.string.ex_order_desc, listOf(ExerciseType.QUIZ_ORDER)) { onOpenQuiz(QuizMode.ORDER) }
                ),
                recite = listOf(
                    Entry(PageGlyphKind.RANDOM, R.string.ex_recite_title, R.string.ex_recite_desc, listOf(ExerciseType.RECITE_ON), onRecite)
                )
            ),
            Skill.PLACES to SkillExercises(
                choose = listOf(
                    Entry(PageGlyphKind.WHERE, R.string.ex_where_title, R.string.ex_where_desc, listOf(ExerciseType.QUIZ_WHERE)) { onOpenQuiz(QuizMode.WHERE) },
                    Entry(PageGlyphKind.PAGE_START, R.string.ex_pstart_title, R.string.ex_pstart_desc, listOf(ExerciseType.QUIZ_PAGE_START)) { onOpenPageQuiz(false) },
                    Entry(PageGlyphKind.PAGE_END, R.string.ex_pend_title, R.string.ex_pend_desc, listOf(ExerciseType.QUIZ_PAGE_END)) { onOpenPageQuiz(true) }
                ),
                recite = listOf(
                    Entry(PageGlyphKind.BOUNDARIES, R.string.ex_boundaries_title, R.string.ex_boundaries_desc, ExerciseType.FROM_PAGE, onOpenBoundaries),
                    Entry(PageGlyphKind.EDGES, R.string.ex_edges_title, R.string.ex_edges_desc, ExerciseType.FROM_VERSE, onOpenPageEdges)
                )
            ),
            Skill.LOOKALIKES to SkillExercises(
                choose = listOf(
                    Entry(PageGlyphKind.SIMILAR, R.string.practice_skill_set_title, R.string.practice_set_lookalikes_desc, Skill.LOOKALIKES.quizTypes) { onOpenQuiz(QuizMode.LOOKALIKES) },
                    Entry(PageGlyphKind.ENDING, R.string.ex_ending_title, R.string.ex_ending_desc, listOf(ExerciseType.QUIZ_ENDING)) { onOpenQuiz(QuizMode.ENDING) },
                    Entry(PageGlyphKind.FINISH, R.string.ex_finish_title, R.string.ex_finish_desc, listOf(ExerciseType.QUIZ_FINISH)) { onOpenQuiz(QuizMode.FINISH) }
                ),
                recite = listOf(
                    Entry(PageGlyphKind.SIMILAR, R.string.ex_similarity_title, R.string.ex_similarity_desc, emptyList(), onSimilarityTest)
                )
            )
        )
    }
    val weakest = PracticeSkills.weakest(skills)
    val quizWeek = (ExerciseType.QUIZ + ExerciseType.PAGE_QUIZ).sumOf { week[it] ?: 0 }

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
            ScopeChoice(scope = scope, preview = preview, onPick = viewModel::setScope, onPlaces = { placing = true }, onCustom = { choosing = true })
            DailyHero(
                question = daily,
                note = if (quizWeek > 0) stringResource(R.string.ex_this_week, quizWeek) else null,
                onStart = { onOpenQuiz(QuizMode.DAILY) }
            )
            // The game: what catches the eye on this screen after the day's set.
            LanternsCard(
                masteryBest = gameBest,
                stars = gameStars,
                onPlay = onPlayGame,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
            )

            SectionHead(stringResource(R.string.practice_section_skills))
            Skill.entries.forEach { skill ->
                SkillRow(skill = skill, score = skills[skill], weakest = skill == weakest, onClick = { openSkill = skill })
            }

            if (missed.isNotEmpty()) {
                SectionHead(stringResource(R.string.practice_section_suggested))
                IndexRow(
                    title = stringResource(R.string.practice_missed_title),
                    description = pluralStringResource(R.plurals.practice_missed_desc, missed.size, missed.size),
                    glyph = PageGlyphKind.WEAK,
                    onClick = { onOpenQuiz(QuizMode.MISSED) }
                )
            }

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
            Spacer(Modifier.height(24.dp))
        }
    }

    openSkill?.let { skill ->
        SkillSheet(
            skill = skill,
            score = skills[skill],
            exercises = exercises.getValue(skill),
            week = week,
            onDismiss = { openSkill = null }
        )
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

    if (placing) {
        PlaceSheet(
            place = scope.place,
            onApply = { place ->
                placing = false
                // Chosen from the row of chips: the place is the whole choice (every memorised page in it).
                viewModel.setScope(PracticeScope(place = place))
            },
            onDismiss = { placing = false }
        )
    }
}

// ── Which pages ─────────────────────────────────────────────────────────────────

/**
 * "Questions from 241 pages" (with the place, when one is chosen), and the one-tap choices: all the hifz, revised today,
 * this week, the weak pages, chosen surahs, juz or pages, or custom.
 */
@Composable
private fun ScopeChoice(
    scope: PracticeScope,
    preview: PracticeScope.Preview?,
    onPick: (PracticeScope) -> Unit,
    onPlaces: () -> Unit,
    onCustom: () -> Unit
) {
    val preset = ScopePreset.entries.firstOrNull { it.scope == scope }
    val place = scope.place
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when {
                preview == null -> " "
                preview.total == 0 -> stringResource(R.string.scope_none_match)
                else -> {
                    val pages = pluralStringResource(R.plurals.scope_span_pages, preview.total, preview.total)
                    stringResource(R.string.practice_scope_from, if (place == null) pages else placeLabel(place) + " · " + pages)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (preview != null && preview.total == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScopePreset.entries.forEach { p ->
                ScopeChip(text = stringResource(p.label), selected = p == preset, onClick = { onPick(p.scope) })
            }
            ScopeChip(text = stringResource(R.string.practice_chip_places), selected = place != null, onClick = onPlaces, opens = true)
            ScopeChip(text = stringResource(R.string.practice_chip_custom), selected = preset == null && place == null, onClick = onCustom, opens = true)
        }
    }
}

/** A pill like the app's filter pills: tonal when chosen. [opens] adds a down arrow: it opens the full choice. */
@Composable
private fun ScopeChip(text: String, selected: Boolean, onClick: () -> Unit, opens: Boolean = false) {
    val colors = FolioTheme.colors
    val source = remember { MutableInteractionSource() }
    val content = if (selected) colors.onTonal else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .selectable(selected = selected, interactionSource = source, indication = null, role = if (opens) Role.Button else Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .pressScale(source)
                .heightIn(min = 36.dp)
                .clip(FolioTheme.shapes.pill)
                .background(if (selected) colors.tonal else colors.folio)
                .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, FolioTheme.shapes.pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
            if (opens) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
    }
}

// ── The day's set ───────────────────────────────────────────────────────────────

/**
 * What to do first: the day's set on the screen's one deep card, with the question it opens with, set as the quiz
 * will show it, and a gold button that starts it. The question's place is kept while it is built, so nothing below
 * moves when it arrives.
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
        QuestionPreview(question)
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

/** The first question of the set: the verse it is about between two gold lines, and what it asks; a quiet wait until built. */
@Composable
private fun QuestionPreview(question: QuizQuestion?) {
    val accent = FolioTheme.colors.heroAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .heightIn(min = PREVIEW_HEIGHT)
            .drawBehind {
                val line = accent.copy(alpha = 0.35f)
                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (question == null) {
            Box(Modifier.fillMaxWidth().heightIn(min = PREVIEW_HEIGHT - 16.dp), contentAlignment = Alignment.Center) {
                PaperLoading(color = accent.copy(alpha = 0.6f))
            }
            return@Column
        }
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

/** Room for two lines of the verse and the line under them, so the card does not grow when the question arrives. */
private val PREVIEW_HEIGHT = 124.dp

// ── Skills ──────────────────────────────────────────────────────────────────────

@StringRes
private fun Skill.title(): Int = when (this) {
    Skill.LINKS -> R.string.practice_links_title
    Skill.PLACES -> R.string.practice_places_title
    Skill.LOOKALIKES -> R.string.practice_lookalikes_title
}

@StringRes
private fun Skill.desc(): Int = when (this) {
    Skill.LINKS -> R.string.practice_links_desc
    Skill.PLACES -> R.string.practice_places_desc
    Skill.LOOKALIKES -> R.string.practice_lookalikes_desc
}

private fun Skill.glyph(): PageGlyphKind = when (this) {
    Skill.LINKS -> PageGlyphKind.NEXT
    Skill.PLACES -> PageGlyphKind.WHERE
    Skill.LOOKALIKES -> PageGlyphKind.ENDING
}

/** The colour of a share right: strong from 80%, fair from 60%, weak below. */
@Composable
private fun scoreColor(score: Float): Color = when {
    score >= 0.8f -> DifficultySmooth
    score >= 0.6f -> DifficultyStruggled
    else -> DifficultyCritical
}

/**
 * A skill as a row: its drawing, name and what it covers, and at the end how the last 30 days went (the share right
 * and a short bar in its level's colour), or "try it" with too few answers. The weakest says "start here".
 */
@Composable
private fun SkillRow(skill: Skill, score: SkillScore?, weakest: Boolean, onClick: () -> Unit) {
    val share = score?.score
    val spoken = if (share != null) {
        stringResource(R.string.practice_skill_score_cd, (share * 100).toInt(), pluralStringResource(R.plurals.practice_answers, score.answered, score.answered))
    } else stringResource(R.string.practice_skill_try)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageGlyph(skill.glyph())
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(skill.title()),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(skill.desc()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (weakest) {
                    Text(
                        text = stringResource(R.string.practice_skill_weakest),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.width(56.dp).clearAndSetSemantics { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (share != null) {
                    Text(
                        text = stringResource(R.string.practice_skill_score, (share * 100).toInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    ScoreBar(share)
                } else {
                    Text(
                        text = stringResource(R.string.practice_skill_try),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Hairline()
    }
}

/** A short bar filled as far as the share right, in its level's colour. */
@Composable
private fun ScoreBar(share: Float) {
    val color = scoreColor(share)
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind {
                drawRect(track)
                // The bar fills from the start of the line: the right in right-to-left.
                val filled = size.width * share.coerceIn(0f, 1f)
                val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - filled else 0f
                drawRect(color, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(filled, size.height))
            }
    )
}

/**
 * A skill's exercises, in a sheet: those where the user chooses and the app checks, then those recited from memory
 * and rated by the user. Each says how many answers it had this week. A pick closes the sheet and starts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillSheet(
    skill: Skill,
    score: SkillScore?,
    exercises: SkillExercises,
    week: Map<ExerciseType, Int>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(skill.title()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
            )
            val share = score?.score
            Text(
                text = if (share != null) {
                    stringResource(R.string.practice_skill_score_cd, (share * 100).toInt(), pluralStringResource(R.plurals.practice_answers, score.answered, score.answered))
                } else stringResource(R.string.practice_skill_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 4.dp)
            )
            SectionHead(stringResource(R.string.practice_sheet_choose))
            exercises.choose.forEach { entry -> EntryRow(entry, week, onDismiss) }
            if (exercises.recite.isNotEmpty()) {
                SectionHead(stringResource(R.string.practice_sheet_recite))
                exercises.recite.forEach { entry -> EntryRow(entry, week, onDismiss) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, week: Map<ExerciseType, Int>, onDismiss: () -> Unit) {
    val count = entry.types.sumOf { week[it] ?: 0 }
    IndexRow(
        title = stringResource(entry.title),
        description = stringResource(entry.desc),
        glyph = entry.glyph,
        note = if (count > 0) stringResource(R.string.ex_this_week, count) else null,
        onClick = { onDismiss(); entry.onClick() }
    )
}
