package com.example.qmemo.ui.surah

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.MemberVerseRef
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupDetailViewModel(
    val groupId: Int,
    val currentSurahId: Int,
    private val dao: QuranDao
) : ViewModel() {

    private val _group = MutableStateFlow<SimilarityGroupEntity?>(null)
    val group: StateFlow<SimilarityGroupEntity?> = _group.asStateFlow()

    /**
     * Verses that belong to [currentSurahId] — the "home" context for this group.
     */
    val contextVerses: StateFlow<List<MemberVerseRef>> = dao
        .getMembersForGroup(groupId)
        .map { list -> list.filter { it.surahId == currentSurahId } }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Verses from every Surah other than [currentSurahId] — the "clashes".
     */
    val clashVerses: StateFlow<List<MemberVerseRef>> = dao
        .getMembersForGroup(groupId)
        .map { list -> list.filter { it.surahId != currentSurahId } }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            _group.value = dao.getGroupById(groupId)
        }
    }
}

class GroupDetailViewModelFactory(
    private val context: Context,
    private val groupId: Int,
    private val currentSurahId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GroupDetailViewModel(
            groupId        = groupId,
            currentSurahId = currentSurahId,
            dao            = AppDatabase.getInstance(context).quranDao()
        ) as T
}
