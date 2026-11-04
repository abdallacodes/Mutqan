package com.example.mutqan.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.ReadyTest
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.SimilarityQuestions
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.TestType
import com.example.mutqan.domain.TestVerseResult
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranWord

/** A lookalike shown with the answer: where it is, its words, and which of them it shares with the verse asked. */
data class SimilarVerse(val reference: String, val words: List<QuranWord>, val shared: Set<Int>, val ayahId: Int)

/** Where the verses of the test come from. */
enum class TestSource { MINE, READY }

sealed class TestUiState {
    object Idle : TestUiState()
    object Loading : TestUiState()
    data class Question(
        val result: TestVerseResult,
        val isRevealed: Boolean = false,
        val source: TestSource = TestSource.MINE,
        /** The words of the verse and the positions it shares with its lookalikes; only for ready-made groups. */
        val words: List<QuranWord> = emptyList(),
        val shared: Set<Int> = emptySet(),
        val similar: List<SimilarVerse> = emptyList(),
        val type: TestType = TestType.RECALL,
        /** For the choice types: what is shown above the options, the options, the right one and the one picked. */
        val lead: MushafSpan = MushafSpan.Empty,
        val options: List<MushafSpan> = emptyList(),
        val correct: Int = -1,
        val picked: Int? = null
    ) : TestUiState()
    object Empty : TestUiState()
    /** The user's own groups are the source and there are none. */
    object NoGroups : TestUiState()
    data class Error(val message: UiText) : TestUiState()
}

/** A verse picked for the test, with the other verses of its group. */
private class Pick(
    val verse: QuranAyah,
    val groupId: Int,
    val groupName: String,
    val shared: Set<Int>,
    val others: List<Other>
)

private class Other(val ayah: QuranAyah, val shared: Set<Int>)

class SimilarityTestViewModel(
    private val context: Context,
    private val study: StudyRepository,
    private val groupId: Int?,
    private val folderId: Int?
) : ViewModel() {

    /** The user can pick the ready-made groups only when the test is not limited to one of their own groups or folders. */
    val canChooseSource: Boolean = (groupId == null || groupId <= 0) && (folderId == null || folderId <= 0)

    private val _source = MutableStateFlow(TestSource.MINE)
    val source = _source.asStateFlow()

    private val _type = MutableStateFlow(TestType.RECALL)
    val type = _type.asStateFlow()

    private val _uiState = MutableStateFlow<TestUiState>(TestUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val recentGroups = ArrayDeque<Int>()
    private val random = Random.Default

    private companion object {
        const val MAX_TRIES = 25
    }

    init {
        loadNextVerse(fallbackToReady = true)
    }

    fun chooseSource(source: TestSource) {
        if (source == _source.value) return
        _source.value = source
        loadNextVerse()
    }

    fun chooseType(type: TestType) {
        if (type == _type.value) return
        _type.value = type
        loadNextVerse()
    }

    /**
     * [fallbackToReady] is for the first question only: a user with no groups of their own opens on the
     * ready-made ones. Afterwards an empty "my groups" is shown as such.
     */
    fun loadNextVerse(fallbackToReady: Boolean = false) {
        _uiState.update { TestUiState.Loading }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var pick = pickVerse(_source.value)
                if (pick == null && _source.value == TestSource.MINE && canChooseSource && fallbackToReady) {
                    _source.value = TestSource.READY
                    pick = pickVerse(TestSource.READY)
                }
                if (pick == null) {
                    _uiState.update {
                        if (_source.value == TestSource.MINE && canChooseSource) TestUiState.NoGroups else TestUiState.Empty
                    }
                    return@launch
                }
                // A verse that does not suit the chosen type is skipped for another, so "finish it" is not quietly
                // turned into a recall question; only when none suits (a group with a single verse) does it recall.
                var built = question(pick, _source.value, _type.value)
                var tries = 0
                while (built == null && tries++ < MAX_TRIES) {
                    val next = pickVerse(_source.value) ?: break
                    built = question(next, _source.value, _type.value)
                }
                _uiState.update { built ?: recallOf(pick, _source.value) }
            } catch (e: Exception) {
                _uiState.update { TestUiState.Error(UiText(R.string.error_unknown)) }
            }
        }
    }

    private suspend fun pickVerse(source: TestSource): Pick? {
        val quran = QuranRepository.getInstance(context)
        return if (source == TestSource.READY) pickReady(quran) else pickMine(quran)
    }

    private suspend fun pickMine(quran: QuranRepository): Pick? {
        val res = study.randomVerseForTest(
            groupId = if (groupId != null && groupId > 0) groupId else null,
            folderId = if (folderId != null && folderId > 0) folderId else null
        ) ?: return null
        val others = study.groupAyahIds(res.groupId).filter { it != res.verse.id }
            .mapNotNull { quran.ayahById(it) }.map { Other(it, emptySet()) }
        return Pick(res.verse, res.groupId, res.groupName, emptySet(), others)
    }

    private suspend fun pickReady(quran: QuranRepository): Pick? {
        val data = SimilarLibrary.load(context)
        val ranges = study.coverage().first()
        val picked = ReadyTest.pick(
            groups = data.groups,
            memorised = { HifzCoverage.containsAll(ranges, it..it) },
            anyMemorised = ranges.isNotEmpty(),
            recent = recentGroups
        ) ?: return null
        val (group, member) = picked
        recentGroups.addLast(group.id)
        while (recentGroups.size > ReadyTest.RECENT_LIMIT) recentGroups.removeFirst()

        val verse = quran.ayahById(member.ayahId) ?: return null
        val others = group.members.filter { it.ayahId != member.ayahId }.mapNotNull { other ->
            quran.ayahById(other.ayahId)?.let { Other(it, ReadyTest.hits(other.matchedWords)) }
        }
        return Pick(verse, group.id, group.description, ReadyTest.hits(member.matchedWords), others)
    }

    private fun wordsOf(quran: QuranRepository, a: QuranAyah): List<QuranWord> =
        quran.wordsOfAyah(a.surah, a.ayah).filter { !it.isEnd }

    private fun reference(a: QuranAyah) = SurahData.nameOf(a.surah) + " · " + a.ayah

    /** The question for [pick] of the chosen [type], or null when that type does not suit the verse. */
    private fun question(pick: Pick, source: TestSource, type: TestType): TestUiState.Question? {
        val quran = QuranRepository.getInstance(context)
        val words = wordsOf(quran, pick.verse)
        val otherWords = pick.others.map { it to wordsOf(quran, it.ayah) }
        val base = TestUiState.Question(
            result = TestVerseResult(pick.verse, pick.groupId, pick.groupName),
            source = source,
            words = words,
            shared = pick.shared,
            similar = otherWords.map { (o, w) -> SimilarVerse(reference(o.ayah), w, o.shared, o.ayah.id) }
        )
        val set = when (type) {
            TestType.RECALL -> null
            TestType.FINISH -> SimilarityQuestions.finish(words, otherWords.map { it.second }, random)
            TestType.WHICH -> SimilarityQuestions.which(
                words, reference(pick.verse), otherWords.map { (o, w) -> reference(o.ayah) to w }, random
            )
        } ?: return if (type == TestType.RECALL) base else null
        return base.copy(type = type, lead = set.lead, options = set.options, correct = set.correct)
    }

    private fun recallOf(pick: Pick, source: TestSource): TestUiState.Question = question(pick, source, TestType.RECALL)!!

    fun reveal() {
        _uiState.update { state ->
            if (state is TestUiState.Question) {
                state.copy(isRevealed = true)
            } else state
        }
    }

    fun pick(index: Int) {
        _uiState.update { state ->
            if (state is TestUiState.Question && state.picked == null) state.copy(picked = index, isRevealed = true) else state
        }
    }
}

class SimilarityTestViewModelFactory(
    private val context: Context,
    private val groupId: Int?,
    private val folderId: Int?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SimilarityTestViewModel(
            context.applicationContext,
            StudyRepository.create(context),
            groupId,
            folderId
        ) as T
}
