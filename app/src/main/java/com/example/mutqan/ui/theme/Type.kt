package com.example.mutqan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mutqan.quran.R as QuranR

/** Amiri is a classical Arabic typeface: used for the Quran text and for display type. */
val AmiriFontFamily = FontFamily(
    Font(QuranR.font.amiri_regular, FontWeight.Normal),
    Font(QuranR.font.amiri_bold, FontWeight.Bold)
)

/**
 * Paper typography: large headings and numerals in Amiri, quiet system sans for everything
 * else, no letter spacing (it breaks Arabic letter joining). Arabic gets taller lines.
 */
fun paperTypography(arabic: Boolean): Typography {
    fun body(size: Int, line: Int, arabicLine: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (if (arabic) arabicLine else line).sp,
        letterSpacing = 0.sp
    )

    fun display(size: Int, line: Int) = TextStyle(
        fontFamily = AmiriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = 0.sp
    )

    return Typography(
        displayLarge = display(72, 84),
        displayMedium = display(48, 58),
        displaySmall = display(36, 46),
        headlineLarge = display(32, 42),
        headlineMedium = display(28, 38),
        headlineSmall = display(24, 34),
        titleLarge = display(24, 36),
        titleMedium = display(20, 32),
        titleSmall = body(15, 22, 24, FontWeight.Medium),
        bodyLarge = body(16, 24, 28),
        bodyMedium = body(14, 22, 24),
        bodySmall = body(12, 18, 20),
        labelLarge = body(14, 20, 22, FontWeight.Medium),
        labelMedium = body(12, 16, 18, FontWeight.Medium),
        labelSmall = body(11, 16, 17, FontWeight.Medium)
    )
}
