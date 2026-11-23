package com.example.mutqan.domain.game

import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlin.random.Random

/**
 * The verses of a round of «فوانيس» and the wrong words beside them, all read before the round starts so the game loop
 * never waits. The wrong words are mostly from the verse itself and the verses next to it: they look right, and only
 * memory tells them apart. In the harder levels some come from look-alike verses: where the two words before the one
 * wanted are followed by another word elsewhere in the Quran, that word floats up too.
 */
class LanternsSource internal constructor(
    private val verses: List<GameVerse>,
    private val neighbours: Map<Int, List<GameWord>>,
    private val pool: List<GameWord>,
    /** Every word of the round by id, to draw them. */
    val words: Map<Int, QuranWord>,
    /** For a verse, by the index of a word in it: words that follow the same words before it in other verses. */
    private val lookAlikes: Map<Int, Map<Int, List<GameWord>>> = emptyMap(),
    /** The chance a wrong lantern carries a look-alike word, where there is one. */
    private val lookAlikeChance: Float = 0f,
    /** For a verse started from nothing: the first words of the verses around it. */
    private val openings: Map<Int, List<GameWord>> = emptyMap(),
    /** A level's verses come once each, and then there are none; the open round's go round and round. */
    private val once: Boolean = false,
    private val random: Random = Random.Default
) : GameSource {

    private var index = 0

    val isEmpty: Boolean get() = verses.isEmpty()

    /**
     * The verses to keep from coming again soon, the first to come first: a level's all, the open round's first few (it
     * rarely goes through them all).
     */
    val drawn: List<Int> = verses.map { it.ayahId }.let { if (once) it else it.take(OPEN_DRAWN) }

    /** The words to build in all the verses. */
    val toBuild: Int = verses.sumOf { it.words.size - it.given }

    override fun nextVerse(): GameVerse? = when {
        verses.isEmpty() -> null
        once -> verses.getOrNull(index++)
        else -> verses[index++ % verses.size]
    }

    override fun decoys(verse: GameVerse, at: Int, count: Int): List<GameWord> {
        val right = verse.words.getOrNull(at) ?: return emptyList()
        val out = LinkedHashMap<String, GameWord>()
        fun take(from: List<GameWord>, upTo: Int, shuffle: Boolean = true) {
            for (word in if (shuffle) from.shuffled(random) else from) {
                if (out.size >= upTo) return
                if (word.key != right.key && word.key !in out) out[word.key] = word
            }
        }
        // A look-alike word, now and then, comes first: the one most like what memory expects.
        val alike = lookAlikes[verse.ayahId]?.get(at).orEmpty()
        if (alike.isNotEmpty() && random.nextFloat() < lookAlikeChance) take(alike, 1, shuffle = false)
        // A verse started from nothing: the openings of the verses around it look right too.
        if (at == 0 && verse.given == 0) take(openings[verse.ayahId].orEmpty(), out.size + count / 2)
        // Half the time from the verse itself first, else from the verses beside it first; then the rest of the round.
        val own = verse.words
        val near = neighbours[verse.ayahId].orEmpty()
        if (random.nextBoolean()) {
            take(own, out.size + count / 2 + 1); take(near, count)
        } else {
            take(near, out.size + count / 2 + 1); take(own, count)
        }
        take(pool, count)
        return out.values.toList()
    }

    companion object {
        /** Verses of three to [MAX_WORDS] words: long enough to build, short enough to finish. */
        const val MIN_WORDS = 3
        const val MAX_WORDS = 14
        private const val ROUND_VERSES = 60

        /** Of the open round's verses, the first this many count as drawn. */
        private const val OPEN_DRAWN = 20

        /** The most slipped verses the open round puts first. */
        private const val OPEN_SLIPPED = 8

        /** Words of the verse before shown to start from when none is given (its end marker apart). */
        private const val CUE_WORDS = 3

        /** Look-alike words read for each word of a verse. */
        private const val ALIKE_EACH = 2

        /** How words are compared: letter for letter and mark for mark (أَنَّ is not إِنَّ), see [WordKey]. */
        fun key(word: QuranWord): String = WordKey.of(word.hafs)

        /** The verses with their lengths; those with the same words (a refrain) share a twin. */
        private fun lengths(ayahs: List<QuranAyah>): List<VerseLength> {
            val twins = HashMap<String, Int>()
            return ayahs.map { VerseLength(it.id, it.wordsCount, twins.getOrPut(WordKey.ofVerse(it.text)) { it.id }) }
        }

        /**
         * Reads an open round from [pages] (the memorised pages, or a juz): up to sixty verses, a few slipped ones first
         * when they are due, then verses not drawn lately in random order ([LevelVerses.order]); their words and the words
         * of the verses on either side of each. Call off the main thread.
         */
        fun load(
            quran: QuranRepository,
            pages: Collection<Int>,
            random: Random = Random.Default,
            slipped: List<Int> = emptyList(),
            recent: List<Int> = emptyList()
        ): LanternsSource {
            val ayahs = ayahsOn(quran, pages).filter { it.wordsCount in MIN_WORDS..MAX_WORDS }
            val ids = LevelVerses.order(lengths(ayahs), slipped, recent, random, OPEN_SLIPPED).take(ROUND_VERSES)
            return read(quran, ids, given = { size -> if (size >= 10) 2 else 1 }, next = null, chance = 0f, once = false, random = random)
        }

        /**
         * Reads a level from [pages]: verses no longer than the level allows (a few longer ones when a small hifz has too
         * few verses otherwise) whose words to build add up to the level's words ([LevelVerses.pick]: no verse twice,
         * not the ones drawn lately, a few slipped ones when due), with the level's words given, the end of the verse
         * before for a verse started from nothing, and look-alike words from [next] when the level has them. Call off
         * the main thread.
         */
        fun forLevel(
            quran: QuranRepository,
            pages: Collection<Int>,
            rules: LevelRules,
            slipped: List<Int>,
            recent: List<Int>,
            next: NextWords?,
            random: Random = Random.Default
        ): LanternsSource {
            val all = lengths(ayahsOn(quran, pages).filter { it.wordsCount in MIN_WORDS..MAX_WORDS })
            val pool = LevelVerses.withEnough(
                reach = all.filter { it.words <= rules.longestVerse },
                beyond = all.filter { it.words > rules.longestVerse }
            )
            val ids = LevelVerses.pick(pool, rules.words, rules.given, slipped, recent, random)
            return read(
                quran, ids,
                given = { rules.given },
                next = next.takeIf { rules.lookAlike > 0f },
                chance = rules.lookAlike,
                once = true,
                random = random
            )
        }

        private fun ayahsOn(quran: QuranRepository, pages: Collection<Int>): List<QuranAyah> =
            pages.distinct().flatMap { quran.ayahsOnPage(it) }.distinctBy { it.id }

        private fun read(
            quran: QuranRepository,
            ayahIds: List<Int>,
            given: (size: Int) -> Int,
            next: NextWords?,
            chance: Float,
            once: Boolean,
            random: Random
        ): LanternsSource {
            val index = quran.ayahIndex
            val words = HashMap<Int, QuranWord>()
            val read = HashMap<Int, List<QuranWord>>()
            /** The words of an ayah with its end marker, read once. */
            fun all(id: Int): List<QuranWord> = read.getOrPut(id) {
                if (id !in index) emptyList()
                else quran.wordsOfAyah(index.surahOf(id), index.ayahOf(id)).filter { it.isEnd || it.hafs.isNotBlank() }
            }
            fun wordsOf(id: Int): List<QuranWord> = all(id).filter { !it.isEnd }.onEach { words[it.id] = it }
            fun game(word: QuranWord) = GameWord(word.id, key(word))
            fun sameSurah(a: Int, b: Int) = a in index && b in index && index.surahOf(a) == index.surahOf(b)

            val verses = ayahIds.mapNotNull { id ->
                val list = wordsOf(id)
                if (list.size < MIN_WORDS) return@mapNotNull null
                val start = given(list.size).coerceIn(0, list.size - 1)
                // Started from nothing: the end of the verse before it (in the same surah) to go on from.
                val cue = if (start == 0 && sameSurah(id - 1, id)) {
                    all(id - 1).takeLast(CUE_WORDS + 1).onEach { words[it.id] = it }.map { it.id }
                } else emptyList()
                GameVerse(id, list.map(::game), given = start, cue = cue)
            }
            val neighbours = verses.associate { verse ->
                val around = listOf(verse.ayahId - 1, verse.ayahId + 1).filter { it in index }.flatMap { id -> wordsOf(id).map(::game) }
                verse.ayahId to around
            }
            val openings = verses.filter { it.given == 0 }.associate { verse ->
                val around = (-3..3).map { verse.ayahId + it }.filter { it != verse.ayahId && sameSurah(it, verse.ayahId) }
                verse.ayahId to around.mapNotNull { id -> wordsOf(id).firstOrNull()?.let(::game) }
            }
            val lookAlikes = if (next == null) emptyMap() else verses.associate { verse ->
                verse.ayahId to (verse.given until verse.words.size).associateWith { at ->
                    next.lookAlikes(verse.ayahId, at, ALIKE_EACH).mapNotNull { place -> wordsOf(place.ayahId).getOrNull(place.index)?.let(::game) }
                }.filterValues { it.isNotEmpty() }
            }
            val pool = verses.flatMap { it.words }
            return LanternsSource(verses, neighbours, pool, words, lookAlikes, chance, openings, once, random)
        }
    }
}
