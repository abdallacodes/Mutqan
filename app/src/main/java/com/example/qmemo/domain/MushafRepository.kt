package com.example.qmemo.domain

import android.content.Context
import java.io.File

/**
 * Handles physical file management for Mushaf pages.
 * Pages are stored in the app's internal storage (filesDir/mushaf_pages).
 */
class MushafRepository(private val context: Context) {

    private val baseDir: File by lazy {
        context.filesDir.resolve("mushaf_pages").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun getPageFile(page: Int): File {
        val fileName = page.toString().padStart(3, '0') + ".png"
        return File(baseDir, fileName)
    }

    fun isPageDownloaded(page: Int): Boolean {
        return getPageFile(page).let { it.exists() && it.length() > 0 }
    }

    fun getDownloadedCount(): Int {
        return (1..604).count { isPageDownloaded(it) }
    }

    fun getMissingPages(): List<Int> {
        return (1..604).filter { !isPageDownloaded(it) }
    }
}
