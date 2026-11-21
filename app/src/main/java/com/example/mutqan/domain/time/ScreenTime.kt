package com.example.mutqan.domain.time

import android.content.Context
import android.os.SystemClock
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.data.local.dao.TimeTotal
import com.example.mutqan.data.local.entity.TimeSpentEntity
import com.example.mutqan.domain.HabitSnapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The parts of the app whose time is counted. [idleMs]: with no touch for this long, the time stops counting. */
enum class TimeKind(val code: String, val idleMs: Long) {
    /** Reading in the Mushaf (a page can take a while to read, and recitation keeps it going). */
    MUSHAF("mushaf", 3 * 60_000L),

    /** The practice exercises, quizzes and the game. */
    PRACTICE("practice", 2 * 60_000L);

    companion object {
        fun fromCode(code: String): TimeKind? = entries.firstOrNull { it.code == code }
    }
}

/** When the user last touched the screen (any screen of the app), on the elapsed-time clock. */
object UserActivity {
    @Volatile
    var lastTouch: Long = SystemClock.elapsedRealtime()
        private set

    fun touched() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    /** Whether there was a touch within [idleMs] of [now]. */
    fun activeWithin(idleMs: Long, now: Long = SystemClock.elapsedRealtime()): Boolean = now - lastTouch <= idleMs
}

/** Reads and adds the time spent; additions go to the local day they are made on. */
class TimeRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context.applicationContext).timeDao()

    suspend fun add(kind: TimeKind, millis: Long, day: Long = HabitSnapshots.localDay()) {
        if (millis <= 0) return
        withContext(Dispatchers.IO) { dao.add(day, kind.code, millis) }
    }

    fun since(fromDay: Long): Flow<List<TimeSpentEntity>> = dao.since(fromDay)

    fun totals(): Flow<List<TimeTotal>> = dao.totals()
}

/** What the screens show of the time spent. */
data class TimeSummary(
    val todayMushaf: Long = 0,
    val todayPractice: Long = 0,
    /** The last seven days, oldest first: (day, Mushaf, practice). */
    val week: List<Triple<Long, Long, Long>> = emptyList(),
    val totalMushaf: Long = 0,
    val totalPractice: Long = 0
) {
    val weekMushaf: Long get() = week.sumOf { it.second }
    val weekPractice: Long get() = week.sumOf { it.third }
    val isEmpty: Boolean get() = totalMushaf + totalPractice == 0L

    companion object {
        fun of(rows: List<TimeSpentEntity>, totals: List<TimeTotal>, today: Long): TimeSummary {
            fun on(day: Long, kind: TimeKind) = rows.filter { it.day == day && it.kind == kind.code }.sumOf { it.millis }
            val week = (today - 6..today).map { Triple(it, on(it, TimeKind.MUSHAF), on(it, TimeKind.PRACTICE)) }
            return TimeSummary(
                todayMushaf = on(today, TimeKind.MUSHAF),
                todayPractice = on(today, TimeKind.PRACTICE),
                week = week,
                totalMushaf = totals.filter { it.kind == TimeKind.MUSHAF.code }.sumOf { it.millis },
                totalPractice = totals.filter { it.kind == TimeKind.PRACTICE.code }.sumOf { it.millis }
            )
        }
    }
}
