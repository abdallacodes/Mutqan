package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One of the user's own lists of verses, for whatever they keep it for (a theme, what they recite in prayer, the
 * duas of the Quran). Its verses are [VerseListItemEntity] rows. [updatedAt] moves with every change to the list or
 * its verses, so the most recently used lists come first.
 */
@Entity(tableName = "verse_lists")
data class VerseListEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * A passage of a list: verses [startAyahId]..[endAyahId] (global ayah ids), with the user's [note] on it. The passages
 * of a list never overlap (see [com.example.mutqan.domain.VerseLists]). Deleting the list deletes its passages.
 */
@Entity(
    tableName = "verse_list_items",
    foreignKeys = [
        ForeignKey(
            entity = VerseListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("list_id"), Index("start_ayah_id")]
)
data class VerseListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "list_id") val listId: Int,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int,
    @ColumnInfo(name = "end_ayah_id") val endAyahId: Int,
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long
)
