package com.example.mutqan.ui.practice

import com.example.mutqan.domain.ExerciseType
import com.example.mutqan.domain.QuizQuestion

/**
 * The first question of today's set, built on the Practice screen so the hero card can show it. The quiz that starts
 * from that card takes it from here, so what was shown is what is asked. It is held once: taking it empties the holder.
 */
object DailyPreview {

    /** [schedule] is the kind of each of the ten questions; its first is the kind of [first]. */
    class Held(val schedule: List<ExerciseType>, val first: QuizQuestion)

    private var held: Held? = null

    @Synchronized fun hold(value: Held) { held = value }

    @Synchronized fun peek(): Held? = held

    @Synchronized fun take(): Held? = held.also { held = null }

    @Synchronized fun clear() { held = null }
}
