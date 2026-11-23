package com.example.mutqan.domain.game

/**
 * How «فوانيس» tells words apart: by every letter and mark that belongs to the word, so أَنَّ and إِنَّ are two words, and
 * so are عَلِمَ and عُلِمَ (the search of the app, which ignores marks and the shape of the alef, is not used here: a
 * reader must tell these apart).
 *
 * What is left out is not part of the word: the stop signs, the signs of a section or of prostration that the Mushaf
 * prints beside it, the stretching line, and the small meem that marks a run-on (iqlab). The three open tanweens the
 * Mushaf prints before some letters are read as the plain ones, since a word is the same word before a pause and before
 * a letter: عَلِيمٞ is عَلِيمٌ.
 */
object WordKey {

    /** The key of one word as the Quran text has it ([QuranWord.hafs][com.example.mutqan.quran.QuranWord.hafs]). */
    fun of(word: String): String {
        val out = StringBuilder(word.length)
        for (c in word) {
            when (c) {
                // Stop signs (small high tah, zain, sali, qali, meem, lam-alef, jeem, three dots), end of ayah, rub el
                // hizb, prostration, the stretching line, the small meems of a run-on.
                'ؕ', 'ؗ', in 'ۖ'..'ۛ', '۝', '۞', 'ۢ', '۩', 'ۭ', 'ـ' -> Unit
                // The open tanweens: fathatan, dammatan and kasratan as the Mushaf writes them before some letters.
                'ٗ' -> out.append('ً')
                'ٞ' -> out.append('ٌ')
                'ٖ' -> out.append('ٍ')
                else -> if (!c.isWhitespace()) out.append(c)
            }
        }
        return out.toString()
    }

    /** The key of a verse: the keys of its words, one space apart. Verses with the same key say the same words. */
    fun ofVerse(text: String): String =
        text.split(' ', '\n', '\t').map(::of).filter { it.isNotEmpty() }.joinToString(" ")
}
