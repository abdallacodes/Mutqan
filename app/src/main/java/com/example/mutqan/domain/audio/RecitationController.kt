package com.example.mutqan.domain.audio

import android.content.Context
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.quran.QuranRepository
import kotlinx.coroutines.flow.first

/** Starts recitations the way the reader has set them up: reciter, repeats, speed and how far it goes. */
class RecitationController private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = UserPreferencesRepository(app)
    private val quran by lazy { QuranRepository.getInstance(app) }

    val player = RecitationPlayer.get(app)
    private val downloads = RecitationDownloads.get(app)

    /** What the run now playing was asked for, to work it out again from the verse sounding when settings change. */
    private var run: RecitationRun? = null

    private class Settings(
        val reciter: Reciter,
        val repeat: Int,
        val loop: Boolean,
        val toSurahEnd: Boolean,
        val speed: Int,
        val runRepeat: Int,
        val sayAfter: Int
    )

    private suspend fun settings() = Settings(
        reciter = Reciter.fromId(prefs.reciterId.first()),
        repeat = prefs.recitationRepeat.first(),
        loop = prefs.recitationLoop.first(),
        toSurahEnd = prefs.recitationToSurahEnd.first(),
        speed = prefs.recitationSpeedPercent.first(),
        runRepeat = prefs.recitationRunRepeat.first(),
        sayAfter = prefs.recitationSayAfterPercent.first()
    )

    /** Plays from verse [ayahId] to the end of [page], or of the surah when the reader chose that. */
    suspend fun playFrom(ayahId: Int, page: Int) {
        start(RecitationRun.Stretch(page), ayahId, settings())
    }

    /** Carries on from the verse the last recitation reached; false when there was none to carry on from. */
    suspend fun resume(): Boolean {
        val id = prefs.lastRecitationAyah.first()
        val index = quran.ayahIndex
        if (id !in index) return false
        playFrom(id, index.firstPageOf(id))
        return true
    }

    /** Plays the verses of [pages] one page after another, as today's review asks for them. */
    suspend fun playPages(pages: Collection<Int>) {
        val s = settings()
        val index = quran.ayahIndex
        val wanted = pages.toSortedSet()
        val ids = (1..TOTAL_AYAHS).filter { it in index && index.firstPageOf(it) in wanted }
        ids.firstOrNull()?.let { start(RecitationRun.Listed(ids), it, s) }
    }

    /** Downloads, for the chosen reciter, every surah that has a verse beginning on [pages]. */
    suspend fun downloadPages(pages: Collection<Int>) {
        val index = quran.ayahIndex
        val wanted = pages.toSet()
        val surahs = (1..TOTAL_AYAHS).filter { it in index && index.firstPageOf(it) in wanted }.map(index::surahOf).toSet()
        downloads.downloadAll(settings().reciter, surahs)
    }

    /** Plays the whole of [surah]. */
    suspend fun playSurah(surah: Int) {
        val s = settings()
        val index = quran.ayahIndex
        val first = (1..TOTAL_AYAHS).firstOrNull { index.surahOf(it) == surah } ?: return
        start(RecitationRun.Surah, first, s)
    }

    /**
     * Applies changed settings to the run now playing: it is worked out again from the verse sounding, so a new choice
     * of how far to go takes effect, and the verse carries on from where it had got to rather than starting over.
     */
    suspend fun reapply() {
        val now = player.state.value
        val id = now.ayahId ?: return
        val current = run ?: return
        if (!now.active) return
        // While the reader's turn to say the verse is on, the verse has just ended; hear it again rather than resume at its end.
        val resumeAt = if (now.yourTurn) 0L else player.positionMs()
        start(current, id, settings(), resumeAt)
    }

    private fun start(run: RecitationRun, from: Int, s: Settings, startPositionMs: Long = 0L) {
        val index = quran.ayahIndex
        val ids = run.verses(from, s.toSurahEnd, TOTAL_AYAHS, index::surahOf, index::firstPageOf)
        if (ids.isEmpty()) return
        this.run = run
        player.play(s.reciter, ids, s.repeat, s.loop, s.speed, s.runRepeat, s.sayAfter, startPositionMs)
    }

    companion object {
        private const val TOTAL_AYAHS = 6236

        @Volatile
        private var instance: RecitationController? = null

        fun get(context: Context): RecitationController =
            instance ?: synchronized(this) { instance ?: RecitationController(context).also { instance = it } }
    }
}
