package com.example.mutqan.ui.practice

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import kotlinx.coroutines.launch
import com.example.mutqan.domain.QuizMode
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.TopBarOverflowMenu
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** How many page-boundary rounds were done in the last seven days. */
class PracticeViewModel(study: StudyRepository, private val prefs: UserPreferencesRepository) : ViewModel() {

    /** Which pages the questions are drawn from. */
    val scope: StateFlow<PracticeScope> = prefs.practiceScope
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeScope())

    fun setScope(scope: PracticeScope) {
        viewModelScope.launch { prefs.setPracticeScope(scope) }
    }

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

/** Every exercise in one list. Each entry opens its own screen. */
@Composable
fun PracticeScreen(
    onOpenBoundaries: () -> Unit,
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            ScopePicker(scope = scope, onChange = viewModel::setScope)
            HubRow(
                title = stringResource(R.string.practice_daily_title),
                subtitle = stringResource(R.string.practice_daily_desc),
                note = if (quizWeek > 0) stringResource(R.string.ex_this_week, quizWeek) else null,
                onClick = { onOpenQuiz(QuizMode.DAILY) }
            )
            HubRow(
                title = stringResource(R.string.ex_similarity_title),
                subtitle = stringResource(R.string.ex_similarity_desc),
                onClick = onSimilarityTest
            )
            HubRow(
                title = stringResource(R.string.ex_ending_title),
                subtitle = stringResource(R.string.ex_ending_desc),
                onClick = { onOpenQuiz(QuizMode.ENDING) }
            )
            HubRow(
                title = stringResource(R.string.ex_next_title),
                subtitle = stringResource(R.string.ex_next_desc),
                onClick = { onOpenQuiz(QuizMode.NEXT) }
            )
            HubRow(
                title = stringResource(R.string.ex_fill_title),
                subtitle = stringResource(R.string.ex_fill_desc),
                onClick = { onOpenQuiz(QuizMode.FILL) }
            )
            HubRow(
                title = stringResource(R.string.ex_order_title),
                subtitle = stringResource(R.string.ex_order_desc),
                onClick = { onOpenQuiz(QuizMode.ORDER) }
            )
            HubRow(
                title = stringResource(R.string.ex_where_title),
                subtitle = stringResource(R.string.ex_where_desc),
                onClick = { onOpenQuiz(QuizMode.WHERE) }
            )
            HubRow(
                title = stringResource(R.string.ex_boundaries_title),
                subtitle = stringResource(R.string.ex_boundaries_desc),
                note = if (week > 0) stringResource(R.string.ex_this_week, week) else null,
                onClick = onOpenBoundaries
            )
            HubRow(
                title = stringResource(R.string.ex_random_title),
                subtitle = stringResource(R.string.ex_random_desc),
                onClick = onRandomVerse
            )
            HubRow(
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(R.string.ex_vault_desc),
                onClick = onOpenVault
            )
        }
    }
}

/** Which pages the questions come from: the levels, and the pages last reviewed. Nothing chosen means all memorised pages. */
@Composable
private fun ScopePicker(scope: PracticeScope, onChange: (PracticeScope) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)) {
        SectionLabel(stringResource(R.string.practice_scope_title))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf(
                PlanTier.WEAK to R.string.practice_scope_weak,
                PlanTier.FAIR to R.string.practice_scope_fair,
                PlanTier.STRONG to R.string.practice_scope_strong
            ).forEach { (tier, label) ->
                PaperChip(
                    selected = tier in scope.tiers,
                    onClick = { onChange(scope.withTier(tier, tier !in scope.tiers)) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.weight(1f)
                )
            }
            PaperChip(
                selected = scope.lastReviewed > 0,
                onClick = { onChange(scope.copy(lastReviewed = if (scope.lastReviewed > 0) 0 else PracticeScope.DEFAULT_LAST)) },
                label = { Text(stringResource(R.string.practice_scope_last)) },
                modifier = Modifier.weight(1.4f)
            )
        }
        if (scope.lastReviewed > 0) {
            Text(
                text = stringResource(R.string.practice_scope_last_how_many),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                PracticeScope.LAST_CHOICES.forEach { count ->
                    PaperChip(
                        selected = count == scope.lastReviewed,
                        onClick = { onChange(scope.copy(lastReviewed = count)) },
                        label = { Text("$count") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Text(
            text = stringResource(if (scope.isAll) R.string.practice_scope_all else R.string.practice_scope_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    Hairline()
}
