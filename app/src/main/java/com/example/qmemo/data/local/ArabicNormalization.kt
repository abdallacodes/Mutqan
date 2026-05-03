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

        // STEP 1: Convert Dagger Alif to standard Alif immediately
        // This is the bridge for الصلوت -> الصلوات
        normalized = normalized.replace("\u0670", "\u0627")

        // STEP 2: Strip ALL diacritics immediately
        // This ensures the "Search Bar" and "Database" are on the same playing field
        normalized = normalized.replace(diacriticsRegex, "")

        // STEP 3: Handle the Waw/Ya seat logic CAREFULLY
        // We only want to convert Waw to Alif in specific Uthmani contexts (like الصلوة)
        // We do NOT want to turn 'وان' into 'ان'.

        // Fix for: الصلوة -> الصلاة (Waw + Teh Marbuta/Heh)
        normalized = normalized.replace(Regex("\u0648[\u0629\u0647]"), "\u0627\u0647")

        // Fix for: الصلوات vs الصلوت
        // Instead of turning 'وا' into 'ا', we ensure the index preserves the 'و'
        // if it's a prefix, but treats 'Dagger Alif' as a standard Alif.
        // (Note: Step 1 already handled the Dagger Alif, so 'الصلوت' is already 'الصلوات')

        // STEP 4: Unify Hamzas/Alifs
        // This converts 'ءاتيناهم' and 'أتيناهم' to 'ااتيناهم'
        normalized = normalized.replace(Regex("[ءأإآٱؤئ]"), "\u0627")

        // STEP 5: Collapse Alif sequences
        // This is the MAGIC fix for 'كيف وان' vs 'كيف ءان'
        // 'ااتيناهم' becomes 'اتيناهم'. 'وان' stays 'وان'.
        normalized = normalized.replace(Regex("\u0627+"), "\u0627")

        // STEP 6: Final Character Unification
        normalized = normalized.replace("\u0649", "\u064A") // ى -> ي
        normalized = normalized.replace("\u0629", "\u0647") // ة -> ه

        return normalized.trim()
    }
}