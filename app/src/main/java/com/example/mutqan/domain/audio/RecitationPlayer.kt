package com.example.mutqan.domain.audio

import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.mutqan.data.SurahData
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Plays a run of verses one after another, each as many times as asked, and says which one is sounding. Used from the
 * main thread. [RecitationService] shows the run in a notification and keeps it going with the screen off. What has been
 * heard is kept on the device (up to [CACHE_BYTES]), so a verse played before works offline.
 */
@OptIn(UnstableApi::class)
class RecitationPlayer private constructor(context: Context) {

    data class State(
        /** True from the moment a run starts until it ends or is stopped. */
        val active: Boolean = false,
        val playing: Boolean = false,
        /** The id of the verse sounding now. */
        val ayahId: Int? = null,
        /** Set when the audio could not be loaded; cleared by [clearFailure]. */
        val failed: Boolean = false
    )

    private val app = context.applicationContext
    private val sources = RecitationSources(app)
    private val quran by lazy { QuranRepository.getInstance(app) }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** The verse behind each item of the playlist: a verse repeated three times takes three items. */
    private var owners: List<Int> = emptyList()

    /** The file behind each item of the playlist, or null for an item that is a part of a longer file. */
    private var urls: List<String?> = emptyList()

    private val prefetcher = Executors.newSingleThreadExecutor()
    private var prefetching: Future<*>? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toIntOrNull()?.let { id -> _state.value = _state.value.copy(ayahId = id) }
            prefetchAhead(exo.currentMediaItemIndex)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(playing = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) stop()
        }

        override fun onPlayerError(error: PlaybackException) {
            stop()
            _state.value = _state.value.copy(failed = true)
        }
    }

    /** Reads through the on-device cache, and from the network (one shared connection pool) for what is not in it. */
    private val cacheSource: CacheDataSource.Factory by lazy {
        val upstream = DefaultDataSource.Factory(app, OkHttpDataSource.Factory(OkHttpClient()))
        CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val exo: ExoPlayer by lazy {
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.addListener(listener)
                // Have the next verse loaded before the one sounding ends, so there is no wait between them.
                it.preloadConfiguration = ExoPlayer.PreloadConfiguration(PRELOAD_US)
            }
    }

    /**
     * Downloads the verses coming up into the cache, a few at a time ahead of the one sounding, so the player finds
     * them on the device rather than waiting on the network between verses.
     */
    private fun prefetchAhead(current: Int) {
        prefetching?.cancel(true)
        val ahead = urls.drop(current + 1).filterNotNull().distinct().take(PREFETCH_AHEAD)
        if (ahead.isEmpty()) return
        prefetching = prefetcher.submit {
            for (url in ahead) {
                if (Thread.currentThread().isInterrupted) return@submit
                try {
                    CacheWriter(cacheSource.createDataSource(), DataSpec(Uri.parse(url)), null, null).cache()
                } catch (_: Exception) {
                    // The player will fetch it itself when its turn comes.
                }
            }
        }
    }

    /**
     * The player as the media session and its notification see it. Next and previous move by verse, not by item, so a
     * verse set to repeat is not stepped through repetition by repetition.
     */
    val sessionPlayer: Player by lazy {
        object : ForwardingPlayer(exo) {
            override fun seekToNext() = next()
            override fun seekToNextMediaItem() = next()
            override fun seekToPrevious() = previous()
            override fun seekToPreviousMediaItem() = previous()
        }
    }

    /**
     * Plays [ayahIds] in order with [reciter], each [repeat] times; with [loop] the run starts over when it ends.
     * Verses the reciter has no sound for are left out.
     */
    fun play(reciter: Reciter, ayahIds: List<Int>, repeat: Int, loop: Boolean, speedPercent: Int) {
        val index = quran.ayahIndex
        val reciterName = app.getString(reciter.nameRes)
        val items = ArrayList<MediaItem>()
        val who = ArrayList<Int>()
        val files = ArrayList<String?>()
        for (id in ayahIds) {
            if (id !in index) continue
            val surah = index.surahOf(id)
            val ayah = index.ayahOf(id)
            val clip = sources.clip(reciter, surah, ayah) ?: continue
            val builder = MediaItem.Builder()
                .setUri(clip.url)
                .setMediaId(id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("${SurahData.nameOf(surah)} · $ayah")
                        .setArtist(reciterName)
                        .build()
                )
            if (clip.endMs >= 0) {
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
            }
            val item = builder.build()
            repeat(repeat.coerceAtLeast(1)) { items += item; who += id; files += if (clip.endMs < 0) clip.url else null }
        }
        if (items.isEmpty()) return

        owners = who
        urls = files
        try {
            startService()
            exo.setMediaItems(items)
            exo.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            exo.setPlaybackSpeed(speedPercent / 100f)
            exo.prepare()
            exo.play()
            _state.value = State(active = true, playing = true, ayahId = who.first())
            prefetchAhead(0)
        } catch (e: Exception) {
            stop()
            _state.value = _state.value.copy(failed = true)
        }
    }

    fun setSpeed(percent: Int) {
        exo.setPlaybackSpeed(percent / 100f)
    }

    fun togglePause() {
        if (!_state.value.active) return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            startService()
            exo.play()
        }
    }

    /** The first item of the verse that item [i] belongs to. */
    private fun firstItemOf(i: Int): Int {
        var j = i
        while (j > 0 && owners[j - 1] == owners[i]) j--
        return j
    }

    fun next() {
        if (!_state.value.active || owners.isEmpty()) return
        val current = exo.currentMediaItemIndex
        var i = current + 1
        while (i < owners.size && owners[i] == owners[current]) i++
        when {
            i < owners.size -> exo.seekTo(i, 0L)
            exo.repeatMode == Player.REPEAT_MODE_ALL -> exo.seekTo(0, 0L)
            else -> stop()
        }
    }

    /** Back to the start of this verse, or, when it has only just begun, to the verse before. */
    fun previous() {
        if (!_state.value.active || owners.isEmpty()) return
        val first = firstItemOf(exo.currentMediaItemIndex)
        if (exo.currentPosition > RESTART_AFTER_MS || first == 0) exo.seekTo(first, 0L) else exo.seekTo(firstItemOf(first - 1), 0L)
    }

    fun stop() {
        prefetching?.cancel(true)
        exo.stop()
        exo.clearMediaItems()
        owners = emptyList()
        urls = emptyList()
        _state.value = State(failed = _state.value.failed)
        app.stopService(Intent(app, RecitationService::class.java))
    }

    fun clearFailure() {
        _state.value = _state.value.copy(failed = false)
    }

    /**
     * Starts the service that keeps playing, and shows the notification, once the app is out of sight. It is started
     * plainly, not as a foreground service: the service moves itself to the foreground when sound starts, and a
     * foreground start it does not answer in time takes the whole app down.
     */
    private fun startService() {
        try {
            app.startService(Intent(app, RecitationService::class.java))
        } catch (_: Exception) {
            // Playback still works in the foreground; only the background notification is lost.
        }
    }

    companion object {
        private const val CACHE_BYTES = 300L * 1024 * 1024
        private const val RESTART_AFTER_MS = 2_000L
        private const val PRELOAD_US = 20_000_000L
        private const val PREFETCH_AHEAD = 4

        @Volatile
        private var instance: RecitationPlayer? = null

        @Volatile
        private var cache: SimpleCache? = null

        fun get(context: Context): RecitationPlayer =
            instance ?: synchronized(this) { instance ?: RecitationPlayer(context).also { instance = it } }

        private fun cache(context: Context): SimpleCache =
            cache ?: synchronized(this) {
                cache ?: SimpleCache(
                    File(context.cacheDir, "recitation"),
                    LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
                    StandaloneDatabaseProvider(context)
                ).also { cache = it }
            }
    }
}
