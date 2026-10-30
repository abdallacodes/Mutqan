package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One revision session, stored as the range of ayahs revised so it does not depend on how the
 * Mushaf is paginated. The ids are global ayah ids (`QuranAyah.id`); pages are derived from
 * them by [com.example.mutqan.domain.StudyRepository].
 * timestamp: epoch millis when the record was saved (System.currentTimeMillis())
 * dateMillis: user-selected UTC-midnight millis representing the date of the session
 * manualStability: the 0.1-1.0 quality the user gave the session
 */
@Entity(tableName = "revision_logs")
data class RevisionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int,
    @ColumnInfo(name = "end_ayah_id") val endAyahId: Int,
    val timestamp: Long,
    @ColumnInfo(name = "date_millis", defaultValue = "0") val dateMillis: Long = 0L,
    @ColumnInfo(name = "manual_stability") val manualStability: Float
)
