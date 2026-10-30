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
