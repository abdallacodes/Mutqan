package com.example.mutqan.ui.mushaf

import androidx.compose.runtime.saveable.Saver

/** A place in the Mushaf: a page, and the verse looked at there (0 for none, a page gone to by its number). */
data class Place(val page: Int, val ayahId: Int = 0)

/**
 * Where the reader has gone by jumping in this Mushaf view (to a look-alike verse, to a page or surah), in order, and
 * where they are now among those places ([at]): like a browser's history. Going back keeps the places after, to go
 * forward to; a new jump from an earlier place drops them. The place being left is always kept as the reader left it
 * (the page they had turned to since), so going back finds them there.
 */
data class PlaceTrail(val places: List<Place> = emptyList(), val at: Int = -1) {

    /** Nothing to go back or forward to yet: no jump made. */
    val isEmpty: Boolean get() = places.size < 2

    val previous: Place? get() = places.getOrNull(at - 1)
    val next: Place? get() = places.getOrNull(at + 1)

    /** Goes from [from] (where the reader is) to [to]. */
    fun jump(from: Place, to: Place): PlaceTrail {
        if (from == to) return this
        val kept = if (at < 0) emptyList() else places.subList(0, at)
        val list = withoutRepeats(kept + from + to).takeLast(MOST)
        return PlaceTrail(list, list.lastIndex)
    }

    /** Goes to the place at [index], leaving [current] (where the reader is now) in place of the one they were at. */
    fun goTo(index: Int, current: Place): PlaceTrail {
        if (index !in places.indices || index == at) return this
        val list = places.toMutableList()
        if (at in list.indices) list[at] = current
        // The place left may now be the same as one beside it: those become one, and the index follows.
        val cleaned = ArrayList<Place>(list.size)
        var target = index
        list.forEachIndexed { i, place ->
            if (cleaned.isNotEmpty() && cleaned.last() == place) {
                if (i <= index) target--
            } else cleaned += place
        }
        return PlaceTrail(cleaned, target.coerceIn(0, cleaned.lastIndex))
    }

    fun back(current: Place): PlaceTrail = goTo(at - 1, current)

    fun forward(current: Place): PlaceTrail = goTo(at + 1, current)

    companion object {
        /** The places kept at most; the oldest go first. */
        const val MOST = 30

        private fun withoutRepeats(list: List<Place>): List<Place> =
            list.fold(ArrayList()) { out, place -> if (out.lastOrNull() != place) out += place; out }

        /** Kept across a turn of the screen as numbers: where the reader is, then each place's page and verse. */
        val Saver: Saver<PlaceTrail, List<Int>> = Saver(
            save = { trail -> listOf(trail.at) + trail.places.flatMap { listOf(it.page, it.ayahId) } },
            restore = { saved ->
                val places = saved.drop(1).chunked(2).filter { it.size == 2 }.map { Place(it[0], it[1]) }
                PlaceTrail(places, saved.firstOrNull()?.coerceIn(-1, places.lastIndex) ?: -1)
            }
        )
    }
}
