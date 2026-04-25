package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_folders",
    indices = [
        Index(value = ["name", "parent_id"], unique = true),
        Index("parent_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = VaultFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VaultFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
