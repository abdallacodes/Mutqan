package com.example.mutqan.ui.home

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Date
import java.util.Locale

/** Today's date in the Umm al-Qura Hijri calendar, written in [locale]'s language, e.g. "5 ربيع الأول 1448". */
fun hijriDate(time: Long, locale: Locale): String {
    val ulocale = ULocale.forLocale(locale).let { ULocale("${it.language}@calendar=islamic-umalqura") }
    return SimpleDateFormat("d MMMM y", ulocale).format(Date(time))
}
