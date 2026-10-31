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

    /** The last verse of the run now playing, to carry on to when settings change. */
    private var runEnd = 0

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
        val s = settings()
        val index = quran.ayahIndex
        start(RecitationRange.from(ayahId, page, s.toSurahEnd, TOTAL_AYAHS, index::surahOf, index::firstPageOf), s)
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
        start(ids, s, whole = false)
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
        start(RecitationRange.from(first, 0, true, TOTAL_AYAHS, index::surahOf, index::firstPageOf), s)
    }

    /** Applies changed settings to the run now playing, from the verse sounding now. */
    suspend fun reapply() {
        val now = player.state.value
        val id = now.ayahId ?: return
        if (!now.active || runEnd < id) return
        start((id..runEnd).toList(), settings())
    }

    /** [whole] is false for a set of verses that is not one stretch, which settings changes cannot carry on from. */
    private fun start(ids: List<Int>, s: Settings, whole: Boolean = true) {
        if (ids.isEmpty()) return
        runEnd = if (whole) ids.last() else 0
        player.play(s.reciter, ids, s.repeat, s.loop, s.speed, s.runRepeat, s.sayAfter)
    }

    companion object {
        private const val TOTAL_AYAHS = 6236

        @Volatile
        private var instance: RecitationController? = null

        fun get(context: Context): RecitationController =
            instance ?: synchronized(this) { instance ?: RecitationController(context).also { instance = it } }
    }
}
