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
        .map { counts -> ExerciseType.QUIZ.sumOf { counts[it] ?: 0 } }
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scroll)) {
            ScopeRow(scope = scope, preview = preview, onClick = { choosing = true })
            DailyHero(
                note = if (quizWeek > 0) stringResource(R.string.ex_this_week, quizWeek) else null,
                onStart = { onOpenQuiz(QuizMode.DAILY) }
            )
            TileGroup(
                label = stringResource(R.string.practice_group_questions),
                tiles = listOf(
                    PracticeTile(R.string.ex_ending_title, R.string.ex_ending_desc) { onOpenQuiz(QuizMode.ENDING) },
                    PracticeTile(R.string.ex_next_title, R.string.ex_next_desc) { onOpenQuiz(QuizMode.NEXT) },
                    PracticeTile(R.string.ex_finish_title, R.string.ex_finish_desc) { onOpenQuiz(QuizMode.FINISH) },
                    PracticeTile(R.string.ex_order_title, R.string.ex_order_desc) { onOpenQuiz(QuizMode.ORDER) }
                )
            )
            TileGroup(
                label = stringResource(R.string.practice_group_place),
                tiles = listOf(
                    PracticeTile(R.string.ex_where_title, R.string.ex_where_desc) { onOpenQuiz(QuizMode.WHERE) },
                    PracticeTile(R.string.ex_boundaries_title, R.string.ex_boundaries_desc, if (week > 0) stringResource(R.string.ex_this_week, week) else null, onOpenBoundaries),
                    PracticeTile(R.string.ex_edges_title, R.string.ex_edges_desc, null, onOpenPageEdges),
                    PracticeTile(R.string.ex_random_title, R.string.ex_random_desc, null, onRandomVerse)
                )
            )
            TileGroup(
                label = stringResource(R.string.practice_group_similar),
                tiles = listOf(
                    PracticeTile(R.string.ex_similarity_title, R.string.ex_similarity_desc, null, onSimilarityTest),
                    PracticeTile(R.string.vault_title, R.string.ex_vault_desc, null, onOpenVault)
                )
            )
            Spacer(Modifier.height(24.dp))
        }
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

/** One exercise in a group: a name, a line about it, and optionally a small note (what was done this week). */
private class PracticeTile(
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val note: String? = null,
    val onClick: () -> Unit
)

/** What to do first: the daily set, named, described, with a button that starts it. */
@Composable
private fun DailyHero(note: String?, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.practice_daily_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.practice_daily_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        PaperButton(text = stringResource(R.string.practice_start), onClick = onStart, modifier = Modifier.fillMaxWidth())
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
    Hairline(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter))
}

/** A named group of exercises set two to a row, ruled like a table. */
@Composable
private fun TileGroup(label: String, tiles: List<PracticeTile>) {
    SectionLabel(label, Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 24.dp, bottom = 8.dp))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Hairline(Modifier.fillMaxWidth())
        tiles.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                TileCell(pair[0], first = true, modifier = Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                if (pair.size > 1) {
                    TileCell(pair[1], first = false, modifier = Modifier.weight(1f).fillMaxHeight())
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            Hairline(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TileCell(tile: PracticeTile, first: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = tile.onClick)
            .padding(start = if (first) 0.dp else 16.dp, end = if (first) 16.dp else 0.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(tile.title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(tile.desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (tile.note != null) {
            Text(text = tile.note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
