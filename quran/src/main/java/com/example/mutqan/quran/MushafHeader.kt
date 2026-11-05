package com.example.mutqan.quran

/** What a page says in its running head: the surah it opens with (its name and number) and the juz it is in. */
internal data class MushafHead(val surah: String, val juz: Int, val surahId: Int = 0)

internal const val HEAD_RULE_OFFSET = 0.42f

/** The words of a printed Mushaf's head and foot, which are Arabic whatever the app's language. */
internal object MushafHeader {

    /** [n] in Arabic-Indic digits, as page numbers are printed. */
    fun digits(n: Int): String = n.toString().map { '\u0660' + (it - '0') }.joinToString("")
}
