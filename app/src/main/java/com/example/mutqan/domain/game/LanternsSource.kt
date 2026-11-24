package com.example.mutqan.domain.game

import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlin.math.pow
import kotlin.random.Random

/**
 * The verses known to look like each verse: the ones the reader put in a mutashabihat group with it (the ones they mix
 * up themselves) first, then the closest of the ready lists ([listed], the closest first).
 */
class AlikeVerses(
    private val mates: Map<Int, List<Int>> = emptyMap(),
    private val listed: (ayahId: Int) -> List<Int> = { emptyList() }
) {
    /** The verses like [ayahId], each with how much it counts for the words it has ([NextWords.candidates]). */
    fun of(ayahId: Int): Map<Int, Float> {
        val out = LinkedHashMap<Int, Float>()
        mates[ayahId].orEmpty().take(MOST_EACH).forEach { out.putIfAbsent(it, NextWords.FROM_GROUP) }
        listed(ayahId).take(MOST_EACH).forEach { out.putIfAbsent(it, NextWords.FROM_LIST) }
        out.remove(ayahId)
        return out
    }

    /** Whether [ayahId] has a verse known to look like it. */
    fun has(ayahId: Int): Boolean = mates[ayahId].orEmpty().any { it != ayahId } || listed(ayahId).any { it != ayahId }

    private companion object {
        const val MOST_EACH = 8
    }
}

/**
 * The wrong words easy to take for one word of a verse: [follows], the words that could come there instead (from the
 * verses that look like it and from the whole Quran), each as close as its [weights] say; and [near], the word itself
 * with other marks, another person, pronoun or joining letter ([WordTraps]).
 */
internal class Traps(val follows: List<GameWord>, val weights: List<Float>, val near: List<GameWord>) {
    val isEmpty: Boolean get() = follows.isEmpty() && near.isEmpty()
}

/**
 * The verses of a round of «فوانيس» and the wrong words beside them, all read before the round starts so the game loop
 * never waits. Many wrong words are traps: most of them words that could really come next — the word that follows the
 * same words in a verse that looks like this one, or anywhere else in the Quran ([NextWords]) — and the rest the same
 * word with other marks, another person or pronoun, another joining letter ([WordTraps]). The others come from the rest
 * of the verse and the verses next to it.
 */
class LanternsSource internal constructor(
    private val verses: List<GameVerse>,
    private val neighbours: Map<Int, List<GameWord>>,
    private val pool: List<GameWord>,
    /** Every word of the round by id, to draw them. */
    val words: Map<Int, QuranWord>,
    /** For a verse, by the index of a word in it: the wrong words easy to take for it. */
    private val traps: Map<Int, Map<Int, Traps>> = emptyMap(),
    /** The chance a wrong lantern is one of those traps, where the word has some. */
    private val trapChance: Float = 0f,
    /** For a verse started from nothing: the first words of the verses around it. */
    private val openings: Map<Int, List<GameWord>> = emptyMap(),
    /** A level's verses come once each, and then there are none; the open round's go round and round. */
    private val once: Boolean = false,
    private val random: Random = Random.Default,
    /** For a verse: the words that could come in it, each with the verse it is from. */
    private val followFrom: Map<Int, Map<Int, Int>> = emptyMap()
) : GameSource {

    private var index = 0

    val isEmpty: Boolean get() = verses.isEmpty()

    /**
     * The verses to keep from coming again soon, the first to come first: a level's all; an endless round's first few
     * only (it rarely reaches far: the ones it reached are noted when it ends, so a slipped verse is not pushed out of the
     * recent ones by verses never seen).
     */
    val drawn: List<Int> = verses.map { it.ayahId }.let { if (once) it else it.take(OPEN_DRAWN) }

    /** The words to build in all the verses. */
    val toBuild: Int = verses.sumOf { it.words.size - it.given }

    /** The verse that the word [wordId], a wrong lantern in the verse [ayahId], comes after the same words in; null for any other. */
    fun lookAlikeOf(ayahId: Int, wordId: Int): Int? = followFrom[ayahId]?.get(wordId)

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
        // A trap, most of the time in the harder levels: most often a word that could really come next (the closer, the
        // likelier), else the word itself with other marks; one of the other kind behind them, should those be up.
        val close = traps[verse.ayahId]?.get(at)
        if (close != null && !close.isEmpty && random.nextFloat() < trapChance) {
            val follows = weighted(close.follows, close.weights)
            val near = close.near.take(TRAP_PICK).shuffled(random)
            val first = close.follows.isNotEmpty() && (close.near.isEmpty() || random.nextFloat() < FOLLOW_SHARE)
            if (first) {
                take(follows, 2, shuffle = false); take(near, out.size + 1, shuffle = false)
            } else {
                take(near, 2, shuffle = false); take(follows, out.size + 1, shuffle = false)
            }
        }
        // A verse started from nothing: the openings of the verses around it look right too.
        if (at == 0 && verse.given == 0) take(openings[verse.ayahId].orEmpty(), out.size + count / 2)
        // Half the time from the rest of the verse first (a word ahead of its turn), else from the verses beside it
        // first; then the rest of the round. Words already in place are no trap.
        val own = verse.words.drop(at + 1)
        val near = neighbours[verse.ayahId].orEmpty()
        if (random.nextBoolean()) {
            take(own, out.size + count / 2 + 1); take(near, count)
        } else {
            take(near, out.size + count / 2 + 1); take(own, count)
        }
        take(pool, count)
        return out.values.toList()
    }

    /** [words] in a random order where each comes first the more often the closer it is (its weight squared). */
    private fun weighted(words: List<GameWord>, weights: List<Float>): List<GameWord> =
        words.indices.sortedByDescending { i ->
            val weight = weights.getOrElse(i) { 1f }.coerceAtLeast(0.1f)
            random.nextDouble().pow(1.0 / (weight * weight))
        }.map { words[it] }

    companion object {
        /** Verses of three to [MAX_WORDS] words: long enough to build, short enough to finish. */
        const val MIN_WORDS = 3
        const val MAX_WORDS = 14
        private const val ROUND_VERSES = 60

        /** Of an endless round's verses, the first this many count as drawn when it starts. */
        private const val OPEN_DRAWN = 3

        /** The most slipped verses the open round puts first. */
        private const val OPEN_SLIPPED = 8

        /** The most slipped verses a calm sitting puts first. */
        private const val CALM_SLIPPED = 2

        /** Words of the verse before shown to start from when none is given (its end marker apart). */
        private const val CUE_WORDS = 3

        /** Words that could come instead, read for each word of a verse. */
        private const val FOLLOW_EACH = NextWords.MOST

        /** Near forms read for each word of a verse. */
        private const val NEAR_EACH = 4

        /** A near-form trap is one of a word's closest this many. */
        private const val TRAP_PICK = 3

        /** Of the traps, the share that are words that could come next, where a word has both kinds. */
        const val FOLLOW_SHARE = 0.75f

        /** How words are compared: letter for letter and mark for mark (أَنَّ is not إِنَّ), see [WordKey]. */
        fun key(word: QuranWord): String = WordKey.of(word.hafs)

        /** The verses with their lengths; those with the same words (a refrain) share a twin. */
        private fun lengths(ayahs: List<QuranAyah>): List<VerseLength> {
            val twins = HashMap<String, Int>()
            return ayahs.map { VerseLength(it.id, it.wordsCount, twins.getOrPut(WordKey.ofVerse(it.text)) { it.id }) }
        }

        /** Words given at the start of a verse in a calm sitting: one, two for a long verse. */
        private fun calmGiven(size: Int): Int = if (size >= 10) 2 else 1

        /**
         * Reads an open round from [pages] (the memorised pages, or a juz): up to sixty verses that go round and round, a
         * few slipped ones first when they are due, then verses not drawn lately in random order ([LevelVerses.order]),
         * those with look-alikes ([alike]) put first by [favour]; their words and the words of the verses on either side
         * of each. [given] words of each verse are in place from the start (with none, the end of the verse before
         * shows), and wrong lanterns are traps ([next], [nearForms]) by [chance]. Call off the main thread.
         */
        fun load(
            quran: QuranRepository,
            pages: Collection<Int>,
            random: Random = Random.Default,
            slipped: List<Int> = emptyList(),
            recent: List<Int> = emptyList(),
            given: (size: Int) -> Int = ::calmGiven,
            next: NextWords? = null,
            nearForms: WordTraps? = null,
            chance: Float = 0f,
            alike: AlikeVerses = AlikeVerses(),
            favour: Float = 0f,
            known: Set<Int> = emptySet()
        ): LanternsSource {
            val ayahs = ayahsOn(quran, pages).filter { it.wordsCount in MIN_WORDS..MAX_WORDS }
            val favoured = if (favour > 0f) ayahs.map { it.id }.filterTo(HashSet()) { alike.has(it) } else emptySet()
            val ids = LevelVerses.order(lengths(ayahs), slipped, recent, random, OPEN_SLIPPED, favoured, favour).take(ROUND_VERSES)
            return read(quran, ids, given, next, alike, pages.toSet() + known, nearForms, chance, once = false, random = random)
        }

        /**
         * Reads a calm sitting from [pages]: [GameModes.CALM_VERSES] verses, once each (a slipped one or two first when
         * due, then verses not drawn lately), one word given (two in a long verse). Call off the main thread.
         */
        fun calm(
            quran: QuranRepository,
            pages: Collection<Int>,
            slipped: List<Int>,
            recent: List<Int>,
            nearForms: WordTraps? = null,
            next: NextWords? = null,
            alike: AlikeVerses = AlikeVerses(),
            random: Random = Random.Default,
            known: Set<Int> = emptySet()
        ): LanternsSource {
            val ayahs = ayahsOn(quran, pages).filter { it.wordsCount in MIN_WORDS..MAX_WORDS }
            val ids = LevelVerses.order(lengths(ayahs), slipped, recent, random, CALM_SLIPPED).take(GameModes.CALM_VERSES)
            return read(quran, ids, ::calmGiven, next, alike, pages.toSet() + known, nearForms, GameModes.CALM_TRAPS, once = true, random = random)
        }

        /**
         * Reads a level from [pages]: verses no longer than the level allows (a few longer ones when a small hifz has too
         * few verses otherwise) whose words to build add up to the level's words ([LevelVerses.pick]: no verse twice,
         * not the ones drawn lately, a few slipped ones when due, and from [LanternsLevels.LOOK_ALIKE_FROM] on those with
         * look-alikes more often), with the level's words given, the end of the verse before for a verse started from
         * nothing, and traps by the level's share. Call off the main thread.
         */
        fun forLevel(
            quran: QuranRepository,
            pages: Collection<Int>,
            rules: LevelRules,
            slipped: List<Int>,
            recent: List<Int>,
            next: NextWords?,
            nearForms: WordTraps? = null,
            alike: AlikeVerses = AlikeVerses(),
            random: Random = Random.Default,
            known: Set<Int> = emptySet()
        ): LanternsSource {
            val all = lengths(ayahsOn(quran, pages).filter { it.wordsCount in MIN_WORDS..MAX_WORDS })
            val pool = LevelVerses.withEnough(
                reach = all.filter { it.words <= rules.longestVerse },
                beyond = all.filter { it.words > rules.longestVerse }
            )
            val favoured = if (rules.lookAlike > 0f) pool.map { it.ayahId }.filterTo(HashSet()) { alike.has(it) } else emptySet()
            val ids = LevelVerses.pick(pool, rules.words, rules.given, slipped, recent, random, favoured, rules.lookAlike)
            return read(quran, ids, { rules.given }, next, alike, pages.toSet() + known, nearForms, rules.traps, once = true, random = random)
        }

        private fun ayahsOn(quran: QuranRepository, pages: Collection<Int>): List<QuranAyah> =
            pages.distinct().flatMap { quran.ayahsOnPage(it) }.distinctBy { it.id }

        private fun read(
            quran: QuranRepository,
            ayahIds: List<Int>,
            given: (size: Int) -> Int,
            next: NextWords?,
            alike: AlikeVerses,
            known: Set<Int>,
            nearForms: WordTraps?,
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
            // For each word to build: the words that could come there instead, the closest first (a word from a verse the
            // reader knows counts more), and near forms of the word itself; their words read in one go.
            fun isKnown(id: Int) = id in index && index.firstPageOf(id) in known
            val toRead = HashSet<Int>()
            val planned = verses.associate { verse ->
                val found = next?.candidates(verse.ayahId, alike.of(verse.ayahId), ::isKnown, FOLLOW_EACH).orEmpty()
                verse.ayahId to (verse.given until verse.words.size).associateWith { at ->
                    val follows = found.getOrNull(at).orEmpty().mapNotNull { c ->
                        next?.wordId(c.place)?.let { id -> Triple(id, c.place.ayahId, c.score) }
                    }.onEach { toRead += it.first }
                    val near = nearForms?.traps(verse.words[at].key, NEAR_EACH).orEmpty().onEach { toRead += it.second }
                    follows to near
                }
            }
            if (toRead.isNotEmpty()) for (word in quran.wordsByIds(toRead)) words[word.id] = word
            val followFrom = HashMap<Int, HashMap<Int, Int>>()
            val traps = planned.mapValues { (ayahId, byAt) ->
                byAt.mapValues { (at, found) ->
                    val right = verses.first { it.ayahId == ayahId }.words[at].key
                    val follows = found.first.mapNotNull { (id, from, score) ->
                        words[id]?.let { Triple(GameWord(id, key(it)), from, score) }
                    }.filter { it.first.key != right }.distinctBy { it.first.key }
                    follows.forEach { (word, from, _) -> followFrom.getOrPut(ayahId) { HashMap() }[word.id] = from }
                    val near = found.second.mapNotNull { (key, id) -> if (id in words) GameWord(id, key) else null }
                        .filter { n -> n.key != right && follows.none { it.first.key == n.key } }
                    Traps(follows.map { it.first }, follows.map { it.third }, near)
                }.filterValues { !it.isEmpty }
            }
            val pool = verses.flatMap { it.words }
            return LanternsSource(verses, neighbours, pool, words, traps, chance, openings, once, random, followFrom)
        }
    }
}
