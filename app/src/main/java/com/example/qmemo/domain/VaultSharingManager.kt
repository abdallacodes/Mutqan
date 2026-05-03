package com.example.qmemo.domain

import android.content.Context
import android.net.Uri
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import com.example.qmemo.data.local.entity.SimilarityMemberEntity
import com.example.qmemo.data.local.entity.VaultFolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class VaultSharingManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.quranDao()

    suspend fun exportVault(uri: Uri, folderId: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val groups = if (folderId != null) {
                dao.getGroupsByFolder(folderId).first()
            } else {
                dao.getAllGroupsWithMemberCount().first()
            }

            val root = JSONObject().apply {
                put("type", "vault_export")
                put("version", 1)
                put("groups", JSONArray().apply {
                    groups.forEach { item ->
                        val members = dao.getMembersForGroup(item.group.id).first()
                        put(JSONObject().apply {
                            put("description", item.group.description)
                            put("master_quality", item.group.masterQuality)
                            put("memorization_notes", item.group.memorizationNotes)
                            put("members", JSONArray().apply {
                                members.forEach { m ->
                                    put(JSONObject().apply {
                                        put("verse_id", m.verseId)
                                    })
                                }
                            })
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

    suspend fun importVault(uri: Uri, newFolderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            val root = JSONObject(jsonString)
            if (root.optString("type") != "vault_export") {
                return@withContext Result.failure(Exception("Invalid vault file format"))
            }

            val groupsArray = root.getJSONArray("groups")
            
            // 1. Create the new folder
            val folderId = dao.insertFolder(VaultFolderEntity(name = newFolderName)).toInt()

            // 2. Import groups into this folder
            for (i in 0 until groupsArray.length()) {
                val groupObj = groupsArray.getJSONObject(i)
                val group = SimilarityGroupEntity(
                    description = groupObj.getString("description"),
                    masterQuality = groupObj.optDouble("master_quality", 0.5).toFloat(),
                    memorizationNotes = groupObj.optString("memorization_notes", ""),
                    folderId = folderId
                )
                
                val newGroupId = dao.insertSimilarityGroup(group).toInt()
                
                val membersArray = groupObj.getJSONArray("members")
                for (j in 0 until membersArray.length()) {
                    val memberObj = membersArray.getJSONObject(j)
                    dao.insertSimilarityMember(
                        SimilarityMemberEntity(
                            groupId = newGroupId,
                            verseId = memberObj.getInt("verse_id")
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
