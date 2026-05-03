package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined "Confusion Point" — a named cluster of similar/confusable verses.
 * masterQuality: overall mastery rating for the group. Range: 0.1 to 1.0 (10% to 100%)
 */
@Entity(
    tableName = "similarity_groups",
    foreignKeys = [
        ForeignKey(
            entity = VaultFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folder_id")]
)
data class SimilarityGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    @ColumnInfo(name = "master_quality", defaultValue = "0.5") val masterQuality: Float = 0.5f,
    /** User notes to aid memorization (mnemonics, patterns, audio cues, etc.). */
    @ColumnInfo(name = "memorization_notes", defaultValue = "") val memorizationNotes: String = "",
    @ColumnInfo(name = "folder_id") val folderId: Int? = null
)
