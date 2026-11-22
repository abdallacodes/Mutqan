package com.example.mutqan.domain.game

import com.example.mutqan.quran.QuranRepository

/**
 * Where else in the Quran the two words before a word come too, followed by a different word: the look-alike
 * continuations a reader mixes up. Built once from the words of every ayah and kept for the life of the app.
 */
class NextWords internal constructor(
    /** Each ayah's words as numbers (the same letters, the same number); index 0 is ayah id 1. */
    private val ids: Array<IntArray>,
    /** Every word that has two words before it, packed as (those two words) << 21 | ayah id << 8 | its index; sorted. */
    private val places: LongArray
) {
    /** A word of the Quran: its ayah, and its index (from 0) among the ayah's words. */
    data class Place(val ayahId: Int, val index: Int)

    /**
     * Places in other ayahs where the two words before word [at] of [ayahId] also come and a different word follows
     * them, each next word once: those where the three words before match first. At most [max].
     */
    fun lookAlikes(ayahId: Int, at: Int, max: Int): List<Place> {
        val words = ids.getOrNull(ayahId - 1) ?: return emptyList()
        if (at < 2 || at >= words.size || max <= 0) return emptyList()
        val key = pair(words[at - 2], words[at - 1])
        var i = lowerBound(key shl 21)
        val close = ArrayList<Place>()
        val far = ArrayList<Place>()
        val seen = HashSet<Int>()
        while (i < places.size && places[i] ushr 21 == key) {
            val p = places[i++]
            val other = ((p ushr 8) and 0x1FFF).toInt()
            val index = (p and 0xFF).toInt()
            if (other == ayahId) continue
            val them = ids[other - 1]
            val next = them[index]
            if (next == words[at] || !seen.add(next)) continue
            val three = at >= 3 && index >= 3 && them[index - 3] == words[at - 3]
            (if (three) close else far) += Place(other, index)
        }
        return (close + far).take(max)
    }

    private fun lowerBound(value: Long): Int {
        var lo = 0
        var hi = places.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (places[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Packs two word numbers (each under 2^15) into one. */
        private fun pair(a: Int, b: Int): Long = (a.toLong() shl 15) or b.toLong()

        /**
         * The letters of a word, its alef forms as one alef, alef maqsura as ya and ta marbuta as ha; marks and signs left
         * out. How two words are compared here.
         */
        fun letters(word: String): String {
            val out = StringBuilder(word.length)
            for (c in word) {
                when (c) {
                    'آ', 'أ', 'إ', 'ٱ' -> out.append('ا')
                    'ى' -> out.append('ي')
                    'ة' -> out.append('ه')
                    in 'ء'..'ي' -> out.append(c)
                }
            }
            return out.toString()
        }

        /** Builds the index from every ayah's words in order, ayah id 1 first. */
        fun build(ayahs: List<List<String>>): NextWords {
            val numbers = HashMap<String, Int>()
            val ids = Array(ayahs.size) { a -> IntArray(ayahs[a].size) { w -> numbers.getOrPut(letters(ayahs[a][w])) { numbers.size } } }
            require(numbers.size < (1 shl 15)) { "too many different words: ${numbers.size}" }
            var count = 0
            for (words in ids) count += (words.size - 2).coerceAtLeast(0)
            val places = LongArray(count)
            var n = 0
            ids.forEachIndexed { a, words ->
                for (i in 2 until minOf(words.size, 256)) {
                    places[n++] = (pair(words[i - 2], words[i - 1]) shl 21) or ((a + 1).toLong() shl 8) or i.toLong()
                }
            }
            val packed = if (n == places.size) places else places.copyOf(n)
            packed.sort()
            return NextWords(ids, packed)
        }

        @Volatile
        private var cache: NextWords? = null

        /** The index, built on first use (a moment's work: call off the main thread). */
        fun get(quran: QuranRepository): NextWords =
            cache ?: synchronized(this) { cache ?: build(quran.hafsOfAllAyahs()).also { cache = it } }
    }
}
