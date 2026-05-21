package com.example.qmemo.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
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
    private val repository = MushafRepository(context)
    private val httpClient = OkHttpClient()

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
            val existingCount = repository.getDownloadedCount()

            if (existingCount == totalPages) {
                _downloadState.value = DownloadState.Completed(totalPages)
            } else {
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
                val pagesToDownload = repository.getMissingPages()

                if (pagesToDownload.isEmpty()) {
                    _downloadState.value = DownloadState.Completed(totalPages)
                    return@launch
                }

                val initialCount = totalPages - pagesToDownload.size
                val completedCount = AtomicInteger(initialCount)
                
                _downloadState.value = DownloadState.Downloading(
                    progress = initialCount.toFloat() / totalPages,
                    current = initialCount,
                    total = totalPages
                )

                for (page in pagesToDownload) {
                    val url = mushafPageUrl(page)
                    val destFile = repository.getPageFile(page)
                    val tempFile = context.cacheDir.resolve("temp_page_${page}.png")
                    
                    try {
                        val request = Request.Builder().url(url).build()
                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("Unexpected code $response")
                            
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        // Atomically rename the temp file to the destination file
                        // This prevents partial/corrupted files from being saved in the repository
                        if (!tempFile.renameTo(destFile)) {
                            // Fallback if rename fails across file systems (though unlikely here)
                            tempFile.copyTo(destFile, overwrite = true)
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("MushafDownloadManager", "Failed to download page $page", e)
                        tempFile.delete()
                        _downloadState.value = DownloadState.Failed("Failed at page $page: ${e.message}")
                        return@launch
                    }
                    
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
        checkExistingDownloads()
    }

    /**
     * Downloads a single page on-demand.
     * This is a blocking call (expected to be called from a background thread).
     */
    fun downloadSinglePage(page: Int) {
        val url = mushafPageUrl(page)
        val destFile = repository.getPageFile(page)
        val tempFile = context.cacheDir.resolve("temp_on_demand_${page}.png")

        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e("MushafDownloadManager", "On-demand download failed for page $page", e)
            tempFile.delete()
        }
    }
}
