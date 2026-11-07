package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.TopBarOverflowMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.mutqan.ui.navigation.OnTabReselected
import com.example.mutqan.ui.navigation.Routes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.components.LocalDockInset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mutqan.ui.components.ActionTile
import com.example.mutqan.ui.components.HeroButton
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.StarMedallion
import com.example.mutqan.ui.theme.FolioTheme

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** How many page-boundary rounds were done in the last seven days. */
class PracticeViewModel(study: StudyRepository, private val prefs: UserPreferencesRepository) : ViewModel() {

    /** Which pages the questions are drawn from. */
    val scope: StateFlow<PracticeScope> = prefs.practiceScope
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeScope())

    fun setScope(scope: PracticeScope) {
        viewModelScope.launch { prefs.setPracticeScope(scope) }
    }

    /** What the current choice gives, for the picker: how many pages, and how many at each level. */
    val preview: StateFlow<PracticeScope.Preview?> =
        combine(scope, study.coveredPages(), study.revisionLogs(), study.selfRatedStrength()) { choice, covered, logs, rated ->
            choice.preview(covered, PassageStates.compute(logs), rated, logs)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val boundariesThisWeek: StateFlow<Int> = study.exerciseCountsSince(System.currentTimeMillis() - WEEK_MS)
        .map { counts -> ExerciseType.PAGE_BOUNDARIES.sumOf { counts[it] ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** How many quiz questions were answered in the last seven days. */
    val quizThisWeek: StateFlow<Int> = study.exerciseCountsSince(System.currentTimeMillis() - WEEK_MS)
        .map { counts -> (ExerciseType.QUIZ + ExerciseType.PAGE_QUIZ).sumOf { counts[it] ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

class PracticeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PracticeViewModel(StudyRepository.create(context), UserPreferencesRepository(context.applicationContext)) as T
}

/**
 * Practice: the choice of pages, then the one thing to start with (the daily set), then every other exercise in small
 * groups. Each entry opens its own screen.
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
    var choosing by remember { mutableStateOf(false) }
    var pickingJuz by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    OnTabReselected(Routes.PRACTICE) { scroll.animateScrollTo(0) }

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
            // Quizzes: questions with an answer to check, and drills where you say the answer and rate yourself. The
            // choice of pages is here because it is what they are drawn from.
            SectionHeading(stringResource(R.string.practice_section_quizzes), first = true)
            ScopeRow(scope = scope, preview = preview, onClick = { choosing = true })
            DailyHero(
                note = if (quizWeek > 0) stringResource(R.string.ex_this_week, quizWeek) else null,
                onStart = { onOpenQuiz(QuizMode.DAILY) }
            )
            TileGroup(
                label = stringResource(R.string.practice_group_questions),
                tiles = listOf(
                    PracticeTile(Icons.Rounded.Flag, R.string.ex_ending_title, R.string.ex_ending_desc) { onOpenQuiz(QuizMode.ENDING) },
                    PracticeTile(Icons.Rounded.SkipNext, R.string.ex_next_title, R.string.ex_next_desc) { onOpenQuiz(QuizMode.NEXT) },
                    PracticeTile(Icons.Rounded.Edit, R.string.ex_finish_title, R.string.ex_finish_desc) { onOpenQuiz(QuizMode.FINISH) },
                    PracticeTile(Icons.Rounded.SwapVert, R.string.ex_order_title, R.string.ex_order_desc) { onOpenQuiz(QuizMode.ORDER) }
                )
            )
            TileGroup(
                label = stringResource(R.string.practice_group_place),
                tiles = listOf(
                    PracticeTile(Icons.Rounded.Place, R.string.ex_where_title, R.string.ex_where_desc) { onOpenQuiz(QuizMode.WHERE) },
                    PracticeTile(Icons.Rounded.VerticalAlignTop, R.string.ex_pstart_title, R.string.ex_pstart_desc) { onOpenPageQuiz(false) },
                    PracticeTile(Icons.Rounded.VerticalAlignBottom, R.string.ex_pend_title, R.string.ex_pend_desc) { onOpenPageQuiz(true) },
                    PracticeTile(Icons.Rounded.Flag, R.string.ex_boundaries_title, R.string.ex_boundaries_desc, if (week > 0) stringResource(R.string.ex_this_week, week) else null, onOpenBoundaries),
                    PracticeTile(Icons.Rounded.Crop, R.string.ex_edges_title, R.string.ex_edges_desc, null, onOpenPageEdges)
                )
            )
            TileGroup(
                label = stringResource(R.string.practice_group_similar),
                tiles = listOf(
                    PracticeTile(Icons.Rounded.Compare, R.string.ex_similarity_title, R.string.ex_similarity_desc, null, onSimilarityTest)
                )
            )

            // Tools: things to use rather than be tested on.
            SectionHeading(stringResource(R.string.practice_section_tools))
            TileGroup(
                label = null,
                tiles = listOf(
                    PracticeTile(Icons.Rounded.Map, R.string.ex_juzmap_title, R.string.ex_juzmap_desc) { pickingJuz = true },
                    PracticeTile(Icons.Rounded.Bookmarks, R.string.vault_title, R.string.ex_vault_desc, null, onOpenVault),
                    PracticeTile(Icons.Rounded.Shuffle, R.string.ex_random_title, R.string.ex_random_desc, null, onRandomVerse)
                )
            )
            Spacer(Modifier.height(24.dp))
        }
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

/** The title of one of the screen's two parts, Quizzes and Tools. */
@Composable
private fun SectionHeading(title: String, first: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = if (first) 8.dp else 32.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Hairline(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** One exercise in a group: a name, a line about it, and optionally a small note (what was done this week). */
private class PracticeTile(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val note: String? = null,
    val onClick: () -> Unit
)

/** What to do first: the daily set on the screen's one deep card, with a gold button that starts it. */
@Composable
private fun DailyHero(note: String?, onStart: () -> Unit) {
    HeroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)) {
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
        Spacer(Modifier.height(16.dp))
        HeroButton(text = stringResource(R.string.practice_start), onClick = onStart, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
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

/** A named group of exercises, two tiles to a row: each a raised card with an icon, so it reads as something to press. */
@Composable
private fun TileGroup(label: String?, tiles: List<PracticeTile>) {
    if (label != null) {
        SectionLabel(label, Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 24.dp, bottom = 10.dp))
    } else {
        Spacer(Modifier.height(14.dp))
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile ->
                    ActionTile(
                        icon = tile.icon,
                        title = stringResource(tile.title),
                        onClick = tile.onClick,
                        description = stringResource(tile.desc),
                        note = tile.note,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
