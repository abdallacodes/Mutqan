package com.example.mutqan.domain

import com.example.mutqan.quran.ArabicNormalization

/*
 * The verse search (2026-11-21, with the owner): one box that takes words of a verse, a surah, a page, a juz or a
 * reference like 2:255, and finds every verse with the words, not just the first forty, marking which words matched.
 */

/** How the search compares text: the Quran search's normalization, plus what typed text brings (tatweel, Persian letters, runs of spaces). */
object SearchText {
    private val spaces = Regex("\\s+")
    private val alifs = Regex("ا+")

    fun normalize(text: String): String =
        ArabicNormalization.normalizeForSearch(
            text.replace('ی', 'ي').replace('ک', 'ك').replace("ـ", "")
        ).replace(spaces, " ").trim()

    /**
     * A normalized text with its spaces taken out and the runs of alif that leaves made one. The Mushaf joins some
     * words that are typed apart (يَٰٓأَيُّهَا is يا أيها), so a phrase that is not found as typed is looked for this way.
     */
    fun joined(normalized: String): String = normalized.replace(" ", "").replace(alifs, "ا")

    /** [text] with Arabic-Indic and Persian digits written as 0-9. */
    fun digits(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                }
            )
        }
    }
}

/** A verse the search found: its global id, and which of its words matched, as 1-based word positions. */
data class VerseMatch(val ayahId: Int, val matched: List<IntRange>)

/**
 * Every verse in searchable form, built once from the words of each verse ([words] index 0 is verse 1, without the
 * verse-number markers). Each verse is kept twice: its words one space apart, and joined (see [SearchText.joined]),
 * each with where every word starts and ends, so a match can be told as the words it covers.
 */
class VerseIndex(words: List<List<String>>) {

    private class Text(val text: String, val starts: IntArray, val ends: IntArray) {
        /** The 1-based positions of the words that [from] until [until] touches. */
        fun wordsIn(from: Int, until: Int): IntRange? {
            var first = -1
            var last = -1
            for (i in starts.indices) {
                if (ends[i] > starts[i] && ends[i] > from && starts[i] < until) {
                    if (first < 0) first = i
                    last = i
                }
            }
            return if (first < 0) null else (first + 1)..(last + 1)
        }
    }

    private val spaced: Array<Text>
    private val joined: Array<Text>

    init {
        spaced = Array(words.size) { v ->
            val sb = StringBuilder()
            val starts = IntArray(words[v].size)
            val ends = IntArray(words[v].size)
            words[v].forEachIndexed { i, word ->
                val norm = SearchText.normalize(word)
                if (sb.isNotEmpty() && norm.isNotEmpty()) sb.append(' ')
                starts[i] = sb.length
                sb.append(norm)
                ends[i] = sb.length
            }
            Text(sb.toString(), starts, ends)
        }
        joined = Array(words.size) { v ->
            val sb = StringBuilder()
            val starts = IntArray(words[v].size)
            val ends = IntArray(words[v].size)
            words[v].forEachIndexed { i, word ->
                starts[i] = sb.length
                for (ch in SearchText.joined(SearchText.normalize(word))) {
                    // An alif that meets the alif ending the word before is one letter, kept by the word before.
                    if (ch == 'ا' && sb.isNotEmpty() && sb[sb.length - 1] == 'ا') continue
                    sb.append(ch)
                }
                ends[i] = sb.length
            }
            Text(sb.toString(), starts, ends)
        }
    }

    /**
     * The verses whose words contain [query], in reading order, among those [accept] lets through. When none has the
     * words as typed, the verses that have them once spaces are ignored (see [SearchText.joined]).
     */
    fun find(query: String, accept: (Int) -> Boolean = { true }): List<VerseMatch> {
        val needle = SearchText.normalize(query)
        if (needle.count { it in 'ء'..'ي' } < MIN_LETTERS) return emptyList()
        val found = scan(spaced, needle, accept)
        if (found.isNotEmpty()) return found
        val together = SearchText.joined(needle)
        return if (together.length >= MIN_LETTERS) scan(joined, together, accept) else emptyList()
    }

    private fun scan(texts: Array<Text>, needle: String, accept: (Int) -> Boolean): List<VerseMatch> {
        val out = ArrayList<VerseMatch>()
        for (v in texts.indices) {
            val text = texts[v]
            var at = text.text.indexOf(needle)
            if (at < 0 || !accept(v + 1)) continue
            val ranges = ArrayList<IntRange>()
            while (at >= 0) {
                text.wordsIn(at, at + needle.length)?.let { ranges += it }
                at = text.text.indexOf(needle, at + 1)
            }
            out += VerseMatch(v + 1, merge(ranges))
        }
        return out
    }

    companion object {
        /** Letters a search needs before it looks. */
        const val MIN_LETTERS = 2

        /** [ranges] sorted, with those that overlap or touch made one. */
        fun merge(ranges: List<IntRange>): List<IntRange> {
            if (ranges.size < 2) return ranges
            val sorted = ranges.sortedBy { it.first }
            val out = ArrayList<IntRange>()
            var current = sorted.first()
            for (r in sorted.drop(1)) {
                current = if (r.first <= current.last + 1) current.first..maxOf(current.last, r.last) else {
                    out += current
                    r
                }
            }
            out += current
            return out
        }
    }
}

/** Which words of a found verse to show: a short verse whole, a long one cut to a stretch around the first match. */
object MatchWindow {
    /** The most words shown of a long verse. */
    const val MOST = 16
    /** Words shown before the first match in a long verse. */
    const val BEFORE = 4
    /** A verse this many words past [MOST] is still shown whole: cutting off a word or two only hides them. */
    private const val SLACK = 3

    /** The 0-based indices of the words to show of a verse of [count] words whose [matched] words (1-based) must be in view. */
    fun of(count: Int, matched: List<IntRange>, most: Int = MOST, before: Int = BEFORE): IntRange {
        if (count <= most + SLACK) return 0 until count
        val first = (matched.minOfOrNull { it.first } ?: 1) - 1
        val end = ((first - before).coerceAtLeast(0) + most - 1).coerceAtMost(count - 1)
        return (end - most + 1).coerceAtLeast(0)..end
    }
}

/** A place the search box can go straight to, besides the verses it finds. */
sealed interface GoTo {
    data class Page(val page: Int) : GoTo
    data class Surah(val surah: Int) : GoTo
    data class Juz(val juz: Int) : GoTo
    data class Verse(val surah: Int, val ayah: Int) : GoTo
}

/** A surah as the search box knows it: its names and how many verses it has. */
data class SurahName(val id: Int, val arabic: String, val english: String, val verses: Int)

/**
 * What a query names besides words: a number (a page, a surah or a juz), a reference (2:255, البقرة 255), a word and
 * a number (صفحة 50, juz 3), or a surah's name.
 */
object SearchQuery {
    private const val PAGES = 604
    private const val JUZ = 30
    private const val MOST_SURAHS = 3

    private val number = Regex("^(\\d{1,3})$")
    private val reference = Regex("^(\\d{1,3})\\s*[:：.\\-/ ]\\s*(\\d{1,3})$")
    private val nameAndNumber = Regex("^(.*\\D)\\s*(\\d{1,3})$")

    private val pageWords = keys("صفحة", "الصفحة", "page", "p", "p.", "pg")
    private val juzWords = keys("جزء", "الجزء", "juz", "juzz", "juz'", "part")
    private val surahWords = keys("سورة", "السورة", "surah", "sura", "surat")

    /** [words] as the query's own word is compared: normalized (الجزء becomes الجزا) and in lower case. */
    private fun keys(vararg words: String): Set<String> = words.mapTo(HashSet()) { SearchText.normalize(it).lowercase() }

    fun goTo(query: String, surahs: List<SurahName>): List<GoTo> {
        val text = SearchText.digits(query).trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return emptyList()

        number.matchEntire(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            return buildList {
                if (n in 1..PAGES) add(GoTo.Page(n))
                if (n in 1..surahs.size) add(GoTo.Surah(n))
                if (n in 1..JUZ) add(GoTo.Juz(n))
            }
        }

        reference.matchEntire(text)?.let { m ->
            val surah = surahs.getOrNull(m.groupValues[1].toInt() - 1) ?: return emptyList()
            return listOf(verseOrSurah(surah, m.groupValues[2].toInt()))
        }

        nameAndNumber.matchEntire(text)?.let { m ->
            val word = m.groupValues[1].trim()
            val n = m.groupValues[2].toInt()
            val key = SearchText.normalize(word).lowercase()
            when (key) {
                in pageWords -> return if (n in 1..PAGES) listOf(GoTo.Page(n)) else emptyList()
                in juzWords -> return if (n in 1..JUZ) listOf(GoTo.Juz(n)) else emptyList()
                in surahWords -> return surahs.getOrNull(n - 1)?.let { listOf(GoTo.Surah(it.id)) } ?: emptyList()
            }
            val surah = surahsNamed(word, surahs).firstOrNull() ?: return emptyList()
            return listOf(verseOrSurah(surah, n))
        }

        return surahsNamed(text, surahs).take(MOST_SURAHS).map { GoTo.Surah(it.id) }
    }

    private fun verseOrSurah(surah: SurahName, ayah: Int): GoTo =
        if (ayah in 1..surah.verses) GoTo.Verse(surah.id, ayah) else GoTo.Surah(surah.id)

    /**
     * The surahs [name] could mean, best first: the name itself (with or without "ال" and "سورة"), then names that
     * begin with it, then, from three letters on, names that hold it. English names are compared by their letters only.
     */
    fun surahsNamed(name: String, surahs: List<SurahName>): List<SurahName> {
        val arabic = bare(SearchText.normalize(name).filter { it == ' ' || it in 'ء'..'ي' }.trim())
        val latin = name.lowercase().filter { it in 'a'..'z' }
        if (arabic.isEmpty() && latin.isEmpty()) return emptyList()
        val scored = surahs.mapNotNull { s ->
            val own = bare(SearchText.normalize(s.arabic))
            val ownLatin = s.english.lowercase().filter { it in 'a'..'z' }
            val score = when {
                arabic.isNotEmpty() && own == arabic -> 4
                arabic.isNotEmpty() && own.startsWith(arabic) -> 3
                arabic.length >= 3 && own.contains(arabic) -> 2
                latin.length >= 3 && (ownLatin == latin || ownLatin.removePrefix("al") == latin) -> 4
                latin.length >= 3 && ownLatin.contains(latin) -> 2
                else -> 0
            }
            if (score > 0) s to score else null
        }
        return scored.sortedWith(compareByDescending<Pair<SurahName, Int>> { it.second }.thenBy { it.first.id }).map { it.first }
    }

    /** A normalized name without a leading "سورة" and article, so البقرة, بقرة and سورة البقرة compare the same. */
    private fun bare(name: String): String =
        name.removePrefix("سوره ").removePrefix("السوره ").trim().let { if (it.length > 2 && it.startsWith("ال")) it.drop(2) else it }.trim()
}
