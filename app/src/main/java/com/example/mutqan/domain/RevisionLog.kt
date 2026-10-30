package com.example.mutqan.domain

import com.example.mutqan.data.local.entity.RevisionLogEntity

/**
 * A revision session as the app shows and replays it: the stored ayah range plus the Mushaf
 * pages it covers in the current layout. Built by [StudyRepository]; the memory engine only
 * reads the pages.
 */
data class RevisionLog(
    val id: Int = 0,
    val startPage: Int,
    val endPage: Int,
    val timestamp: Long,
    val manualStability: Float,
    val dateMillis: Long = 0L,
    val startAyahId: Int = 0,
    val endAyahId: Int = 0,
    val outcome: RecallOutcome = RecallOutcome.INDEPENDENT,
    val mistakeTags: Set<MistakeTag> = emptySet()
) {
    fun toEntity() = RevisionLogEntity(
        id = id,
        startAyahId = startAyahId,
        endAyahId = endAyahId,
        timestamp = timestamp,
        dateMillis = dateMillis,
        manualStability = manualStability,
        outcome = outcome.code,
        mistakeTags = MistakeTag.toMask(mistakeTags)
    )
}
