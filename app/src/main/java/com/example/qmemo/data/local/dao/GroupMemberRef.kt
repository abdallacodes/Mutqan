package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

/**
 * Flat row returned by [QuranDao.getMemberRefsBySurahGroups].
 *
 * Joins similarity_groups + similarity_members + verses in a single reactive
 * query so the caller can reconstruct full [GroupWithVerses] models in Kotlin
 * without N+1 round-trips to the database.
 */
data class GroupMemberRef(
    @ColumnInfo(name = "group_id")        val groupId: Int,
    @ColumnInfo(name = "description")     val description: String,
    @ColumnInfo(name = "master_strength") val masterStrength: Int,
    @ColumnInfo(name = "verse_id")        val verseId: Int,
    @ColumnInfo(name = "surah_id")        val surahId: Int,
    @ColumnInfo(name = "ayah_number")     val ayahNumber: Int,
    @ColumnInfo(name = "page_number")     val pageNumber: Int
)
