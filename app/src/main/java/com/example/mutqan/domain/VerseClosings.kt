package com.example.mutqan.domain

import android.content.Context
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * How every verse of the Quran ends, to find the other closings that stand where one verse's own does: many verses
 * close with "إن الله" and then a different name or two, and telling which one belongs to which verse is memory work.
 * [verses] are the plain words of each verse, in order; index 0 is verse id 1.
 */
class VerseClosings(verses: List<List<String>>) {

    /** Another way a verse closes: [ending] (plain words), found at the end of verse [ayahId]. */
    class Rival(val ayahId: Int, val ending: List<String>)

    /** The last [length] words of the verse are the ones to complete; [rivals] are what other verses put in the same place. */
    class Choice(val length: Int, val rivals: List<Rival>)

    private val words: List<List<String>> = verses.map { v -> v.map(QuizText::normalize) }

    /**
     * The longest closing of [ayahId] (up to [MAX_LENGTH] words) that other verses close differently after the same
     * two words, with at least [MIN_RIVALS] different closings to choose from; null when it has none. The rivals that
     * share words with the real closing, and then the commonest, are kept.
     */
    fun choiceFor(ayahId: Int, random: Random): Choice? {
        val own = words.getOrNull(ayahId - 1) ?: return null
        for (length in MAX_LENGTH downTo 1) {
            // Something must be left in front of the two words that mark the place.
            if (own.size < length + FRAME + MIN_SHOWN) continue
            val ending = own.takeLast(length)
            val frame = own.subList(own.size - length - FRAME, own.size - length)

            val rivals = LinkedHashMap<List<String>, Rival>()
            val counts = HashMap<List<String>, Int>()
            for (i in words.indices) {
                if (i == ayahId - 1) continue
                val other = words[i]
                if (other.size < length + FRAME) continue
                if (other.subList(other.size - length - FRAME, other.size - length) != frame) continue
                val closing = other.takeLast(length)
                if (closing == ending) continue
                rivals.getOrPut(closing) { Rival(i + 1, closing) }
                counts[closing] = (counts[closing] ?: 0) + 1
            }
            if (rivals.size < MIN_RIVALS) continue

            val ranked = rivals.values.sortedWith(
                compareByDescending<Rival> { r -> r.ending.count { it in ending } }.thenByDescending { counts[it.ending] ?: 0 }
            )
            return Choice(length, ranked.take(MAX_RIVALS).shuffled(random))
        }
        return null
    }

    companion object {
        const val MAX_LENGTH = 3
        const val FRAME = 2
        const val MIN_SHOWN = 2
        const val MIN_RIVALS = 3
        const val MAX_RIVALS = 6
    }
}

/** Reads the words of every verse once, for [VerseClosings]. */
object VerseClosingsLibrary {

    @Volatile
    private var cache: VerseClosings? = null

    suspend fun load(context: Context): VerseClosings {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            VerseClosings(QuranRepository.getInstance(context).hafsOfAllAyahs()).also { cache = it }
        }
    }
}
