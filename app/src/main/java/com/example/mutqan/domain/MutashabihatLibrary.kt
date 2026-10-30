package com.example.mutqan.domain

import android.content.Context
import com.example.mutqan.quran.ArabicNormalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One ayah that contains a shared phrase; the word range is 1-based and inclusive. */
data class PhraseVerse(val ayahId: Int, val firstWord: Int, val lastWord: Int)

/** A phrase that is repeated across the Quran, with every ayah it appears in. */
data class ReadyPhrase(
    val id: Int,
    val text: String,
    val verses: List<PhraseVerse>
) {
    /** Search form of [text]: no diacritics, unified letter shapes. */
    val searchText: String = ArabicNormalization.normalizeForSearch(text)
}

/**
 * The bundled, ready-made list of repeated phrases (mutashabihat), built by
 * `scripts/build_mutashabihat_phrases.py` into `assets/mutashabihat_phrases.json`.
 * Ordered by how many ayahs share the phrase, most first.
 */
object MutashabihatLibrary {
    private const val ASSET = "mutashabihat_phrases.json"

    @Volatile
    private var cache: List<ReadyPhrase>? = null

    suspend fun phrases(context: Context): List<ReadyPhrase> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(ASSET).use { it.readBytes().decodeToString() }
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val v = o.getJSONArray("v")
                ReadyPhrase(
                    id = o.getInt("id"),
                    text = o.getString("t"),
                    verses = List(v.length()) { j ->
                        val r = v.getJSONArray(j)
                        PhraseVerse(r.getInt(0), r.getInt(1), r.getInt(2))
                    }
                )
            }.also { cache = it }
        }
    }
}

/** An ayah with the word ranges (1-based, inclusive) that match its neighbours; empty when QUL gives none. */
data class GroupMember(val ayahId: Int, val matchedWords: List<IntRange>)

/** A ready-made group of ayahs that resemble each other; [description] names it in the vault. */
data class ReadyGroup(val id: Int, val description: String, val members: List<GroupMember>)

/** An ayah that resembles another: QUL's score (50..100) and, when known, the matching words in [ayahId]. */
data class SimilarMatch(val ayahId: Int, val score: Int, val matchedWords: List<IntRange>)

class SimilarData(
    val groups: List<ReadyGroup>,
    private val byAyah: Map<Int, List<SimilarMatch>>
) {
    /** Ayahs that resemble [ayahId], best match first. */
    fun similarTo(ayahId: Int): List<SimilarMatch> = byAyah[ayahId].orEmpty()
}

/**
 * The bundled "similar ayahs" data (`assets/mutashabihat_similar.json`, built by
 * `scripts/build_mutashabihat_similar.py`): ready-made groups, and the lookalikes of each ayah.
 */
object SimilarLibrary {
    private const val ASSET = "mutashabihat_similar.json"

    @Volatile
    private var cache: SimilarData? = null

    suspend fun load(context: Context): SimilarData {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(ASSET).use { it.readBytes().decodeToString() }
            val root = org.json.JSONObject(json)

            val g = root.getJSONArray("groups")
            val groups = List(g.length()) { i ->
                val o = g.getJSONObject(i)
                val ranges = HashMap<Int, List<IntRange>>()
                val w = o.getJSONArray("w")
                for (j in 0 until w.length()) {
                    val r = w.getJSONArray(j)
                    ranges[r.getInt(0)] = pairs(r, from = 1)
                }
                val m = o.getJSONArray("m")
                ReadyGroup(
                    id = o.getInt("id"),
                    description = o.getString("d"),
                    members = List(m.length()) { k -> m.getInt(k).let { GroupMember(it, ranges[it].orEmpty()) } }
                )
            }

            val a = root.getJSONObject("ayahs")
            val byAyah = HashMap<Int, List<SimilarMatch>>(a.length() * 2)
            for (key in a.keys()) {
                val list = a.getJSONArray(key)
                byAyah[key.toInt()] = List(list.length()) { i ->
                    val r = list.getJSONArray(i)
                    SimilarMatch(r.getInt(0), r.getInt(1), pairs(r, from = 2))
                }
            }
            SimilarData(groups, byAyah).also { cache = it }
        }
    }

    /** Reads [from], [from]+1 ... of [array] as (first, last) word pairs. */
    private fun pairs(array: JSONArray, from: Int): List<IntRange> =
        (from until array.length() step 2).map { array.getInt(it)..array.getInt(it + 1) }
}
