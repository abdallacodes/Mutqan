package com.example.qmemo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.RevisionLogEntity
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import com.example.qmemo.data.local.entity.SimilarityMemberEntity
import com.example.qmemo.data.local.entity.VaultFolderEntity
import com.example.qmemo.data.local.entity.VerseEntity
import com.example.qmemo.data.local.entity.VerseFtsEntity
import org.json.JSONArray

@Database(
    entities = [
        VerseEntity::class,
        RevisionLogEntity::class,
        SimilarityGroupEntity::class,
        SimilarityMemberEntity::class,
        VaultFolderEntity::class,
        VerseFtsEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun quranDao(): QuranDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds date_millis column to revision_logs (existing rows default to 0). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE revision_logs ADD COLUMN date_millis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Adds optional memorization notes on mutashabihat groups. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE similarity_groups ADD COLUMN memorization_notes TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** Adds vault_folders table and links similarity_groups to it. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vault_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `parent_id` INTEGER DEFAULT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`parent_id`) REFERENCES `vault_folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_vault_folders_name_parent_id` ON `vault_folders` (`name`, `parent_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vault_folders_parent_id` ON `vault_folders` (`parent_id`)"
                )
                db.execSQL(
                    "ALTER TABLE similarity_groups ADD COLUMN folder_id INTEGER DEFAULT NULL"
                )
            }
        }

        /** Adds normalized_content to verses and creates FTS table. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE verses ADD COLUMN normalized_content TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `verses_fts` USING fts4(content=`verses`, `normalized_content`)"
                )
            }
        }
        
        /** Refines normalized_content logic. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Population handled in onOpen
            }
        }

        /** Second refinement of normalized_content logic (Hamza-Alif symmetry). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Population handled in onOpen
            }
        }

        /** Refined normalized_content logic for الصلوات and ءاتيناهم. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Population handled in onOpen
            }
        }

        /** Adds manual_stability column to revision_logs. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE revision_logs ADD COLUMN manual_stability REAL"
                )
            }
        }

        // Migration 10-11 is destructive, so we rely on fallbackToDestructiveMigration()

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qmemo.db"
                )
                    .addCallback(PrepopulateCallback(context.applicationContext))
                    .addMigrations(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    // Early-dev: wipe and reseed when no migration path exists.
                    // Remove once the schema stabilises and write proper migrations.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }

    private class PrepopulateCallback(private val context: Context) : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON")
            // Ensure FTS is synced if we just migrated
            seedVerses(context, db)
            db.execSQL("INSERT INTO verses_fts(verses_fts) VALUES('rebuild')")
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL("PRAGMA foreign_keys = ON")
            seedVerses(context, db)
        }

        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            db.execSQL("PRAGMA foreign_keys = ON")
            seedVerses(context, db)
        }
    }
}

private const val VERSES_ASSET = "quran_metadata.json"

private fun seedVerses(context: Context, db: SupportSQLiteDatabase) {
    val json = context.assets.open(VERSES_ASSET).bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        val textArabic = obj.optString("text_arabic", "")
        val normalized = ArabicNormalization.normalizeForSearch(textArabic)
        
        db.execSQL(
            "INSERT OR REPLACE INTO verses (id, surah_id, ayah_number, page_number, juz_id, text_arabic, normalized_content) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                obj.getInt("id"),
                obj.getInt("surah_id"),
                obj.getInt("ayah_number"),
                obj.getInt("page_number"),
                obj.getInt("juz_id"),
                textArabic,
                normalized
            )
        )
    }
}
