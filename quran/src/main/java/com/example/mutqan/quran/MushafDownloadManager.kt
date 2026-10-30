package com.example.mutqan.quran

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DownloadState {
    data class Idle(val progress: Float, val current: Int, val total: Int) : DownloadState()
    data class Downloading(val progress: Float, val current: Int, val total: Int) : DownloadState()
    data class Completed(val total: Int) : DownloadState()
    /** The download stopped at the pack covering [firstPage]..[lastPage]. */
    data class Failed(val firstPage: Int, val lastPage: Int) : DownloadState()
}

/**
 * "Download for offline" for the whole Mushaf: fetches every font pack that is not on the
 * device yet. Progress is counted in pages so the Settings label stays "pages x/604".
 * Individual pages are fetched on demand by [MushafFontManager.ensure] without this.
 */
class MushafDownloadManager(context: Context) {

    private val fonts = MushafFontManager.getInstance(context)
    private val totalPages = 604

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle(0f, 0, totalPages))
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        refreshState()
    }

    private fun refreshState() {
        scope.launch {
            val existing = fonts.availablePageCount()
            _downloadState.value =
                if (existing == totalPages) DownloadState.Completed(totalPages)
                else DownloadState.Idle(existing.toFloat() / totalPages, existing, totalPages)
        }
    }

    fun startDownload() {
        val current = _downloadState.value
        if (current is DownloadState.Downloading || current is DownloadState.Completed) return

        downloadJob = scope.launch {
            val missing = fonts.packs.filterNot { fonts.isPackComplete(it) }
            for (pack in missing) {
                publishProgress()
                if (!fonts.downloadPack(pack)) {
                    _downloadState.value = DownloadState.Failed(pack.firstPage, pack.lastPage)
                    return@launch
                }
            }
            _downloadState.value = DownloadState.Completed(totalPages)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        refreshState()
    }

    private fun publishProgress() {
        val done = fonts.availablePageCount()
        _downloadState.value = DownloadState.Downloading(done.toFloat() / totalPages, done, totalPages)
    }
}
