package com.example.mutqan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mutqan.data.local.dao.StudyDao
import com.example.mutqan.data.local.entity.HifzCoverageEntity
import com.example.mutqan.data.local.entity.RevisionLogEntity
import com.example.mutqan.data.local.entity.RevisionPlanEntity
import com.example.mutqan.data.local.entity.SavedPassageEntity
import com.example.mutqan.data.local.entity.SimilarityGroupEntity
import com.example.mutqan.data.local.entity.SimilarityMemberEntity
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.data.local.entity.VaultFolderEntity
import com.example.mutqan.domain.HifzCoverage
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.quran.QuranRepository

/**
 * The user's own data only. Quran text, pages, juz and hizb quarters live in the `:quran`
 * module; this database refers to them by ayah id.
 */
@Database(
    entities = [
        RevisionLogEntity::class,
        SimilarityGroupEntity::class,
        SimilarityMemberEntity::class,
        VaultFolderEntity::class,
        UserSubjectEntity::class,
        HifzCoverageEntity::class,
        SavedPassageEntity::class,
        RevisionPlanEntity::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Quran text moved to the `:quran` module: drops `verses`, its FTS index and
         * `structure_units`, and rebuilds the two tables that had foreign keys into them.
         * Group members keep their ayah ids; a subject now belongs to the hizb quarter that
         * contains its start ayah, so `unit_id` is gone.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `verses_fts`")
                db.execSQL("DROP TABLE IF EXISTS `verses`")

                db.execSQL(
                    "CREATE TABLE `similarity_members_new` (`group_id` INTEGER NOT NULL, `verse_id` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`group_id`, `verse_id`), " +
                        "FOREIGN KEY(`group_id`) REFERENCES `similarity_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("INSERT INTO `similarity_members_new` SELECT `group_id`, `verse_id` FROM `similarity_members`")
                db.execSQL("DROP TABLE `similarity_members`")
                db.execSQL("ALTER TABLE `similarity_members_new` RENAME TO `similarity_members`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_similarity_members_group_id` ON `similarity_members` (`group_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_similarity_members_verse_id` ON `similarity_members` (`verse_id`)")

                db.execSQL(
                    "CREATE TABLE `user_subjects_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subject_text` TEXT NOT NULL, `start_ayah_id` INTEGER NOT NULL, `order_index` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `user_subjects_new` (`id`, `subject_text`, `start_ayah_id`, `order_index`) " +
                        "SELECT `id`, `subject_text`, `start_ayah_id`, `order_index` FROM `user_subjects`"
                )
                db.execSQL("DROP TABLE `user_subjects`")
                db.execSQL("ALTER TABLE `user_subjects_new` RENAME TO `user_subjects`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_subjects_start_ayah_id` ON `user_subjects` (`start_ayah_id`)")

                db.execSQL("DROP TABLE IF EXISTS `structure_units`")
            }
        }

        /**
         * Revision logs move from page numbers to ayah ranges. A log of pages p1..p2 becomes the
         * ayahs that begin on those pages (every page has one, so the pages come back exactly).
         * Rows whose pages match no ayah are dropped.
         */
        fun migration14to15(context: Context) = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val quran = QuranRepository.getInstance(context)
                db.execSQL(
                    "CREATE TABLE `revision_logs_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`start_ayah_id` INTEGER NOT NULL, `end_ayah_id` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`date_millis` INTEGER NOT NULL DEFAULT 0, `manual_stability` REAL NOT NULL)"
                )
                db.query("SELECT `id`, `start_page`, `end_page`, `timestamp`, `date_millis`, `manual_stability` FROM `revision_logs`").use { c ->
                    while (c.moveToNext()) {
                        val start = c.getInt(1).coerceIn(1, 604)
                        val end = c.getInt(2).coerceIn(start, 604)
                        val range = quran.ayahIdRangeOfPages(start, end) ?: continue
                        db.execSQL(
                            "INSERT INTO `revision_logs_new` (`id`, `start_ayah_id`, `end_ayah_id`, `timestamp`, `date_millis`, `manual_stability`) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                            arrayOf<Any>(c.getInt(0), range.first, range.last, c.getLong(3), c.getLong(4), if (c.isNull(5)) 0.5f else c.getFloat(5))
                        )
                    }
                }
                db.execSQL("DROP TABLE `revision_logs`")
                db.execSQL("ALTER TABLE `revision_logs_new` RENAME TO `revision_logs`")
            }
        }

        /**
         * Recall outcomes and the memorised-coverage table. Each old log gets the outcome closest
         * to its slider value (see [RecallOutcome.fromQuality]); the coverage starts as the
         * ayahs the user has already revised, so nothing they track disappears from the heatmap.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `revision_logs` ADD COLUMN `outcome` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `revision_logs` ADD COLUMN `mistake_tags` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `revision_logs` SET `outcome` = CASE " +
                        "WHEN `manual_stability` >= 0.89 THEN ${RecallOutcome.INDEPENDENT.code} " +
                        "WHEN `manual_stability` >= 0.69 THEN ${RecallOutcome.SELF_CORRECTED.code} " +
                        "WHEN `manual_stability` >= 0.39 THEN ${RecallOutcome.HINTED.code} " +
                        "ELSE ${RecallOutcome.UNABLE.code} END"
                )

                db.execSQL(
                    "CREATE TABLE `hifz_coverage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`start_ayah_id` INTEGER NOT NULL, `end_ayah_id` INTEGER NOT NULL)"
                )
                val revised = ArrayList<IntRange>()
                db.query("SELECT `start_ayah_id`, `end_ayah_id` FROM `revision_logs`").use { c ->
                    while (c.moveToNext()) revised.add(c.getInt(0)..c.getInt(1))
                }
                for (range in HifzCoverage.merge(revised)) {
                    db.execSQL(
                        "INSERT INTO `hifz_coverage` (`start_ayah_id`, `end_ayah_id`) VALUES (?, ?)",
                        arrayOf<Any>(range.first, range.last)
                    )
                }
            }
        }

        /** Revision plans. */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `revision_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `unit` INTEGER NOT NULL, `amount_per_day` INTEGER NOT NULL, " +
                        "`rest_days` INTEGER NOT NULL DEFAULT 0, `plan_order` INTEGER NOT NULL DEFAULT 0, " +
                        "`paused` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL)"
                )
            }
        }

        /** Bookmarks and weak passages. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `saved_passages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` INTEGER NOT NULL, `start_ayah_id` INTEGER NOT NULL, `end_ayah_id` INTEGER NOT NULL, " +
                        "`word_id` INTEGER NOT NULL DEFAULT 0, `tags` INTEGER NOT NULL DEFAULT 0, " +
                        "`priority` INTEGER NOT NULL DEFAULT 2, `note` TEXT NOT NULL DEFAULT '', " +
                        "`created_at` INTEGER NOT NULL, `resolved_at` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_passages_start_ayah_id` ON `saved_passages` (`start_ayah_id`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context, "mutqan.db").also { INSTANCE = it }
            }

        /** A database file of the given name with the app's migrations; tests use their own name. */
        internal fun create(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addCallback(ForeignKeysCallback())
                .addMigrations(MIGRATION_13_14, migration14to15(context.applicationContext), MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                // Versions 1..12 never had a migration path (see git history); start them fresh.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .build()
    }

    private class ForeignKeysCallback : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON")
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
