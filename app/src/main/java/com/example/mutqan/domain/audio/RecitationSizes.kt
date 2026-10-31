package com.example.mutqan.domain.audio

import android.content.Context
import org.json.JSONObject

/**
 * How big each surah is to download, per reciter, from the sizes of the files as they were measured
 * (`assets/recitation/sizes.json`), so the size can be shown before anything is downloaded.
 */
class RecitationSizes private constructor(context: Context) {

    private val bytes: Map<String, LongArray> by lazy {
        try {
            val root = JSONObject(context.assets.open("recitation/sizes.json").bufferedReader().use { it.readText() })
            root.keys().asSequence().associateWith { id ->
                val arr = root.getJSONArray(id)
                LongArray(arr.length()) { arr.getLong(it) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** The bytes it takes to download [surah] for [reciter], or null when that is not known. */
    fun bytesOf(reciter: Reciter, surah: Int): Long? = bytes[reciter.id]?.getOrNull(surah - 1)?.takeIf { it > 0 }

    companion object {
        @Volatile
        private var instance: RecitationSizes? = null

        fun get(context: Context): RecitationSizes =
            instance ?: synchronized(this) { instance ?: RecitationSizes(context.applicationContext).also { instance = it } }
    }
}
