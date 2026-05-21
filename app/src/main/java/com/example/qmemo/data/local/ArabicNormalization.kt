package com.example.qmemo.data.local

/**
 * Robust Arabic normalization utility for Quranic search.
 * Handles Uthmani script discrepancies vs standard user input.
 */
object ArabicNormalization {

    // 1. All Harakaat and Quranic symbols
    private val diacriticsRegex = Regex("[\u064B-\u065F\u06D6-\u06ED]")

    fun normalizeForSearch(text: String): String {
        if (text.isBlank()) return ""

        var normalized = text

        // STEP 1: Handle the Waw/Ya seat logic FIRST
        // This handles cases like الصلوة and الصلوٰة
        // \u0648: Waw, \u0670: Dagger Alif, [\u0629\u0647]: Teh Marbuta or Heh
        normalized = normalized.replace(Regex("\u0648\u0670?([\u0629\u0647])"), "\u0627$1")

        // STEP 2: Convert remaining Dagger Alifs to standard Alif
        normalized = normalized.replace("\u0670", "\u0627")

        // STEP 3: Strip ALL diacritics
        normalized = normalized.replace(diacriticsRegex, "")

        // STEP 4: Unify Hamzas/Alifs
        normalized = normalized.replace(Regex("[ءأإآٱؤئ]"), "\u0627")

        // STEP 5: Collapse Alif sequences
        normalized = normalized.replace(Regex("\u0627+"), "\u0627")

        // STEP 6: Final Character Unification
        normalized = normalized.replace("\u0649", "\u064A") // ى -> ي
        normalized = normalized.replace("\u0629", "\u0647") // ة -> ه

        return normalized.trim()
    }
}
