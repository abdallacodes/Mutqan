package com.example.mutqan.domain

import android.content.Context
import android.util.LruCache
import com.example.mutqan.quran.QuranRepository
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.InflaterInputStream
import android.database.sqlite.SQLiteDatabase

/** The bundled tafsirs. [id] is stored in `assets/tafsir.db`, so existing ids must never change. */
enum class TafsirSource(val id: Int) {
    /** Tafsir al-Saadi: the full commentary. */
    SAADI(1),

    /** Al-Mukhtasar fi Tafsir al-Quran: a short, clear paraphrase. */
    MUKHTASAR(2),

    /** Aysar al-Tafasir by Abu Bakr al-Jazairi: word meanings, the meaning of the verse and its lessons. */
    AYSAR(3),

    /** Al-I'rab al-Muyassar: the grammatical analysis, word by word. */
    IRAB(4);

    companion object {
        fun fromId(id: Int): TafsirSource = entries.firstOrNull { it.id == id } ?: SAADI
    }
}

/** What a tafsir says about verses [fromId]..[toId] (global ayah ids): scholars explain neighbouring verses together. */
class TafsirEntry(val fromId: Int, val toId: Int, val body: String) {
    val coversSeveral: Boolean get() = toId > fromId
}

/** A stretch of tafsir text: either the commentary itself or a quotation from the Quran. */
class TafsirRun(val text: String, val quote: Boolean)

/** One paragraph; a [heading] is a short label such as "Meaning of the verse:". */
class TafsirBlock(val runs: List<TafsirRun>, val heading: Boolean)

/** Turns the stored tafsir text (see `scripts/build_tafsir.py`) into paragraphs and quotations. */
object TafsirText {
    private const val QUOTE_OPEN = '\u0001'
    private const val QUOTE_CLOSE = '\u0002'
    private const val HEADING_MAX_LENGTH = 40

    fun parse(body: String): List<TafsirBlock> =
        body.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val runs = ArrayList<TafsirRun>()
            var quote = false
            val current = StringBuilder()
            fun flush() {
                if (current.isNotEmpty()) runs += TafsirRun(current.toString(), quote)
                current.clear()
            }
            for (c in line) {
                when (c) {
                    QUOTE_OPEN -> { flush(); quote = true }
                    QUOTE_CLOSE -> { flush(); quote = false }
                    else -> current.append(c)
                }
            }
            flush()
            val heading = runs.size == 1 && !runs[0].quote && runs[0].text.length <= HEADING_MAX_LENGTH && runs[0].text.endsWith(":")
            TafsirBlock(runs, heading)
        }
}

/**
 * The tafsir texts, kept in `assets/tafsir.db` as one compressed chunk per source and surah; the chunk of the last
 * surahs looked at stays in memory.
 */
class TafsirLibrary private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val quran = QuranRepository.getInstance(appContext)
    private val db: SQLiteDatabase by lazy { openDatabase() }
    private val chunks = LruCache<Int, List<TafsirEntry>>(CACHED_CHUNKS)

    /** What [source] says about the verse with global id [ayahId], or null when it has no text for it. */
    fun entryFor(source: TafsirSource, ayahId: Int): TafsirEntry? {
        if (ayahId !in quran.ayahIndex) return null
        val surah = quran.ayahIndex.surahOf(ayahId)
        return chunk(source, surah).firstOrNull { ayahId in it.fromId..it.toId }
    }

    private fun chunk(source: TafsirSource, surah: Int): List<TafsirEntry> {
        val key = source.id * 1000 + surah
        chunks.get(key)?.let { return it }
        val entries = db.rawQuery("SELECT data FROM chunks WHERE source = ? AND surah = ?", arrayOf(source.id.toString(), surah.toString())).use { c ->
            if (!c.moveToFirst()) return@use emptyList()
            val json = InflaterInputStream(ByteArrayInputStream(c.getBlob(0))).use { it.readBytes() }.decodeToString()
            val array = JSONArray(json)
            List(array.length()) { i ->
                val e = array.getJSONArray(i)
                TafsirEntry(e.getInt(0), e.getInt(1), e.getString(2))
            }
        }
        chunks.put(key, entries)
        return entries
    }

    private fun openDatabase(): SQLiteDatabase {
        val dir = appContext.noBackupFilesDir
        val target = File(dir, "tafsir_v$CONTENT_VERSION.db")
        if (!target.exists() || target.length() == 0L) {
            dir.listFiles { f -> f.name.startsWith("tafsir_v") }?.forEach { it.delete() }
            val temp = File(dir, target.name + ".tmp")
            appContext.assets.open(ASSET_NAME).use { input -> temp.outputStream().use { input.copyTo(it) } }
            check(temp.renameTo(target)) { "Could not install $ASSET_NAME" }
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    companion object {
        private const val ASSET_NAME = "tafsir.db"
        private const val CONTENT_VERSION = 1
        private const val CACHED_CHUNKS = 6

        @Volatile
        private var instance: TafsirLibrary? = null

        fun get(context: Context): TafsirLibrary =
            instance ?: synchronized(this) { instance ?: TafsirLibrary(context).also { instance = it } }
    }
}
