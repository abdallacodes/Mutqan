package com.example.mutqan.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.example.mutqan.data.local.entity.TimeSpentEntity
import kotlinx.coroutines.flow.Flow

/** All the time counted for one kind. */
data class TimeTotal(val kind: String, val millis: Long)

@Dao
abstract class TimeDao {

    @Query("INSERT OR IGNORE INTO time_spent (day, kind, millis) VALUES (:day, :kind, 0)")
    protected abstract suspend fun ensure(day: Long, kind: String)

    @Query("UPDATE time_spent SET millis = millis + :millis WHERE day = :day AND kind = :kind")
    protected abstract suspend fun addTo(day: Long, kind: String, millis: Long)

    /** Adds [millis] to [kind] on [day]. (No upsert: Android before 11 has an SQLite without it.) */
    @Transaction
    open suspend fun add(day: Long, kind: String, millis: Long) {
        ensure(day, kind)
        addTo(day, kind, millis)
    }

    @Query("SELECT * FROM time_spent WHERE day >= :from ORDER BY day")
    abstract fun since(from: Long): Flow<List<TimeSpentEntity>>

    @Query("SELECT kind, SUM(millis) AS millis FROM time_spent GROUP BY kind")
    abstract fun totals(): Flow<List<TimeTotal>>
}
