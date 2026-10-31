package com.example.mutqan.ui.topics

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.Topic
import com.example.mutqan.domain.TopicIndex
import com.example.mutqan.domain.TopicLanguage
import com.example.mutqan.domain.TopicsLibrary
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Topics, whether the Arabic interface lists the English-only ones, and what the user has memorised. */
class TopicsViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val prefs = UserPreferencesRepository(appContext)

    private val _index = MutableStateFlow<TopicIndex?>(null)
    val index: StateFlow<TopicIndex?> = _index

    val showAll: StateFlow<Boolean> = prefs.topicsShowAll.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Ayah-id ranges the user has memorised. */
    val coverage: StateFlow<List<IntRange>> = StudyRepository.create(appContext).coverage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _index.value = TopicsLibrary.load(appContext) }
    }

    fun setShowAll(on: Boolean) {
        viewModelScope.launch { prefs.setTopicsShowAll(on) }
    }
}

class TopicsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TopicsViewModel(context) as T
}

@Composable
fun rememberTopicLanguage(showAll: Boolean): TopicLanguage {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    return TopicLanguage(arabicUi = arabic, showAll = showAll)
}

@Composable
private fun TopicRow(topic: Topic, language: TopicLanguage, verseCount: Int, onClick: () -> Unit) {
    val count = stringResource(R.string.topic_verses, verseCount)
    val second = language.secondary(topic)
    HubRow(
        title = language.title(topic),
        subtitle = if (second != null) "$second · $count" else count,
        onClick = onClick
    )
}

/** The Topics tab of the Quran page: a search field, the two top-level groups, and the most mentioned topics. */
fun LazyListScope.topicsTab(
    index: TopicIndex?,
    language: TopicLanguage,
    query: String,
    onQueryChange: (String) -> Unit,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    onOpenTopic: (Int) -> Unit
) {
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.topics_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
        )
        if (language.arabicUi) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.topics_show_all),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = showAll, onCheckedChange = onShowAllChange)
            }
        }
    }
    if (index == null) return

    if (query.isNotBlank()) {
        val found = index.search(query, language, limit = 40)
        if (found.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.topics_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(PaperSpace.Gutter)
                )
            }
        }
        items(found, key = { "found-${it.id}" }) { TopicRow(it, language, index.versesOf(it).size) { onOpenTopic(it.id) } }
        return
    }

    val themes = index.themeRoots.filter(language::visible)
    val concepts = index.conceptRoots.filter(language::visible)
    val popular = index.popular(language, limit = 15)

    item { GroupLabel(stringResource(R.string.topics_themes)) }
    items(themes, key = { "theme-${it.id}" }) { TopicRow(it, language, index.versesOf(it).size) { onOpenTopic(it.id) } }
    item { GroupLabel(stringResource(R.string.topics_concepts)) }
    items(concepts, key = { "concept-${it.id}" }) { TopicRow(it, language, index.versesOf(it).size) { onOpenTopic(it.id) } }
    item { GroupLabel(stringResource(R.string.topics_popular)) }
    items(popular, key = { "popular-${it.id}" }) { TopicRow(it, language, index.versesOf(it).size) { onOpenTopic(it.id) } }
}

@Composable
private fun GroupLabel(text: String) {
    Column(modifier = Modifier.padding(start = PaperSpace.Gutter, end = PaperSpace.Gutter, top = 20.dp, bottom = 4.dp)) {
        SectionLabel(text)
        Spacer(Modifier.height(4.dp))
        Hairline()
    }
}

/** One topic: what it is, the topics under it and next to it, and every verse it is listed under. */
@Composable
fun TopicScreen(topicId: Int, onBack: () -> Unit, onOpenMushaf: (Int) -> Unit, onOpenTopic: (Int) -> Unit) {
    val context = LocalContext.current
    val viewModel: TopicsViewModel = viewModel(factory = TopicsViewModelFactory(context))
    val index by viewModel.index.collectAsState()
    val showAll by viewModel.showAll.collectAsState()
    val ranges by viewModel.coverage.collectAsState()
    val language = rememberTopicLanguage(showAll)
    var memorisedOnly by rememberSaveable { mutableStateOf(false) }

    val topic = index?.get(topicId)
    Scaffold(
        topBar = {
            PaperHeader(
                title = topic?.let(language::title) ?: "",
                subtitle = topic?.let(language::secondary),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (index == null || topic == null) return@Scaffold
        val idx = index!!
        val children = remember(topic, language) {
            idx.childrenOf(topic.id).filter { language.visible(it) && idx.versesOf(it).isNotEmpty() }
                .sortedByDescending { idx.versesOf(it).size }
        }
        val related = remember(topic, language) { topic.related.toList().mapNotNull { idx[it] }.filter { language.visible(it) && idx.versesOf(it).isNotEmpty() }.take(8) }
        val allVerses = remember(topic) { idx.versesOf(topic) }
        val memorised = remember(allVerses, ranges) { allVerses.count { HifzCoverage.containsAll(ranges, it..it) } }
        val verses = remember(allVerses, ranges, memorisedOnly) {
            if (memorisedOnly) allVerses.filter { HifzCoverage.containsAll(ranges, it..it) } else allVerses.toList()
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (topic.description.isNotEmpty() && (!language.arabicUi || showAll)) {
                item {
                    Text(
                        text = topic.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
                    )
                }
            }
            if (children.isNotEmpty()) {
                item { GroupLabel(stringResource(R.string.topic_subtopics)) }
                items(children, key = { "child-${it.id}" }) { TopicRow(it, language, idx.versesOf(it).size) { onOpenTopic(it.id) } }
            }
            if (related.isNotEmpty()) {
                item { GroupLabel(stringResource(R.string.topic_related)) }
                items(related, key = { "related-${it.id}" }) { TopicRow(it, language, idx.versesOf(it).size) { onOpenTopic(it.id) } }
            }
            if (allVerses.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(start = PaperSpace.Gutter, end = PaperSpace.Gutter, top = 20.dp, bottom = 4.dp)) {
                        SectionLabel(stringResource(R.string.topic_verses_header, allVerses.size))
                        if (ranges.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.topic_memorised_of, memorised, allVerses.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row {
                                PaperChip(
                                    selected = memorisedOnly,
                                    onClick = { memorisedOnly = !memorisedOnly },
                                    label = { Text(stringResource(R.string.topic_only_memorised)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Hairline()
                    }
                }
                items(verses, key = { "verse-$it" }) { id ->
                    VerseLine(id, memorised = HifzCoverage.containsAll(ranges, id..id), onOpenMushaf = onOpenMushaf)
                }
            }
        }
    }
}

@Composable
private fun VerseLine(ayahId: Int, memorised: Boolean, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val loaded by produceState<Triple<String, Int, String>?>(null, ayahId) {
        value = withContext(Dispatchers.IO) {
            val repo = QuranRepository.getInstance(context)
            val ayah = repo.ayahById(ayahId) ?: return@withContext null
            val text = repo.wordsOfAyah(ayah.surah, ayah.ayah).filter { !it.isEnd }.joinToString(" ") { it.hafs }
            Triple(SurahData.nameOf(ayah.surah) + " · " + ayah.ayah, ayah.firstPage, text)
        }
    }
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, enabled = loaded != null) { loaded?.let { onOpenMushaf(it.second) } }
                .padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            loaded?.let { (ref, _, text) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ref, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (memorised) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.quran_memorised),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = text,
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

/** The topics a verse is listed under, as chips; tapping one opens the topic. Nothing when it has none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerseTopics(ayahId: Int, onOpenTopic: (Int) -> Unit) {
    val context = LocalContext.current
    val index by produceState<TopicIndex?>(null) { value = TopicsLibrary.load(context) }
    val showAll by remember { UserPreferencesRepository(context.applicationContext).topicsShowAll }.collectAsState(initial = false)
    val language = rememberTopicLanguage(showAll)
    val topics = remember(index, ayahId, language) { index?.topicsOf(ayahId)?.filter(language::visible)?.take(8).orEmpty() }
    if (topics.isEmpty()) return

    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hairline()
        Text(
            text = stringResource(R.string.peek_topics_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topics.forEach { topic ->
                Text(
                    text = language.title(topic),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button) { onOpenTopic(topic.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
