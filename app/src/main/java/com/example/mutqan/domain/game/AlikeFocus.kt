package com.example.mutqan.domain.game

/**
 * A round that dwells on mutashabihat: the verses it draws ([favoured]: each has a look-alike the reader knows), and for
 * each, its look-alikes that the round can hold, to come right after it so the two are told apart side by side.
 */
class AlikeFocus(val favoured: Set<Int>, private val partners: Map<Int, List<Int>>) {

    val isEmpty: Boolean get() = favoured.isEmpty()

    /** The look-alikes of [ayahId] the round can hold, the closest first. */
    fun partnersOf(ayahId: Int): List<Int> = partners[ayahId].orEmpty()

    companion object {
        /**
         * The focus for a round drawn from [pool]: a verse counts when it has a look-alike in the reader's own groups, or
         * one on a page they know ([known]) among the ready lists' ([alike]) and the verses sharing most of its words
         * ([close]). Its partners are those look-alikes that are in [pool] too.
         */
        fun build(pool: Collection<Int>, alike: AlikeVerses, close: (Int) -> List<Int>, known: (Int) -> Boolean): AlikeFocus {
            val inPool = pool.toHashSet()
            val favoured = HashSet<Int>()
            val partners = HashMap<Int, List<Int>>()
            for (id in inPool) {
                val others = LinkedHashSet<Int>()
                for ((other, weight) in alike.of(id)) if (weight >= NextWords.FROM_GROUP || known(other)) others += other
                for (other in close(id)) if (known(other)) others += other
                others.remove(id)
                if (others.isEmpty()) continue
                favoured += id
                val playable = others.filter { it in inPool }
                if (playable.isNotEmpty()) partners[id] = playable
            }
            return AlikeFocus(favoured, partners)
        }
    }
}
