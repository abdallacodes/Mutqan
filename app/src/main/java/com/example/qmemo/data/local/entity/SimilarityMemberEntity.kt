package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table linking a SimilarityGroup to its member verses.
 *
 * Foreign key constraints ensure referential integrity:
 * - Deleting a SimilarityGroup cascades and removes all its members automatically.
 * - Deleting a Verse cascades and removes all group memberships for that verse.
 *
 * Composite primary key (group_id, verse_id) prevents duplicate membership entries.
 * Explicit index on verse_id speeds up reverse lookups (find all groups for a verse).
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
        ),
        ForeignKey(
            entity = VerseEntity::class,
            parentColumns = ["id"],
            childColumns = ["verse_id"],
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
