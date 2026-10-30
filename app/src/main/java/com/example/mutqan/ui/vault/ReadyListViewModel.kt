package com.example.mutqan.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.data.local.entity.SimilarityMemberEntity
import com.example.mutqan.domain.MutashabihatLibrary
import com.example.mutqan.domain.ReadyPhrase
import com.example.mutqan.quran.ArabicNormalization
import com.example.mutqan.ui.components.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadyListViewModel(
    private val appContext: Context,
    private val dao: StudyDao
) : ViewModel() {

    private val all = MutableStateFlow<List<ReadyPhrase>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _expandedId = MutableStateFlow<Int?>(null)
    val expandedId: StateFlow<Int?> = _expandedId.asStateFlow()

    private val _events = MutableSharedFlow<UiText>()
    val events: SharedFlow<UiText> = _events.asSharedFlow()

    /** Phrases matching the search; the whole list when the search is empty. */
    val phrases: StateFlow<List<ReadyPhrase>> = combine(all, _query) { list, q ->
        val needle = ArabicNormalization.normalizeForSearch(q)
        if (needle.isBlank()) list else list.filter { needle in it.searchText }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Phrases already in the vault, matched by group description. */
    val addedTexts: StateFlow<Set<String>> = dao.observeGroups()
        .map { groups -> groups.map { it.description }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch { all.value = MutashabihatLibrary.phrases(appContext) }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggle(phrase: ReadyPhrase) {
        _expandedId.value = if (_expandedId.value == phrase.id) null else phrase.id
    }

    /** Creates a vault group holding every ayah of [phrase]. */
    fun addToVault(phrase: ReadyPhrase) {
        if (phrase.text in addedTexts.value) return
        viewModelScope.launch {
            val groupId = dao.insertSimilarityGroup(SimilarityGroupEntity(description = phrase.text)).toInt()
            dao.insertSimilarityMembers(phrase.verses.map { SimilarityMemberEntity(groupId, it.ayahId) })
            _events.emit(UiText(R.string.ready_list_added_toast))
        }
    }
}

class ReadyListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext
        return ReadyListViewModel(app, AppDatabase.getInstance(app).studyDao()) as T
    }
}
