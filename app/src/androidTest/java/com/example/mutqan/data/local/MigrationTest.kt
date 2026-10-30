package com.example.mutqan.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a database laid out like Room version 13 (the shape installed apps have), fills it with
 * user data, then opens it through the app's migrations (13 -> 14 -> 15 -> 16 -> 17 -> 18 -> 19 -> 20) and checks that the
 * data survived and Room accepts the resulting schema. Uses its own file, never `mutqan.db`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun cleanUp() { context.deleteDatabase(name) }

    private fun createVersion13() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        db.use {
            // Static Quran tables that migration 13 -> 14 must drop.
            it.execSQL("CREATE TABLE `verses` (`id` INTEGER PRIMARY KEY NOT NULL, `surah_id` INTEGER NOT NULL, `ayah_number` INTEGER NOT NULL, `page_number` INTEGER NOT NULL, `juz_id` INTEGER NOT NULL, `text_arabic` TEXT NOT NULL, `normalized_content` TEXT NOT NULL DEFAULT '')")
            it.execSQL("CREATE VIRTUAL TABLE `verses_fts` USING FTS4(`normalized_content`, content=`verses`)")
            it.execSQL("CREATE TABLE `structure_units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `juz_id` INTEGER NOT NULL, `hizb_number` INTEGER NOT NULL, `quarter_number` INTEGER NOT NULL, `start_ayah_id` INTEGER NOT NULL, `end_ayah_id` INTEGER NOT NULL, `start_page` INTEGER NOT NULL, `end_page` INTEGER NOT NULL)")
            it.execSQL("INSERT INTO verses VALUES (262, 2, 255, 42, 3, 'x', 'x'), (7, 1, 7, 1, 1, 'x', 'x')")
            it.execSQL("INSERT INTO structure_units VALUES (1, 1, 1, 1, 1, 32, 1, 5)")

            // User tables as Room 13 created them (unchanged ones match today's entities).
            it.execSQL("CREATE TABLE `revision_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `start_page` INTEGER NOT NULL, `end_page` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `date_millis` INTEGER NOT NULL DEFAULT 0, `manual_stability` REAL NOT NULL)")
            it.execSQL("CREATE TABLE `vault_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `parent_id` INTEGER, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`parent_id`) REFERENCES `vault_folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            it.execSQL("CREATE UNIQUE INDEX `index_vault_folders_name_parent_id` ON `vault_folders` (`name`, `parent_id`)")
            it.execSQL("CREATE INDEX `index_vault_folders_parent_id` ON `vault_folders` (`parent_id`)")
            it.execSQL("CREATE TABLE `similarity_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `description` TEXT NOT NULL, `master_quality` REAL NOT NULL DEFAULT 0.5, `memorization_notes` TEXT NOT NULL DEFAULT '', `folder_id` INTEGER, FOREIGN KEY(`folder_id`) REFERENCES `vault_folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            it.execSQL("CREATE INDEX `index_similarity_groups_folder_id` ON `similarity_groups` (`folder_id`)")
            it.execSQL("CREATE TABLE `similarity_members` (`group_id` INTEGER NOT NULL, `verse_id` INTEGER NOT NULL, PRIMARY KEY(`group_id`, `verse_id`), FOREIGN KEY(`group_id`) REFERENCES `similarity_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`verse_id`) REFERENCES `verses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            it.execSQL("CREATE INDEX `index_similarity_members_group_id` ON `similarity_members` (`group_id`)")
            it.execSQL("CREATE INDEX `index_similarity_members_verse_id` ON `similarity_members` (`verse_id`)")
            it.execSQL("CREATE TABLE `user_subjects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `unit_id` INTEGER NOT NULL, `subject_text` TEXT NOT NULL, `start_ayah_id` INTEGER NOT NULL, `order_index` INTEGER NOT NULL, FOREIGN KEY(`unit_id`) REFERENCES `structure_units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            it.execSQL("INSERT INTO revision_logs VALUES (1, 1, 1, 1000, 0, 0.9), (2, 42, 45, 2000, 86400000, 0.5), (3, 600, 700, 3000, 0, 0.3)")
            it.execSQL("INSERT INTO vault_folders VALUES (1, 'Folder', NULL, 5)")
            it.execSQL("INSERT INTO similarity_groups VALUES (1, 'group', 0.6, 'notes', 1)")
            it.execSQL("INSERT INTO similarity_members VALUES (1, 262), (1, 7)")
            it.execSQL("INSERT INTO user_subjects VALUES (1, 1, 'subject', 7, 3)")
            it.version = 13
        }
    }

    @Test
    fun userDataSurvivesMigrationsFrom13() = runBlocking {
        createVersion13()
        val db = AppDatabase.create(context, name)
        try {
            val dao = db.studyDao()

            // Revision logs: pages become the ayahs that begin on them.
            val logs = dao.getAllRevisionLogsSync().sortedBy { it.id }
            assertEquals(3, logs.size)
            assertEquals(1, logs[0].startAyahId)
            assertEquals(7, logs[0].endAyahId)
            assertEquals(1000L, logs[0].timestamp)
            assertEquals(86_400_000L, logs[1].dateMillis)
            assertEquals(0.5f, logs[1].manualStability, 0f)
            // pages 600..700 clamp to 600..604, ending at the last ayah of the Quran
            assertEquals(6236, logs[2].endAyahId)

            // Recall outcomes come from the old slider values (0.9, 0.5, 0.3).
            assertEquals(listOf(1, 3, 4), logs.map { it.outcome })
            assertEquals(listOf(0, 0, 0), logs.map { it.mistakeTags })

            // The memorised coverage starts as the revised ayahs, merged.
            val coverage = dao.getAllCoverageSync()
            assertEquals(3, coverage.size)
            assertEquals(1, coverage.first().startAyahId)
            assertEquals(7, coverage.first().endAyahId)
            assertEquals(6236, coverage.last().endAyahId)

            // Bookmarks and weak passages start empty, and the table works.
            assertEquals(0, dao.getAllSavedPassagesSync().size)
            dao.upsertSavedPassage(
                com.example.mutqan.data.local.entity.SavedPassageEntity(kind = 1, startAyahId = 7, endAyahId = 7, createdAt = 1L)
            )
            assertEquals(7, dao.getSavedPassageAt(7, 0)?.startAyahId)

            // Revision plans start empty, and the table works.
            assertEquals(0, dao.getAllPlansSync().size)
            dao.upsertPlan(
                com.example.mutqan.data.local.entity.RevisionPlanEntity(name = "p", unit = 0, amountPerDay = 1, restDays = 0b1100000, createdAt = 1L)
            )
            assertEquals(0b1100000, dao.getAllPlansSync().single().restDays)
            // Strength tiers start at their defaults.
            assertEquals(listOf(40, 20, 8), dao.getAllPlansSync().single().let { listOf(it.strongDays, it.fairDays, it.weakDays) })

            // Exercise results start empty, and the table works.
            assertEquals(0, dao.getAllExerciseResultsSync().size)
            dao.insertExerciseResult(
                com.example.mutqan.data.local.entity.ExerciseResultEntity(type = 1, startPage = 5, endPage = 5, outcome = 1, timestamp = 1L)
            )
            assertEquals(5, dao.getAllExerciseResultsSync().single().startPage)

            // Everything else is kept.
            assertEquals(listOf(7, 262), dao.getAllSimilarityMembersSync().map { it.verseId }.sorted())
            assertEquals("group", dao.getAllSimilarityGroupsSync().single().description)
            assertEquals("Folder", dao.getAllFoldersSync().single().name)
            val subject = dao.getAllUserSubjectsSync().single()
            assertEquals("subject", subject.subjectText)
            assertEquals(7, subject.startAyahId)
            assertEquals(3, subject.orderIndex)

            // Quran tables are gone.
            val tables = db.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
            assertFalse(tables.any { it == "verses" || it.startsWith("verses_fts") || it == "structure_units" })
        } finally {
            db.close()
        }
    }
}
