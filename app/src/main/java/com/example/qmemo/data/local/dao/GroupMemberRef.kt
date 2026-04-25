package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

data class GroupMemberRef(
    @ColumnInfo(name = "group_id") val groupId: Int,
    val description: String,
    @ColumnInfo(name = "master_strength") val masterStrength: Int,
    @ColumnInfo(name = "memorization_notes") val memorizationNotes: String,
    @ColumnInfo(name = "folder_id") val folderId: Int?,
    @ColumnInfo(name = "verse_id") val verseId: Int,
    @ColumnInfo(name = "surah_id") val surahId: Int,
    @ColumnInfo(name = "ayah_number") val ayahNumber: Int,
    @ColumnInfo(name = "page_number") val pageNumber: Int
)
