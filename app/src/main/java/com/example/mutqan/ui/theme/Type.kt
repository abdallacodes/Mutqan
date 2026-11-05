package com.example.mutqan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.quran.R as QuranR

/** Amiri is a classical Arabic typeface: used for the Quran text and for display type. */
val AmiriFontFamily = FontFamily(
    Font(QuranR.font.amiri_regular, FontWeight.Normal),
    Font(QuranR.font.amiri_bold, FontWeight.Bold)
)

/** IBM Plex Sans Arabic (SIL OFL, licence in assets/licenses): the interface sans of design system v2; Latin is Plex Sans. */
val PlexSansArabicFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold)
)

/**
 * Design system v2 type: Amiri for display, headline, title-large and big numerals; IBM Plex Sans Arabic for
 * every smaller role. No letter spacing (it breaks Arabic joining); Arabic gets taller lines.
 */
fun folioTypography(arabic: Boolean): Typography {
    fun sans(size: Int, line: Int, arabicLine: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = PlexSansArabicFamily,
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
        titleMedium = sans(17, 24, 28, FontWeight.SemiBold),
        titleSmall = sans(15, 22, 25, FontWeight.SemiBold),
        bodyLarge = sans(17, 25, 30),
        bodyMedium = sans(15, 23, 26),
        bodySmall = sans(13, 19, 22),
        labelLarge = sans(15, 21, 24, FontWeight.Medium),
        labelMedium = sans(13, 17, 20, FontWeight.Medium),
        labelSmall = sans(12, 16, 19, FontWeight.Medium)
    )
}
