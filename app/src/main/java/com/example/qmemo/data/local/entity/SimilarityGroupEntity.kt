package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined "Confusion Point" — a named cluster of similar/confusable verses.
 * masterStrength: overall mastery rating for the group. 1 = Weak, 2 = Fair, 3 = Strong
 */
@Entity(tableName = "similarity_groups")
data class SimilarityGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    @ColumnInfo(name = "master_strength") val masterStrength: Int
)
