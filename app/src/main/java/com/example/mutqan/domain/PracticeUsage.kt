package com.example.mutqan.domain

/** Which exercises a reader has practised most, for the "most practised" part of the Practice list. */
object PracticeUsage {

    /**
     * The positions in [kinds] of the [count] most practised, most first. Each entry of [kinds] is the exercise types
     * one Practice entry records its answers under; [counts] is how many answers each type has. Entries tied, or
     * without any answer, keep their order in [kinds], so a new reader sees the first ones in the list as given.
     */
    fun mostPractised(counts: Map<ExerciseType, Int>, kinds: List<List<ExerciseType>>, count: Int): List<Int> =
        kinds.indices
            .sortedWith(compareByDescending<Int> { i -> kinds[i].sumOf { counts[it] ?: 0 } }.thenBy { it })
            .take(count)
}
