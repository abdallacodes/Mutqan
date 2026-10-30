package com.example.mutqan.quran

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.LruCache
import java.io.File

// ── Identifiers ───────────────────────────────────────────────────────────────
//
// Everything outside this module should refer to Quran text by these ids only:
//   surah  1..114
//   ayah   number within its surah, or the global [QuranAyah.id] 1..6236
//   word   global [QuranWord.id] 1..83,668 (the last word of each ayah is its end marker)
// Page numbers (1..604) belong to the KFGQPC V2 (1421H) layout this module ships.

data class QuranSurah(
    val id: Int,
    val nameArabic: String,
    val nameSimple: String,
    val nameComplex: String,
    val revelationPlace: String,
    val revelationOrder: Int,
    val versesCount: Int,
    val bismillahPre: Boolean
)

data class QuranAyah(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    /** Words excluding the end marker. */
    val wordsCount: Int,
    val firstWordId: Int,
    val lastWordId: Int,
    /** Hafs text, ending with the ayah number. */
    val text: String,
    val firstPage: Int,
    val lastPage: Int,
    val juz: Int,
    val hizb: Int,
    val rub: Int,
    val manzil: Int,
    val ruku: Int,
    /** "recommended" / "optional" for the 15 sajda ayahs, otherwise null. */
    val sajda: String?
)

data class QuranWord(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    /** 1-based position within the ayah. */
    val position: Int,
    /** QPC V2 glyph; only displays correctly in that page's font. */
    val glyph: String,
    /** Plain Hafs text, safe to search and copy. */
    val hafs: String
)

enum class MushafLineType { AYAH, SURAH_NAME, BASMALLAH }

/**
 * One of the (up to) 15 lines of a Mushaf page.
 * [words] holds the QPC V2 glyph of every word, first word first (right-most on screen);
 * it is empty for surah-name and basmallah lines, where [surahNameArabic] names the surah.
 */
data class MushafLine(
    val number: Int,
    val type: MushafLineType,
    val isCentered: Boolean,
    val words: List<String>,
    val surahNameArabic: String?
)

data class MushafPageData(val page: Int, val lines: List<MushafLine>)

/**
 * Read-only Quran content: surahs, ayahs, words, and the Mushaf page layout.
 * Knows nothing about revision, memory or user data.
 *
 * Backed by assets/quran.db (built by scripts/build_quran_db.py from the QUL KFGQPC V2
 * layout and glyph script), copied to internal storage once per [CONTENT_VERSION]; bump
 * the version whenever the asset is regenerated.
 *
 * Every query blocks on SQLite: call from a background dispatcher.
 */
class QuranRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val pageCache = LruCache<Int, MushafPageData>(PAGE_CACHE_SIZE)

    private val db: SQLiteDatabase by lazy { openDatabase() }

    // ── Surahs ────────────────────────────────────────────────────────────────

    fun surahs(): List<QuranSurah> =
        db.rawQuery("SELECT $SURAH_COLUMNS FROM surahs ORDER BY id", null).use { it.readAll(::surah) }

    fun surah(id: Int): QuranSurah? =
        db.rawQuery("SELECT $SURAH_COLUMNS FROM surahs WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) surah(c) else null }

    // ── Ayahs ─────────────────────────────────────────────────────────────────

    fun ayah(surah: Int, ayah: Int): QuranAyah? =
        db.rawQuery("SELECT $AYAH_COLUMNS FROM ayahs WHERE surah = ? AND ayah = ?", arrayOf(surah.toString(), ayah.toString()))
            .use { c -> if (c.moveToFirst()) ayah(c) else null }

    fun ayahById(id: Int): QuranAyah? =
        db.rawQuery("SELECT $AYAH_COLUMNS FROM ayahs WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) ayah(c) else null }

    fun ayahsOfSurah(surah: Int): List<QuranAyah> =
        db.rawQuery("SELECT $AYAH_COLUMNS FROM ayahs WHERE surah = ? ORDER BY ayah", arrayOf(surah.toString()))
            .use { it.readAll(::ayah) }

    /** Every ayah that has at least one word on [page], in reading order. */
    fun ayahsOnPage(page: Int): List<QuranAyah> =
        db.rawQuery(
            "SELECT $AYAH_COLUMNS FROM ayahs WHERE first_page <= ?1 AND last_page >= ?1 ORDER BY id",
            arrayOf(page.toString())
        ).use { it.readAll(::ayah) }

    /** Page on which the ayah starts, or null for an unknown ayah. */
    fun pageOf(surah: Int, ayah: Int): Int? = ayah(surah, ayah)?.firstPage

    // ── Pages ─────────────────────────────────────────────────────────────────

    /** Surah ids with text on [page], in reading order (a surah that only starts there counts). */
    fun surahsOnPage(page: Int): List<Int> =
        db.rawQuery(
            "SELECT DISTINCT surah FROM words WHERE page = ? ORDER BY id",
            arrayOf(page.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }

    /** The first surah on each page (1..604), the one a page is usually named after. */
    fun firstSurahByPage(): Map<Int, Int> =
        db.rawQuery(
            "SELECT page, surah FROM words WHERE id IN (SELECT MIN(id) FROM words GROUP BY page)",
            null
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) } }

    /** Lines of a page ready for [MushafPageView]. */
    fun page(page: Int): MushafPageData {
        pageCache.get(page)?.let { return it }
        val lines = db.rawQuery(
            """SELECT l.line, l.type, l.is_centered, l.first_word_id, l.last_word_id, s.name_arabic
               FROM lines l LEFT JOIN surahs s ON s.id = l.surah
               WHERE l.page = ? ORDER BY l.line""",
            arrayOf(page.toString())
        ).use { c ->
            c.readAll {
                val type = when (it.getString(1)) {
                    "surah_name" -> MushafLineType.SURAH_NAME
                    "basmallah"  -> MushafLineType.BASMALLAH
                    else         -> MushafLineType.AYAH
                }
                MushafLine(
                    number          = it.getInt(0),
                    type            = type,
                    isCentered      = it.getInt(2) != 0,
                    words           = if (type == MushafLineType.AYAH) glyphs(it.getInt(3), it.getInt(4)) else emptyList(),
                    surahNameArabic = if (it.isNull(5)) null else it.getString(5)
                )
            }
        }
        return MushafPageData(page, lines).also { pageCache.put(page, it) }
    }

    // ── Words ─────────────────────────────────────────────────────────────────

    /** All words of an ayah including its end marker, in order. */
    fun wordsOfAyah(surah: Int, ayah: Int): List<QuranWord> =
        db.rawQuery(
            "SELECT id, surah, ayah, word, glyph, hafs FROM words WHERE surah = ? AND ayah = ? ORDER BY word",
            arrayOf(surah.toString(), ayah.toString())
        ).use { c ->
            c.readAll { QuranWord(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getString(4), it.getString(5)) }
        }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun glyphs(firstWordId: Int, lastWordId: Int): List<String> =
        db.rawQuery(
            "SELECT glyph FROM words WHERE id BETWEEN ? AND ? ORDER BY id",
            arrayOf(firstWordId.toString(), lastWordId.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun surah(c: Cursor) = QuranSurah(
        id = c.getInt(0), nameArabic = c.getString(1), nameSimple = c.getString(2),
        nameComplex = c.getString(3), revelationPlace = c.getString(4),
        revelationOrder = c.getInt(5), versesCount = c.getInt(6), bismillahPre = c.getInt(7) != 0
    )

    private fun ayah(c: Cursor) = QuranAyah(
        id = c.getInt(0), surah = c.getInt(1), ayah = c.getInt(2), wordsCount = c.getInt(3),
        firstWordId = c.getInt(4), lastWordId = c.getInt(5), text = c.getString(6),
        firstPage = c.getInt(7), lastPage = c.getInt(8), juz = c.getInt(9), hizb = c.getInt(10),
        rub = c.getInt(11), manzil = c.getInt(12), ruku = c.getInt(13),
        sajda = if (c.isNull(14)) null else c.getString(14)
    )

    private inline fun <T> Cursor.readAll(row: (Cursor) -> T): List<T> =
        buildList { while (moveToNext()) add(row(this@readAll)) }

    private fun openDatabase(): SQLiteDatabase {
        val dir = appContext.noBackupFilesDir
        val target = File(dir, "quran_v$CONTENT_VERSION.db")
        if (!target.exists() || target.length() == 0L) {
            dir.listFiles { f -> f.name.startsWith("quran_v") }?.forEach { it.delete() }
            val temp = File(dir, target.name + ".tmp")
            appContext.assets.open(ASSET_NAME).use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            check(temp.renameTo(target)) { "Could not install $ASSET_NAME" }
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    companion object {
        private const val ASSET_NAME = "quran.db"
        private const val CONTENT_VERSION = 1
        private const val PAGE_CACHE_SIZE = 12

        private const val SURAH_COLUMNS =
            "id, name_arabic, name_simple, name_complex, revelation_place, revelation_order, verses_count, bismillah_pre"
        private const val AYAH_COLUMNS =
            "id, surah, ayah, words_count, first_word_id, last_word_id, text, first_page, last_page, juz, hizb, rub, manzil, ruku, sajda"

        @Volatile private var instance: QuranRepository? = null

        fun getInstance(context: Context): QuranRepository =
            instance ?: synchronized(this) {
                instance ?: QuranRepository(context).also { instance = it }
            }
    }
}
