package com.example.mutqan.ui.surah

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.SurahInfo
import com.example.mutqan.data.local.dao.MemberVerseRef
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A SimilarityGroup enriched with its member verses split by Surah context.
 *
 * Built from a single [StudyRepository.memberRefsBySurahGroups] flow so the UI
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
    private val study: StudyRepository,
    private val quran: QuranRepository
) : ViewModel() {

    val surahInfo: SurahInfo? = SurahData.getById(surahId)

    private val _mushafStartPage = MutableStateFlow<Int?>(null)

    /** First Mushaf page for this Surah; null until it has been read from the Quran data. */
    val mushafStartPage: StateFlow<Int?> = _mushafStartPage.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _mushafStartPage.value = quran.surah(surahId)?.firstPage
        }
    }

    /**
     * All groups that touch this Surah, each enriched with their member verses
     * split into internal (same Surah) and external (other Surahs).
     * One reactive query; grouping and sorting are done in Kotlin.
     */
    val groupsWithVerses: StateFlow<List<GroupWithVerses>> = study
        .memberRefsBySurahGroups(surahId)
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
                            masterQuality      = first.masterQuality,
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
        SurahDetailViewModel(surahId, StudyRepository.create(context), QuranRepository.getInstance(context)) as T
}
