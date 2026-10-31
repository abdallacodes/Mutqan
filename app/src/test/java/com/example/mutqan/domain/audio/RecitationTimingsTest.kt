package com.example.mutqan.domain.audio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The verse timings shipped for the reciters who have one file per surah must cover the Quran and run in order. */
class RecitationTimingsTest {

    private val ayahsPerSurah = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77,
        227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62,
        55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29,
        19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )

    private fun check(id: String) {
        val root = JSONObject(File("src/main/assets/recitation/$id.json").readText())
        val times = root.getJSONObject("t")
        assertTrue(root.getString("url").contains("%03d"))
        for (surah in 1..114) {
            val windows = times.getJSONArray(surah.toString())
            assertEquals("$id surah $surah", ayahsPerSurah[surah - 1], windows.length())
            var previousEnd = 0L
            for (i in 0 until windows.length()) {
                val from = windows.getJSONArray(i).getLong(0)
                val to = windows.getJSONArray(i).getLong(1)
                assertTrue("$id $surah:${i + 1} starts before the one before ends", from >= previousEnd)
                if (to == AudioClip.TO_END) {
                    assertEquals("$id $surah: only the last verse runs to the end", windows.length() - 1, i)
                } else {
                    assertTrue("$id $surah:${i + 1} ends after it starts", to > from)
                    previousEnd = to
                }
            }
        }
    }

    @Test
    fun `husary mujawwad timings are in order`() = check("husary_mujawwad")

    @Test
    fun `muaiqly timings are in order`() = check("muaiqly")

    @Test
    fun `basfar timings are in order`() = check("basfar")

    @Test
    fun `ali jaber timings are in order`() = check("ali_jaber")
}
