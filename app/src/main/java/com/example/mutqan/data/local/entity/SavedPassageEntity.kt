package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bookmark or weak passage the user saved: an ayah range (global ayah ids), optionally a
 * single word of its first ayah.
 * kind: [com.example.mutqan.domain.SavedKind.code]
 * tags: bit set of [com.example.mutqan.domain.WeakTag.bit]
 * priority: 1 low, 2 normal, 3 high
 * resolvedAt: epoch millis when a weak passage was marked solid again; 0 = still open
 */
@Entity(tableName = "saved_passages", indices = [Index("start_ayah_id")])
data class SavedPassageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val kind: Int,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int,
    @ColumnInfo(name = "end_ayah_id") val endAyahId: Int,
    @ColumnInfo(name = "word_id", defaultValue = "0") val wordId: Int = 0,
    @ColumnInfo(defaultValue = "0") val tags: Int = 0,
    @ColumnInfo(defaultValue = "2") val priority: Int = 2,
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "resolved_at", defaultValue = "0") val resolvedAt: Long = 0L
)
