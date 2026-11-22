package com.example.mutqan.domain.game

/**
 * The verses slipped on in «فوانيس», kept so they come first in the next rounds: each with the clean builds it still
 * needs to leave the list. A slip (a wrong lantern, the wanted word floating away, or the verse left unfinished) puts a
 * verse at the front needing [CLEAN_TO_LEAVE] clean builds; each clean build counts one off.
 */
object GameSlips {
    const val CLEAN_TO_LEAVE = 2

    /** The most verses kept; the oldest slips go first. */
    const val MOST = 60

    /** "ayah:left,ayah:left", the most recent slip first. */
    fun parse(text: String?): LinkedHashMap<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        for (item in text.orEmpty().split(',')) {
            val parts = item.split(':')
            val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
            val left = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: CLEAN_TO_LEAVE
            if (id > 0 && left > 0 && id !in out) out[id] = left.coerceAtMost(CLEAN_TO_LEAVE)
        }
        return out
    }

    fun format(slips: Map<Int, Int>): String = slips.entries.joinToString(",") { "${it.key}:${it.value}" }

    /** The list after a round in which [played] were played, in the order they came. */
    fun after(slips: Map<Int, Int>, played: List<PlayedVerse>): LinkedHashMap<Int, Int> {
        val list = LinkedHashMap(slips)
        for (verse in played) {
            if (verse.mistakes > 0 || !verse.done) {
                // To the front.
                list.remove(verse.ayahId)
                val rest = LinkedHashMap(list)
                list.clear()
                list[verse.ayahId] = CLEAN_TO_LEAVE
                list.putAll(rest)
            } else {
                val left = list[verse.ayahId] ?: continue
                if (left <= 1) list.remove(verse.ayahId) else list[verse.ayahId] = left - 1
            }
        }
        while (list.size > MOST) list.remove(list.keys.last())
        return list
    }
}
