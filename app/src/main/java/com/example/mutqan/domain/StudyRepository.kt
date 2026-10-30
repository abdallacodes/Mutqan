package com.example.mutqan.domain

import android.content.Context
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.GroupMemberRef
import com.example.mutqan.data.local.dao.GroupWithCount
import com.example.mutqan.data.local.dao.MemberVerseRef
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.dao.SurahMeta
import com.example.mutqan.data.local.entity.HifzCoverageEntity
import com.example.mutqan.data.local.entity.RevisionLogEntity
import com.example.mutqan.data.local.entity.SavedPassageEntity
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Two pages that share a similarity group, used for semantic interference in the memory engine. */
data class PageSimilarityLink(val pageA: Int, val pageB: Int)

data class TestVerseResult(
    val verse: QuranAyah,
    val groupId: Int,
    val groupName: String
)

/** A group an ayah belongs to, with the group's other ayahs in reading order. */
data class AyahGroup(val group: SimilarityGroupEntity, val others: List<QuranAyah>)

object SimilarityLinks {

    /**
     * Page links implied by similarity groups. [pagesByMember] maps a group id to the pages each
     * of its member ayahs occupies (an ayah that runs over a page break occupies two).
     * Two members of a group link every pair of distinct pages between them, in both
     * directions; the pages of a single ayah are never linked to each other.
     */
    fun pageLinks(pagesByMember: Map<Int, List<IntRange>>): List<PageSimilarityLink> {
        val links = LinkedHashSet<PageSimilarityLink>()
        for (members in pagesByMember.values) {
            for (i in members.indices) {
                for (j in members.indices) {
                    if (i == j) continue
                    for (a in members[i]) for (b in members[j]) if (a != b) links.add(PageSimilarityLink(a, b))
                }
            }
        }
        return links.toList()
    }
}

/**
 * Questions that need both the user's data (groups, members) and Quran data (which surah,
 * ayah and page an ayah id is). Room holds only the ayah ids; everything about the ayah
 * comes from the `:quran` module through its in-memory [QuranRepository.ayahIndex].
 */
class StudyRepository(
    private val dao: StudyDao,
    private val quran: QuranRepository
) {

    companion object {
        fun create(context: Context) =
            StudyRepository(AppDatabase.getInstance(context).studyDao(), QuranRepository.getInstance(context))
    }

    private val index by lazy { quran.ayahIndex }
    private val surahs by lazy { quran.surahs() }

    private fun ref(verseId: Int): MemberVerseRef? =
        if (verseId in index) MemberVerseRef(verseId, index.surahOf(verseId), index.ayahOf(verseId), index.firstPageOf(verseId))
        else null

    private val byReadingOrder = compareBy<MemberVerseRef>({ it.surahId }, { it.ayahNumber })

    // ── Revision logs ─────────────────────────────────────────────────────────

    /** Pages [RevisionLogEntity] covers in the current layout; null if its ayah ids are unknown. */
    private fun RevisionLogEntity.toLog(): RevisionLog? {
        if (startAyahId !in index || endAyahId !in index) return null
        return RevisionLog(
            id = id,
            startPage = index.firstPageOf(startAyahId),
            endPage = index.firstPageOf(endAyahId).coerceAtLeast(index.firstPageOf(startAyahId)),
            timestamp = timestamp,
            manualStability = manualStability,
            dateMillis = dateMillis,
            startAyahId = startAyahId,
            endAyahId = endAyahId,
            outcome = RecallOutcome.fromCode(outcome),
            mistakeTags = MistakeTag.fromMask(mistakeTags)
        )
    }

    /** Newest first. */
    fun revisionLogs(): Flow<List<RevisionLog>> =
        dao.getAllRevisionLogs().map { logs -> logs.mapNotNull { it.toLog() } }.flowOn(Dispatchers.IO)

    /** Records that pages [startPage]..[endPage] were revised. Returns false if no ayah begins there. */
    suspend fun addRevision(
        startPage: Int,
        endPage: Int,
        outcome: RecallOutcome,
        mistakeTags: Set<MistakeTag> = emptySet(),
        dateMillis: Long = 0L
    ): Boolean =
        withContext(Dispatchers.IO) {
            val range = quran.ayahIdRangeOfPages(startPage, endPage) ?: return@withContext false
            dao.insertRevisionLog(
                RevisionLogEntity(
                    startAyahId = range.first,
                    endAyahId = range.last,
                    timestamp = System.currentTimeMillis(),
                    dateMillis = dateMillis,
                    manualStability = outcome.quality,
                    outcome = outcome.code,
                    mistakeTags = if (outcome.isRecallAttempt) MistakeTag.toMask(mistakeTags) else 0
                )
            )
            true
        }

    /**
     * Rewrites [log] to cover [startPage]..[endPage]; its creation timestamp is kept. A log
     * whose outcome is unchanged keeps its stored quality, so editing the date does not
     * flatten a migrated slider value.
     */
    suspend fun updateRevision(
        log: RevisionLog,
        startPage: Int,
        endPage: Int,
        outcome: RecallOutcome,
        mistakeTags: Set<MistakeTag>,
        dateMillis: Long
    ): Boolean =
        withContext(Dispatchers.IO) {
            val range = quran.ayahIdRangeOfPages(startPage, endPage) ?: return@withContext false
            dao.updateRevisionLog(
                log.toEntity().copy(
                    startAyahId = range.first,
                    endAyahId = range.last,
                    dateMillis = dateMillis,
                    manualStability = if (outcome == log.outcome) log.manualStability else outcome.quality,
                    outcome = outcome.code,
                    mistakeTags = if (outcome.isRecallAttempt) MistakeTag.toMask(mistakeTags) else 0
                )
            )
            true
        }

    // ── Saved passages (bookmarks and weak passages) ──────────────────────────

    /** Newest first; entries whose ayah ids are unknown are left out. */
    fun savedPassages(): Flow<List<SavedPassage>> =
        dao.observeSavedPassages()
            .map { rows -> rows.filter { it.startAyahId in index }.map(SavedPassage::fromEntity) }
            .flowOn(Dispatchers.IO)

    /** The saved passage that starts at [ayahId] (and [wordId], 0 = whole ayah), if any. */
    suspend fun savedPassageAt(ayahId: Int, wordId: Int = 0): SavedPassage? = withContext(Dispatchers.IO) {
        dao.getSavedPassageAt(ayahId, wordId)?.let(SavedPassage::fromEntity)
    }

    /**
     * Saves [passage]. A passage that starts where another already does replaces it (keeping
     * the original's creation time), so one ayah never holds two entries.
     */
    suspend fun savePassage(passage: SavedPassage) = withContext(Dispatchers.IO) {
        val existing = dao.getSavedPassageAt(passage.startAyahId, passage.wordId)
        val entity: SavedPassageEntity = passage.toEntity().let {
            if (it.id == 0 && existing != null) it.copy(id = existing.id, createdAt = existing.createdAt) else it
        }
        dao.upsertSavedPassage(entity)
    }

    suspend fun deleteSavedPassage(id: Int) = withContext(Dispatchers.IO) { dao.deleteSavedPassage(id) }

    // ── Hifz coverage ─────────────────────────────────────────────────────────

    /** The memorised ranges as global ayah ids, sorted and merged; empty if the user has not said. */
    fun coverage(): Flow<List<IntRange>> =
        dao.observeCoverage().map { rows -> HifzCoverage.merge(rows.map { it.startAyahId..it.endAyahId }) }
            .flowOn(Dispatchers.IO)

    suspend fun setCoverage(ranges: List<IntRange>) = withContext(Dispatchers.IO) {
        dao.replaceCoverage(HifzCoverage.merge(ranges).map { HifzCoverageEntity(startAyahId = it.first, endAyahId = it.last) })
    }

    /** Which pages (index 0 = page 1) the user has memorised; null means no restriction. */
    fun coveredPages(): Flow<BooleanArray?> =
        coverage().map { ranges -> HifzCoverage.coveredPages(ranges.filter { it.first in index && it.last in index }) { index.firstPageOf(it) } }
            .flowOn(Dispatchers.IO)

    // ── Vault lists ───────────────────────────────────────────────────────────

    fun groupsByFolder(folderId: Int?): Flow<List<GroupWithCount>> = withCounts(dao.observeGroupsInFolder(folderId))

    fun allGroupsWithMemberCount(): Flow<List<GroupWithCount>> = withCounts(dao.observeGroups())

    private fun withCounts(groups: Flow<List<SimilarityGroupEntity>>): Flow<List<GroupWithCount>> =
        combine(groups, dao.observeMembers()) { groupList, members ->
            val byGroup = members.groupBy { it.groupId }
            groupList.map { group ->
                val own = byGroup[group.id].orEmpty()
                GroupWithCount(
                    group       = group,
                    memberCount = own.size,
                    surahIds    = own.mapNotNull { ref(it.verseId)?.surahId }.distinct().sorted()
                )
            }
        }.flowOn(Dispatchers.IO)

    // ── Group members ─────────────────────────────────────────────────────────

    fun membersForGroup(groupId: Int): Flow<List<MemberVerseRef>> =
        dao.observeMembersOfGroup(groupId)
            .map { members -> members.mapNotNull { ref(it.verseId) }.sortedWith(byReadingOrder) }
            .flowOn(Dispatchers.IO)

    /** Every member of every group that has at least one member in [surahId]. */
    fun memberRefsBySurahGroups(surahId: Int): Flow<List<GroupMemberRef>> =
        combine(dao.observeGroups(), dao.observeMembers()) { groups, members ->
            val byGroup = members.groupBy { it.groupId }
            groups.sortedBy { it.id }.flatMap { group ->
                val refs = byGroup[group.id].orEmpty().mapNotNull { ref(it.verseId) }
                if (refs.none { it.surahId == surahId }) emptyList()
                else refs.sortedWith(byReadingOrder).map { r ->
                    GroupMemberRef(
                        groupId           = group.id,
                        description       = group.description,
                        masterQuality     = group.masterQuality,
                        memorizationNotes = group.memorizationNotes,
                        folderId          = group.folderId,
                        verseId           = r.verseId,
                        surahId           = r.surahId,
                        ayahNumber        = r.ayahNumber,
                        pageNumber        = r.pageNumber
                    )
                }
            }
        }.flowOn(Dispatchers.IO)

    // ── Surah list ────────────────────────────────────────────────────────────

    fun surahMetaList(): Flow<List<SurahMeta>> =
        dao.observeMembers().map { members ->
            val groupsBySurah = members
                .mapNotNull { m -> ref(m.verseId)?.let { it.surahId to m.groupId } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.distinct().size }
            surahs.map { SurahMeta(it.id, it.versesCount, it.startJuz, it.firstPage, groupsBySurah[it.id] ?: 0) }
        }.flowOn(Dispatchers.IO)

    /** The groups [ayahId] belongs to, each with its other member ayahs. */
    suspend fun groupsOfAyah(ayahId: Int): List<AyahGroup> = withContext(Dispatchers.IO) {
        dao.getGroupsOfAyah(ayahId).map { group ->
            val others = dao.getMemberAyahIds(group.id)
                .filter { it != ayahId }
                .mapNotNull { quran.ayahById(it) }
            AyahGroup(group, others)
        }
    }

    // ── Memory engine / quiz ──────────────────────────────────────────────────

    suspend fun pageSimilarityLinks(): List<PageSimilarityLink> = withContext(Dispatchers.IO) {
        val pages = dao.getAllSimilarityMembersSync()
            .filter { it.verseId in index }
            .groupBy({ it.groupId }, { index.firstPageOf(it.verseId)..index.lastPageOf(it.verseId) })
        SimilarityLinks.pageLinks(pages)
    }

    /** A random member ayah of the given group or folder tree, or null if there is none. */
    suspend fun randomVerseForTest(groupId: Int?, folderId: Int?): TestVerseResult? = withContext(Dispatchers.IO) {
        dao.getTestCandidates(groupId, folderId).shuffled().firstNotNullOfOrNull { candidate ->
            quran.ayahById(candidate.verseId)?.let { TestVerseResult(it, candidate.groupId, candidate.groupName) }
        }
    }
}
