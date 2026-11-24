package com.example.mutqan.domain.game

import com.example.mutqan.quran.QuranRepository
import kotlin.math.max
import kotlin.math.min

/**
 * The words that could come next at each place of a verse: real words of the Quran that come after the same words in
 * other verses. A reader who knows the Quran but not this verse exactly reaches for them, so they are the wrong lanterns
 * that test the memory most. Words are the same when [WordKey] says so (أَنَّ and إِنَّ are two words).
 *
 * They are found two ways:
 * - over the whole Quran: a place where the words before are the same (two or more, or the first word of both verses),
 *   or the word before and the word after are (a word put in another's place, or one more put in), and another word
 *   comes there. The more words agree on both sides, the closer it is: وَمَا ٱلۡحَيَوٰةُ ٱلدُّنۡيَآ has هَٰذِهِ waiting
 *   after وَمَا, from وَمَا هَٰذِهِ ٱلۡحَيَوٰةُ ٱلدُّنۡيَآ;
 * - in the verses that look like it (the reader's own mutashabihat groups, the ready lists, and verses the first way finds
 *   to share much with it), laid side by side word for word: the word the other verse has after a word they share, and
 *   where a few words differ between shared ones, the other verse's word in each place (لَعِبٞ وَلَهۡوٞ / لَهۡوٞ وَلَعِبٞ),
 *   and its opening.
 *
 * Built once from the words of every ayah and kept for the life of the app.
 */
class NextWords internal constructor(
    /** Each ayah's words as numbers (the same word, the same number); index 0 is ayah id 1. */
    private val ids: Array<IntArray>,
    /** The id of each ayah's first word (its words have the ids after it); empty when not known. */
    private val firstWord: IntArray,
    /** Every word with two words before it: (those two) << 21 | ayah id << 8 | its index; sorted. */
    private val byPair: LongArray,
    /** Every word with a word on each side: (the one before, the one after) << 21 | ayah id << 8 | its index; sorted. */
    private val bySkip: LongArray,
    /** Every ayah by its first word: (that word) << 13 | ayah id; sorted. */
    private val byStart: LongArray
) {
    /** A word of the Quran: its ayah, and its index (from 0) among the ayah's words. */
    data class Place(val ayahId: Int, val index: Int)

    /** A word that could come at a place: where it is in the Quran, and how close it looks (the higher, the closer). */
    data class Candidate(val place: Place, val score: Float)

    /** The Quran word id at [place], or null when the ids are not known. */
    fun wordId(place: Place): Int? = firstWord.getOrNull(place.ayahId - 1)?.takeIf { it > 0 }?.plus(place.index)

    /**
     * For each word of [ayahId], the words that could come there instead, the closest first, at most [max] each (and
     * none that looks right only by chance). [alike] are verses known to look like it, each with how much that counts
     * ([FROM_GROUP], [FROM_LIST]); [near] says whether a verse is one the reader knows (its words are the ones they
     * reach for).
     */
    fun candidates(
        ayahId: Int,
        alike: Map<Int, Float> = emptyMap(),
        near: (ayahId: Int) -> Boolean = { false },
        max: Int = MOST
    ): List<List<Candidate>> {
        val v = ids.getOrNull(ayahId - 1) ?: return emptyList()
        val n = v.size
        val best = Array(n) { HashMap<Int, Candidate>() }
        val count = Array(n) { HashMap<Int, Int>() }
        val sharing = HashMap<Int, Int>()

        fun offer(i: Int, other: Int, j: Int, bonus: Float, framed: Boolean) {
            if (other == ayahId) return
            val s = ids.getOrNull(other - 1) ?: return
            if (i !in 0 until n || j !in s.indices) return
            var left = 0
            while (left < MAX_LEFT && i - left - 1 >= 0 && j - left - 1 >= 0 && v[i - left - 1] == s[j - left - 1]) left++
            // Both verses start here: that counts as a word, a weak one.
            val before = left + if (left < MAX_LEFT && i - left == 0 && j - left == 0) START else 0f
            val same = v[i] == s[j]
            val after = max(
                // The other verse's word in this one's place, and the same words after it...
                run(v, i + 1, s, j + 1),
                // ...or a word more there, this verse's words after it.
                if (same) 0 else run(v, i, s, j + 1)
            )
            if (agreement(v, i, s, j) >= SHARING) sharing.merge(other, 1, Int::plus)
            if (same) return
            if (left == 0 && !framed) return
            var score = before + after + bonus
            if (framed && left == 0) score += FRAME
            if (near(other)) score += NEAR
            if (i >= n - 2 && j >= s.size - 2) score += ENDING
            if (score < MIN_SCORE) return
            val word = s[j]
            count[i].merge(word, 1, Int::plus)
            val had = best[i][word]
            if (had == null || had.score < score) best[i][word] = Candidate(Place(other, j), score)
        }

        forEachHit(v) { i, other, j -> offer(i, other, j, 0f, false) }

        // The verses that look like this one, laid beside it: the known ones, and those found sharing much with it.
        val beside = LinkedHashMap<Int, Float>()
        for ((other, bonus) in alike) if (other != ayahId) beside.putIfAbsent(other, bonus)
        sharing.entries.sortedByDescending { it.value }.asSequence()
            .map { it.key }
            .filter { it !in beside && !ids[it - 1].contentEquals(v) }
            .take(MOST_FOUND)
            .forEach { beside[it] = 0f }
        for ((other, bonus) in beside) {
            val s = ids.getOrNull(other - 1) ?: continue
            for ((i, j) in framePairs(v, s)) offer(i, other, j, bonus, true)
        }

        return List(n) { i ->
            best[i].entries
                .map { (word, c) -> c.copy(score = c.score + TIE * min((count[i][word] ?: 1) - 1, MOST_TIES)) }
                .sortedByDescending { it.score }
                .take(max)
        }
    }

    /**
     * The verses that share much with [ayahId] (four words or more agreeing around one place), the most such places
     * first, at most [most]: look-alikes the ready lists may not have. Verses with the very same words are among them.
     */
    fun lookAlikes(ayahId: Int, most: Int = MOST_LOOK_ALIKES): List<Int> {
        val v = ids.getOrNull(ayahId - 1) ?: return emptyList()
        val sharing = HashMap<Int, Int>()
        forEachHit(v) { i, other, j ->
            if (other != ayahId && agreement(v, i, ids[other - 1], j) >= SHARING) sharing.merge(other, 1, Int::plus)
        }
        return sharing.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).take(most).map { it.key }
    }

    /**
     * How alike two verses are as a whole (0 to 1): the words they share in order, twice, over the words of both. A long
     * verse that only holds a short one's words is not much like it.
     */
    fun likeness(ayahId: Int, other: Int): Float {
        val v = ids.getOrNull(ayahId - 1) ?: return 0f
        val s = ids.getOrNull(other - 1) ?: return 0f
        if (v.isEmpty() || s.isEmpty()) return 0f
        return 2f * shared(v, s).size / (v.size + s.size)
    }

    /** The words of [ayahId] that none of [others] has, laid side by side with each, as ranges of word numbers from 1. */
    fun ownWords(ayahId: Int, others: Collection<Int>): List<IntRange> {
        val v = ids.getOrNull(ayahId - 1) ?: return emptyList()
        val sharedWith = BooleanArray(v.size)
        for (other in others) {
            if (other == ayahId) continue
            val s = ids.getOrNull(other - 1) ?: continue
            for ((i, _) in shared(v, s)) sharedWith[i] = true
        }
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < v.size) {
            if (sharedWith[i]) { i++; continue }
            val start = i
            while (i < v.size && !sharedWith[i]) i++
            out += (start + 1)..i
        }
        return out
    }

    /** Calls [hit] with each place (another ayah, a word index) the indexes find for each word [i] of [v]. */
    private inline fun forEachHit(v: IntArray, hit: (i: Int, other: Int, j: Int) -> Unit) {
        val n = v.size
        for (i in 0 until n) {
            if (i == 1) forEach(byStart, v[0].toLong(), START_SHIFT) { p ->
                val other = (p and AYAH_IN_START).toInt()
                if (ids[other - 1].size > 1) hit(1, other, 1)
            }
            if (i >= 2) forEach(byPair, pair(v[i - 2], v[i - 1]), SHIFT) { p -> hit(i, ayahOf(p), indexOf(p)) }
            if (i >= 1 && i + 1 < n) forEach(bySkip, pair(v[i - 1], v[i + 1]), SHIFT) { p -> hit(i, ayahOf(p), indexOf(p)) }
            if (i >= 1) forEach(bySkip, pair(v[i - 1], v[i]), SHIFT) { p -> hit(i, ayahOf(p), indexOf(p)) }
        }
    }

    /**
     * The words of [other] that [ayahId] has too, laid side by side, as ranges of word numbers from 1: what to show in gold
     * beside it.
     */
    fun sharedWords(ayahId: Int, other: Int): List<IntRange> {
        val v = ids.getOrNull(ayahId - 1) ?: return emptyList()
        val s = ids.getOrNull(other - 1) ?: return emptyList()
        val out = ArrayList<IntRange>()
        for ((_, j) in shared(v, s)) {
            val word = j + 1
            val last = out.lastOrNull()
            if (last != null && last.last == word - 1) out[out.size - 1] = last.first..word else out += word..word
        }
        return out
    }

    /** Calls [action] with each entry of [sorted] whose part above [shift] bits is [key]. */
    private inline fun forEach(sorted: LongArray, key: Long, shift: Int, action: (Long) -> Unit) {
        var i = lowerBound(sorted, key shl shift)
        while (i < sorted.size && sorted[i] ushr shift == key) action(sorted[i++])
    }

    companion object {
        /** Candidates kept for each word. */
        const val MOST = 4

        /** Verses found sharing much with one, at most. */
        const val MOST_LOOK_ALIKES = 12

        /** What a verse from the reader's own mutashabihat groups adds; one from the ready lists. */
        const val FROM_GROUP = 3f
        const val FROM_LIST = 2f

        /** Both verses starting at the same place counts as this much of a word. */
        private const val START = 0.5f

        /** A word in a look-alike verse's place where the words before differ (in a short gap between shared ones). */
        private const val FRAME = 2f

        /** A word from a verse the reader knows. */
        private const val NEAR = 1.5f

        /** Both words among the last two of their verses: the endings readers mix up most (غَفُورٞ رَّحِيمٌ / عَزِيزٌ حَكِيمٞ). */
        private const val ENDING = 1f

        /** Less than this looks right only by chance. */
        private const val MIN_SCORE = 2f

        /** Words agreeing at one place for another verse to count as sharing much with this one. */
        private const val SHARING = 4f

        /** Of the verses found sharing much, the most laid beside it. */
        private const val MOST_FOUND = 6

        /** A short gap between shared words, word for word. */
        private const val GAP = 3

        private const val MAX_LEFT = 5
        private const val MAX_RIGHT = 3

        /** For each more place a word comes at, a little closer (up to [MOST_TIES] places). */
        private const val TIE = 0.05f
        private const val MOST_TIES = 5

        private const val SHIFT = 21
        private const val START_SHIFT = 13
        private const val AYAH_IN_START = (1L shl START_SHIFT) - 1

        private fun ayahOf(p: Long): Int = ((p ushr 8) and 0x1FFF).toInt()
        private fun indexOf(p: Long): Int = (p and 0xFF).toInt()

        /** Packs two word numbers (each under 2^15) into one. */
        private fun pair(a: Int, b: Int): Long = (a.toLong() shl 15) or b.toLong()

        /**
         * How much [v] and [s] agree around word [i] of [v] and word [j] of [s]: the words before that are the same (the
         * start of both counting as half), the word itself, and the words after (one more or one fewer between).
         */
        private fun agreement(v: IntArray, i: Int, s: IntArray, j: Int): Float {
            if (i !in v.indices || j !in s.indices) return 0f
            var left = 0
            while (left < MAX_LEFT && i - left - 1 >= 0 && j - left - 1 >= 0 && v[i - left - 1] == s[j - left - 1]) left++
            val before = left + if (left < MAX_LEFT && i - left == 0 && j - left == 0) START else 0f
            if (v[i] == s[j]) return before + 1 + run(v, i + 1, s, j + 1)
            return before + maxOf(run(v, i + 1, s, j + 1), run(v, i, s, j + 1), run(v, i + 1, s, j))
        }

        /** How many words of [v] from [i] and of [s] from [j] are the same, one for one (at most [MAX_RIGHT]). */
        private fun run(v: IntArray, i: Int, s: IntArray, j: Int): Int {
            var k = 0
            while (k < MAX_RIGHT && i + k in v.indices && j + k in s.indices && v[i + k] == s[j + k]) k++
            return k
        }

        /** The words [v] and [s] share in order, as (index in v, index in s): the longest such run of the two. */
        internal fun shared(v: IntArray, s: IntArray): List<Pair<Int, Int>> {
            val n = v.size
            val m = s.size
            if (n == 0 || m == 0) return emptyList()
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                dp[i][j] = if (v[i] == s[j]) dp[i + 1][j + 1] + 1 else max(dp[i + 1][j], dp[i][j + 1])
            }
            val out = ArrayList<Pair<Int, Int>>()
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    v[i] == s[j] -> { out += i to j; i++; j++ }
                    dp[i + 1][j] >= dp[i][j + 1] -> i++
                    else -> j++
                }
            }
            return out
        }

        /**
         * Places of [v] with the word of [s] that stands there when the two are laid side by side: after each word they
         * share, and word for word where both have a few words of their own between shared ones (or before the first,
         * or after the last).
         */
        internal fun framePairs(v: IntArray, s: IntArray): List<Pair<Int, Int>> {
            val pairs = shared(v, s)
            if (pairs.isEmpty()) return emptyList()
            val out = ArrayList<Pair<Int, Int>>()
            for ((a, b) in pairs) if (a + 1 < v.size && b + 1 < s.size) out += (a + 1) to (b + 1)
            val bounds = listOf(-1 to -1) + pairs + listOf(v.size to s.size)
            for (k in 0 until bounds.size - 1) {
                val (a1, b1) = bounds[k]
                val (a2, b2) = bounds[k + 1]
                val gapV = a2 - a1 - 1
                val gapS = b2 - b1 - 1
                if (gapV in 1..GAP && gapS in 1..GAP) for (t in 0 until min(gapV, gapS)) out += (a1 + 1 + t) to (b1 + 1 + t)
            }
            return out
        }

        private fun lowerBound(sorted: LongArray, value: Long): Int {
            var lo = 0
            var hi = sorted.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid] < value) lo = mid + 1 else hi = mid
            }
            return lo
        }

        /** Builds the index from every ayah's words in order, ayah id 1 first, and each ayah's first word id when known. */
        fun build(ayahs: List<List<String>>, firstWord: IntArray = IntArray(0)): NextWords {
            val numbers = HashMap<String, Int>()
            val ids = Array(ayahs.size) { a -> IntArray(ayahs[a].size) { w -> numbers.getOrPut(WordKey.of(ayahs[a][w])) { numbers.size } } }
            require(numbers.size < (1 shl 15)) { "too many different words: ${numbers.size}" }
            require(ids.size < (1 shl 13)) { "too many ayahs: ${ids.size}" }
            val pairs = ArrayList<Long>()
            val skips = ArrayList<Long>()
            val starts = LongArray(ids.count { it.isNotEmpty() })
            var s = 0
            ids.forEachIndexed { a, words ->
                val ayah = (a + 1).toLong() shl 8
                if (words.isNotEmpty()) starts[s++] = (words[0].toLong() shl START_SHIFT) or (a + 1).toLong()
                for (i in 1 until minOf(words.size, 256)) {
                    if (i >= 2) pairs += (pair(words[i - 2], words[i - 1]) shl SHIFT) or ayah or i.toLong()
                    if (i + 1 < words.size) skips += (pair(words[i - 1], words[i + 1]) shl SHIFT) or ayah or i.toLong()
                }
            }
            return NextWords(
                ids,
                firstWord,
                pairs.toLongArray().also { it.sort() },
                skips.toLongArray().also { it.sort() },
                starts.also { it.sort() }
            )
        }

        @Volatile
        private var cache: NextWords? = null

        /** The index, built on first use (a moment's work: call off the main thread). */
        fun get(quran: QuranRepository): NextWords =
            cache ?: synchronized(this) { cache ?: build(quran.hafsOfAllAyahs(), quran.firstWordIds()).also { cache = it } }
    }
}
