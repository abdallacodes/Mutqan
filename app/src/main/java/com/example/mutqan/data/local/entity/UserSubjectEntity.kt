package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-defined thematic anchor in the Quran.
 *
 * It belongs to the hizb quarter (`QuranRub`) that contains [startAyahId], so the unit is
 * derived from the ayah and never stored.
 */
@Entity(
    tableName = "user_subjects",
    indices = [Index("start_ayah_id")]
)
data class UserSubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "subject_text") val subjectText: String,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int, // The global ID of the starting ayah
    @ColumnInfo(name = "order_index") val orderIndex: Int
)
