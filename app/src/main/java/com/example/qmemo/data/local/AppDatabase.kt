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
import org.json.JSONArray

@Database(
    entities = [
        VerseEntity::class,
        RevisionLogEntity::class,
        SimilarityGroupEntity::class,
        SimilarityMemberEntity::class,
        VaultFolderEntity::class
    ],
    version = 5,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qmemo.db"
                )
                    .addCallback(PrepopulateCallback(context.applicationContext))
                    .addMigrations(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }

    private class PrepopulateCallback(private val context: Context) : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON")
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
        db.execSQL(
            "INSERT OR IGNORE INTO verses (id, surah_id, ayah_number, page_number, juz_id, text_arabic) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(
                obj.getInt("id"),
                obj.getInt("surah_id"),
                obj.getInt("ayah_number"),
                obj.getInt("page_number"),
                obj.getInt("juz_id"),
                obj.optString("text_arabic", "")
            )
        )
    }
}
