package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Static reference table — pre-populated once from bundled asset data.
 * Covers the full Quran: 114 Surahs, 6,236 Ayahs, 604 Pages, 30 Juz.
 * id is a stable integer key assigned during pre-population (e.g. sequential 1–6236).
 *
 * [textArabic] stores the full Uthmani-script Arabic text for Quick Peek display.
 */
@Entity(tableName = "verses")
data class VerseEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "surah_id") val surahId: Int,
    @ColumnInfo(name = "ayah_number") val ayahNumber: Int,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "juz_id") val juzId: Int,
    @ColumnInfo(name = "text_arabic") val textArabic: String = ""
)
