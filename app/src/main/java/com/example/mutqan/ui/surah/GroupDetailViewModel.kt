package com.example.mutqan.ui.surah

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.vault.GroupMember
import com.example.mutqan.ui.vault.GroupVerses
import com.example.mutqan.ui.vault.LookAlikes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One of the user's mutashabihat groups: its verses (each with the words it shares with the others), and the verses
 * that look like them and are not in it yet, to choose from and add.
 */
class GroupDetailViewModel(
    val groupId: Int,
    val currentSurahId: Int,
    private val appContext: Context,
    private val dao: StudyDao,
    private val study: StudyRepository
) : ViewModel() {

    private val quran = QuranRepository.getInstance(appContext)

    val group: StateFlow<SimilarityGroupEntity?> = dao.observeGroups()
        .map { groups -> groups.firstOrNull { it.id == groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The group's verses in reading order, each with its own words; null while they are read. */
    val members: StateFlow<List<GroupMember>?> = study.membersForGroup(groupId)
        .map { refs -> GroupVerses.withOwn(quran, refs) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The verses like the group's that are not in it, the most alike first; null while they are found. */
    val suggestions: StateFlow<LookAlikes?> = study.membersForGroup(groupId)
        .map { refs -> GroupVerses.lookAlikes(appContext, quran, refs.map { it.verseId }) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Puts [ayahIds] in the group; returns how many were not in it already. */
    suspend fun add(ayahIds: Collection<Int>): Int = ayahIds.count { study.addAyahToGroup(groupId, it) }
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
            appContext     = context.applicationContext,
            dao            = AppDatabase.getInstance(context).studyDao(),
            study          = StudyRepository.create(context)
        ) as T
}
