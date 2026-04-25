package com.example.qmemo.domain

import android.content.Context
import android.net.Uri
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.entity.RevisionLogEntity
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import com.example.qmemo.data.local.entity.SimilarityMemberEntity
import com.example.qmemo.data.local.entity.VaultFolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.quranDao()

    suspend fun exportData(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val logs = dao.getAllRevisionLogsSync()
            val groups = dao.getAllSimilarityGroupsSync()
            val members = dao.getAllSimilarityMembersSync()
            val folders = dao.getAllFoldersSync()

            val root = JSONObject().apply {
                put("type", "full_backup")
                put("version", 2)
                put("revision_logs", JSONArray().apply {
                    logs.forEach { log ->
                        put(JSONObject().apply {
                            put("id", log.id)
                            put("start_page", log.startPage)
                            put("end_page", log.endPage)
                            put("timestamp", log.timestamp)
                            put("difficulty", log.difficulty)
                            put("date_millis", log.dateMillis)
                        })
                    }
                })
                put("vault_folders", JSONArray().apply {
                    folders.forEach { folder ->
                        put(JSONObject().apply {
                            put("id", folder.id)
                            put("name", folder.name)
                            put("parent_id", folder.parentId)
                            put("timestamp", folder.timestamp)
                        })
                    }
                })
                put("similarity_groups", JSONArray().apply {
                    groups.forEach { group ->
                        put(JSONObject().apply {
                            put("id", group.id)
                            put("description", group.description)
                            put("master_strength", group.masterStrength)
                            put("memorization_notes", group.memorizationNotes)
                            put("folder_id", group.folderId)
                        })
                    }
                })
                put("similarity_members", JSONArray().apply {
                    members.forEach { member ->
                        put(JSONObject().apply {
                            put("group_id", member.groupId)
                            put("verse_id", member.verseId)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(root.toString(2).toByteArray())
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importData(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            val root = JSONObject(jsonString)
            
            val logs = mutableListOf<RevisionLogEntity>()
            val logsArray = root.optJSONArray("revision_logs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val obj = logsArray.getJSONObject(i)
                    logs.add(RevisionLogEntity(
                        id = obj.optInt("id", 0),
                        startPage = obj.getInt("start_page"),
                        endPage = obj.getInt("end_page"),
                        timestamp = obj.getLong("timestamp"),
                        difficulty = obj.getInt("difficulty"),
                        dateMillis = obj.optLong("date_millis", 0L)
                    ))
                }
            }

            val folders = mutableListOf<VaultFolderEntity>()
            val foldersArray = root.optJSONArray("vault_folders")
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val obj = foldersArray.getJSONObject(i)
                    folders.add(VaultFolderEntity(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        parentId = if (obj.isNull("parent_id")) null else obj.getInt("parent_id"),
                        timestamp = obj.getLong("timestamp")
                    ))
                }
            }

            val groups = mutableListOf<SimilarityGroupEntity>()
            val groupsArray = root.optJSONArray("similarity_groups")
            if (groupsArray != null) {
                for (i in 0 until groupsArray.length()) {
                    val obj = groupsArray.getJSONObject(i)
                    groups.add(SimilarityGroupEntity(
                        id = obj.optInt("id", 0),
                        description = obj.getString("description"),
                        masterStrength = obj.getInt("master_strength"),
                        memorizationNotes = obj.optString("memorization_notes", ""),
                        folderId = if (obj.isNull("folder_id")) null else obj.getInt("folder_id")
                    ))
                }
            }

            val members = mutableListOf<SimilarityMemberEntity>()
            val membersArray = root.optJSONArray("similarity_members")
            if (membersArray != null) {
                for (i in 0 until membersArray.length()) {
                    val obj = membersArray.getJSONObject(i)
                    members.add(SimilarityMemberEntity(
                        groupId = obj.getInt("group_id"),
                        verseId = obj.getInt("verse_id")
                    ))
                }
            }

            // Perform DB operations
            dao.clearUserData()
            dao.insertFolders(folders)
            dao.insertRevisionLogs(logs)
            dao.insertSimilarityGroups(groups)
            dao.insertSimilarityMembers(members)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
