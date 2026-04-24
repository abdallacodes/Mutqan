package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

/**
 * One row per Surah returned by [QuranDao.getSurahMetaList].
 * A single LEFT JOIN query supplies verse count, starting juz,
 * and similarity-group count all at once — no per-row sub-queries needed.
 */
data class SurahMeta(
    @ColumnInfo(name = "surah_id")    val surahId: Int,
    @ColumnInfo(name = "verse_count") val verseCount: Int,
    @ColumnInfo(name = "start_juz")   val startJuz: Int,
    @ColumnInfo(name = "group_count") val groupCount: Int
)
