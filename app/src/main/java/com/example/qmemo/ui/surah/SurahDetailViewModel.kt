package com.example.qmemo.ui.surah

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.SurahData
import com.example.qmemo.data.SurahInfo
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.MemberVerseRef
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * A SimilarityGroup enriched with its member verses split by Surah context.
 *
 * Built from a single [QuranDao.getMemberRefsBySurahGroups] query so the UI
 * never needs N+1 database calls.
 */
data class GroupWithVerses(
    val group: SimilarityGroupEntity,
    /** Verses that belong to the currently viewed Surah. */
    val internalVerses: List<MemberVerseRef>,
    /** Verses from every other Surah in this group. */
    val externalVerses: List<MemberVerseRef>
) {
    val hasExternal: Boolean get() = externalVerses.isNotEmpty()
}

class SurahDetailViewModel(
    val surahId: Int,
    private val dao: QuranDao
) : ViewModel() {

    val surahInfo: SurahInfo? = SurahData.getById(surahId)

    /** First Mushaf page for this Surah; null until [verses] is populated. */
    val mushafStartPage: StateFlow<Int?> = dao
        .observeStartPageForSurah(surahId)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /**
     * All groups that touch this Surah, each enriched with their member verses
     * split into internal (same Surah) and external (other Surahs).
     * One reactive query; grouping and sorting are done in Kotlin.
     */
    val groupsWithVerses: StateFlow<List<GroupWithVerses>> = dao
        .getMemberRefsBySurahGroups(surahId)
        .map { refs ->
            refs.groupBy { it.groupId }
                .entries
                .filter { (_, groupRefs) -> groupRefs.size >= 2 }  // guard: skip malformed groups
                .sortedBy { it.key }
                .map { (groupId, groupRefs) ->
                    val first = groupRefs.first()
                    GroupWithVerses(
                        group = SimilarityGroupEntity(
                            id                 = groupId,
                            description        = first.description,
                            masterStrength     = first.masterStrength,
                            memorizationNotes  = first.memorizationNotes
                        ),
                        internalVerses = groupRefs
                            .filter { it.surahId == surahId }
                            .sortedBy { it.ayahNumber }
                            .map { MemberVerseRef(it.verseId, it.surahId, it.ayahNumber, it.pageNumber) },
                        externalVerses = groupRefs
                            .filter { it.surahId != surahId }
                            .sortedWith(compareBy({ it.surahId }, { it.ayahNumber }))
                            .map { MemberVerseRef(it.verseId, it.surahId, it.ayahNumber, it.pageNumber) }
                    )
                }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}

class SurahDetailViewModelFactory(
    private val context: Context,
    private val surahId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SurahDetailViewModel(surahId, AppDatabase.getInstance(context).quranDao()) as T
}
