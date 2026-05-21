package com.example.qmemo.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A manual loader for Mushaf page bitmaps.
 * Handles memory caching, background decoding, and pre-fetching.
 */
class MushafPageLoader(
    private val context: Context,
    private val repository: MushafRepository,
    private val downloadManager: MushafDownloadManager? = null
) {
    private val tag = "MushafPageLoader"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Memory cache for Bitmaps
    // max 6 pages to save RAM (enough for a spread + prefetch on both sides)
    private val memoryCache = object : LruCache<Int, Bitmap>(6) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            // No manual recycle() on modern Android to avoid "canvas: trying to use a recycled bitmap"
            // if Compose is still drawing it. Let GC handle it.
        }
    }

    private val _loadingState = MutableStateFlow<Map<Int, Bitmap?>>(emptyMap())
    val loadingState: StateFlow<Map<Int, Bitmap?>> = _loadingState

    private val activeJobs = mutableMapOf<Int, Job>()

    /**
     * Loads a page into the memory cache and emits it.
     */
    fun loadPage(page: Int) {
        if (page !in 1..604) return

        // 1. Check Cache
        val cached = memoryCache.get(page)
        if (cached != null) {
            updateState(page, cached)
            prefetch(page)
            return
        }

        // 2. Already loading?
        if (activeJobs.containsKey(page)) return

        // 3. Load from disk
        activeJobs[page] = scope.launch {
            try {
                // On-demand download if file is missing
                if (!repository.isPageDownloaded(page) && downloadManager != null) {
                    downloadManager.downloadSinglePage(page)
                }

                val bitmap = decodePage(page)
                if (bitmap != null) {
                    memoryCache.put(page, bitmap)
                    updateState(page, bitmap)
                    prefetch(page)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load page $page", e)
            } finally {
                activeJobs.remove(page)
            }
        }
    }

    private fun prefetch(currentPage: Int) {
        // Prefetch next spread and previous spread for smooth swiping
        val toPrefetch = listOf(currentPage + 1, currentPage + 2, currentPage - 1, currentPage - 2)
        toPrefetch.filter { it in 1..604 && memoryCache.get(it) == null && !activeJobs.containsKey(it) }
            .forEach { page ->
                activeJobs[page] = scope.launch {
                    try {
                        val bitmap = decodePage(page)
                        if (bitmap != null) {
                            memoryCache.put(page, bitmap)
                        }
                    } catch (e: Exception) {
                        // Prefetch failures are silent
                    } finally {
                        activeJobs.remove(page)
                    }
                }
            }
    }

    private suspend fun decodePage(page: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = repository.getPageFile(page)
        if (!file.exists()) return@withContext null

        return@withContext try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Dynamic scaling based on screen size to prevent OOM
            // Most Mushaf PNGs are high res (e.g. 1100x1600)
            val metrics = context.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels

            options.inSampleSize = calculateInSampleSize(options, screenW, screenH)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: OutOfMemoryError) {
            Log.e(tag, "OOM decoding page $page", e)
            System.gc()
            memoryCache.evictAll()
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun updateState(page: Int, bitmap: Bitmap) {
        val current = _loadingState.value.toMutableMap()
        current[page] = bitmap
        _loadingState.value = current
    }

    fun clear() {
        memoryCache.evictAll()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _loadingState.value = emptyMap()
    }
}
