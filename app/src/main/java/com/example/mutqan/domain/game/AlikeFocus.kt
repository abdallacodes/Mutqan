package com.example.mutqan.domain.game

/**
 * A round that dwells on mutashabihat: the verses it draws ([favoured]: each has a look-alike the reader knows that the
 * round can play), and for each, those look-alikes, to come right after it so the two are told apart side by side. A
 * look-alike may be anywhere the reader knows, outside the pages the verses are drawn from.
 */
class AlikeFocus(val favoured: Set<Int>, private val partners: Map<Int, List<Int>>) {

    val isEmpty: Boolean get() = favoured.isEmpty()

    /** The look-alikes of [ayahId] the round can play, the closest first. */
    fun partnersOf(ayahId: Int): List<Int> = partners[ayahId].orEmpty()

    companion object {
        /**
         * The focus for a round drawn from [pool]: a verse counts when it has a look-alike the round can play ([playable],
         * not with its very words, [same]) in the reader's own groups, or on a page they know ([known]) among the ready
         * lists' ([alike]) and the verses sharing most of its words ([close]). Those look-alikes are its partners.
         */
        fun build(
            pool: Collection<Int>,
            alike: AlikeVerses,
            close: (Int) -> List<Int>,
            known: (Int) -> Boolean,
            playable: (Int) -> Boolean = { true },
            same: (Int, Int) -> Boolean = { _, _ -> false }
        ): AlikeFocus {
            val favoured = HashSet<Int>()
            val partners = HashMap<Int, List<Int>>()
            for (id in pool.toHashSet()) {
                val others = LinkedHashSet<Int>()
                for ((other, weight) in alike.of(id)) if (weight >= NextWords.FROM_GROUP || known(other)) others += other
                for (other in close(id)) if (known(other)) others += other
                others.remove(id)
                val playing = others.filter { playable(it) && !same(id, it) }
                if (playing.isEmpty()) continue
                favoured += id
                partners[id] = playing
            }
            return AlikeFocus(favoured, partners)
        }
    }
}
