package com.example.qmemo.util

/**
 * Global sanitizer for Quranic text.
 * Keeps Arabic letters and Tashkeel/Harakat.
 * Removes Waqf signs and structural markers (Rub el Hizb, End of Ayah).
 */
fun String.toCleanQuranicText(): String {
    if (this.isBlank()) return ""

    // 1. Define Waqf Signs (Stop signs)
    // \u0615: Small High Tah
    // \u0617: Small High Zain
    // \u06D6 - \u06DB: Small High Sali, Qali, Meem, Lam Alif, Jeem, Three Dots
    val waqfSigns = "[\u0615\u0617\u06D6\u06D7\u06D8\u06D9\u06DA\u06DB]"

    // 2. Define Structural Markers
    // \u06DE: Rub el Hizb (۞)
    // \u06DD: End of Ayah symbol
    val structuralMarkers = "[\u06DE\u06DD]"

    val pattern = Regex("$waqfSigns|$structuralMarkers")
    
    // Remove matches and collapse extra spaces
    return this.replace(pattern, "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
