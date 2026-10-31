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
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.TestVerseResult
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A lookalike shown with the answer: where it is, its words, and which of them it shares with the verse asked. */
data class SimilarVerse(val reference: String, val words: List<String>, val shared: Set<Int>)

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
        val words: List<String> = emptyList(),
        val shared: Set<Int> = emptySet(),
        val similar: List<SimilarVerse> = emptyList()
    ) : TestUiState()
    object Empty : TestUiState()
    data class Error(val message: UiText) : TestUiState()
}

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

    private val _uiState = MutableStateFlow<TestUiState>(TestUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val recentGroups = ArrayDeque<Int>()

    init {
        loadNextVerse()
    }

    fun chooseSource(source: TestSource) {
        if (source == _source.value) return
        _source.value = source
        loadNextVerse()
    }

    fun loadNextVerse() {
        _uiState.update { TestUiState.Loading }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_source.value == TestSource.READY) {
                    loadReady()
                    return@launch
                }
                val res = study.randomVerseForTest(
                    groupId = if (groupId != null && groupId > 0) groupId else null,
                    folderId = if (folderId != null && folderId > 0) folderId else null
                )
                when {
                    res != null -> _uiState.update { TestUiState.Question(res) }
                    canChooseSource -> {
                        // An empty vault: the ready-made groups are the test.
                        _source.value = TestSource.READY
                        loadReady()
                    }
                    else -> _uiState.update { TestUiState.Empty }
                }
            } catch (e: Exception) {
                _uiState.update { TestUiState.Error(UiText(R.string.error_unknown)) }
            }
        }
    }

    private suspend fun loadReady() {
        val data = SimilarLibrary.load(context)
        val ranges = study.coverage().first()
        val picked = ReadyTest.pick(
            groups = data.groups,
            memorised = { HifzCoverage.containsAll(ranges, it..it) },
            anyMemorised = ranges.isNotEmpty(),
            recent = recentGroups
        )
        if (picked == null) {
            _uiState.update { TestUiState.Empty }
            return
        }
        val (group, member) = picked
        recentGroups.addLast(group.id)
        while (recentGroups.size > ReadyTest.RECENT_LIMIT) recentGroups.removeFirst()

        val quran = QuranRepository.getInstance(context)
        val verse = quran.ayahById(member.ayahId)
        if (verse == null) {
            _uiState.update { TestUiState.Empty }
            return
        }
        val similar = group.members.filter { it.ayahId != member.ayahId }.mapNotNull { other ->
            val ayah = quran.ayahById(other.ayahId) ?: return@mapNotNull null
            SimilarVerse(
                reference = SurahData.nameOf(ayah.surah) + " · " + ayah.ayah,
                words = quran.wordsOfAyah(ayah.surah, ayah.ayah).filter { !it.isEnd }.map { it.hafs },
                shared = ReadyTest.hits(other.matchedWords)
            )
        }
        _uiState.update {
            TestUiState.Question(
                result = TestVerseResult(verse, group.id, group.description),
                source = TestSource.READY,
                words = quran.wordsOfAyah(verse.surah, verse.ayah).filter { !it.isEnd }.map { it.hafs },
                shared = ReadyTest.hits(member.matchedWords),
                similar = similar
            )
        }
    }

    fun reveal() {
        _uiState.update { state ->
            if (state is TestUiState.Question) {
                state.copy(isRevealed = true)
            } else state
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
