package com.example.mutqan.domain

import android.content.Context
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.VerseListEntity
import com.example.mutqan.data.local.entity.VerseListItemEntity
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A list as the Quran tab shows it: its name and note, how many verses and passages, and where it starts. */
data class VerseListSummary(
    val id: Int,
    val name: String,
    val note: String,
    val verses: Int,
    val passages: Int,
    val firstAyahId: Int?,
    val updatedAt: Long
)

/** A list with its passages in reading order. */
data class VerseListDetail(val id: Int, val name: String, val note: String, val passages: List<ListPassage>) {
    val verses: Int get() = VerseLists.verseCount(passages)
}

/** The user's own lists of verses (see [VerseLists] for how passages are kept). */
class VerseListsRepository(private val dao: StudyDao, private val surahOf: (Int) -> Int) {

    companion object {
        fun create(context: Context): VerseListsRepository {
            val index = QuranRepository.getInstance(context).ayahIndex
            return VerseListsRepository(AppDatabase.getInstance(context).studyDao()) { index.surahOf(it) }
        }

        private fun passage(item: VerseListItemEntity) = ListPassage(item.startAyahId, item.endAyahId, item.note, item.createdAt)
    }

    /** Every list, the most recently changed first. */
    fun lists(): Flow<List<VerseListSummary>> =
        combine(dao.observeVerseLists(), dao.observeVerseListItems()) { lists, items ->
            val byList = items.groupBy { it.listId }
            lists.map { list ->
                val own = byList[list.id].orEmpty()
                VerseListSummary(
                    id = list.id, name = list.name, note = list.note,
                    verses = own.sumOf { it.endAyahId - it.startAyahId + 1 }, passages = own.size,
                    firstAyahId = own.minOfOrNull { it.startAyahId }, updatedAt = list.updatedAt
                )
            }
        }.flowOn(Dispatchers.Default)

    /** One list with its passages; null once it is deleted. */
    fun list(id: Int): Flow<VerseListDetail?> =
        combine(dao.observeVerseList(id), dao.observeItemsOfList(id)) { list, items ->
            list?.let { VerseListDetail(it.id, it.name, it.note, items.map(::passage)) }
        }

    /** The passages of every list, by list id (a list without passages is missing). */
    fun passagesByList(): Flow<Map<Int, List<ListPassage>>> =
        dao.observeVerseListItems().map { items -> items.groupBy({ it.listId }, ::passage) }.flowOn(Dispatchers.Default)

    /** The list called [name] (or a name that differs only in diacritics or case), other than [except]. */
    suspend fun findByName(name: String, except: Int = 0): Int? = withContext(Dispatchers.IO) {
        val key = VerseLists.nameKey(name)
        dao.getAllVerseListsSync().firstOrNull { it.id != except && VerseLists.nameKey(it.name) == key }?.id
    }

    /** A new list; its id. */
    suspend fun create(name: String, note: String = ""): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insertVerseList(VerseListEntity(name = VerseLists.cleanName(name), note = note.trim(), createdAt = now, updatedAt = now)).toInt()
    }

    suspend fun edit(id: Int, name: String, note: String) = withContext(Dispatchers.IO) {
        val list = dao.getVerseList(id) ?: return@withContext
        dao.updateVerseList(list.copy(name = VerseLists.cleanName(name), note = note.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) { dao.deleteVerseList(id) }

    /** Adds verses [start]..[end] to list [listId]. */
    suspend fun add(listId: Int, start: Int, end: Int): ListAdd = change(listId) { passages, now ->
        VerseLists.add(passages, start, end, now, surahOf)
    }

    /** Adds each of [ayahIds] to list [listId]; how many were not in it before. */
    suspend fun addAll(listId: Int, ayahIds: Collection<Int>): Int = change(listId) { passages, now ->
        VerseLists.addAll(passages, ayahIds, now, surahOf)
    }

    /** Takes verses [start]..[end] out of list [listId]. */
    suspend fun remove(listId: Int, start: Int, end: Int) {
        change(listId) { passages, _ -> VerseLists.remove(passages, start, end) to Unit }
    }

    /** Passage [old] of list [listId] becomes verses [start]..[end] with [note] (joining any passage it now meets). */
    suspend fun editPassage(listId: Int, old: ListPassage, start: Int, end: Int, note: String) {
        change(listId) { passages, _ ->
            val edited = ListPassage(minOf(start, end), maxOf(start, end), note.trim(), old.createdAt)
            VerseLists.tidy(passages.filterNot { it == old } + edited, surahOf) to Unit
        }
    }

    private suspend fun <T> change(listId: Int, how: (List<ListPassage>, Long) -> Pair<List<ListPassage>, T>): T =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val before = dao.getItemsOfList(listId).map(::passage)
            val (after, result) = how(before, now)
            if (after != before) {
                dao.replaceItemsOfList(
                    listId,
                    after.map { VerseListItemEntity(listId = listId, startAyahId = it.start, endAyahId = it.end, note = it.note, createdAt = it.createdAt) },
                    now
                )
            }
            result
        }
}
