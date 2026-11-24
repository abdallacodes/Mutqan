package com.example.mutqan.ui.surah

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.MemberVerseRef
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.game.NextWords
import com.example.mutqan.quran.QuranRepository
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

    /** A verse of the group: where it is, and its own words (1-based ranges): those none of the other verses of the group has. */
    data class Member(val ref: MemberVerseRef, val own: List<IntRange>)

    /** A verse like the group's that is not in it: where it is, and its words shared with the verse of the group it is most like. */
    data class Suggestion(val ayahId: Int, val surahId: Int, val ayahNumber: Int, val page: Int, val shared: List<IntRange>)

    /** The look-alikes found: the closest [shown], and how many more there are. */
    data class Suggestions(val shown: List<Suggestion>, val more: Int)

    private val quran = QuranRepository.getInstance(appContext)

    val group: StateFlow<SimilarityGroupEntity?> = dao.observeGroups()
        .map { groups -> groups.firstOrNull { it.id == groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The group's verses in reading order, each with its own words; null while they are read. */
    val members: StateFlow<List<Member>?> = study.membersForGroup(groupId)
        .map { refs -> withOwn(refs) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The verses like the group's that are not in it, the most alike first; null while they are found. */
    val suggestions: StateFlow<Suggestions?> = study.membersForGroup(groupId)
        .map { refs -> suggest(refs.map { it.verseId }) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Puts [ayahIds] in the group; returns how many were not in it already. */
    suspend fun add(ayahIds: Collection<Int>): Int = ayahIds.count { study.addAyahToGroup(groupId, it) }

    private fun withOwn(refs: List<MemberVerseRef>): List<Member> {
        if (refs.size < 2) return refs.map { Member(it, emptyList()) }
        val next = NextWords.get(quran)
        val ids = refs.map { it.verseId }
        return refs.map { ref -> Member(ref, next.ownWords(ref.verseId, ids)) }
    }

    private suspend fun suggest(ids: List<Int>): Suggestions {
        if (ids.isEmpty()) return Suggestions(emptyList(), 0)
        val listed = SimilarLibrary.load(appContext)
        val next = NextWords.get(quran)
        val index = quran.ayahIndex
        val inGroup = ids.toSet()
        // Each look-alike with the verse of the group it is most like, and how alike they are.
        val closest = HashMap<Int, Pair<Int, Float>>()
        for (member in ids) {
            val found = listed.similarTo(member).map { it.ayahId } + next.lookAlikes(member)
            for (other in found) {
                if (other in inGroup || other !in index) continue
                val likeness = next.likeness(member, other)
                val had = closest[other]
                if (had == null || had.second < likeness) closest[other] = member to likeness
            }
        }
        val ranked = closest.entries.sortedWith(compareByDescending<Map.Entry<Int, Pair<Int, Float>>> { it.value.second }.thenBy { it.key })
        val shown = ranked.take(MOST_SHOWN).map { (other, best) ->
            Suggestion(other, index.surahOf(other), index.ayahOf(other), index.firstPageOf(other), next.sharedWords(best.first, other))
        }
        return Suggestions(shown, (ranked.size - shown.size).coerceAtLeast(0))
    }

    private companion object {
        /** Look-alikes shown at most. */
        const val MOST_SHOWN = 40
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
            appContext     = context.applicationContext,
            dao            = AppDatabase.getInstance(context).studyDao(),
            study          = StudyRepository.create(context)
        ) as T
}
