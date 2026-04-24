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
import com.example.qmemo.data.local.entity.VerseEntity
import org.json.JSONArray

@Database(
    entities = [
        VerseEntity::class,
        RevisionLogEntity::class,
        SimilarityGroupEntity::class,
        SimilarityMemberEntity::class
    ],
    version = 3,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qmemo.db"
                )
                    .addCallback(PrepopulateCallback(context.applicationContext))
                    .addMigrations(MIGRATION_1_2)
                    // Early-dev: wipe and reseed when no migration path exists.
                    // Remove once the schema stabilises and write proper migrations.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }

    /**
     * Seeds the [verses] table synchronously on whichever thread Room opens
     * the database on (always an IO thread via Room's coroutine integration).
     *
     * Two overrides are needed:
     *  - [onCreate]              – first-time DB creation (fresh install)
     *  - [onDestructiveMigration] – schema bump with [fallbackToDestructiveMigration]
     *
     * Using the raw [SupportSQLiteDatabase] directly avoids any INSTANCE-null
     * race and keeps the data ready before the first user-facing DAO call returns.
     */
    private class PrepopulateCallback(private val context: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            seedVerses(context, db)
        }

        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            seedVerses(context, db)
        }
    }
}

private const val VERSES_ASSET = "quran_metadata.json"

/**
 * Reads [VERSES_ASSET] and bulk-inserts all 6,236 verse rows directly via
 * the [SupportSQLiteDatabase] handle that Room passes to the Callback.
 *
 * Running synchronously against the raw DB object (rather than through a DAO
 * coroutine) guarantees the data is committed before the first user-facing
 * query returns, eliminating the previous race condition.
 *
 * INSERT OR IGNORE makes repeat calls safe (e.g. if somehow called twice).
 */
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
