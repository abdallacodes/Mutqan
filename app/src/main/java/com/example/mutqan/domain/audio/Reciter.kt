package com.example.mutqan.domain.audio

import android.content.Context
import androidx.annotation.StringRes
import com.example.mutqan.R
import org.json.JSONObject

/**
 * The reciters whose recordings the app can play. The first three have one file per verse under [verseUrlBase];
 * Ali Jaber has one file per surah, and the time each verse starts and ends in it (`assets/recitation/ali_jaber.json`).
 */
enum class Reciter(val id: String, @StringRes val nameRes: Int, val verseUrlBase: String?) {
    HUSARY("husary", R.string.reciter_husary, "https://audio-cdn.tarteel.ai/quran/husaryMuallim/"),
    MINSHAWI("minshawi", R.string.reciter_minshawi, "https://audio-cdn.tarteel.ai/quran/minshawyMurattal/"),
    SHATRI("shatri", R.string.reciter_shatri, "https://audio-cdn.tarteel.ai/quran/abuBakrAlShatri/"),
    ALI_JABER("ali_jaber", R.string.reciter_ali_jaber, null);

    companion object {
        fun fromId(id: String): Reciter = entries.firstOrNull { it.id == id } ?: HUSARY
    }
}

/** Where one verse's sound is: a file, and the part of it to play ([endMs] is -1 for the whole file). */
class AudioClip(val url: String, val startMs: Long = 0L, val endMs: Long = -1L)

/** Finds the audio of a verse for a reciter. */
class RecitationSources(private val context: Context) {

    private class Surahs(val urlPattern: String, val times: Map<Int, List<LongArray>>)

    private val aliJaber: Surahs by lazy {
        val root = JSONObject(context.assets.open("recitation/ali_jaber.json").bufferedReader().use { it.readText() })
        val t = root.getJSONObject("t")
        val times = HashMap<Int, List<LongArray>>()
        for (key in t.keys()) {
            val arr = t.getJSONArray(key)
            times[key.toInt()] = List(arr.length()) { i -> arr.getJSONArray(i).let { longArrayOf(it.getLong(0), it.getLong(1)) } }
        }
        Surahs(root.getString("url"), times)
    }

    /** The clip of [surah]:[ayah] (1-based within the surah), or null when this reciter has none. */
    fun clip(reciter: Reciter, surah: Int, ayah: Int): AudioClip? {
        val base = reciter.verseUrlBase
        if (base != null) return AudioClip("$base%03d%03d.mp3".format(surah, ayah))
        val window = aliJaber.times[surah]?.getOrNull(ayah - 1) ?: return null
        return AudioClip(aliJaber.urlPattern.format(surah), window[0], window[1])
    }
}

/** Which verses a recitation covers. */
object RecitationRange {

    /**
     * The verses to play when starting at [start]: through the end of the page the starting verse is on (or [page],
     * if later), or to the end of the surah when [toSurahEnd]. [total] is the number of verses in the Quran.
     */
    fun from(
        start: Int,
        page: Int,
        toSurahEnd: Boolean,
        total: Int,
        surahOf: (Int) -> Int,
        firstPageOf: (Int) -> Int
    ): List<Int> {
        if (start !in 1..total) return emptyList()
        val surah = surahOf(start)
        val pageLimit = maxOf(page, firstPageOf(start))
        val out = ArrayList<Int>()
        var id = start
        while (id <= total) {
            val inRange = if (toSurahEnd) surahOf(id) == surah else firstPageOf(id) <= pageLimit
            if (!inRange) break
            out += id
            id++
        }
        return out
    }
}
