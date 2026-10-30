package com.example.mutqan.data.local.dao

import com.example.mutqan.data.local.entity.SimilarityGroupEntity

/**
 * A similarity group with its member count and the surahs its members belong to.
 * Built by [com.example.mutqan.domain.StudyRepository] so the list screen needs no
 * per-row queries.
 */
data class GroupWithCount(
    val group: SimilarityGroupEntity,
    val memberCount: Int,
    /** Sorted distinct surah ids of the group's members; empty when it has no members yet. */
    val surahIds: List<Int>
)
