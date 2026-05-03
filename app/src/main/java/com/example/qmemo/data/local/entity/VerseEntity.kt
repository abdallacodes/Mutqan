package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Static reference table — pre-populated once from bundled asset data.
 * Covers the full Quran: 114 Surahs, 6,236 Ayahs, 604 Pages, 30 Juz.
 */
@Entity(tableName = "verses")
data class VerseEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "surah_id") val surahId: Int,
    @ColumnInfo(name = "ayah_number") val ayahNumber: Int,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "juz_id") val juzId: Int,
    @ColumnInfo(name = "text_arabic") val textArabic: String = "",
    @ColumnInfo(name = "normalized_content") val normalizedContent: String = ""
)

/**
 * FTS4 Virtual Table for high-performance searching.
 * Shadows the [verses] table.
 */
@Fts4(contentEntity = VerseEntity::class)
@Entity(tableName = "verses_fts")
data class VerseFtsEntity(
    @ColumnInfo(name = "normalized_content") val normalizedContent: String
)
