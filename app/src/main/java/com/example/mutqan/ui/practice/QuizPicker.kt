package com.example.mutqan.ui.practice

import android.content.Context
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.PassageState
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.domain.QuizEngine
import com.example.mutqan.domain.QuizQuestion
import com.example.mutqan.domain.QuizText
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.TopicLanguage
import com.example.mutqan.domain.TopicsLibrary
import com.example.mutqan.domain.VerseClosingsLibrary
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Chooses the pages and builds the questions of a sitting. Pages are drawn mostly from what is memorised, favouring
 * pages that are weak or were missed recently, and not the last few again. The quiz screen and the Practice screen's
 * preview use the same picker, so the preview is a question the quiz could really ask.
 */
class QuizPicker private constructor(
    private val quran: QuranRepository,
    private val engine: QuizEngine,
    private val weights: IntArray,
    private val random: Random,
    /** True when a choice of pages was made and no memorised page fits it. */
    val scopeEmpty: Boolean
) {
    private val recent = ArrayDeque<Int>()

    /** Counts [page] as just asked, so the next questions look elsewhere. */
    fun markAsked(page: Int) {
        recent.addLast(page)
        while (recent.size > RECENT_PAGES) recent.removeFirst()
    }

    /** A question of [type], or null when none could be built from the pages allowed. */
    fun build(type: ExerciseType, hard: Boolean): QuizQuestion? {
        repeat(TRIES) {
            val page = pickPage() ?: return null
            val ayahs = quran.ayahsOnPage(page)
            if (ayahs.isEmpty()) return@repeat
            val q = engine.build(type, ayahs.random(random).id, hard)
            if (q != null) {
                markAsked(page)
                return q
            }
        }
        return null
    }

    private fun pickPage(): Int? {
        val w = IntArray(PAGES) { i -> if (i + 1 in recent) 0 else weights[i] }
        val index = QuizText.weightedPick(w, random) ?: QuizText.weightedPick(weights, random)
        return index?.plus(1)
    }

    companion object {
        private const val PAGES = 604
        private const val BASE_WEIGHT = 2
        private const val WEAK_BONUS = 6
        private const val MISS_BONUS = 4
        private const val TRIES = 60
        private const val RECENT_PAGES = 8
        private const val MISS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * A picker over the saved choice of pages, or, when [only] is given, over exactly those pages. Loads what the
         * engine needs (the look-alike verses, closings and topics), so it runs off the main thread.
         */
        suspend fun create(
            context: Context,
            study: StudyRepository,
            quran: QuranRepository,
            only: Set<Int>? = null,
            random: Random = Random.Default
        ): QuizPicker = withContext(Dispatchers.Default) {
            val appContext = context.applicationContext
            val covered = study.coveredPages().first()
            val logs = study.revisionLogs().first()
            val states = PassageStates.compute(logs)
            val misses = study.quizMissesSince(System.currentTimeMillis() - MISS_WINDOW_MS).first().map { it.page }.toSet()
            val allowed: Set<Int>
            val scopeEmpty: Boolean
            if (only != null) {
                allowed = only
                scopeEmpty = false
            } else {
                val scope = UserPreferencesRepository(appContext).practiceScope.first()
                allowed = scope.pages(covered, states, study.selfRatedStrength().first(), logs)
                scopeEmpty = !scope.isAll && allowed.isEmpty()
            }
            val weights = IntArray(PAGES) { i ->
                if ((i + 1) !in allowed) 0
                else BASE_WEIGHT + (if (states[i] == PassageState.NEEDS_ATTENTION) WEAK_BONUS else 0) + (if (i + 1 in misses) MISS_BONUS else 0)
            }
            val language = TopicLanguage(arabicUi = appContext.resources.configuration.locales[0].language == "ar")
            val engine = QuizEngine(
                quran, SimilarLibrary.load(appContext), random, VerseClosingsLibrary.load(appContext),
                TopicsLibrary.load(appContext), language
            )
            QuizPicker(quran, engine, weights, random, scopeEmpty)
        }
    }
}
