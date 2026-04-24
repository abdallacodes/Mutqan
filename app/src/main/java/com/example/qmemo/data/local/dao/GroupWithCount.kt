package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.qmemo.data.local.entity.SimilarityGroupEntity

/**
 * Flat projection returned by [QuranDao.getAllGroupsWithMemberCount].
 * Avoids a second query-per-row to get member counts in the list screen.
 *
 * [surahIdsRaw] is a comma-separated string of distinct Surah IDs that appear
 * in this group (e.g. "2,3,18").  Parse with [surahIds].  Empty string when
 * the group has no members yet.
 */
data class GroupWithCount(
    @Embedded val group: SimilarityGroupEntity,
    @ColumnInfo(name = "member_count")  val memberCount:  Int,
    @ColumnInfo(name = "surah_ids_raw") val surahIdsRaw:  String = ""
) {
    /** Sorted list of distinct Surah IDs for this group. */
    val surahIds: List<Int>
        get() = if (surahIdsRaw.isBlank()) emptyList()
                else surahIdsRaw.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
}
