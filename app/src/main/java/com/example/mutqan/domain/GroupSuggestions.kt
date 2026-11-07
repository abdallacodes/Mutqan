package com.example.mutqan.domain

/**
 * An ayah that could join a group: [hits] is how many of the group's ayahs it resembles, [score] the best QUL score
 * (50..100) among them, [matchedWords] the words in this ayah that match (1-based, inclusive; empty when unknown).
 */
data class GroupSuggestion(val ayahId: Int, val hits: Int, val score: Int, val matchedWords: List<IntRange>)

/** Which ayahs to propose for a mutashabihat group, from the bundled similar-ayahs data. */
object GroupSuggestions {

    /**
     * The ayahs that resemble the ones in [members], best first: those that resemble more of the group's ayahs come
     * before those that resemble one, then by the strongest match. Ayahs already in the group or in [dismissed] are left
     * out. Nothing is proposed for an empty group. [limit] caps the list.
     */
    fun rank(
        similar: SimilarData,
        members: Collection<Int>,
        dismissed: Set<Int> = emptySet(),
        limit: Int = 40
    ): List<GroupSuggestion> {
        if (members.isEmpty()) return emptyList()
        val inGroup = members.toSet()
        val best = HashMap<Int, SimilarMatch>()
        val hits = HashMap<Int, Int>()
        for (member in inGroup) {
            for (match in similar.similarTo(member)) {
                val id = match.ayahId
                if (id in inGroup || id in dismissed) continue
                hits[id] = (hits[id] ?: 0) + 1
                val current = best[id]
                if (current == null || match.score > current.score) best[id] = match
            }
        }
        return best.values
            .map { GroupSuggestion(it.ayahId, hits.getValue(it.ayahId), it.score, it.matchedWords) }
            .sortedWith(
                compareByDescending<GroupSuggestion> { it.hits }
                    .thenByDescending { it.score }
                    .thenBy { it.ayahId }
            )
            .take(limit)
    }
}
