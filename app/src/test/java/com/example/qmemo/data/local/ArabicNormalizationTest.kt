package com.example.qmemo.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicNormalizationTest {

    @Test
    fun testHamzaAlifSymmetry() {
        val standard = ArabicNormalization.normalizeForSearch("اتيناهم")
        val uthmani = ArabicNormalization.normalizeForSearch("ءاتيناهم")
        
        assertEquals("Standard and Uthmani Hamza-Alif should match", standard, uthmani)
        
        val madda = ArabicNormalization.normalizeForSearch("آتيناهم")
        assertEquals("Madda Alif should also match", standard, madda)
    }

    @Test
    fun testOmittedAlifSymmetry() {
        val standard = ArabicNormalization.normalizeForSearch("الصلوات")
        // Note: The second string contains the Dagger Alif (U+0670) on the Waw
        val uthmani = ArabicNormalization.normalizeForSearch("الصلو\u0670ت")
        
        assertEquals("Standard Alif and Uthmani Dagger Alif should match", standard, uthmani)
    }

    @Test
    fun testWawAlifSymmetry() {
        val standard = ArabicNormalization.normalizeForSearch("الصلاة")
        val uthmani = ArabicNormalization.normalizeForSearch("الصلو\u0670ة") // الصلوٰة
        
        assertEquals("Modern Alif and Uthmani Waw-Dagger-Teh pattern should match", standard, uthmani)
        
        val uthmaniSimple = ArabicNormalization.normalizeForSearch("الصلوة")
        assertEquals("Modern Alif and Uthmani Waw-Teh pattern should match", standard, uthmaniSimple)
    }

    @Test
    fun testCharacterUnification() {
        assertEquals("ى should match ي", 
            ArabicNormalization.normalizeForSearch("موسى"), 
            ArabicNormalization.normalizeForSearch("موسي")
        )
        assertEquals("ة should match ه", 
            ArabicNormalization.normalizeForSearch("الجنة"), 
            ArabicNormalization.normalizeForSearch("الجنه")
        )
    }
}
