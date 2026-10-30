package com.example.mutqan.data.local.dao

/**
 * One row per surah for the surah list: verse count, starting juz and page (from the Quran
 * data) and how many similarity groups touch the surah (from user data).
 */
data class SurahMeta(
    val surahId: Int,
    val verseCount: Int,
    val startJuz: Int,
    val startPage: Int,
    val groupCount: Int
)
