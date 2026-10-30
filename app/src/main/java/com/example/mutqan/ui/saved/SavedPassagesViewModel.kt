package com.example.mutqan.ui.saved

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.domain.SavedKind
import com.example.mutqan.domain.SavedPassage
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.WeakTag
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.toCleanQuranicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A saved passage with the Quran details needed to show it. */
@Immutable
data class SavedItem(
    val passage: SavedPassage,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val page: Int,
    /** Opening of the first ayah, without its number. */
    val snippet: String,
    /** 1-based position of the saved word within its ayah; 0 when the whole ayah is saved. */
    val wordPosition: Int
)

@Immutable
data class SavedUiState(
    val items: List<SavedItem> = emptyList(),
    val kindFilter: SavedKind? = null,
    val tagFilter: Set<WeakTag> = emptySet(),
    val isLoaded: Boolean = false
)

class SavedPassagesViewModel(
    private val study: StudyRepository,
    private val quran: QuranRepository
) : ViewModel() {

    private val kindFilter = MutableStateFlow<SavedKind?>(null)
    private val tagFilter = MutableStateFlow<Set<WeakTag>>(emptySet())

    val uiState: StateFlow<SavedUiState> = combine(study.savedPassages(), kindFilter, tagFilter) { all, kind, tags ->
        val shown = all
            .filter { kind == null || it.kind == kind }
            .filter { tags.isEmpty() || it.tags.any { tag -> tag in tags } }
            // Open weak passages first, most important first, then newest.
            .sortedWith(
                compareBy<SavedPassage> { it.isResolved }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.createdAt }
            )
        SavedUiState(items = shown.mapNotNull(::describe), kindFilter = kind, tagFilter = tags, isLoaded = true)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    private fun describe(p: SavedPassage): SavedItem? {
        val first = quran.ayahById(p.startAyahId) ?: return null
        val last = if (p.endAyahId == p.startAyahId) first else quran.ayahById(p.endAyahId) ?: first
        val position = if (p.wordId == 0) 0
        else quran.wordsOfAyah(first.surah, first.ayah).firstOrNull { it.id == p.wordId }?.position ?: 0
        return SavedItem(
            passage = p,
            startSurah = first.surah,
            startAyah = first.ayah,
            endSurah = last.surah,
            endAyah = last.ayah,
            page = first.firstPage,
            snippet = first.text.toCleanQuranicText().take(SNIPPET_CHARS),
            wordPosition = position
        )
    }

    fun setKindFilter(kind: SavedKind?) {
        kindFilter.value = kind
        if (kind == SavedKind.BOOKMARK) tagFilter.value = emptySet()
    }

    fun toggleTagFilter(tag: WeakTag) {
        tagFilter.update { if (tag in it) it - tag else it + tag }
    }

    fun save(passage: SavedPassage) {
        viewModelScope.launch { study.savePassage(passage) }
    }

    fun delete(id: Int) {
        viewModelScope.launch { study.deleteSavedPassage(id) }
    }

    /** Marks a weak passage solid again, or reopens it. */
    fun toggleSolid(passage: SavedPassage) {
        save(passage.copy(resolvedAt = if (passage.isResolved) 0L else System.currentTimeMillis()))
    }

    private companion object {
        const val SNIPPET_CHARS = 120
    }
}

class SavedPassagesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SavedPassagesViewModel(StudyRepository.create(context), QuranRepository.getInstance(context)) as T
}
