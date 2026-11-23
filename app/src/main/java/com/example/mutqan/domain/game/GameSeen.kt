package com.example.mutqan.domain.game

/**
 * The verses lately drawn for rounds of «فوانيس», kept (this device only) so the next rounds draw others while there are
 * others to draw: the most recent first, each verse once.
 */
object GameSeen {
    /** The most verses kept; the oldest go first. */
    const val MOST = 300

    /** "id,id,id", the most recent first. */
    fun parse(text: String?): List<Int> =
        text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id > 0 } }.distinct().take(MOST)

    fun format(ids: List<Int>): String = ids.joinToString(",")

    /** The list after a round drew [drawn] (in the order they come): those in front, each verse once. */
    fun after(seen: List<Int>, drawn: List<Int>): List<Int> = (drawn + seen).distinct().take(MOST)
}
