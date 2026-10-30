package com.example.mutqan.domain

/**
 * The part of the Quran the user has memorised, as sorted, non-touching ranges of global ayah
 * ids. An empty list means the user has not said what they know; the app then does not restrict
 * anything (see [coveredPages]).
 */
object HifzCoverage {

    private const val TOTAL_PAGES = 604

    /** Sorts [ranges] and joins any that overlap or sit next to each other. Empty ranges are dropped. */
    fun merge(ranges: Collection<IntRange>): List<IntRange> {
        val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
        val out = ArrayList<IntRange>(sorted.size)
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                if (r.last > last.last) out[out.lastIndex] = last.first..r.last
            } else {
                out.add(r)
            }
        }
        return out
    }

    fun add(ranges: List<IntRange>, added: IntRange): List<IntRange> = merge(ranges + listOf(added))

    /** [ranges] without the ayahs in [removed]; a range that straddles it is split in two. */
    fun remove(ranges: List<IntRange>, removed: IntRange): List<IntRange> {
        if (removed.isEmpty()) return merge(ranges)
        val out = ArrayList<IntRange>()
        for (r in merge(ranges)) {
            if (r.last < removed.first || r.first > removed.last) {
                out.add(r)
                continue
            }
            if (r.first < removed.first) out.add(r.first until removed.first)
            if (r.last > removed.last) out.add((removed.last + 1)..r.last)
        }
        return out
    }

    fun contains(ranges: List<IntRange>, ayahId: Int): Boolean = ranges.any { ayahId in it }

    /** True when every ayah in [range] is covered. */
    fun containsAll(ranges: List<IntRange>, range: IntRange): Boolean =
        merge(ranges).any { range.first >= it.first && range.last <= it.last }

    fun ayahCount(ranges: List<IntRange>): Int = merge(ranges).sumOf { it.last - it.first + 1 }

    /**
     * Pages (index 0 = page 1) that hold at least one covered ayah, counting the page each ayah
     * starts on. Null when [ranges] is empty, meaning "no restriction".
     */
    fun coveredPages(ranges: List<IntRange>, firstPageOf: (Int) -> Int): BooleanArray? {
        if (ranges.isEmpty()) return null
        val pages = BooleanArray(TOTAL_PAGES)
        for (range in ranges) {
            for (id in range) {
                val page = firstPageOf(id)
                if (page in 1..TOTAL_PAGES) pages[page - 1] = true
            }
        }
        return pages
    }
}
