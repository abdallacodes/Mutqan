package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined "Confusion Point" — a named cluster of similar/confusable verses.
 * masterStrength: overall mastery rating for the group. 1 = Weak, 2 = Fair, 3 = Strong
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
    @ColumnInfo(name = "master_strength") val masterStrength: Int,
    /** User notes to aid memorization (mnemonics, patterns, audio cues, etc.). */
    @ColumnInfo(name = "memorization_notes", defaultValue = "") val memorizationNotes: String = "",
    @ColumnInfo(name = "folder_id") val folderId: Int? = null
)
