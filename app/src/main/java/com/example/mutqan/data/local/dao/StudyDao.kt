package com.example.mutqan.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mutqan.data.local.entity.HifzCoverageEntity
import com.example.mutqan.data.local.entity.RevisionLogEntity
import com.example.mutqan.data.local.entity.SavedPassageEntity
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.data.local.entity.SimilarityMemberEntity
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.data.local.entity.VaultFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * The user's own data: revision logs, similarity groups and their members, vault folders and
 * subjects. Quran text is never stored here; members and subjects refer to ayahs by their
 * global id and are resolved through the `:quran` module (see
 * [com.example.mutqan.domain.StudyRepository]).
 */
@Dao
abstract class StudyDao {

    // ─────────────────────────────────────────────────────────
    // Revision Logs
    // ─────────────────────────────────────────────────────────

    @Insert
    abstract suspend fun insertRevisionLog(log: RevisionLogEntity)

    @Delete
    abstract suspend fun deleteRevisionLog(log: RevisionLogEntity)

    @Query("SELECT * FROM revision_logs ORDER BY timestamp DESC")
    abstract fun getAllRevisionLogs(): Flow<List<RevisionLogEntity>>

    @Query("DELETE FROM revision_logs WHERE id = :id")
    abstract suspend fun deleteRevisionLogById(id: Int)

    @Update
    abstract suspend fun updateRevisionLog(log: RevisionLogEntity)

    // ─────────────────────────────────────────────────────────
    // Hifz coverage
    // ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM hifz_coverage ORDER BY start_ayah_id")
    abstract fun observeCoverage(): Flow<List<HifzCoverageEntity>>

    @Query("SELECT * FROM hifz_coverage ORDER BY start_ayah_id")
    abstract suspend fun getAllCoverageSync(): List<HifzCoverageEntity>

    @Query("DELETE FROM hifz_coverage")
    abstract suspend fun deleteAllCoverage()

    @Insert
    abstract suspend fun insertCoverage(ranges: List<HifzCoverageEntity>)

    /** Replaces the whole coverage with [ranges] in one transaction. */
    @Transaction
    open suspend fun replaceCoverage(ranges: List<HifzCoverageEntity>) {
        deleteAllCoverage()
        insertCoverage(ranges)
    }

    // ─────────────────────────────────────────────────────────
    // Saved passages (bookmarks and weak passages)
    // ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM saved_passages ORDER BY created_at DESC")
    abstract fun observeSavedPassages(): Flow<List<SavedPassageEntity>>

    @Query("SELECT * FROM saved_passages ORDER BY created_at DESC")
    abstract suspend fun getAllSavedPassagesSync(): List<SavedPassageEntity>

    @Query("SELECT * FROM saved_passages WHERE start_ayah_id = :ayahId AND word_id = :wordId LIMIT 1")
    abstract suspend fun getSavedPassageAt(ayahId: Int, wordId: Int): SavedPassageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSavedPassage(passage: SavedPassageEntity): Long

    @Query("DELETE FROM saved_passages WHERE id = :id")
    abstract suspend fun deleteSavedPassage(id: Int)

    @Query("DELETE FROM saved_passages")
    abstract suspend fun deleteAllSavedPassages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSavedPassages(passages: List<SavedPassageEntity>)

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

    @Query("SELECT * FROM similarity_groups WHERE id = :id LIMIT 1")
    abstract suspend fun getGroupById(id: Int): SimilarityGroupEntity?

    @Query("SELECT * FROM similarity_groups ORDER BY id DESC")
    abstract fun observeGroups(): Flow<List<SimilarityGroupEntity>>

    @Query("SELECT * FROM similarity_groups WHERE folder_id IS :folderId ORDER BY id DESC")
    abstract fun observeGroupsInFolder(folderId: Int?): Flow<List<SimilarityGroupEntity>>

    @Query("SELECT * FROM similarity_members")
    abstract fun observeMembers(): Flow<List<SimilarityMemberEntity>>

    @Query("SELECT * FROM similarity_members WHERE group_id = :groupId")
    abstract fun observeMembersOfGroup(groupId: Int): Flow<List<SimilarityMemberEntity>>

    @Query("SELECT g.* FROM similarity_groups g JOIN similarity_members m ON m.group_id = g.id WHERE m.verse_id = :ayahId ORDER BY g.id")
    abstract suspend fun getGroupsOfAyah(ayahId: Int): List<SimilarityGroupEntity>

    @Query("SELECT verse_id FROM similarity_members WHERE group_id = :groupId ORDER BY verse_id")
    abstract suspend fun getMemberAyahIds(groupId: Int): List<Int>

    /**
     * Every (ayah, group) pair in the given group or folder tree, for the self-test quiz.
     * The caller picks one at random.
     */
    @Query(
        """
        WITH RECURSIVE subfolders AS (
            SELECT id FROM vault_folders WHERE (:folderId IS NOT NULL AND id = :folderId)
            UNION ALL
            SELECT vf.id FROM vault_folders vf
            JOIN subfolders sf ON vf.parent_id = sf.id
        )
        SELECT sm.verse_id AS verse_id, sg.id AS group_id, sg.description AS group_name
        FROM similarity_members sm
        JOIN similarity_groups sg ON sm.group_id = sg.id
        WHERE (:groupId IS NOT NULL AND sg.id = :groupId)
           OR (:folderId IS NOT NULL AND sg.folder_id IN subfolders)
        """
    )
    abstract suspend fun getTestCandidates(groupId: Int?, folderId: Int?): List<TestCandidate>

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

    // ─────────────────────────────────────────────────────────
    // Subjects (structural mode anchors)
    // ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM user_subjects WHERE start_ayah_id BETWEEN :firstAyahId AND :lastAyahId ORDER BY start_ayah_id ASC, order_index ASC")
    abstract fun observeUserSubjectsBetween(firstAyahId: Int, lastAyahId: Int): Flow<List<UserSubjectEntity>>

    @Query("SELECT * FROM user_subjects WHERE start_ayah_id BETWEEN :firstAyahId AND :lastAyahId ORDER BY start_ayah_id ASC, order_index ASC")
    abstract suspend fun getUserSubjectsBetween(firstAyahId: Int, lastAyahId: Int): List<UserSubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUserSubject(subject: UserSubjectEntity)

    @Update
    abstract suspend fun updateUserSubject(subject: UserSubjectEntity)

    @Delete
    abstract suspend fun deleteUserSubject(subject: UserSubjectEntity)

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

    @Transaction
    open suspend fun clearUserData() {
        deleteAllRevisionLogs()
        deleteAllSimilarityGroups()
        deleteAllSimilarityMembers()
        deleteAllFolders()
        deleteAllUserSubjects()
        deleteAllCoverage()
        deleteAllSavedPassages()
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
}

data class TestCandidate(
    @ColumnInfo(name = "verse_id") val verseId: Int,
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "group_name") val groupName: String
)
