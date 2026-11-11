package com.example.mutqan.domain

/**
 * The pages worth a focused set of questions: those a recall attempt showed to be weak, and if there are none, those
 * that show some recall but have not yet been repeated. Only memorised pages count. Pages never tested are not
 * "weak", so a reader who has only just begun gets no suggestion rather than the whole Mushaf.
 */
object FocusPages {

    /** The recall state the pages are in, and the pages (1-based). */
    class Focus(val state: PassageState, val pages: Set<Int>)

    private val ORDER = listOf(PassageState.NEEDS_ATTENTION, PassageState.DEVELOPING)

    /** [covered] is what is memorised (null: not set, so every page); [states] is each page's state, index 0 = page 1. */
    fun pick(covered: BooleanArray?, states: Array<PassageState>): Focus? {
        for (state in ORDER) {
            val pages = LinkedHashSet<Int>()
            for (i in states.indices) {
                val memorised = covered?.getOrElse(i) { false } ?: true
                if (memorised && states[i] == state) pages += i + 1
            }
            if (pages.isNotEmpty()) return Focus(state, pages)
        }
        return null
    }
}
