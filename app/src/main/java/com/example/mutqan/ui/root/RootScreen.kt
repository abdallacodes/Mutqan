package com.example.mutqan.ui.root

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.Morphology
import com.example.mutqan.domain.MorphologyLibrary
import com.example.mutqan.domain.WordLocation
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A lemma of the root and how many times it occurs, for the filter row. */
data class RootForm(val lemma: Int, val text: String, val count: Int)

/** One verse where the root occurs, with the positions of the words that carry it. */
data class RootVerse(val surah: Int, val ayah: Int, val hits: Set<Int>)

data class RootUiState(
    val loaded: Boolean = false,
    val letters: String = "",
    val words: Int = 0,
    val forms: List<RootForm> = emptyList(),
    /** The lemma chosen in the filter row; null shows every form. */
    val lemma: Int? = null,
    val verses: List<RootVerse> = emptyList()
)

/** Every verse that uses one root, optionally narrowed to one of its lemmas. */
class RootViewModel(private val rootId: Int, private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(RootUiState())
    val state: StateFlow<RootUiState> = _state.asStateFlow()

    private var morphology: Morphology? = null
    private var places: List<WordLocation> = emptyList()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val m = MorphologyLibrary.load(context)
            morphology = m
            places = m.occurrencesOfRoot(rootId)
            val forms = places.groupingBy { it.lemma }.eachCount()
                .filterKeys { it >= 0 }
                .map { (lemma, count) -> RootForm(lemma, m.lemmaText(lemma), count) }
                .sortedByDescending { it.count }
            _state.value = RootUiState(
                loaded = true, letters = m.rootSpaced(rootId), words = places.size, forms = forms, verses = versesOf(null)
            )
        }
    }

    fun selectLemma(lemma: Int?) {
        _state.update { it.copy(lemma = lemma, verses = versesOf(lemma)) }
    }

    private fun versesOf(lemma: Int?): List<RootVerse> =
        places.filter { lemma == null || it.lemma == lemma }
            .groupBy { it.surah * 1000 + it.ayah }
            .map { (_, list) -> RootVerse(list.first().surah, list.first().ayah, list.map { it.position }.toSet()) }
}

class RootViewModelFactory(private val context: Context, private val rootId: Int) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RootViewModel(rootId, context.applicationContext) as T
}

/** All the verses that use a root; the words that carry it are marked, and a lemma can be chosen to narrow the list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RootScreen(rootId: Int, onBack: () -> Unit, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val viewModel: RootViewModel = viewModel(key = "root-$rootId", factory = RootViewModelFactory(context, rootId))
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            PaperHeader(
                title = state.letters,
                subtitle = if (state.loaded) stringResource(R.string.root_summary, state.words, state.verses.size) else null,
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.forms.size > 1) {
                item {
                    Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.root_forms),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PaperChip(
                                selected = state.lemma == null,
                                onClick = { viewModel.selectLemma(null) },
                                label = { Text(stringResource(R.string.root_all_forms)) }
                            )
                            state.forms.forEach { form ->
                                PaperChip(
                                    selected = state.lemma == form.lemma,
                                    onClick = { viewModel.selectLemma(form.lemma) },
                                    label = {
                                        Text("${form.text} ${form.count}", fontFamily = AmiriFontFamily, fontSize = 20.sp)
                                    }
                                )
                            }
                        }
                    }
                    Hairline()
                }
            }
            items(state.verses, key = { it.surah * 1000 + it.ayah }) { verse ->
                VerseLine(verse, onOpenMushaf)
            }
        }
    }
}

@Composable
private fun VerseLine(verse: RootVerse, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val mark = MaterialTheme.colorScheme.primary
    val loaded by produceState<Pair<Int, List<String>>?>(null, verse) {
        value = withContext(Dispatchers.IO) {
            val repo = QuranRepository.getInstance(context)
            val page = repo.ayah(verse.surah, verse.ayah)?.firstPage ?: 1
            page to repo.wordsOfAyah(verse.surah, verse.ayah).filter { !it.isEnd }.map { it.hafs }
        }
    }
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, enabled = loaded != null) { loaded?.let { onOpenMushaf(it.first) } }
                .padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.quiz_ref, SurahData.nameOf(verse.surah), verse.ayah),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            loaded?.second?.let { words ->
                Text(
                    text = buildAnnotatedString {
                        words.forEachIndexed { i, word ->
                            if (i > 0) append(" ")
                            if (i + 1 in verse.hits) withStyle(SpanStyle(color = mark, fontWeight = FontWeight.Bold)) { append(word) }
                            else append(word)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Rtl, fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 42.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Hairline()
    }
}
