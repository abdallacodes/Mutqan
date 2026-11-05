package com.example.mutqan.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What a passage is about, in one line: the ayahs [first]..[last] (global ayah ids) and the [text], which is English. */
class AyahTheme(val first: Int, val last: Int, val text: String) {
    val ayahs: IntRange get() = first..last
}

/**
 * The passages of the Quran and what each is about (`assets/ayah_themes.json`, built by `scripts/build_ayah_themes.py`).
 * The passages do not overlap and are in reading order; a few ayahs lie between two and have no theme.
 */
class AyahThemeIndex(val themes: List<AyahTheme>) {

    /** The passage that holds [ayahId], or null where QUL names none. */
    fun themeOf(ayahId: Int): AyahTheme? {
        var low = 0
        var high = themes.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val theme = themes[mid]
            when {
                ayahId < theme.first -> high = mid - 1
                ayahId > theme.last -> low = mid + 1
                else -> return theme
            }
        }
        return null
    }

    /** The passages whose text contains every word of [query], ignoring case, in reading order. */
    fun search(query: String): List<AyahTheme> {
        val words = query.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return themes
        return themes.filter { theme -> words.all { theme.text.contains(it, ignoreCase = true) } }
    }
}

/** Loads `ayah_themes.json` once. */
object AyahThemesLibrary {
    private const val ASSET = "ayah_themes.json"

    @Volatile
    private var cache: AyahThemeIndex? = null

    suspend fun load(context: Context): AyahThemeIndex {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open(ASSET).use { it.readBytes().decodeToString() }
            val array = JSONObject(json).getJSONArray("t")
            AyahThemeIndex(
                List(array.length()) { i ->
                    val o = array.getJSONArray(i)
                    AyahTheme(o.getInt(0), o.getInt(1), o.getString(2))
                }
            ).also { cache = it }
        }
    }
}
