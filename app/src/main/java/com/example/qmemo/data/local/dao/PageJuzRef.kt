package com.example.qmemo.data.local.dao

import androidx.room.ColumnInfo

/** Lightweight projection used to build the page → juz mapping in memory. */
data class PageJuzRef(
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "juz_id")      val juzId:      Int
)
