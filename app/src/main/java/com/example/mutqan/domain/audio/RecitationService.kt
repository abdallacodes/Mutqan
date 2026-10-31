package com.example.mutqan.domain.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.mutqan.R

/**
 * Keeps a recitation going when the screen is off or the app is not in front, and puts its controls in the
 * notification and on the lock screen. The sound itself is played by [RecitationPlayer]; this only shows it.
 */
@OptIn(UnstableApi::class)
class RecitationService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, RecitationPlayer.get(this).sessionPlayer).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_recitation) }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
