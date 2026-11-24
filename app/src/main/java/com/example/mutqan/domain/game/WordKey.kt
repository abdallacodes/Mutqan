package com.example.mutqan.domain.game

/**
 * How «فوانيس» tells words apart: by every letter and mark that belongs to the word, so أَنَّ and إِنَّ are two words, and
 * so are مِن and مَن, عَلِمَ and عُلِمَ, ٱلسَّمَآءُ and ٱلسَّمَآءِ (the search of the app, which ignores marks and the shape of
 * the alef, is not used here: a reader must tell these apart).
 *
 * What is left out is not part of the word: the stop signs, the signs of a section or of prostration that the Mushaf
 * prints beside it, the stretching line, and the small meem that marks a run-on (iqlab). And the marks the Mushaf puts
 * on a word because of the word next to it, which a word has in one verse and not in another, are left out too:
 * - the open tanweens before some letters are the plain ones (عَلِيمٞ is عَلِيمٌ);
 * - a shadda on the first letter comes from the word before merging into it (رَّبِّكَ is رَبِّكَ);
 * - a sukun on the last letter is written or not by what follows (هُمۡ is هُم, مِنۡ is مِّن);
 * - a madda on the last letter is there because the next word starts with a hamza (إِذَآ is إِذَا, ءَامَنُوٓاْ is ءَامَنُواْ);
 * - a tanween before a ب is written as its short vowel and a small meem (أَلِيمُۢ is أَلِيمٌ, كَافِرِۭ is كَافِرٍ).
 */
object WordKey {

    /** The key of one word as the Quran text has it ([QuranWord.hafs][com.example.mutqan.quran.QuranWord.hafs]). */
    fun of(word: String): String {
        val kept = StringBuilder(word.length)
        for (c in runOnTanween(word)) {
            when (c) {
                // Stop signs (small high tah, zain, sali, qali, meem, lam-alef, jeem, three dots), end of ayah, rub el
                // hizb, prostration, the stretching line, the small meems of a run-on.
                'ؕ', 'ؗ', in 'ۖ'..'ۛ', '۝', '۞', 'ۢ', '۩', 'ۭ', 'ـ' -> Unit
                // The open tanweens: fathatan, dammatan and kasratan as the Mushaf writes them before some letters.
                'ٗ' -> kept.append('ً')
                'ٞ' -> kept.append('ٌ')
                'ٖ' -> kept.append('ٍ')
                else -> if (!c.isWhitespace()) kept.append(c)
            }
        }
        val s = kept.toString()
        val letters = s.indices.filter { isLetter(s[it]) }
        if (letters.isEmpty()) return s
        val first = letters.first()
        val second = letters.getOrElse(1) { s.length }
        // The last letter that is said: a silent alef at the end (its round sign on it) does not count.
        var last = letters.last()
        if (s[last] == 'ا' && letters.size > 1 && s.substring(last + 1).any { it == 'ْ' || it == '۟' }) last = letters[letters.size - 2]
        val lastEnd = letters.firstOrNull { it > last } ?: s.length
        val out = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            if (c == SHADDA && i in (first + 1) until second) continue
            if ((c == MADDA || c == SUKUN || c == SUKUN_ROUND) && i in (last + 1) until lastEnd) continue
            out.append(c)
        }
        return out.toString()
    }

    /**
     * A tanween run on into a ب (iqlab) as the plain tanween: the Mushaf writes it as the short vowel with a small meem
     * (أَلِيمُۢ, كَافِرِۭ, أَبَدَۢا), at the end of the word (an alef may follow).
     */
    private fun runOnTanween(word: String): String {
        if (word.indexOf(IQLAB_HIGH) < 0 && word.indexOf(IQLAB_LOW) < 0) return word
        val out = StringBuilder(word.length)
        for (i in word.indices) {
            val c = word[i]
            val atEnd = (i + 1 until word.length).none { isLetter(word[it]) && word[it] != 'ا' && word[it] != 'ى' }
            val tanween = when {
                !atEnd -> null
                c == IQLAB_HIGH && out.lastOrNull() == 'َ' -> 'ً'
                c == IQLAB_HIGH && out.lastOrNull() == 'ُ' -> 'ٌ'
                c == IQLAB_LOW && out.lastOrNull() == 'ِ' -> 'ٍ'
                else -> null
            }
            if (tanween != null) out.setCharAt(out.length - 1, tanween) else out.append(c)
        }
        return out.toString()
    }

    /** The key of a verse: the keys of its words, one space apart. Verses with the same key say the same words. */
    fun ofVerse(text: String): String =
        text.split(' ', '\n', '\t').map(::of).filter { it.isNotEmpty() }.joinToString(" ")

    /** An Arabic letter of the text (the small waw and ya written after a letter count too); not a mark. */
    internal fun isLetter(c: Char): Boolean = c in 'ء'..'ي' || c == 'ٱ' || c == 'ۥ' || c == 'ۦ'

    private const val SHADDA = 'ّ'
    private const val MADDA = 'ٓ'
    private const val SUKUN = 'ۡ'
    private const val SUKUN_ROUND = 'ْ'
    private const val IQLAB_HIGH = 'ۢ'
    private const val IQLAB_LOW = 'ۭ'
}
