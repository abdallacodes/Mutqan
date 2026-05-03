package com.example.qmemo.domain

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.example.qmemo.ui.mushaf.getMushafImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

sealed class DownloadState {
    data class Idle(val progress: Float, val current: Int, val total: Int) : DownloadState()
    data class Downloading(val progress: Float, val current: Int, val total: Int) : DownloadState()
    data class Completed(val total: Int) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class MushafDownloadManager(private val context: Context) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle(0f, 0, 604))
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val imageLoader = getMushafImageLoader(context)

    init {
        checkExistingDownloads()
    }

    private fun mushafPageUrl(page: Int): String {
        val p = page.toString().padStart(3, '0')
        return "https://raw.githubusercontent.com/GovarJabbar/Quran-PNG/master/$p.png"
    }

    private fun checkExistingDownloads() {
        scope.launch {
            val totalPages = 604
            var existingCount = 0
            val diskCache = imageLoader.diskCache
            
            if (diskCache != null) {
                for (page in 1..totalPages) {
                    val key = "mushaf_page_$page"
                    val snapshot = diskCache.openSnapshot(key)
                    if (snapshot != null) {
                        existingCount++
                        snapshot.close()
                    }
                }
            }

            if (existingCount == totalPages) {
                _downloadState.value = DownloadState.Completed(totalPages)
            } else {
                // If not complete, show IDLE but include the current count so the UI can show "X/604"
                _downloadState.value = DownloadState.Idle(
                    current = existingCount,
                    total = totalPages,
                    progress = existingCount.toFloat() / totalPages
                )
            }
        }
    }

    fun startDownload() {
        val currentState = _downloadState.value
        if (currentState is DownloadState.Downloading) return
        if (currentState is DownloadState.Completed) return

        downloadJob = scope.launch {
            try {
                val totalPages = 604
                // Start from what we already have in cache
                val initialCount = if (currentState is DownloadState.Idle) currentState.current else 0
                val completedCount = AtomicInteger(initialCount)
                
                _downloadState.value = DownloadState.Downloading(
                    progress = initialCount.toFloat() / totalPages,
                    current = initialCount,
                    total = totalPages
                )

                // Only download pages that are NOT in cache
                val diskCache = imageLoader.diskCache
                val pagesToDownload = (1..totalPages).filter { page ->
                    val key = "mushaf_page_$page"
                    val snapshot = diskCache?.openSnapshot(key)
                    val exists = snapshot != null
                    snapshot?.close()
                    !exists
                }

                if (pagesToDownload.isEmpty()) {
                    _downloadState.value = DownloadState.Completed(totalPages)
                    return@launch
                }

                // Download pages one by one to ensure absolute stability and avoid stalling.
                for (page in pagesToDownload) {
                    val url = mushafPageUrl(page)
                    val cacheKey = "mushaf_page_$page"
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .diskCacheKey(cacheKey) // Stable custom key, ignores URL redirects
                        .size(Size.ORIGINAL)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build()
                    
                    imageLoader.execute(request)
                    
                    val current = completedCount.incrementAndGet()
                    _downloadState.value = DownloadState.Downloading(
                        progress = current.toFloat() / totalPages,
                        current = current,
                        total = totalPages
                    )
                }
                
                _downloadState.value = DownloadState.Completed(totalPages)
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        checkExistingDownloads() // Re-check to update idle state with current progress
    }
}
