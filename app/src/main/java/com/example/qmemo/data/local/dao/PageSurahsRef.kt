package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

/**
 * Projection returned by [QuranDao.getPageSurahsForJuz].
 * Each row represents one page in the Juz, carrying the comma-separated Surah IDs
 * that have at least one verse on that page.
 */
data class PageSurahsRef(
    @ColumnInfo(name = "page_number")  val pageNumber:  Int,
    @ColumnInfo(name = "surah_ids_raw") val surahIdsRaw: String = ""
) {
    /**
     * Sorted list of Surah IDs on this page (1 entry = single-surah page,
     * 2 entries = transition page crossing a Surah boundary).
     */
    val surahIds: List<Int>
        get() = if (surahIdsRaw.isBlank()) emptyList()
                else surahIdsRaw.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
}
