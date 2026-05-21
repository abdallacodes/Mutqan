package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a structural division of the Quran (Hizb Quarter).
 * There are 240 quarters in total (30 Juz * 8 Quarters/Juz).
 */
@Entity(tableName = "structure_units")
data class StructureUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "juz_id") val juzId: Int,
    @ColumnInfo(name = "hizb_number") val hizbNumber: Int, // 1-60
    @ColumnInfo(name = "quarter_number") val quarterNumber: Int, // 1-8 within Juz
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int,
    @ColumnInfo(name = "end_ayah_id") val endAyahId: Int,
    @ColumnInfo(name = "start_page") val startPage: Int,
    @ColumnInfo(name = "end_page") val endPage: Int
)
