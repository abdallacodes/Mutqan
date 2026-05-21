package com.example.qmemo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * User-defined thematic anchor associated with a structural unit.
 */
@Entity(
    tableName = "user_subjects",
    foreignKeys = [
        ForeignKey(
            entity = StructureUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserSubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "unit_id") val unitId: Int,
    @ColumnInfo(name = "subject_text") val subjectText: String,
    @ColumnInfo(name = "start_ayah_id") val startAyahId: Int, // The global ID of the starting ayah
    @ColumnInfo(name = "order_index") val orderIndex: Int
)
