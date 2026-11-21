package com.example.mutqan.data.local.entity

import androidx.room.Entity

/**
 * Time spent in one part of the app on one local day: [kind] is a [com.example.mutqan.domain.time.TimeKind] code,
 * [day] the local epoch day, [millis] the time counted while it was in use. Kept on this device only (not in backups
 * or sync), like a phone's own screen time.
 */
@Entity(tableName = "time_spent", primaryKeys = ["day", "kind"])
data class TimeSpentEntity(
    val day: Long,
    val kind: String,
    val millis: Long
)
