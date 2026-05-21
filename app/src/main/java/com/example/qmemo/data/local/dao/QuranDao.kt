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
import com.example.qmemo.data.local.entity.StructureUnitEntity
import com.example.qmemo.data.local.entity.UserSubjectEntity
import com.example.qmemo.data.local.entity.VaultFolderEntity
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

    @Query(
        """
        SELECT sg.*,
               COUNT(DISTINCT sm.verse_id)                      AS member_count,
               COALESCE(GROUP_CONCAT(DISTINCT v.surah_id), '')  AS surah_ids_raw
        FROM   similarity_groups sg
               LEFT JOIN similarity_members sm ON sm.group_id = sg.id
               LEFT JOIN verses             v  ON v.id        = sm.verse_id
        GROUP  BY sg.id
        ORDER  BY sg.id DESC
        """
    )
    abstract fun getAllGroupsWithMemberCount(): Flow<List<GroupWithCount>>

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

    @Query("SELECT * FROM verses WHERE surah_id = :surahId AND ayah_number = :ayahNumber LIMIT 1")
    abstract suspend fun findVerse(surahId: Int, ayahNumber: Int): VerseEntity?

    @Query("SELECT * FROM verses WHERE juz_id = :juzId AND ayah_number = :ayahNumber LIMIT 1")
    abstract suspend fun findVerseInJuz(juzId: Int, ayahNumber: Int): VerseEntity?

    /**
     * Upgraded search functionality using the pre-normalized content column.
     * High performance text search leveraging the shadow FTS table.
     */
    @Query("""
        SELECT v.* FROM verses v
        JOIN verses_fts f ON v.id = f.rowid
        WHERE (
            :surahId IS NULL OR v.surah_id = :surahId
        ) AND (
            :juzStart IS NULL OR v.juz_id >= :juzStart
        ) AND (
            :juzEnd IS NULL OR v.juz_id <= :juzEnd
        ) AND f.normalized_content LIKE '%' || :query || '%'
        LIMIT 40
    """)
    abstract suspend fun searchVerses(
        query: String,
        surahId: Int? = null,
        juzStart: Int? = null,
        juzEnd: Int? = null
    ): List<VerseEntity>

    @Query("SELECT * FROM similarity_groups WHERE id = :id LIMIT 1")
    abstract suspend fun getGroupById(id: Int): SimilarityGroupEntity?

    /**
     * Returns all pairs of pages that are "linked" through similarity groups.
     * Used for Semantic Interference calculation in the Memory Engine.
     */
    @Query("""
        SELECT DISTINCT v1.page_number as pageA, v2.page_number as pageB
        FROM similarity_members sm1
        JOIN verses v1 ON sm1.verse_id = v1.id
        JOIN similarity_members sm2 ON sm1.group_id = sm2.group_id
        JOIN verses v2 ON sm2.verse_id = v2.id
        WHERE v1.page_number != v2.page_number
    """)
    abstract suspend fun getPageSimilarityLinks(): List<PageSimilarityLink>

    // ─────────────────────────────────────────────────────────
    // Folders
    // ─────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertFolder(folder: VaultFolderEntity): Long

    @Update
    abstract suspend fun updateFolder(folder: VaultFolderEntity)

    @Delete
    abstract suspend fun deleteFolder(folder: VaultFolderEntity)

    @Query("SELECT * FROM vault_folders WHERE parent_id IS :parentId ORDER BY name ASC")
    abstract fun getFoldersByParent(parentId: Int?): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE name = :name AND parent_id IS :parentId LIMIT 1")
    abstract suspend fun getFolderByName(name: String, parentId: Int?): VaultFolderEntity?

    @Query("SELECT * FROM vault_folders WHERE id = :id LIMIT 1")
    abstract suspend fun getFolderById(id: Int): VaultFolderEntity?

    @Query(
        """
        SELECT sg.*,
               COUNT(DISTINCT sm.verse_id)                      AS member_count,
               COALESCE(GROUP_CONCAT(DISTINCT v.surah_id), '')  AS surah_ids_raw
        FROM   similarity_groups sg
               LEFT JOIN similarity_members sm ON sm.group_id = sg.id
               LEFT JOIN verses             v  ON v.id        = sm.verse_id
        WHERE  sg.folder_id IS :folderId
        GROUP  BY sg.id
        ORDER  BY sg.id DESC
        """
    )
    abstract fun getGroupsByFolder(folderId: Int?): Flow<List<GroupWithCount>>

    // ─────────────────────────────────────────────────────────
    // Surah Explorer
    // ─────────────────────────────────────────────────────────

    @Query(
        """
        SELECT
            v.surah_id,
            COUNT(DISTINCT v.id)         AS verse_count,
            MIN(v.juz_id)                AS start_juz,
            MIN(v.page_number)           AS start_page,
            COUNT(DISTINCT sm.group_id)  AS group_count
        FROM   verses v
               LEFT JOIN similarity_members sm ON sm.verse_id = v.id
        GROUP  BY v.surah_id
        ORDER  BY v.surah_id ASC
        """
    )
    abstract fun getSurahMetaList(): Flow<List<SurahMeta>>

    @Query(
        """
        SELECT MIN(page_number)
        FROM   verses
        WHERE  surah_id = :surahId
        """
    )
    abstract fun observeStartPageForSurah(surahId: Int): Flow<Int?>

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

    @Query(
        """
        SELECT sg.id            AS group_id,
               sg.description,
               sg.master_quality,
               sg.memorization_notes,
               sg.folder_id,
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

    @Query("DELETE FROM revision_logs WHERE id = :id")
    abstract suspend fun deleteRevisionLogById(id: Int)

    @Update
    abstract suspend fun updateRevisionLog(log: RevisionLogEntity)

    // ─────────────────────────────────────────────────────────
    // Backup & Restore
    // ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM revision_logs")
    abstract suspend fun getAllRevisionLogsSync(): List<RevisionLogEntity>

    @Query("SELECT * FROM similarity_groups")
    abstract suspend fun getAllSimilarityGroupsSync(): List<SimilarityGroupEntity>

    @Query("SELECT * FROM similarity_members")
    abstract suspend fun getAllSimilarityMembersSync(): List<SimilarityMemberEntity>

    @Query("SELECT * FROM vault_folders")
    abstract suspend fun getAllFoldersSync(): List<VaultFolderEntity>

    @Query("SELECT * FROM user_subjects")
    abstract suspend fun getAllUserSubjectsSync(): List<UserSubjectEntity>

    @Query("DELETE FROM revision_logs")
    abstract suspend fun deleteAllRevisionLogs()

    @Query("DELETE FROM similarity_groups")
    abstract suspend fun deleteAllSimilarityGroups()

    @Query("DELETE FROM similarity_members")
    abstract suspend fun deleteAllSimilarityMembers()

    @Query("DELETE FROM vault_folders")
    abstract suspend fun deleteAllFolders()

    @Query("DELETE FROM user_subjects")
    abstract suspend fun deleteAllUserSubjects()

    @androidx.room.Transaction
    open suspend fun clearUserData() {
        deleteAllRevisionLogs()
        deleteAllSimilarityGroups()
        deleteAllSimilarityMembers()
        deleteAllFolders()
        deleteAllUserSubjects()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSimilarityGroups(groups: List<SimilarityGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSimilarityMembers(members: List<SimilarityMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRevisionLogs(logs: List<RevisionLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFolders(folders: List<VaultFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUserSubjects(subjects: List<UserSubjectEntity>)

    // ─────────────────────────────────────────────────────────
    // Juz Explorer — static page/surah mappings
    // ─────────────────────────────────────────────────────────

    @Query(
        """
        SELECT   page_number,
                 GROUP_CONCAT(DISTINCT surah_id) AS surah_ids_raw
        FROM     verses
        GROUP BY page_number
        ORDER BY page_number ASC
        """
    )
    abstract suspend fun getAllPageSurahMappings(): List<PageSurahsRef>

    @Query(
        """
        SELECT DISTINCT page_number, juz_id
        FROM   verses
        ORDER  BY juz_id ASC, page_number ASC
        """
    )
    abstract suspend fun getAllPageJuzMappings(): List<PageJuzRef>

    @Query(
        """
        SELECT DISTINCT page_number
        FROM   verses
        WHERE  juz_id = :juzId
        ORDER  BY page_number ASC
        """
    )
    abstract suspend fun getPagesByJuz(juzId: Int): List<Int>

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

    @Query("SELECT MIN(surah_id) AS first_surah, MAX(surah_id) AS last_surah FROM verses WHERE juz_id = :juzId")
    abstract suspend fun getSurahRangeForJuz(juzId: Int): JuzSurahRange?

    // ─────────────────────────────────────────────────────────
    // Structural Mode
    // ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM verses WHERE page_number = :pageNumber ORDER BY id ASC")
    abstract suspend fun getVersesByPage(pageNumber: Int): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE id = :id LIMIT 1")
    abstract suspend fun getVerseById(id: Int): VerseEntity?

    @Query("SELECT * FROM structure_units WHERE juz_id = :juzId ORDER BY id ASC")
    abstract fun getStructureUnitsByJuz(juzId: Int): Flow<List<StructureUnitEntity>>

    @Query("SELECT * FROM user_subjects WHERE unit_id IN (SELECT id FROM structure_units WHERE juz_id = :juzId) ORDER BY unit_id ASC, order_index ASC")
    abstract fun getUserSubjectsByJuz(juzId: Int): Flow<List<UserSubjectEntity>>

    @Query("SELECT * FROM user_subjects WHERE unit_id IN (SELECT id FROM structure_units WHERE juz_id = :juzId) ORDER BY unit_id ASC, order_index ASC")
    abstract suspend fun getUserSubjectsByJuzSync(juzId: Int): List<UserSubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUserSubject(subject: UserSubjectEntity)

    @Update
    abstract suspend fun updateUserSubject(subject: UserSubjectEntity)

    @Delete
    abstract suspend fun deleteUserSubject(subject: UserSubjectEntity)

    // ─────────────────────────────────────────────────────────
    // Random Ayah
    // ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM verses WHERE juz_id = :juzId ORDER BY RANDOM() LIMIT 1")
    abstract suspend fun getRandomVerseInJuz(juzId: Int): VerseEntity?

    @Query("SELECT * FROM verses WHERE surah_id = :surahId ORDER BY RANDOM() LIMIT 1")
    abstract suspend fun getRandomVerseInSurah(surahId: Int): VerseEntity?

    @Query("SELECT * FROM verses WHERE page_number BETWEEN :startPage AND :endPage ORDER BY RANDOM() LIMIT 1")
    abstract suspend fun getRandomVerseInPageRange(startPage: Int, endPage: Int): VerseEntity?

    @Query(
        """
        WITH RECURSIVE subfolders AS (
            SELECT id FROM vault_folders WHERE (:folderId IS NOT NULL AND id = :folderId)
            UNION ALL
            SELECT vf.id FROM vault_folders vf
            JOIN subfolders sf ON vf.parent_id = sf.id
        )
        SELECT v.*, sg.id as group_id, sg.description as group_name
        FROM verses v
        JOIN similarity_members sm ON v.id = sm.verse_id
        JOIN similarity_groups sg ON sm.group_id = sg.id
        WHERE (:groupId IS NOT NULL AND sg.id = :groupId)
           OR (:folderId IS NOT NULL AND sg.folder_id IN subfolders)
        ORDER BY RANDOM() LIMIT 1
        """
    )
    abstract suspend fun getRandomVerseForTest(groupId: Int?, folderId: Int?): TestVerseResult?
}

data class TestVerseResult(
    @androidx.room.Embedded val verse: VerseEntity,
    @androidx.room.ColumnInfo(name = "group_id") val groupId: Int,
    @androidx.room.ColumnInfo(name = "group_name") val groupName: String
)

data class PageSimilarityLink(val pageA: Int, val pageB: Int)
