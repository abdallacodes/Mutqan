package com.example.mutqan.domain

import android.content.Context
import android.net.Uri
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class StructuralSharingManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.studyDao()
    private val quran = QuranRepository.getInstance(context)

    suspend fun exportSubjects(uri: Uri, juzId: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val juzAyahs = juzId?.let { quran.ayahIdRangeOfJuz(it) }
            val subjects = if (juzId != null) {
                juzAyahs?.let { dao.getUserSubjectsBetween(it.first, it.last) }.orEmpty()
            } else {
                dao.getAllUserSubjectsSync()
            }

            val root = JSONObject().apply {
                put("type", "structural_export")
                put("version", 1)
                put("juz_id", juzId ?: -1)
                put("subjects", JSONArray().apply {
                    subjects.forEach { subject ->
                        put(JSONObject().apply {
                            put("subject_text", subject.subjectText)
                            put("start_ayah_id", subject.startAyahId)
                            put("order_index", subject.orderIndex)
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

    suspend fun importSubjects(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            val root = JSONObject(jsonString)
            if (root.optString("type") != "structural_export") {
                return@withContext Result.failure(Exception("Invalid structural export file format"))
            }

            val subjectsArray = root.getJSONArray("subjects")
            val subjectsToInsert = mutableListOf<UserSubjectEntity>()
            
            for (i in 0 until subjectsArray.length()) {
                val obj = subjectsArray.getJSONObject(i)
                subjectsToInsert.add(
                    UserSubjectEntity(
                        subjectText = obj.getString("subject_text"),
                        startAyahId = obj.getInt("start_ayah_id"),
                        orderIndex = obj.getInt("order_index")
                    )
                )
            }

            dao.insertUserSubjects(subjectsToInsert)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
