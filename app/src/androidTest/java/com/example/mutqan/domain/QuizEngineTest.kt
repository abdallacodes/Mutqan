package com.example.mutqan.domain

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Builds real questions from the bundled Quran and look-alike data and checks they are well formed. */
@RunWith(AndroidJUnit4::class)
class QuizEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyKindOfQuestionIsWellFormed() = runBlocking {
        val quran = QuranRepository.getInstance(context)
        val engine = QuizEngine(
            quran, SimilarLibrary.load(context), closings = VerseClosingsLibrary.load(context),
            topics = TopicsLibrary.load(context), topicLanguage = TopicLanguage(arabicUi = true)
        )
        for (type in ExerciseType.QUIZ) {
            var built = 0
            for (id in 1..6236 step 5) {
                when (val q = engine.build(type, id, hard = id % 2 == 0)) {
                    null -> {}
                    is ChoiceQuestion -> {
                        built++
                        assertTrue(q.options.size in 2..4)
                        assertTrue(q.correct in q.options.indices)
                        assertEquals(q.options.size, q.options.map { QuizText.normalize(it.plain) }.toSet().size)
                        assertTrue(q.prompt.plain.isNotBlank() && q.revealed.plain.isNotBlank())
                        if (type == ExerciseType.QUIZ_FINISH) assertTrue(q.prompt.plain.contains(MushafSpan.BLANK))
                    }
                    is OrderQuestion -> {
                        built++
                        assertEquals(q.shuffled.size, q.correctOrder.toSet().size)
                        assertTrue(q.correctOrder != q.correctOrder.sorted() || q.shuffled.size < 2)
                        assertEquals(q.shuffled.size, q.ayahNumbers.size)
                    }
                    is WhereQuestion -> {
                        built++
                        assertTrue(q.subject.surah in q.surahOptions)
                        assertTrue(q.subject.ayah in q.ayahOptions)
                        assertEquals(q.surahOptions.size, q.surahOptions.toSet().size)
                    }
                }
            }
            assertTrue("$type built only $built questions", built >= 20)
        }
    }
}
