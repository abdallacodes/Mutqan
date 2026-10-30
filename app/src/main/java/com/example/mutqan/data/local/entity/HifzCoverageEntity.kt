package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One range of memorised ayahs, as global ayah ids (`QuranAyah.id`). The table holds the
 * ranges sorted and merged (see [com.example.mutqan.domain.HifzCoverage]); no rows means the
 * user has not said what they have memorised.
 */
@Entity(tableName = "hifz_coverage")
data class HifzCoverageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int,
    @ColumnInfo(name = "end_ayah_id") val endAyahId: Int
)
