package com.example.mutqan.quran

/** What a page says in its running head: the surah it opens with and the juz it is in. */
internal data class MushafHead(val surah: String, val juz: Int)

internal const val HEAD_RULE_OFFSET = 0.55f

/** The words of a printed Mushaf's head and foot, which are Arabic whatever the app's language. */
internal object MushafHeader {

    private val ORDINALS = listOf(
        "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
        "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر",
        "الثامن عشر", "التاسع عشر", "العشرون", "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون",
        "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون",
        "التاسع والعشرون", "الثلاثون"
    )

    fun juzTitle(juz: Int): String = "الجزء " + ORDINALS[(juz - 1).coerceIn(0, ORDINALS.lastIndex)]

    /** [n] in Arabic-Indic digits, as page numbers are printed. */
    fun digits(n: Int): String = n.toString().map { '\u0660' + (it - '0') }.joinToString("")
}
