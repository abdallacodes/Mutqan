package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One finished round of a practice exercise (see [com.example.mutqan.domain.ExerciseType]).
 * These are kept apart from the revision logs on purpose: a quick check of a page's edges is
 * weaker evidence than reciting the page, so it never moves a page's recall state.
 * type: [com.example.mutqan.domain.ExerciseType.code]; outcome: [com.example.mutqan.domain.RecallOutcome.code]
 */
@Entity(tableName = "exercise_results")
data class ExerciseResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: Int,
    @ColumnInfo(name = "start_page") val startPage: Int,
    @ColumnInfo(name = "end_page") val endPage: Int,
    val outcome: Int,
    val timestamp: Long
)
