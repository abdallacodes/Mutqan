package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table linking a SimilarityGroup to its member ayahs.
 *
 * [verseId] is the global ayah id (1..6236) of the `:quran` module's `QuranAyah.id`;
 * the Quran text itself is not stored here.
 *
 * Deleting a SimilarityGroup cascades and removes all its members automatically.
 * Composite primary key (group_id, verse_id) prevents duplicate membership entries.
 * Explicit index on verse_id speeds up reverse lookups (find all groups for an ayah).
 */
@Entity(
    tableName = "similarity_members",
    primaryKeys = ["group_id", "verse_id"],
    foreignKeys = [
        ForeignKey(
            entity = SimilarityGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("group_id"),
        Index("verse_id")
    ]
)
data class SimilarityMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "verse_id") val verseId: Int
)
