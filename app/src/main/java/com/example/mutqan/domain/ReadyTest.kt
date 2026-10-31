package com.example.mutqan.domain

import kotlin.random.Random

/** Choosing a question for the mutashabihat test from the bundled, ready-made groups. */
object ReadyTest {

    /** How many groups are remembered so that the next question is about a different one. */
    const val RECENT_LIMIT = 12

    /**
     * A group and one of its verses to ask about. Groups where the user has memorised two or more of the lookalikes
     * come first (that is where they can mix them up), then groups with one; when nothing they have memorised is in a
     * group, or they have memorised nothing, any group will do. Groups in [recent] are skipped while others remain.
     * [memorised] says whether a verse (global ayah id) is memorised; [anyMemorised] whether the user has any coverage.
     */
    fun pick(
        groups: List<ReadyGroup>,
        memorised: (Int) -> Boolean,
        anyMemorised: Boolean,
        recent: Collection<Int>,
        random: Random = Random
    ): Pair<ReadyGroup, GroupMember>? {
        if (groups.isEmpty()) return null
        val fresh = groups.filter { it.id !in recent }.ifEmpty { groups }
        val pool = if (anyMemorised) {
            val known = fresh.associateWith { g -> g.members.count { memorised(it.ayahId) } }
            known.filterValues { it >= 2 }.keys.toList()
                .ifEmpty { known.filterValues { it >= 1 }.keys.toList() }
                .ifEmpty { fresh }
        } else {
            fresh
        }
        val group = pool.random(random)
        val member = group.members.filter { memorised(it.ayahId) }.ifEmpty { group.members }.random(random)
        return group to member
    }

    /** The word positions (1-based) covered by [ranges]. */
    fun hits(ranges: List<IntRange>): Set<Int> = ranges.flatMapTo(HashSet()) { it.toList() }
}
