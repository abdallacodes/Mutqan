package com.example.mutqan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How well the user says they know a page, before any recall history shows it. [level] is
 * [com.example.mutqan.domain.PlanTier.code]. It only places the page in a tier of the plan until
 * the page has recall evidence of its own; it is never counted as evidence.
 */
@Entity(tableName = "page_strength")
data class PageStrengthEntity(
    @PrimaryKey val page: Int,
    val level: Int
)
