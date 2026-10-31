package com.example.mutqan.domain.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.mutqan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps recitation downloads going when the app is closed, and shows how far they have got. The downloading itself
 * is done by [RecitationDownloads]; this runs only while it has something to fetch.
 */
class RecitationDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        // A service started as a foreground one must say so at once, before anything else.
        val first = notification(0)
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, first,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watching == null) {
            watching = scope.launch {
                RecitationDownloads.get(this@RecitationDownloadService).state.collect { state ->
                    if (state.downloading.isEmpty()) {
                        stopSelf()
                    } else {
                        val done = state.downloading.values.sumOf { it.done }
                        val total = state.downloading.values.sumOf { it.total }.coerceAtLeast(1)
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(done * 100 / total))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(percent: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recitation)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.audio_download_notification, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.audio_download_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "recitation_downloads"
        private const val NOTIFICATION_ID = 7312

        /** Starts the service; if the system will not allow it, the download simply carries on while the app lives. */
        fun start(context: Context) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, RecitationDownloadService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}
