package com.example.mutqan.domain.sync

/**
 * The ids of the pieces under `users/{uid}/chunks`: `0000`, `0001`, ...
 *
 * Builds up to v6.4.1 made them with `"%04d".format(i)`, which follows the phone's language: on a device whose Arabic
 * uses Arabic-Indic numerals the first piece was saved as `٠٠٠٠`, and every other device then looked for `0000`, found
 * nothing and called the copy damaged. Ids are now always ASCII, and a copy saved under the old ones is still read.
 */
object ChunkIds {
    /** The id a piece is written under. */
    fun of(index: Int): String = "%04d".format(java.util.Locale.ROOT, index)

    /**
     * The piece an id stands for, whatever digits it is written in (`0001`, `٠٠٠١`, `۰۰۰۱`), or null if it is not a number.
     */
    fun indexOf(id: String): Int? {
        if (id.isEmpty() || id.length > 9) return null
        var n = 0
        for (c in id) {
            val d = Character.digit(c, 10)
            if (d < 0) return null
            n = n * 10 + d
        }
        return n
    }

    /** The ids an older build may have saved piece [index] under (Arabic-Indic and Persian digits), other than [of]. */
    fun legacy(index: Int): List<String> {
        val ascii = of(index)
        return listOf('٠', '۰').map { zero -> ascii.map { zero + (it - '0') }.joinToString("") }
    }

    /**
     * The pieces `0 until count` in order, taken from [found] (id -> data), or null if any is missing. An ASCII id wins
     * over an older spelling of the same piece.
     */
    fun assemble(count: Int, found: Map<String, String>): List<String>? {
        val byIndex = HashMap<Int, String>()
        for ((id, data) in found) {
            val i = indexOf(id) ?: continue
            if (id == of(i) || i !in byIndex) byIndex[i] = data
        }
        return (0 until count).map { byIndex[it] ?: return null }
    }
}
