package com.example.mutqan.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.TestVerseResult
import com.example.mutqan.ui.components.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class TestUiState {
    object Idle : TestUiState()
    object Loading : TestUiState()
    data class Question(
        val result: TestVerseResult,
        val isRevealed: Boolean = false
    ) : TestUiState()
    object Empty : TestUiState()
    data class Error(val message: UiText) : TestUiState()
}

class SimilarityTestViewModel(
    private val study: StudyRepository,
    private val groupId: Int?,
    private val folderId: Int?
) : ViewModel() {

    private val _uiState = MutableStateFlow<TestUiState>(TestUiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        loadNextVerse()
    }

    fun loadNextVerse() {
        _uiState.update { TestUiState.Loading }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = study.randomVerseForTest(
                    groupId = if (groupId != null && groupId > 0) groupId else null,
                    folderId = if (folderId != null && folderId > 0) folderId else null
                )
                if (res != null) {
                    _uiState.update { TestUiState.Question(res) }
                } else {
                    _uiState.update { TestUiState.Empty }
                }
            } catch (e: Exception) {
                _uiState.update { TestUiState.Error(UiText(R.string.error_unknown)) }
            }
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
            StudyRepository.create(context),
            groupId,
            folderId
        ) as T
}
