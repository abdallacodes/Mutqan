package com.example.qmemo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.qmemo.data.local.entity.RevisionLogEntity
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import com.example.qmemo.data.local.entity.SimilarityMemberEntity
import com.example.qmemo.data.local.entity.VerseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Dao
abstract class QuranDao {

    // ─────────────────────────────────────────────────────────
    // Static data — called once during pre-population
    // ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertVerses(verses: List<VerseEntity>)

    /**
     * Lazily fetches the Arabic text for a single verse.
     * Called only when the Quick Peek sheet opens — keeps list queries lightweight.
     */
    @Query("SELECT text_arabic FROM verses WHERE id = :verseId LIMIT 1")
    abstract suspend fun getArabicText(verseId: Int): String?

    // ─────────────────────────────────────────────────────────
    // Revision Logs
    // ─────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertRevisionLog(log: RevisionLogEntity)

    @Delete
    abstract suspend fun deleteRevisionLog(log: RevisionLogEntity)

    @Query("SELECT * FROM revision_logs ORDER BY timestamp DESC")
    abstract fun getAllRevisionLogs(): Flow<List<RevisionLogEntity>>

    // ─────────────────────────────────────────────────────────
    // Similarity Groups + Members
    // ─────────────────────────────────────────────────────────

    /** Returns the new row id so the caller can immediately insert members. */
    @Insert
    abstract suspend fun insertSimilarityGroup(group: SimilarityGroupEntity): Long

    @Update
    abstract suspend fun updateSimilarityGroup(group: SimilarityGroupEntity)

    @Delete
    abstract suspend fun deleteSimilarityGroup(group: SimilarityGroupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSimilarityMember(member: SimilarityMemberEntity)

    @Delete
    abstract suspend fun deleteSimilarityMember(member: SimilarityMemberEntity)

    /**
     * All groups with their member counts and a comma-separated list of distinct
     * Surah IDs for the vault list screen.  The [GroupWithCount.surahIds] helper
     * parses the raw string into a sorted [List<Int>].
     */
    @Query(
        """
        SELECT sg.*,
               COUNT(DISTINCT sm.verse_id)    AS member_count,
               GROUP_CONCAT(DISTINCT v.surah_id) AS surah_ids_raw
        FROM   similarity_groups sg
               LEFT JOIN similarity_members sm ON sm.group_id = sg.id
               LEFT JOIN verses             v  ON v.id        = sm.verse_id
        GROUP  BY sg.id
        ORDER  BY sg.id DESC
        """
    )
    abstract fun getAllGroupsWithMemberCount(): Flow<List<GroupWithCount>>

    /** Verse members of a single group, joined with verse metadata. */
    @Query(
        """
        SELECT v.id AS verse_id, v.surah_id, v.ayah_number, v.page_number
        FROM   similarity_members sm
               INNER JOIN verses v ON v.id = sm.verse_id
        WHERE  sm.group_id = :groupId
        ORDER  BY v.surah_id ASC, v.ayah_number ASC
        """
    )
    abstract fun getMembersForGroup(groupId: Int): Flow<List<MemberVerseRef>>

    /** Look up a verse by its human-readable reference. Returns null if not found. */
    @Query("SELECT * FROM verses WHERE surah_id = :surahId AND ayah_number = :ayahNumber LIMIT 1")
    abstract suspend fun findVerse(surahId: Int, ayahNumber: Int): VerseEntity?

    /** Fetch a single group by id (used when reopening the edit screen). */
    @Query("SELECT * FROM similarity_groups WHERE id = :groupId LIMIT 1")
    abstract suspend fun getGroupById(groupId: Int): SimilarityGroupEntity?

    // ─────────────────────────────────────────────────────────
    // Surah Explorer
    // ─────────────────────────────────────────────────────────

    /**
     * One row per Surah: verse count, the Juz the Surah starts in, and how many
     * distinct SimilarityGroups touch it.  Uses DISTINCT v.id to avoid
     * inflating verse counts when a verse belongs to multiple groups.
     * Reactive — re-emits whenever the similarity_members table changes.
     */
    @Query(
        """
        SELECT
            v.surah_id,
            COUNT(DISTINCT v.id)         AS verse_count,
            MIN(v.juz_id)                AS start_juz,
            COUNT(DISTINCT sm.group_id)  AS group_count
        FROM   verses v
               LEFT JOIN similarity_members sm ON sm.verse_id = v.id
        GROUP  BY v.surah_id
        ORDER  BY v.surah_id ASC
        """
    )
    abstract fun getSurahMetaList(): Flow<List<SurahMeta>>

    /**
     * For an External group: returns the IDs of every Surah that contributes
     * a member verse to [groupId] EXCEPT [currentSurahId] itself.
     * Sorted ascending so chip order is stable.
     */
    @Query(
        """
        SELECT DISTINCT v.surah_id
        FROM   similarity_members sm
               INNER JOIN verses v ON v.id = sm.verse_id
        WHERE  sm.group_id      = :groupId
        AND    v.surah_id      != :currentSurahId
        ORDER  BY v.surah_id ASC
        """
    )
    abstract suspend fun getOtherSurahsInGroup(groupId: Int, currentSurahId: Int): List<Int>

    // ─────────────────────────────────────────────────────────
    // Master Query — Internal vs. External split
    // ─────────────────────────────────────────────────────────

    /**
     * Internal groups: the group touches [surahId], and NOT A SINGLE member
     * belongs to a different Surah — the group is fully contained within this Surah.
     */
    @Query(
        """
        SELECT DISTINCT sg.*
        FROM   similarity_groups sg
               INNER JOIN similarity_members sm ON sm.group_id = sg.id
               INNER JOIN verses             v  ON v.id        = sm.verse_id
        WHERE  v.surah_id = :surahId
        AND    NOT EXISTS (
                   SELECT 1
                   FROM   similarity_members sm2
                          INNER JOIN verses v2 ON v2.id = sm2.verse_id
                   WHERE  sm2.group_id = sg.id
                   AND    v2.surah_id != :surahId
               )
        ORDER BY sg.id ASC
        """
    )
    abstract fun getInternalGroupsForSurah(surahId: Int): Flow<List<SimilarityGroupEntity>>

    /**
     * External groups: the group touches [surahId], but at least one other member
     * belongs to a different Surah — this is a cross-Surah confusion point.
     */
    @Query(
        """
        SELECT DISTINCT sg.*
        FROM   similarity_groups sg
               INNER JOIN similarity_members sm ON sm.group_id = sg.id
               INNER JOIN verses             v  ON v.id        = sm.verse_id
        WHERE  v.surah_id = :surahId
        AND    EXISTS (
                   SELECT 1
                   FROM   similarity_members sm2
                          INNER JOIN verses v2 ON v2.id = sm2.verse_id
                   WHERE  sm2.group_id = sg.id
                   AND    v2.surah_id != :surahId
               )
        ORDER BY sg.id ASC
        """
    )
    abstract fun getExternalGroupsForSurah(surahId: Int): Flow<List<SimilarityGroupEntity>>

    /**
     * Combines the two reactive streams into a single [SurahGroupsResult].
     * The Flow re-emits automatically whenever either list changes in the DB.
     */
    fun getGroupsBySurah(surahId: Int): Flow<SurahGroupsResult> =
        combine(
            getInternalGroupsForSurah(surahId),
            getExternalGroupsForSurah(surahId)
        ) { internal, external ->
            SurahGroupsResult(
                internalGroups = internal,
                externalGroups = external
            )
        }

    // ─────────────────────────────────────────────────────────
    // Surah Detail — unified group+verse query
    // ─────────────────────────────────────────────────────────

    /**
     * Returns every (group, verse) pair for all groups that touch [surahId].
     * One row per member verse; callers group by [GroupMemberRef.groupId] in Kotlin.
     * Ordered by group id then by Surah/Ayah so the in-memory groupBy produces
     * a stable, sorted result.
     */
    @Query(
        """
        SELECT sg.id            AS group_id,
               sg.description,
               sg.master_strength,
               v.id             AS verse_id,
               v.surah_id,
               v.ayah_number,
               v.page_number
        FROM   similarity_members sm
               INNER JOIN verses            v  ON v.id  = sm.verse_id
               INNER JOIN similarity_groups sg ON sg.id = sm.group_id
        WHERE  sm.group_id IN (
                   SELECT DISTINCT sm2.group_id
                   FROM   similarity_members sm2
                          INNER JOIN verses v2 ON v2.id = sm2.verse_id
                   WHERE  v2.surah_id = :surahId
               )
        ORDER  BY sg.id ASC, v.surah_id ASC, v.ayah_number ASC
        """
    )
    abstract fun getMemberRefsBySurahGroups(surahId: Int): Flow<List<GroupMemberRef>>

    // ─────────────────────────────────────────────────────────
    // Revision Log — edit & hard-delete
    // ─────────────────────────────────────────────────────────

    /** Hard-deletes a single revision log by primary key. */
    @Query("DELETE FROM revision_logs WHERE id = :id")
    abstract suspend fun deleteRevisionLogById(id: Int)

    /** Overwrites all mutable fields of an existing revision log. */
    @Update
    abstract suspend fun updateRevisionLog(log: RevisionLogEntity)

    // ─────────────────────────────────────────────────────────
    // Juz Explorer — static page/surah mappings
    // ─────────────────────────────────────────────────────────

    /**
     * Returns all (page_number, juz_id) pairs from the static verses table,
     * ordered by juz then page. Used once on startup to build a juz → pages
     * lookup map for the 30-Juz Dashboard.
     */
    @Query(
        """
        SELECT DISTINCT page_number, juz_id
        FROM   verses
        ORDER  BY juz_id ASC, page_number ASC
        """
    )
    abstract suspend fun getAllPageJuzMappings(): List<PageJuzRef>

    /**
     * Returns distinct page numbers belonging to [juzId], ordered ascending.
     * One-shot suspend call; result never changes after pre-population.
     */
    @Query(
        """
        SELECT DISTINCT page_number
        FROM   verses
        WHERE  juz_id = :juzId
        ORDER  BY page_number ASC
        """
    )
    abstract suspend fun getPagesByJuz(juzId: Int): List<Int>

    /**
     * For every page in [juzId], returns the comma-separated Surah IDs that have
     * at least one verse on that page.  Results are ordered by page number.
     *
     * Transition pages (where one Surah ends and the next begins) will have two
     * Surah IDs in [PageSurahsRef.surahIdsRaw], e.g. "8,9" for the Al-Anfal /
     * At-Tawbah boundary.  Cached once in [JuzDetailViewModel] — never re-queried.
     */
    @Query(
        """
        SELECT   page_number,
                 GROUP_CONCAT(DISTINCT surah_id) AS surah_ids_raw
        FROM     verses
        WHERE    juz_id = :juzId
        GROUP BY page_number
        ORDER BY page_number ASC
        """
    )
    abstract suspend fun getPageSurahsForJuz(juzId: Int): List<PageSurahsRef>

    /**
     * Returns the first and last Surah IDs that appear in [juzId].
     * Used to populate the subtitle in JuzDetailScreen.
     */
    @Query(
        """
        SELECT MIN(surah_id) AS first_surah,
               MAX(surah_id) AS last_surah
        FROM   verses
        WHERE  juz_id = :juzId
        """
    )
    abstract suspend fun getSurahRangeForJuz(juzId: Int): JuzSurahRange?
}
