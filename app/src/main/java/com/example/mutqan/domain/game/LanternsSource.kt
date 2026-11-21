package com.example.mutqan.domain.game

import com.example.mutqan.domain.QuizText
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlin.random.Random

/**
 * The verses of a round of «فوانيس» and the wrong words beside them, all read before the round starts so the game loop
 * never waits. The wrong words are mostly from the verse itself and the verses next to it: they look right, and only
 * memory tells them apart.
 */
class LanternsSource internal constructor(
    private val verses: List<GameVerse>,
    private val neighbours: Map<Int, List<GameWord>>,
    private val pool: List<GameWord>,
    /** Every word of the round by id, to draw them. */
    val words: Map<Int, QuranWord>,
    private val random: Random = Random.Default
) : GameSource {

    private var index = 0

    val isEmpty: Boolean get() = verses.isEmpty()

    override fun nextVerse(): GameVerse? = if (verses.isEmpty()) null else verses[index++ % verses.size]

    override fun decoys(verse: GameVerse, right: GameWord, count: Int): List<GameWord> {
        val out = LinkedHashMap<String, GameWord>()
        fun take(from: List<GameWord>, upTo: Int) {
            for (word in from.shuffled(random)) {
                if (out.size >= upTo) return
                if (word.key != right.key && word.key !in out) out[word.key] = word
            }
        }
        // Half the time from the verse itself first, else from the verses beside it first; then the rest of the round.
        val own = verse.words
        val near = neighbours[verse.ayahId].orEmpty()
        if (random.nextBoolean()) {
            take(own, count / 2 + 1); take(near, count)
        } else {
            take(near, count / 2 + 1); take(own, count)
        }
        take(pool, count)
        return out.values.toList()
    }

    companion object {
        /** Verses of three to [MAX_WORDS] words: long enough to build, short enough to finish. */
        const val MIN_WORDS = 3
        const val MAX_WORDS = 14
        private const val ROUND_VERSES = 60

        fun key(word: QuranWord): String = QuizText.normalize(word.hafs)

        /**
         * Reads a round from [pages] (the memorised pages, or a juz): up to sixty verses in random order, their words and
         * the words of the verses on either side of each. Call off the main thread.
         */
        fun load(quran: QuranRepository, pages: Collection<Int>, random: Random = Random.Default): LanternsSource {
            val ayahs = pages.distinct().flatMap { quran.ayahsOnPage(it) }
                .distinctBy { it.id }
                .filter { it.wordsCount in MIN_WORDS..MAX_WORDS }
                .shuffled(random)
                .take(ROUND_VERSES)
            val words = HashMap<Int, QuranWord>()
            fun wordsOf(surah: Int, ayah: Int): List<QuranWord> =
                quran.wordsOfAyah(surah, ayah).filter { !it.isEnd && it.hafs.isNotBlank() }.onEach { words[it.id] = it }

            val verses = ayahs.mapNotNull { ayah ->
                val list = wordsOf(ayah.surah, ayah.ayah)
                if (list.size < MIN_WORDS) null
                else GameVerse(ayah.id, list.map { GameWord(it.id, key(it)) }, given = if (list.size >= 10) 2 else 1)
            }
            val index = quran.ayahIndex
            val neighbours = verses.associate { verse ->
                val around = listOf(verse.ayahId - 1, verse.ayahId + 1).filter { it in index }.flatMap { id ->
                    wordsOf(index.surahOf(id), index.ayahOf(id)).map { GameWord(it.id, key(it)) }
                }
                verse.ayahId to around
            }
            val pool = verses.flatMap { it.words }
            return LanternsSource(verses, neighbours, pool, words, random)
        }
    }
}
