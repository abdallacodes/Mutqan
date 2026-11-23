package com.example.mutqan.domain.game

import com.example.mutqan.quran.QuranRepository

/**
 * Wrong words easy to take for the right one, each a real word of the Quran: the same letters with other marks or
 * another hamza (مِن / مَن, أَنَّ / إِنَّ, ٱلسَّمَآءُ / ٱلسَّمَآءِ), another joining letter (وَ / فَ / none), another
 * preposition (بِ / لِ / none), another person (يَعۡلَمُونَ / تَعۡلَمُونَ), another pronoun (رَبِّهِمۡ / رَبِّكُمۡ), plural for
 * plural (ـونَ / ـينَ), with the article or without. A reader who knows the verse only roughly takes these. Built once
 * from every word of the Quran and kept for the life of the app.
 */
class WordTraps internal constructor(
    /** Each skeleton (letters only, one shape of alef) with the keys of the words that have it, the most frequent first. */
    private val keysBySkeleton: Map<String, List<String>>,
    /** A word id showing each key. */
    private val wordOfKey: Map<String, Int>
) {

    /** Up to [max] words easy to take for the word with [key] ([WordKey]), each as its key and a word id showing it; the closest first. */
    fun traps(key: String, max: Int): List<Pair<String, Int>> {
        val skeleton = skeleton(key)
        if (skeleton.isEmpty() || max <= 0) return emptyList()
        val out = LinkedHashMap<String, Int>()
        fun addFrom(s: String) {
            for (k in keysBySkeleton[s].orEmpty()) {
                if (out.size >= max) return
                if (k != key && k !in out) wordOfKey[k]?.let { out[k] = it }
            }
        }
        addFrom(skeleton)
        for (variant in variants(skeleton)) {
            if (out.size >= max) break
            addFrom(variant)
        }
        return out.map { it.key to it.value }
    }

    companion object {
        private val PRONOUNS = listOf("هما", "كما", "هم", "هن", "كم", "كن", "نا", "ها", "ه", "ك", "ي")
        private const val PERSONS = "يتنا"

        /** The letters of a word's key: marks left out, every alef one alef, alef maqsura as ya, ta marbuta as ha. */
        fun skeleton(key: String): String {
            val out = StringBuilder(key.length)
            for (c in key) {
                when (c) {
                    'آ', 'أ', 'إ', 'ٱ' -> out.append('ا')
                    'ى' -> out.append('ي')
                    'ة' -> out.append('ه')
                    in 'ء'..'ي' -> if (c != 'ـ') out.append(c)
                }
            }
            return out.toString()
        }

        /** Skeletons a word with [s] is easily mixed up with, the closest kinds first. */
        internal fun variants(s: String): List<String> {
            val out = ArrayList<String>()
            // A joining letter: وَ for فَ, or one more, or one less.
            val joined = s.length > 2 && (s[0] == 'و' || s[0] == 'ف')
            if (joined) {
                out += s.substring(1)
                out += (if (s[0] == 'و') "ف" else "و") + s.substring(1)
            } else {
                out += "و$s"
                out += "ف$s"
            }
            val pre = if (joined && s.length > 3) s.substring(0, 1) else ""
            val core = s.substring(pre.length)
            // Another person of the verb.
            if (core.length >= 4 && core[0] in PERSONS) for (p in PERSONS) if (p != core[0]) out += pre + p + core.substring(1)
            // Another pronoun at the end.
            PRONOUNS.firstOrNull { core.endsWith(it) && core.length - it.length >= 2 }?.let { suffix ->
                val stem = core.dropLast(suffix.length)
                for (other in PRONOUNS) if (other != suffix) out += pre + stem + other
            }
            // A plural ending for another.
            when {
                core.length > 4 && core.endsWith("ون") -> listOf("ين", "وا", "").forEach { out += pre + core.dropLast(2) + it }
                core.length > 4 && core.endsWith("ين") -> out += pre + core.dropLast(2) + "ون"
                core.length > 4 && core.endsWith("وا") -> out += pre + core.dropLast(2) + "ون"
            }
            // A preposition: بِ, لِ or كَ for another, or none.
            if (core.length > 3 && core[0] in "بلك") {
                out += pre + core.substring(1)
                for (p in "بلك") if (p != core[0]) out += pre + p + core.substring(1)
            } else {
                out += pre + "ب" + core
                out += pre + "ل" + core
            }
            // The article, or none.
            if (core.length > 3 && core.startsWith("ال")) out += pre + core.substring(2) else if (core.length >= 2) out += pre + "ال" + core
            return out
        }

        /** Builds the traps from every word of the Quran (id and Hafs text, in order). */
        fun build(words: List<Pair<Int, String>>): WordTraps {
            val count = HashMap<String, Int>()
            val wordOfKey = HashMap<String, Int>()
            for ((id, hafs) in words) {
                val key = WordKey.of(hafs)
                if (key.isEmpty()) continue
                count[key] = (count[key] ?: 0) + 1
                wordOfKey.putIfAbsent(key, id)
            }
            val bySkeleton = HashMap<String, MutableList<String>>()
            for (key in wordOfKey.keys) bySkeleton.getOrPut(skeleton(key)) { ArrayList() } += key
            val sorted = bySkeleton.mapValues { (_, keys) -> keys.sortedByDescending { count[it] ?: 0 } }
            return WordTraps(sorted, wordOfKey)
        }

        @Volatile
        private var cache: WordTraps? = null

        /** The traps, built on first use (a moment's work: call off the main thread). */
        fun get(quran: QuranRepository): WordTraps =
            cache ?: synchronized(this) { cache ?: build(quran.wordTexts()).also { cache = it } }
    }
}
