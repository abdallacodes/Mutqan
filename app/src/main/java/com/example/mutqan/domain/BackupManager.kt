package com.example.mutqan.domain

import com.example.mutqan.data.local.entity.ExerciseResultEntity
import android.content.Context
import android.net.Uri
import com.example.mutqan.R
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.entity.HifzCoverageEntity
import com.example.mutqan.data.local.entity.PageStrengthEntity
import com.example.mutqan.data.local.entity.RevisionLogEntity
import com.example.mutqan.data.local.entity.RevisionPlanEntity
import com.example.mutqan.data.local.entity.SavedPassageEntity
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.data.local.entity.SimilarityMemberEntity
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.data.local.entity.VaultFolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.studyDao()
    private val quran = QuranRepository.getInstance(context)
    private val index by lazy { quran.ayahIndex }

    /** Everything the user has made, as the JSON that backups and cloud sync both carry. */
    suspend fun buildSnapshot(): JSONObject {
            val logs = dao.getAllRevisionLogsSync()
            val groups = dao.getAllSimilarityGroupsSync()
            val members = dao.getAllSimilarityMembersSync()
            val folders = dao.getAllFoldersSync()
            val subjects = dao.getAllUserSubjectsSync()
            val coverage = dao.getAllCoverageSync()
            val saved = dao.getAllSavedPassagesSync()
            val plans = dao.getAllPlansSync()
            val exercises = dao.getAllExerciseResultsSync()
            val strengths = dao.getAllPageStrengthSync()

            val root = JSONObject().apply {
                put("type", "full_backup")
                put("version", 5)
                put("revision_logs", JSONArray().apply {
                    logs.forEach { log ->
                        put(JSONObject().apply {
                            put("id", log.id)
                            put("start_ayah_id", log.startAyahId)
                            put("end_ayah_id", log.endAyahId)
                            // Derived page numbers: informational, and read by older builds.
                            put("start_page", if (log.startAyahId in index) index.firstPageOf(log.startAyahId) else 0)
                            put("end_page", if (log.endAyahId in index) index.firstPageOf(log.endAyahId) else 0)
                            put("timestamp", log.timestamp)
                            put("manual_stability", log.manualStability)
                            put("date_millis", log.dateMillis)
                            put("outcome", log.outcome)
                            put("mistake_tags", log.mistakeTags)
                        })
                    }
                })
                put("hifz_coverage", JSONArray().apply {
                    coverage.forEach { range ->
                        put(JSONObject().apply {
                            put("start_ayah_id", range.startAyahId)
                            put("end_ayah_id", range.endAyahId)
                        })
                    }
                })
                put("page_strength", JSONArray().apply {
                    strengths.forEach { r -> put(JSONObject().apply { put("page", r.page); put("level", r.level) }) }
                })
                put("exercise_results", JSONArray().apply {
                    exercises.forEach { r ->
                        put(JSONObject().apply {
                            put("type", r.type)
                            put("start_page", r.startPage)
                            put("end_page", r.endPage)
                            put("outcome", r.outcome)
                            put("timestamp", r.timestamp)
                        })
                    }
                })
                put("revision_plans", JSONArray().apply {
                    plans.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("unit", p.unit)
                            put("amount_per_day", p.amountPerDay)
                            put("rest_days", p.restDays)
                            put("plan_order", p.order)
                            put("paused", p.paused)
                            put("strong_pages", p.strongPages)
                            put("fair_pages", p.fairPages)
                            put("weak_pages", p.weakPages)
                            put("created_at", p.createdAt)
                        })
                    }
                })
                put("saved_passages", JSONArray().apply {
                    saved.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("kind", p.kind)
                            put("start_ayah_id", p.startAyahId)
                            put("end_ayah_id", p.endAyahId)
                            put("word_id", p.wordId)
                            put("tags", p.tags)
                            put("priority", p.priority)
                            put("note", p.note)
                            put("created_at", p.createdAt)
                            put("resolved_at", p.resolvedAt)
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
                            put("master_quality", group.masterQuality)
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
                put("user_subjects", JSONArray().apply {
                    subjects.forEach { subject ->
                        put(JSONObject().apply {
                            put("id", subject.id)
                            put("subject_text", subject.subjectText)
                            put("start_ayah_id", subject.startAyahId)
                            put("order_index", subject.orderIndex)
                        })
                    }
                })
            }
            return root
    }

    suspend fun exportData(uri: Uri, password: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = buildSnapshot()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val text = root.toString(2)
                outputStream.write((if (password.isNullOrEmpty()) text else BackupCrypto.encrypt(text, password)).toByteArray())
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.err_open_output)))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Replaces everything the user has made with the contents of [root], a snapshot from [buildSnapshot] or a backup file. */
    suspend fun restoreSnapshot(root: JSONObject) {
            
            val logs = mutableListOf<RevisionLogEntity>()
            val logsArray = root.optJSONArray("revision_logs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val obj = logsArray.getJSONObject(i)
                    // Version 4 stores ayah ids; older files only have page numbers.
                    val range = if (obj.has("start_ayah_id") && obj.has("end_ayah_id")) {
                        obj.getInt("start_ayah_id")..obj.getInt("end_ayah_id")
                    } else {
                        val start = obj.getInt("start_page").coerceIn(1, 604)
                        quran.ayahIdRangeOfPages(start, obj.getInt("end_page").coerceIn(start, 604)) ?: continue
                    }
                    val quality = obj.getDouble("manual_stability").toFloat()
                    logs.add(RevisionLogEntity(
                        id = obj.optInt("id", 0),
                        startAyahId = range.first,
                        endAyahId = range.last,
                        timestamp = obj.getLong("timestamp"),
                        manualStability = quality,
                        dateMillis = obj.optLong("date_millis", 0L),
                        // Files from before version 5 only have the quality slider.
                        outcome = if (obj.has("outcome")) obj.getInt("outcome") else RecallOutcome.fromQuality(quality).code,
                        mistakeTags = obj.optInt("mistake_tags", 0)
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
                        masterQuality = obj.optDouble("master_quality", 0.5).toFloat(),
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

            val subjects = mutableListOf<UserSubjectEntity>()
            val subjectsArray = root.optJSONArray("user_subjects")
            if (subjectsArray != null) {
                for (i in 0 until subjectsArray.length()) {
                    val obj = subjectsArray.getJSONObject(i)
                    subjects.add(UserSubjectEntity(
                        id = obj.optInt("id", 0),
                        subjectText = obj.getString("subject_text"),
                        startAyahId = obj.getInt("start_ayah_id"),
                        orderIndex = obj.getInt("order_index")
                    ))
                }
            }

            // Version 5 stores what the user has memorised; older files do not, so it is taken
            // from the ayahs they revised, as when the database was upgraded.
            val coverageArray = root.optJSONArray("hifz_coverage")
            val coverageRanges = if (coverageArray != null) {
                (0 until coverageArray.length()).map {
                    val obj = coverageArray.getJSONObject(it)
                    obj.getInt("start_ayah_id")..obj.getInt("end_ayah_id")
                }
            } else {
                logs.map { it.startAyahId..it.endAyahId }
            }

            val plans = mutableListOf<RevisionPlanEntity>()
            val plansArray = root.optJSONArray("revision_plans")
            if (plansArray != null) {
                for (i in 0 until plansArray.length()) {
                    val obj = plansArray.getJSONObject(i)
                    plans.add(RevisionPlanEntity(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        unit = obj.getInt("unit"),
                        amountPerDay = obj.getInt("amount_per_day"),
                        restDays = obj.optInt("rest_days", 0),
                        order = obj.optInt("plan_order", 0),
                        paused = obj.optInt("paused", 0),
                        strongPages = obj.optInt("strong_pages", 4).coerceAtLeast(0),
                        fairPages = obj.optInt("fair_pages", 6).coerceAtLeast(0),
                        weakPages = obj.optInt("weak_pages", 10).coerceAtLeast(0),
                        createdAt = obj.getLong("created_at")
                    ))
                }
            }

            val exercises = mutableListOf<ExerciseResultEntity>()
            root.optJSONArray("exercise_results")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    exercises.add(ExerciseResultEntity(
                        type = obj.getInt("type"),
                        startPage = obj.getInt("start_page").coerceIn(1, 604),
                        endPage = obj.getInt("end_page").coerceIn(1, 604),
                        outcome = obj.getInt("outcome"),
                        timestamp = obj.getLong("timestamp")
                    ))
                }
            }

            val strengths = mutableListOf<PageStrengthEntity>()
            root.optJSONArray("page_strength")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val page = obj.getInt("page")
                    if (page in 1..604) strengths.add(PageStrengthEntity(page, obj.getInt("level")))
                }
            }

            val savedPassages = mutableListOf<SavedPassageEntity>()
            val savedArray = root.optJSONArray("saved_passages")
            if (savedArray != null) {
                for (i in 0 until savedArray.length()) {
                    val obj = savedArray.getJSONObject(i)
                    savedPassages.add(SavedPassageEntity(
                        id = obj.optInt("id", 0),
                        kind = obj.getInt("kind"),
                        startAyahId = obj.getInt("start_ayah_id"),
                        endAyahId = obj.getInt("end_ayah_id"),
                        wordId = obj.optInt("word_id", 0),
                        tags = obj.optInt("tags", 0),
                        priority = obj.optInt("priority", 2),
                        note = obj.optString("note", ""),
                        createdAt = obj.getLong("created_at"),
                        resolvedAt = obj.optLong("resolved_at", 0L)
                    ))
                }
            }

            // Perform DB operations
            dao.clearUserData()
            dao.insertCoverage(HifzCoverage.merge(coverageRanges).map { HifzCoverageEntity(startAyahId = it.first, endAyahId = it.last) })
            dao.insertFolders(folders)
            dao.insertRevisionLogs(logs)
            dao.insertSimilarityGroups(groups)
            dao.insertSimilarityMembers(members)
            dao.insertUserSubjects(subjects)
            dao.insertSavedPassages(savedPassages)
            dao.insertPlans(plans)
            dao.insertExerciseResults(exercises)
            dao.insertPageStrength(strengths)
    }

    suspend fun importData(uri: Uri, password: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: return@withContext Result.failure(Exception(context.getString(R.string.err_open_input)))
            val jsonString = when {
                !BackupCrypto.isEncrypted(fileText) -> fileText
                password.isNullOrEmpty() -> return@withContext Result.failure(PasswordRequiredException())
                else -> BackupCrypto.decrypt(fileText, password)
            }

            restoreSnapshot(JSONObject(jsonString))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
