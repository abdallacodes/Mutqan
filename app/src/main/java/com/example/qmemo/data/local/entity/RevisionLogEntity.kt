package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records a single revision session.
 * difficulty: 1 = Smooth, 2 = Struggled, 3 = Critical
 * timestamp: epoch millis when the record was saved (System.currentTimeMillis())
 * dateMillis: user-selected UTC-midnight millis representing the date of the session
 */
@Entity(tableName = "revision_logs")
data class RevisionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "start_page") val startPage: Int,
    @ColumnInfo(name = "end_page") val endPage: Int,
    val timestamp: Long,
    val difficulty: Int,
    @ColumnInfo(name = "date_millis", defaultValue = "0") val dateMillis: Long = 0L
)
