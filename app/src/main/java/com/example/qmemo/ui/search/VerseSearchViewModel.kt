package com.example.qmemo.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.ArabicNormalization
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.VerseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SearchUiState(
    val query: String = "",
    val filterSurahId: Int? = null,
    val filterJuzStart: Int? = null,
    val filterJuzEnd: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class VerseSearchViewModel(private val dao: QuranDao) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val searchResults: StateFlow<List<VerseEntity>> = _uiState
        .debounce(300L)
        .flatMapLatest { state ->
            if (state.query.length < 2) return@flatMapLatest flowOf(emptyList())
            flow {
                emit(dao.searchVerses(
                    query = ArabicNormalization.normalizeForSearch(state.query),
                    surahId = state.filterSurahId,
                    juzStart = state.filterJuzStart,
                    juzEnd = state.filterJuzEnd
                ))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onQueryChange(q: String) = _uiState.update { it.copy(query = q) }

    fun onSurahFilterSelect(id: Int?) = _uiState.update {
        it.copy(filterSurahId = id, filterJuzStart = null, filterJuzEnd = null)
    }

    fun onJuzRangeChange(start: Int?, end: Int?) = _uiState.update {
        it.copy(filterJuzStart = start, filterJuzEnd = end, filterSurahId = null)
    }
}

class VerseSearchViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VerseSearchViewModel(AppDatabase.getInstance(context).quranDao()) as T
}
