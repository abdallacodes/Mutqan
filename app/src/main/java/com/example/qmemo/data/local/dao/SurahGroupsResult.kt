package com.example.qmemo.data.local.dao

import com.example.qmemo.data.local.entity.SimilarityGroupEntity

/**
 * Container returned by [QuranDao.getGroupsBySurah].
 *
 * internalGroups — every member of the group belongs to the queried Surah.
 * externalGroups — at least one member belongs to the queried Surah,
 *                  but the group also contains members from other Surahs.
 */
data class SurahGroupsResult(
    val internalGroups: List<SimilarityGroupEntity>,
    val externalGroups: List<SimilarityGroupEntity>
)
