package com.example.mutqan.domain.audio

import android.content.Context
import androidx.annotation.StringRes
import com.example.mutqan.R
import org.json.JSONObject

/**
 * The reciters whose recordings the app can play. Those with a [verseUrlBase] have one file per verse under it; the
 * others have one file per surah, and the time each verse starts and ends in it (`assets/recitation/<id>.json`).
 */
enum class Reciter(val id: String, @StringRes val nameRes: Int, val verseUrlBase: String?) {
    HUSARY("husary", R.string.reciter_husary, "https://audio-cdn.tarteel.ai/quran/husary/"),
    HUSARY_MUALLIM("husary_muallim", R.string.reciter_husary_muallim, "https://audio-cdn.tarteel.ai/quran/husaryMuallim/"),
    HUSARY_MUJAWWAD("husary_mujawwad", R.string.reciter_husary_mujawwad, null),
    MINSHAWI("minshawi", R.string.reciter_minshawi, "https://audio-cdn.tarteel.ai/quran/minshawyMurattal/"),
    SHATRI("shatri", R.string.reciter_shatri, "https://audio-cdn.tarteel.ai/quran/abuBakrAlShatri/"),
    MUAIQLY("muaiqly", R.string.reciter_muaiqly, null),
    BASFAR("basfar", R.string.reciter_basfar, null),
    ALI_JABER("ali_jaber", R.string.reciter_ali_jaber, null);

    companion object {
        fun fromId(id: String): Reciter = entries.firstOrNull { it.id == id } ?: HUSARY
    }
}

/**
 * Where one verse's sound is: a file, and the part of it to play. [endMs] is [WHOLE] for the whole file, [TO_END]
 * for from [startMs] to the end of the file (the last verse of a surah), or the time the verse ends.
 */
class AudioClip(val url: String, val startMs: Long = 0L, val endMs: Long = WHOLE) {
    companion object {
        const val WHOLE = -1L
        const val TO_END = -2L
    }
}

/** Finds the audio of a verse for a reciter. */
class RecitationSources(private val context: Context) {

    private class Surahs(val urlPattern: String, val times: Map<Int, List<LongArray>>)

    private val timed = HashMap<Reciter, Surahs>()

    private fun timingsOf(reciter: Reciter): Surahs = synchronized(timed) { timed.getOrPut(reciter) { load(reciter) } }

    private fun load(reciter: Reciter): Surahs {
        val root = JSONObject(context.assets.open("recitation/${reciter.id}.json").bufferedReader().use { it.readText() })
        val t = root.getJSONObject("t")
        val times = HashMap<Int, List<LongArray>>()
        for (key in t.keys()) {
            val arr = t.getJSONArray(key)
            times[key.toInt()] = List(arr.length()) { i -> arr.getJSONArray(i).let { longArrayOf(it.getLong(0), it.getLong(1)) } }
        }
        return Surahs(root.getString("url"), times)
    }

    /** The clip of [surah]:[ayah] (1-based within the surah), or null when this reciter has none. */
    fun clip(reciter: Reciter, surah: Int, ayah: Int): AudioClip? {
        val base = reciter.verseUrlBase
        if (base != null) return AudioClip("$base%03d%03d.mp3".format(java.util.Locale.ROOT, surah, ayah))
        val surahs = timingsOf(reciter)
        val window = surahs.times[surah]?.getOrNull(ayah - 1) ?: return null
        return AudioClip(surahs.urlPattern.format(java.util.Locale.ROOT, surah), window[0].coerceAtLeast(0L), window[1])
    }
}

/**
 * What a recitation was asked to cover. It is kept while the recitation plays so that, when a setting changes, the
 * run can be worked out again from the verse sounding: a run asked for "to the end of the page" must grow when the
 * reader then turns on "to the end of the surah", and the other way round.
 */
sealed interface RecitationRun {

    /** The verses to play when starting at [from], with the reader's present choice of [toSurahEnd]. */
    fun verses(
        from: Int,
        toSurahEnd: Boolean,
        total: Int,
        surahOf: (Int) -> Int,
        firstPageOf: (Int) -> Int
    ): List<Int>

    /** From a verse to the end of its page ([page] is the one it was asked on), or of the surah when the reader chose that. */
    data class Stretch(val page: Int) : RecitationRun {
        override fun verses(from: Int, toSurahEnd: Boolean, total: Int, surahOf: (Int) -> Int, firstPageOf: (Int) -> Int) =
            RecitationRange.from(from, page, toSurahEnd, total, surahOf, firstPageOf)
    }

    /** A whole surah, whatever the reader chose for the runs that go by pages. */
    data object Surah : RecitationRun {
        override fun verses(from: Int, toSurahEnd: Boolean, total: Int, surahOf: (Int) -> Int, firstPageOf: (Int) -> Int) =
            RecitationRange.from(from, 0, true, total, surahOf, firstPageOf)
    }

    /** A given set of verses, such as the pages of today's review, in order; the choice of how far to go does not apply. */
    data class Listed(val ids: List<Int>) : RecitationRun {
        override fun verses(from: Int, toSurahEnd: Boolean, total: Int, surahOf: (Int) -> Int, firstPageOf: (Int) -> Int) =
            ids.dropWhile { it != from }
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
