package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

/** First and last Surah IDs that appear in a given Juz (used for subtitle display). */
data class JuzSurahRange(
    @ColumnInfo(name = "first_surah") val firstSurah: Int,
    @ColumnInfo(name = "last_surah")  val lastSurah:  Int
)
