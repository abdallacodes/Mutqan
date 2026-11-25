package com.example.mutqan.domain

/** A phrase a verse shares with other verses: the phrase, where it is in this verse (1-based words), and the other verses that hold it. */
data class SharedPhrase(val id: Int, val text: String, val here: IntRange, val others: List<PhraseVerse>) {
    /** How many words the phrase has. */
    val length: Int get() = here.last - here.first + 1
}

/**
 * Which of the ready-made repeated phrases ([MutashabihatLibrary]) a verse holds. Built once over all the phrases: each
 * verse to the phrases in it.
 */
class PhraseIndex(phrases: List<ReadyPhrase>) {
    private val byAyah: Map<Int, List<ReadyPhrase>> = HashMap<Int, MutableList<ReadyPhrase>>().also { map ->
        for (phrase in phrases) for (verse in phrase.verses.distinctBy { it.ayahId }) map.getOrPut(verse.ayahId) { ArrayList() } += phrase
    }

    /**
     * The phrases [ayahId] shares with other verses, each with those verses: the longest phrase first (it tells the verses
     * apart least, so it is the one they are mixed up by), then the one in fewest verses. A phrase only this verse has
     * (said twice in it) is left out.
     */
    fun phrasesOf(ayahId: Int): List<SharedPhrase> = byAyah[ayahId].orEmpty().mapNotNull { phrase ->
        val here = phrase.verses.firstOrNull { it.ayahId == ayahId } ?: return@mapNotNull null
        val others = phrase.verses.filter { it.ayahId != ayahId }.distinctBy { it.ayahId }
        if (others.isEmpty()) null else SharedPhrase(phrase.id, phrase.text, here.firstWord..here.lastWord, others)
    }.sortedWith(compareByDescending<SharedPhrase> { it.length }.thenBy { it.others.size }.thenBy { it.id })
}
