package com.example.mutqan.data.local.dao

data class GroupMemberRef(
    val groupId: Int,
    val description: String,
    val masterQuality: Float,
    val memorizationNotes: String,
    val folderId: Int?,
    val verseId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val pageNumber: Int
)
