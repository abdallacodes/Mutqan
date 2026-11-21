package com.example.mutqan.domain

import com.example.mutqan.quran.ArabicNormalization

/** A run of verses in one of the user's lists (global ayah ids [start]..[end]), with the user's note on it. */
data class ListPassage(val start: Int, val end: Int, val note: String = "", val createdAt: Long = 0L) {
    val size: Int get() = end - start + 1
    operator fun contains(ayahId: Int): Boolean = ayahId in start..end
}

/** What adding verses to a list did. */
enum class ListAdd { NEW, GREW, ALREADY }

/**
 * The rules of a list's passages. A list holds a verse once: verses added inside a passage change nothing; verses that
 * overlap a passage, or run on from it in the same surah, make it longer; other verses become a passage of their own.
 * Taking verses out shortens a passage or splits it in two. Passages are kept in reading order.
 */
object VerseLists {
    /** The longest name a list can have. */
    const val NAME_MAX = 60

    /** [passages] with verses [start]..[end] added. [surahOf] tells which surah a verse is in. */
    fun add(passages: List<ListPassage>, start: Int, end: Int, now: Long, surahOf: (Int) -> Int): Pair<List<ListPassage>, ListAdd> {
        val from = minOf(start, end)
        val to = maxOf(start, end)
        if (passages.any { from >= it.start && to <= it.end }) return passages to ListAdd.ALREADY
        val joined = passages.filter { touches(it, from, to, surahOf) }
        if (joined.isEmpty()) return (passages + ListPassage(from, to, "", now)).sortedBy { it.start } to ListAdd.NEW
        val grown = ListPassage(
            start = minOf(from, joined.minOf { it.start }),
            end = maxOf(to, joined.maxOf { it.end }),
            note = joinNotes(joined.map { it.note }),
            createdAt = joined.minOf { it.createdAt }
        )
        return (passages.filterNot { it in joined } + grown).sortedBy { it.start } to ListAdd.GREW
    }

    /**
     * [passages] with each of [ayahIds] added, runs of verses that follow each other in one surah as one passage.
     * The count is how many of the verses were not in the list before.
     */
    fun addAll(passages: List<ListPassage>, ayahIds: Collection<Int>, now: Long, surahOf: (Int) -> Int): Pair<List<ListPassage>, Int> {
        var out = passages
        var added = 0
        for (run in runs(ayahIds.toSortedSet(), surahOf)) {
            added += run.count { id -> out.none { id in it } }
            out = add(out, run.first, run.last, now, surahOf).first
        }
        return out to added
    }

    /** [passages] without verses [start]..[end]: a passage they cut into is shortened or split in two. */
    fun remove(passages: List<ListPassage>, start: Int, end: Int): List<ListPassage> {
        val from = minOf(start, end)
        val to = maxOf(start, end)
        return passages.flatMap { p ->
            if (to < p.start || from > p.end) listOf(p)
            else listOfNotNull(
                p.takeIf { p.start < from }?.copy(end = from - 1),
                p.takeIf { to < p.end }?.copy(start = to + 1)
            )
        }.sortedBy { it.start }
    }

    /** True when every verse of [start]..[end] is in [passages]. */
    fun containsAll(passages: List<ListPassage>, start: Int, end: Int): Boolean =
        (minOf(start, end)..maxOf(start, end)).all { id -> passages.any { id in it } }

    /** True when any verse of [start]..[end] is in [passages]. */
    fun containsAny(passages: List<ListPassage>, start: Int, end: Int): Boolean =
        passages.any { maxOf(start, end) >= it.start && minOf(start, end) <= it.end }

    /** How many verses the list holds. */
    fun verseCount(passages: List<ListPassage>): Int = passages.sumOf { it.size }

    /**
     * [passages] in reading order with no two overlapping (as merging two devices' copies can leave them): overlapping
     * ones become one. With [surahOf], ones that run on from each other in a surah are joined too.
     */
    fun tidy(passages: List<ListPassage>, surahOf: ((Int) -> Int)? = null): List<ListPassage> {
        val out = ArrayList<ListPassage>()
        for (p in passages.filter { it.start <= it.end }.sortedWith(compareBy({ it.start }, { it.end }))) {
            val last = out.lastOrNull()
            val runsOn = last != null && surahOf != null && p.start == last.end + 1 && surahOf(p.start) == surahOf(last.end)
            if (last != null && (p.start <= last.end || runsOn)) {
                out[out.size - 1] = last.copy(
                    end = maxOf(last.end, p.end),
                    note = joinNotes(listOf(last.note, p.note)),
                    createdAt = minOf(last.createdAt, p.createdAt)
                )
            } else {
                out.add(p)
            }
        }
        return out
    }

    /** What tells two list names apart: the same name written with other diacritics or letter case is the same list. */
    fun nameKey(name: String): String =
        ArabicNormalization.normalizeForSearch(name.trim()).lowercase().replace(SPACES, " ").trim()

    /** A name for a list: trimmed, on one line, and no longer than [NAME_MAX]. */
    fun cleanName(name: String): String = name.replace(SPACES, " ").trim().take(NAME_MAX)

    private val SPACES = Regex("\\s+")

    private fun touches(p: ListPassage, from: Int, to: Int, surahOf: (Int) -> Int): Boolean =
        (from <= p.end && to >= p.start) ||
            (to + 1 == p.start && surahOf(to) == surahOf(p.start)) ||
            (p.end + 1 == from && surahOf(p.end) == surahOf(from))

    private fun joinNotes(notes: List<String>): String =
        notes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

    /** [ids] (sorted) as runs of verses that follow each other within one surah. */
    private fun runs(ids: Collection<Int>, surahOf: (Int) -> Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        var last = -1
        for (id in ids) {
            if (start != -1 && id == last + 1 && surahOf(id) == surahOf(last)) {
                last = id
                continue
            }
            if (start != -1) out.add(start..last)
            start = id
            last = id
        }
        if (start != -1) out.add(start..last)
        return out
    }
}
