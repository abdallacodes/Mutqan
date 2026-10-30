package com.example.mutqan.data.local.dao

/**
 * Lightweight ayah reference shown inside a group's member list: a member's ayah id with the
 * human-readable surah:ayah and the page it starts on.
 */
data class MemberVerseRef(
    val verseId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val pageNumber: Int
)
