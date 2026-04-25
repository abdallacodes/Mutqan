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

    /**
     * Relaxed full-text search with optional filters.
     * 18 levels of REPLACE used to normalize:
     * - All Alef/Hamza variants (آ أ إ ٱ ء ئ ؤ ٰ) -> ا
     * - Yaa/Maksura (ي ى) -> ي
     * - Teh Marbuta/Heh (ة ه) -> ه
     * - All common diacritics stripped.
     */
    @Query("""
        SELECT * FROM verses
        WHERE (
            :surahId IS NULL OR surah_id = :surahId
        ) AND (
            :juzStart IS NULL OR juz_id >= :juzStart
        ) AND (
            :juzEnd IS NULL OR juz_id <= :juzEnd
        ) AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(text_arabic,
              char(1611),''),char(1612),''),char(1613),''),char(1614),''),char(1615),''),char(1616),''),char(1617),''),char(1618),''),
              char(1648),char(1575)),
              char(1570),char(1575)),char(1571),char(1575)),char(1573),char(1575)),char(1649),char(1575)),
              char(1569),char(1575)),char(1572),char(1575)),char(1574),char(1575)),
              char(1609),char(1610)),
              char(1577),char(1607))
        LIKE '%' || :query || '%'
        LIMIT 40
    """)
    abstract suspend fun searchVerses(
        query: String,
        surahId: Int? = null,
        juzStart: Int? = null,
        juzEnd: Int? = null
    ): List<VerseEntity>

    @Query("SELECT * FROM similarity_groups WHERE id = :groupId LIMIT 1")
    abstract suspend fun getGroupById(groupId: Int): SimilarityGroupEntity?

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
               sg.master_strength,
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

    @Query("DELETE FROM revision_logs")
    abstract suspend fun deleteAllRevisionLogs()

    @Query("DELETE FROM similarity_groups")
    abstract suspend fun deleteAllSimilarityGroups()

    @Query("DELETE FROM similarity_members")
    abstract suspend fun deleteAllSimilarityMembers()

    @Query("DELETE FROM vault_folders")
    abstract suspend fun deleteAllFolders()

    @androidx.room.Transaction
    open suspend fun clearUserData() {
        deleteAllRevisionLogs()
        deleteAllSimilarityGroups()
        deleteAllSimilarityMembers()
        deleteAllFolders()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSimilarityGroups(groups: List<SimilarityGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSimilarityMembers(members: List<SimilarityMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRevisionLogs(logs: List<RevisionLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFolders(folders: List<VaultFolderEntity>)

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
