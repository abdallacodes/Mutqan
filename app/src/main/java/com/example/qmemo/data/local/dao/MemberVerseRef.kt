package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

/**
 * Lightweight verse reference shown inside a group's member list.
 * Joins similarity_members → verses to surface human-readable Surah:Ayah labels.
 */
data class MemberVerseRef(
    @ColumnInfo(name = "verse_id")    val verseId: Int,
    @ColumnInfo(name = "surah_id")    val surahId: Int,
    @ColumnInfo(name = "ayah_number") val ayahNumber: Int,
    @ColumnInfo(name = "page_number") val pageNumber: Int
)
