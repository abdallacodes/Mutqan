package com.example.mutqan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A revision plan's settings (see [com.example.mutqan.domain.RevisionPlan]).
 * unit: [com.example.mutqan.domain.PlanUnit.code]; order: [com.example.mutqan.domain.PlanOrder.code]
 * restDays: bit set, bit 0 = Monday .. bit 6 = Sunday
 * paused: 0 or 1
 */
@Entity(tableName = "revision_plans")
data class RevisionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val unit: Int,
    @ColumnInfo(name = "amount_per_day") val amountPerDay: Int,
    @ColumnInfo(name = "rest_days", defaultValue = "0") val restDays: Int = 0,
    @ColumnInfo(name = "plan_order", defaultValue = "0") val order: Int = 0,
    @ColumnInfo(defaultValue = "0") val paused: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
