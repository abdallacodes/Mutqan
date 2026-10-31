package com.example.mutqan.domain

import android.content.Context
import com.example.mutqan.quran.ArabicNormalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One word of the Quran with its root and lemma ids (-1 when it has none, e.g. particles). */
data class MorphWord(val position: Int, val root: Int, val lemma: Int)

/** Where a word sits: surah, ayah and 1-based position in the ayah (end marker excluded). */
data class WordLocation(val surah: Int, val ayah: Int, val position: Int, val lemma: Int)

/** How common a root is: its words, and the verses they stand in. */
data class RootInfo(val words: Int, val verses: Int)

/**
 * Roots and lemmas of every Quran word, from QUL's morphology data
 * (`assets/morphology.json`, built by `scripts/build_morphology.py`). Entries are parallel arrays sorted by
 * location, `surah * 1_000_000 + ayah * 1000 + position`.
 */
class Morphology(
    private val roots: List<String>,
    private val lemmas: List<String>,
    private val loc: IntArray,
    private val rootOfEntry: IntArray,
    private val lemmaOfEntry: IntArray
) {
    /** The letters of a root joined: "كتب". */
    fun rootLetters(root: Int): String = roots[root]

    /** The letters of a root spaced out for display: "ك ت ب". */
    fun rootSpaced(root: Int): String = roots[root].toList().joinToString(" ")

    fun lemmaText(lemma: Int): String = lemmas[lemma]

    private fun firstIndexAtOrAfter(code: Int): Int {
        var lo = 0
        var hi = loc.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (loc[mid] < code) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The words of an ayah that have a root or lemma, in order. */
    fun wordsOfAyah(surah: Int, ayah: Int): List<MorphWord> {
        val base = surah * 1_000_000 + ayah * 1000
        val result = ArrayList<MorphWord>()
        var i = firstIndexAtOrAfter(base + 1)
        while (i < loc.size && loc[i] <= base + 999) {
            result += MorphWord(loc[i] - base, rootOfEntry[i], lemmaOfEntry[i])
            i++
        }
        return result
    }

    private fun locationAt(i: Int): WordLocation =
        WordLocation(loc[i] / 1_000_000, loc[i] / 1000 % 1000, loc[i] % 1000, lemmaOfEntry[i])

    private val entriesByRoot: Map<Int, IntArray> by lazy {
        val lists = HashMap<Int, MutableList<Int>>()
        for (i in loc.indices) if (rootOfEntry[i] >= 0) lists.getOrPut(rootOfEntry[i]) { ArrayList() } += i
        lists.mapValues { it.value.toIntArray() }
    }

    private val entriesByLemma: Map<Int, IntArray> by lazy {
        val lists = HashMap<Int, MutableList<Int>>()
        for (i in loc.indices) if (lemmaOfEntry[i] >= 0) lists.getOrPut(lemmaOfEntry[i]) { ArrayList() } += i
        lists.mapValues { it.value.toIntArray() }
    }

    /** Every word that has [root], in reading order. */
    fun occurrencesOfRoot(root: Int): List<WordLocation> = entriesByRoot[root]?.map(::locationAt).orEmpty()

    /** Every word that has [lemma], in reading order. */
    fun occurrencesOfLemma(lemma: Int): List<WordLocation> = entriesByLemma[lemma]?.map(::locationAt).orEmpty()

    fun infoOfRoot(root: Int): RootInfo {
        val places = occurrencesOfRoot(root)
        return RootInfo(places.size, places.map { it.surah * 1000 + it.ayah }.toSet().size)
    }

    private val rootsByKey: Map<String, List<Int>> by lazy {
        roots.indices.groupBy { key(roots[it]) }
    }

    /** Roots whose letters are exactly what was typed, ignoring spaces, diacritics and alef/hamza shapes. */
    fun findRoots(query: String): List<Int> = rootsByKey[key(query)].orEmpty()

    private fun key(text: String): String = ArabicNormalization.normalizeForSearch(text.filterNot { it.isWhitespace() })
}

/** Loads `morphology.json` once. */
object MorphologyLibrary {
    private const val ASSET = "morphology.json"

    @Volatile
    private var cache: Morphology? = null

    suspend fun load(context: Context): Morphology {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(ASSET).use { it.readBytes().decodeToString() }
            val o = JSONObject(json)
            fun ints(name: String): IntArray = o.getJSONArray(name).let { a -> IntArray(a.length()) { a.getInt(it) } }
            fun strings(name: String): List<String> = o.getJSONArray(name).let { a -> List(a.length()) { a.getString(it) } }
            Morphology(strings("roots"), strings("lemmas"), ints("loc"), ints("r"), ints("l")).also { cache = it }
        }
    }
}
