package com.example.mutqan.domain

import com.example.mutqan.quran.QuranWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The look-alike questions, on the verses where Iblis would not bow down: Al-Baqarah 34, Al-A'raf 11, Al-Isra 61,
 * Al-Kahf 50 and Taha 116, in the bundled Hafs text.
 */
class QuizPartingTest {

    private fun words(vararg hafs: String) = hafs.mapIndexed { i, t -> QuranWord(i + 1, 1, 1, i + 1, t, t, false, 1) }
    private fun plain(w: List<QuranWord>) = w.map { QuizText.normalize(it.hafs) }

    private val baqarah34 = words("وَإِذۡ", "قُلۡنَا", "لِلۡمَلَٰٓئِكَةِ", "ٱسۡجُدُواْ", "لِأٓدَمَ", "فَسَجَدُوٓاْ", "إِلَّآ", "إِبۡلِيسَ", "أَبَىٰ", "وَٱسۡتَكۡبَرَ", "وَكَانَ", "مِنَ", "ٱلۡكَٰفِرِينَ")
    private val araf11 = words("وَلَقَدۡ", "خَلَقۡنَٰكُمۡ", "ثُمَّ", "صَوَّرۡنَٰكُمۡ", "ثُمَّ", "قُلۡنَا", "لِلۡمَلَٰٓئِكَةِ", "ٱسۡجُدُواْ", "لِأٓدَمَ", "فَسَجَدُوٓاْ", "إِلَّآ", "إِبۡلِيسَ", "لَمۡ", "يَكُن", "مِّنَ", "ٱلسَّٰجِدِينَ")
    private val isra61 = words("وَإِذۡ", "قُلۡنَا", "لِلۡمَلَٰٓئِكَةِ", "ٱسۡجُدُواْ", "لِأٓدَمَ", "فَسَجَدُوٓاْ", "إِلَّآ", "إِبۡلِيسَ", "قَالَ", "ءَأَسۡجُدُ", "لِمَنۡ", "خَلَقۡتَ", "طِينٗا")
    private val kahf50 = words("وَإِذۡ", "قُلۡنَا", "لِلۡمَلَٰٓئِكَةِ", "ٱسۡجُدُواْ", "لِأٓدَمَ", "فَسَجَدُوٓاْ", "إِلَّآ", "إِبۡلِيسَ", "كَانَ", "مِنَ", "ٱلۡجِنِّ", "فَفَسَقَ", "عَنۡ", "أَمۡرِ", "رَبِّهِۦٓۗ", "أَفَتَتَّخِذُونَهُۥ", "وَذُرِّيَّتَهُۥٓ", "أَوۡلِيَآءَ", "مِن", "دُونِي", "وَهُمۡ", "لَكُمۡ", "عَدُوُّۢۚ", "بِئۡسَ", "لِلظَّٰلِمِينَ", "بَدَلٗا")
    private val taha116 = words("وَإِذۡ", "قُلۡنَا", "لِلۡمَلَٰٓئِكَةِ", "ٱسۡجُدُواْ", "لِأٓدَمَ", "فَسَجَدُوٓاْ", "إِلَّآ", "إِبۡلِيسَ", "أَبَىٰ")
    private val baqarah67 = words("وَإِذۡ", "قَالَ", "مُوسَىٰ", "لِقَوۡمِهِۦٓ", "إِنَّ", "ٱللَّهَ", "يَأۡمُرُكُمۡ", "أَن", "تَذۡبَحُواْ", "بَقَرَةٗۖ", "قَالُوٓاْ", "أَتَتَّخِذُنَا", "هُزُوٗاۖ", "قَالَ", "أَعُوذُ", "بِٱللَّهِ", "أَنۡ", "أَكُونَ", "مِنَ", "ٱلۡجَٰهِلِينَ")
    private val baqarah68 = words("قَالُواْ", "ٱدۡعُ", "لَنَا", "رَبَّكَ", "يُبَيِّن", "لَّنَا", "مَا", "هِيَۚ", "قَالَ", "إِنَّهُۥ", "يَقُولُ", "إِنَّهَا", "بَقَرَةٞ", "لَّا", "فَارِضٞ", "وَلَا", "بِكۡرٌ", "عَوَانُۢ", "بَيۡنَ", "ذَٰلِكَۖ", "فَٱفۡعَلُواْ", "مَا", "تُؤۡمَرُونَ")
    private val baqarah69 = words("قَالُواْ", "ٱدۡعُ", "لَنَا", "رَبَّكَ", "يُبَيِّن", "لَّنَا", "مَا", "لَوۡنُهَاۚ", "قَالَ", "إِنَّهُۥ", "يَقُولُ", "إِنَّهَا", "بَقَرَةٞ", "صَفۡرَآءُ", "فَاقِعٞ", "لَّوۡنُهَا", "تَسُرُّ", "ٱلنَّٰظِرِينَ")
    private val baqarah70 = words("قَالُواْ", "ٱدۡعُ", "لَنَا", "رَبَّكَ", "يُبَيِّن", "لَّنَا", "مَا", "هِيَ", "إِنَّ", "ٱلۡبَقَرَ", "تَشَٰبَهَ", "عَلَيۡنَا", "وَإِنَّآ", "إِن", "شَآءَ", "ٱللَّهُ", "لَمُهۡتَدُونَ")
    private val rahman13 = words("فَبِأَيِّ", "ءَالَآءِ", "رَبِّكُمَا", "تُكَذِّبَانِ")
    private val rahman14 = words("خَلَقَ", "ٱلۡإِنسَٰنَ", "مِن", "صَلۡصَٰلٖ", "كَٱلۡفَخَّارِ")
    private val rahman15 = words("وَخَلَقَ", "ٱلۡجَآنَّ", "مِن", "مَّارِجٖ", "مِّن", "نَّارٖ")
    private val rahman16 = words("فَبِأَيِّ", "ءَالَآءِ", "رَبِّكُمَا", "تُكَذِّبَانِ")

    @Test
    fun `the words two look-alikes share are found word for word, not where the data's loose match ends`() {
        // The look-alike data gives words 1 to 10 of Al-Kahf 50 as shared with Al-Baqarah 34: it takes "كان من" for
        // "وكان من". Word for word they share the first eight.
        val run = QuizText.sharedRun(plain(baqarah34), plain(kahf50), hint = 1..10, least = 3)!!
        assertEquals(1..8, run.inA)
        assertEquals(1..8, run.inB)
        // Al-A'raf 11 has the same words from its sixth on.
        val araf = QuizText.sharedRun(plain(baqarah34), plain(araf11), least = 3)!!
        assertEquals(2..8, araf.inA)
        assertEquals(6..12, araf.inB)
    }

    @Test
    fun `the question stops where the look-alikes part, the same word for all, and every choice goes on from there`() {
        val verse = plain(baqarah34)
        val looks = listOf(taha116, isra61, kahf50, araf11)
        val others = looks.map { plain(it) to QuizText.sharedRun(verse, plain(it), least = 3)!! }
        val parting = QuizText.parting(verse, others, { it in 3 until baqarah34.size }, shared = 3)!!
        // After "إلا إبليس", where they part: not after Taha's "أبى", which only Taha shares.
        assertEquals(8, parting.cut)
        assertEquals(4, parting.branches.size)
        // Taha 116 comes first: it shares "أبى" too and ends there, the mix-up most worth testing.
        assertEquals(0, parting.branches.first().index)
        val next = parting.branches.associate { it.index to looks[it.index].drop(it.from).first().hafs }
        assertEquals(taha116[8].hafs, next[0])
        assertEquals(isra61[8].hafs, next[1])
        // Al-Kahf goes on with "كان", not from the middle of its phrase.
        assertEquals(kahf50[8].hafs, next[2])
        assertEquals(araf11[12].hafs, next[3])
        // With no cut allowed there is no question.
        assertNull(QuizText.parting(verse, others, { false }, shared = 3))
    }

    @Test
    fun `asked about Taha 116, Al-Baqarah 34 is the choice that goes on past its end`() {
        val verse = plain(taha116)
        val looks = listOf(baqarah34, isra61)
        val others = looks.map { plain(it) to QuizText.sharedRun(verse, plain(it), least = 3)!! }
        val parting = QuizText.parting(verse, others, { it in 3 until taha116.size }, shared = 3)!!
        assertEquals(8, parting.cut)
        assertEquals(baqarah34[8].hafs, baqarah34.drop(parting.branches.first { it.index == 0 }.from).first().hafs)
    }

    @Test
    fun `a choice runs to the end of its verse or to a pause sign, and never ends in an ellipsis`() {
        // Al-Kahf 50 after "إلا إبليس": up to the pause sign after "ربه".
        val kahf = QuizText.phrase(kahf50.drop(8), most = 7, fallback = 5)
        assertEquals(7, kahf.words.size)
        assertTrue(QuizText.pauses(kahf.words.last()))
        // Al-Baqarah 34 ends within the limit: all of it.
        assertEquals(5, QuizText.phrase(baqarah34.drop(8), most = 7, fallback = 5).words.size)
        // No pause sign and no end within the limit: the first few words.
        val long = words("a", "b", "c", "d", "e", "f", "g", "h", "i")
        assertEquals(5, QuizText.phrase(long, most = 7, fallback = 5).words.size)
        for (p in listOf(kahf, QuizText.phrase(long, most = 7, fallback = 5))) assertFalse(p.plain.contains("…"))
    }
    @Test
    fun `the vault's finish question stops where all the look-alikes part, with Taha's whole ending among the choices`() {
        val set = SimilarityQuestions.finish(baqarah34, listOf(araf11, isra61, kahf50, taha116), Random(1))!!
        // The lead runs up to "إلا إبليس", not to "أبى", which only Taha shares.
        assertTrue(set.lead.plain.contains(baqarah34[7].hafs))
        assertFalse(set.lead.plain.contains(baqarah34[8].hafs))
        assertEquals(4, set.options.size)
        assertTrue(set.options[set.correct].plain.startsWith(baqarah34[8].hafs))
        // Taha's ending is "أبى" alone, with no ellipsis to make it seem to go on.
        assertTrue(set.options.any { it.plain == taha116[8].hafs })
        assertTrue(set.options.none { it.plain.contains("…") })
    }

    @Test
    fun `an order question shows verses that look alike until they differ, and takes either order for ones that are the same`() {
        // Al-Baqarah 68 and 70 share their first eight words: they are shown until they differ.
        val texts = QuizText.orderTexts(listOf(baqarah67, baqarah68, baqarah69, baqarah70), words = 8, whole = 9)
        assertEquals(4, texts.map { QuizText.normalize(it.plain) }.toSet().size)
        // Ar-Rahman 13 and 16 are the same word for word: they stay the same, and either order of the two is right.
        val rahman = QuizText.orderTexts(listOf(rahman13, rahman14, rahman15, rahman16), words = 8, whole = 9)
        assertTrue(QuizText.sameText(rahman[0], rahman[3]))
        val q = OrderQuestion(
            ExerciseType.QUIZ_ORDER, QuizRef(55, 13, 532), shuffled = listOf(rahman[3], rahman[1], rahman[0], rahman[2]),
            correctOrder = listOf(2, 1, 3, 0), ayahNumbers = listOf(13, 14, 15, 16), ayahIds = listOf(1, 2, 3, 4)
        )
        assertTrue(q.isRight(listOf(2, 1, 3, 0)))
        assertTrue(q.isRight(listOf(0, 1, 3, 2)))
        assertTrue((0..3).all { q.placedRight(listOf(0, 1, 3, 2), it) })
        assertFalse(q.isRight(listOf(1, 2, 3, 0)))
        assertFalse(q.placedRight(listOf(1, 2, 3, 0), 1))
    }

    @Test
    fun `a verse's end is shown from a pause sign, a too-early pause is passed over, and there is never an ellipsis`() {
        // Al-Kahf 50 ends "وهم لكم عدو، بئس للظالمين بدلا": shown from just after the pause sign.
        val end = QuizText.phraseEnd(kahf50, most = 8, fallback = 6)
        assertEquals(3, end.words.size)
        assertTrue(QuizText.pauses(kahf50[kahf50.size - 4]))
        assertFalse(end.plain.contains("…"))
        // A short verse whole; with no pause sign near the end, its last few words.
        assertEquals(taha116.size, QuizText.phraseEnd(taha116, most = 9, fallback = 6).words.size)
        val long = words("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        assertEquals(6, QuizText.phraseEnd(long, most = 8, fallback = 6).words.size)
        // A pause sign after the first word would leave too little to tell choices apart: the first few words instead.
        val early = words("قالۖ", "b", "c", "d", "e", "f", "g", "h", "i")
        assertEquals(6, QuizText.phrase(early, most = 8, fallback = 6).words.size)
    }
}
