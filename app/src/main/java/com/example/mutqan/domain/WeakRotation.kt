package com.example.mutqan.domain

/**
 * Which few of the user's open weak passages Today shows. With a hundred of them, showing all would bury the plan, and
 * showing the same top two every day would leave the rest unseen; so a small window moves along the list, in its order
 * (most important first), by one window each day, and by one more each time the user asks for other ones.
 */
object WeakRotation {

    /**
     * [size] items of [items] starting where day number [day] and the [others] requests put the window. The list is a ring:
     * the window that runs past the end carries on from the start. A list no longer than [size] is shown whole.
     */
    fun <T> pick(items: List<T>, day: Long, others: Int, size: Int): List<T> {
        if (items.size <= size) return items
        val start = Math.floorMod((day + others) * size, items.size.toLong()).toInt()
        return List(size) { items[(start + it) % items.size] }
    }
}
