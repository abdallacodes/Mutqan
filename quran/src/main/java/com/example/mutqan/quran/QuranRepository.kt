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
//   rub    hizb quarter 1..240 ([QuranRub.number]); 8 per juz
// Page numbers (1..604) belong to the KFGQPC V2 (1421H) layout this module ships.

data class QuranSurah(
    val id: Int,
    val nameArabic: String,
    val nameSimple: String,
    val nameComplex: String,
    val revelationPlace: String,
    val revelationOrder: Int,
    val versesCount: Int,
    val bismillahPre: Boolean,
    /** Page on which the surah starts. */
    val firstPage: Int,
    /** Juz in which the surah starts. */
    val startJuz: Int
)

data class QuranAyah(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    /** Words excluding the end marker. */
    val wordsCount: Int,
    val firstWordId: Int,
    val lastWordId: Int,
    /** Plain Hafs text, without the ayah number. */
    val text: String,
    /** Page on which the ayah starts; an ayah may continue onto [lastPage]. */
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
    val hafs: String,
    /** True for the ayah-number marker that closes every ayah. */
    val isEnd: Boolean
)

/** A hizb quarter: the 1/240 division of the Quran. */
data class QuranRub(
    /** 1..240 */
    val number: Int,
    val juz: Int,
    /** 1..60 */
    val hizb: Int,
    /** 1..8 within its juz. */
    val quarterInJuz: Int,
    val startAyahId: Int,
    val endAyahId: Int,
    val startPage: Int,
    val endPage: Int
)

/**
 * Compact id -> (surah, ayah, pages, juz) table for all 6,236 ayahs, for code that has to
 * resolve many ayah ids without touching SQLite (e.g. enriching user data).
 */
class AyahIndex internal constructor(
    private val surah: IntArray,
    private val ayah: IntArray,
    private val firstPage: IntArray,
    private val lastPage: IntArray,
    private val juz: IntArray
) {
    operator fun contains(id: Int) = id in 1 until surah.size && surah[id] != 0
    fun surahOf(id: Int) = surah[id]
    fun ayahOf(id: Int) = ayah[id]
    fun firstPageOf(id: Int) = firstPage[id]
    fun lastPageOf(id: Int) = lastPage[id]
    fun juzOf(id: Int) = juz[id]
}

enum class MushafLineType { AYAH, SURAH_NAME, BASMALLAH }

/**
 * One of the (up to) 15 lines of a Mushaf page.
 * [words] holds the QPC V2 glyph of every word, first word first (right-most on screen);
 * it is empty for surah-name and basmallah lines, where [surahNameArabic] names the surah.
 * [wordIds] and [ayahIds] run parallel to [words]: the global word id and the global ayah id
 * (`QuranAyah.id`) of each word.
 */
data class MushafLine(
    val number: Int,
    val type: MushafLineType,
    val isCentered: Boolean,
    val words: List<String>,
    val surahNameArabic: String?,
    val wordIds: List<Int> = emptyList(),
    val ayahIds: List<Int> = emptyList()
)

data class MushafPageData(val page: Int, val lines: List<MushafLine>)

/**
 * Read-only Quran content: surahs, ayahs, words, hizb quarters, search, and the Mushaf
 * page layout. Knows nothing about revision, memory or user data.
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
        db.rawQuery("$SURAH_SELECT ORDER BY s.id", null).use { it.readAll(::surah) }

    fun surah(id: Int): QuranSurah? =
        db.rawQuery("$SURAH_SELECT WHERE s.id = ?", arrayOf(id.toString()))
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

    /** Ayahs with ids in [firstId]..[lastId], in order. */
    fun ayahsInRange(firstId: Int, lastId: Int): List<QuranAyah> =
        db.rawQuery(
            "SELECT $AYAH_COLUMNS FROM ayahs WHERE id BETWEEN ? AND ? ORDER BY id",
            arrayOf(firstId.toString(), lastId.toString())
        ).use { it.readAll(::ayah) }

    /** Every ayah that has at least one word on [page], in reading order. */
    fun ayahsOnPage(page: Int): List<QuranAyah> =
        db.rawQuery(
            "SELECT $AYAH_COLUMNS FROM ayahs WHERE first_page <= ?1 AND last_page >= ?1 ORDER BY id",
            arrayOf(page.toString())
        ).use { it.readAll(::ayah) }

    /** Page on which the ayah starts, or null for an unknown ayah. */
    fun pageOf(surah: Int, ayah: Int): Int? = ayah(surah, ayah)?.firstPage

    fun randomAyahInJuz(juz: Int): QuranAyah? = randomAyah("juz = ?", juz)

    fun randomAyahInSurah(surah: Int): QuranAyah? = randomAyah("surah = ?", surah)

    /** A random ayah that starts on a page in [startPage]..[endPage]. */
    fun randomAyahInPages(startPage: Int, endPage: Int): QuranAyah? = randomAyah("first_page BETWEEN ? AND ?", startPage, endPage)

    private fun randomAyah(where: String, vararg args: Int): QuranAyah? =
        db.rawQuery(
            "SELECT $AYAH_COLUMNS FROM ayahs WHERE $where ORDER BY RANDOM() LIMIT 1",
            Array(args.size) { args[it].toString() }
        ).use { c -> if (c.moveToFirst()) ayah(c) else null }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Ayahs whose text contains [query], ignoring diacritics and common spelling variants
     * (see [ArabicNormalization]), optionally limited to one surah or a juz range.
     * The first call builds an in-memory index of the normalized text (a few hundred ms).
     */
    fun search(
        query: String,
        surah: Int? = null,
        juzStart: Int? = null,
        juzEnd: Int? = null,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): List<QuranAyah> {
        val needle = ArabicNormalization.normalizeForSearch(query)
        if (needle.isBlank()) return emptyList()
        val matches = ArrayList<Int>()
        for (entry in searchIndex) {
            if (surah != null && entry.surah != surah) continue
            if (juzStart != null && entry.juz < juzStart) continue
            if (juzEnd != null && entry.juz > juzEnd) continue
            if (entry.normalized.contains(needle)) {
                matches.add(entry.id)
                if (matches.size >= limit) break
            }
        }
        return matches.mapNotNull(::ayahById)
    }

    private class SearchEntry(val id: Int, val surah: Int, val juz: Int, val normalized: String)

    private val searchIndex: List<SearchEntry> by lazy {
        db.rawQuery("SELECT id, surah, juz, text FROM ayahs ORDER BY id", null).use { c ->
            c.readAll { SearchEntry(it.getInt(0), it.getInt(1), it.getInt(2), ArabicNormalization.normalizeForSearch(it.getString(3))) }
        }
    }

    // ── Juz, pages and hizb quarters ──────────────────────────────────────────

    /** Pages (first..last) that hold text of [juz]; boundary pages belong to both neighbouring juz. */
    fun pagesOfJuz(juz: Int): List<Int> =
        db.rawQuery("SELECT MIN(first_page), MAX(last_page) FROM ayahs WHERE juz = ?", arrayOf(juz.toString()))
            .use { c -> if (c.moveToFirst() && !c.isNull(0)) (c.getInt(0)..c.getInt(1)).toList() else emptyList() }

    /** Juz number -> its pages, for all 30 juz. */
    fun pagesByJuz(): Map<Int, List<Int>> =
        db.rawQuery("SELECT juz, MIN(first_page), MAX(last_page) FROM ayahs GROUP BY juz ORDER BY juz", null)
            .use { c -> buildMap { while (c.moveToNext()) put(c.getInt(0), (c.getInt(1)..c.getInt(2)).toList()) } }

    /** For every page of [juz], the surahs that have text on it, in reading order. */
    fun surahsByPageInJuz(juz: Int): Map<Int, List<Int>> =
        db.rawQuery(
            """SELECT w.page, w.surah FROM words w
               JOIN ayahs a ON a.surah = w.surah AND a.ayah = w.ayah
               WHERE a.juz = ? GROUP BY w.page, w.surah ORDER BY w.page, MIN(w.id)""",
            arrayOf(juz.toString())
        ).use { c ->
            val result = LinkedHashMap<Int, MutableList<Int>>()
            while (c.moveToNext()) result.getOrPut(c.getInt(0)) { ArrayList() }.add(c.getInt(1))
            result
        }

    /** First and last surah that have ayahs in [juz]. */
    fun surahRangeOfJuz(juz: Int): IntRange? =
        db.rawQuery("SELECT MIN(surah), MAX(surah) FROM ayahs WHERE juz = ?", arrayOf(juz.toString()))
            .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0)..c.getInt(1) else null }

    /** Global ayah ids of the first and last ayah of [juz]. */
    fun ayahIdRangeOfJuz(juz: Int): IntRange? =
        db.rawQuery("SELECT MIN(id), MAX(id) FROM ayahs WHERE juz = ?", arrayOf(juz.toString()))
            .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0)..c.getInt(1) else null }

    /**
     * The ayahs that begin on pages [startPage]..[endPage] (inclusive), as global ids; null when
     * none do. Every Mushaf page has an ayah that starts on it, so
     * `firstPageOf(range.first)..firstPageOf(range.last)` gives the pages back exactly.
     */
    fun ayahIdRangeOfPages(startPage: Int, endPage: Int): IntRange? =
        db.rawQuery(
            "SELECT MIN(id), MAX(id) FROM ayahs WHERE first_page BETWEEN ? AND ?",
            arrayOf(startPage.toString(), endPage.toString())
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0)..c.getInt(1) else null }

    /** The eight hizb quarters of [juz]. */
    fun rubs(juz: Int): List<QuranRub> =
        db.rawQuery(
            """SELECT rub, juz, hizb, MIN(id), MAX(id), MIN(first_page), MAX(last_page)
               FROM ayahs WHERE juz = ? GROUP BY rub ORDER BY rub""",
            arrayOf(juz.toString())
        ).use { c ->
            c.readAll {
                QuranRub(
                    number = it.getInt(0), juz = it.getInt(1), hizb = it.getInt(2),
                    quarterInJuz = (it.getInt(0) - 1) % QUARTERS_PER_JUZ + 1,
                    startAyahId = it.getInt(3), endAyahId = it.getInt(4),
                    startPage = it.getInt(5), endPage = it.getInt(6)
                )
            }
        }

    /** Compact lookup of surah / ayah / pages / juz by global ayah id; built once, then cached. */
    val ayahIndex: AyahIndex by lazy {
        val size = TOTAL_AYAHS + 1
        val surah = IntArray(size); val ayah = IntArray(size)
        val first = IntArray(size); val last = IntArray(size); val juz = IntArray(size)
        db.rawQuery("SELECT id, surah, ayah, first_page, last_page, juz FROM ayahs", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getInt(0)
                surah[id] = c.getInt(1); ayah[id] = c.getInt(2)
                first[id] = c.getInt(3); last[id] = c.getInt(4); juz[id] = c.getInt(5)
            }
        }
        AyahIndex(surah, ayah, first, last, juz)
    }

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

    /** The plain Hafs words printed on [page], in order, without ayah-number markers. */
    fun pageWords(page: Int): List<String> =
        db.rawQuery(
            "SELECT hafs FROM words WHERE page = ? AND is_end = 0 ORDER BY id",
            arrayOf(page.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** Lines of a page ready for [MushafPageView]. */
    fun page(page: Int): MushafPageData {
        pageCache.get(page)?.let { return it }
        val ayahOfWord = ayahIdsOfWordsOnPage(page)
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
                val words = if (type == MushafLineType.AYAH) glyphs(it.getInt(3), it.getInt(4)) else emptyList()
                MushafLine(
                    number          = it.getInt(0),
                    type            = type,
                    isCentered      = it.getInt(2) != 0,
                    words           = words.map { w -> w.second },
                    surahNameArabic = if (it.isNull(5)) null else it.getString(5),
                    wordIds         = words.map { w -> w.first },
                    ayahIds         = words.map { w -> ayahOfWord[w.first] ?: 0 }
                )
            }
        }
        return MushafPageData(page, lines).also { pageCache.put(page, it) }
    }

    // ── Words ─────────────────────────────────────────────────────────────────

    /** All words of an ayah including its end marker, in order. */
    fun wordsOfAyah(surah: Int, ayah: Int): List<QuranWord> =
        db.rawQuery(
            "SELECT id, surah, ayah, word, glyph, hafs, is_end FROM words WHERE surah = ? AND ayah = ? ORDER BY word",
            arrayOf(surah.toString(), ayah.toString())
        ).use { c ->
            c.readAll {
                QuranWord(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getString(4), it.getString(5), it.getInt(6) != 0)
            }
        }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** (word id, QPC V2 glyph) of every word in the id range, in order. */
    private fun glyphs(firstWordId: Int, lastWordId: Int): List<Pair<Int, String>> =
        db.rawQuery(
            "SELECT id, glyph FROM words WHERE id BETWEEN ? AND ? ORDER BY id",
            arrayOf(firstWordId.toString(), lastWordId.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getInt(0) to c.getString(1)) } }

    private fun ayahIdsOfWordsOnPage(page: Int): Map<Int, Int> =
        db.rawQuery(
            "SELECT w.id, a.id FROM words w JOIN ayahs a ON a.surah = w.surah AND a.ayah = w.ayah WHERE w.page = ?",
            arrayOf(page.toString())
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) } }

    private fun surah(c: Cursor) = QuranSurah(
        id = c.getInt(0), nameArabic = c.getString(1), nameSimple = c.getString(2),
        nameComplex = c.getString(3), revelationPlace = c.getString(4),
        revelationOrder = c.getInt(5), versesCount = c.getInt(6), bismillahPre = c.getInt(7) != 0,
        firstPage = c.getInt(8), startJuz = c.getInt(9)
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
        private const val CONTENT_VERSION = 2
        private const val PAGE_CACHE_SIZE = 12
        private const val DEFAULT_SEARCH_LIMIT = 40
        private const val TOTAL_AYAHS = 6236
        private const val QUARTERS_PER_JUZ = 8

        private const val SURAH_SELECT =
            """SELECT s.id, s.name_arabic, s.name_simple, s.name_complex, s.revelation_place,
                      s.revelation_order, s.verses_count, s.bismillah_pre,
                      (SELECT MIN(first_page) FROM ayahs a WHERE a.surah = s.id),
                      (SELECT MIN(juz) FROM ayahs a WHERE a.surah = s.id)
               FROM surahs s"""
        private const val AYAH_COLUMNS =
            "id, surah, ayah, words_count, first_word_id, last_word_id, text, first_page, last_page, juz, hizb, rub, manzil, ruku, sajda"

        @Volatile private var instance: QuranRepository? = null

        fun getInstance(context: Context): QuranRepository =
            instance ?: synchronized(this) {
                instance ?: QuranRepository(context).also { instance = it }
            }
    }
}
