package com.example.mutqan.ui.game

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.game.LanternsGame
import com.example.mutqan.domain.game.LanternsLevels
import com.example.mutqan.domain.game.LanternsSource
import com.example.mutqan.domain.game.LevelRules
import com.example.mutqan.domain.game.NextWords
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «فوانيس» and what is around a round: the menu, the map of levels and a level's card, the loading, the round itself
 * and its end. A round is a level of the map ([level]) or the open round (no level).
 */
class LanternsViewModel(appContext: Context) : ViewModel() {

    enum class Phase { MENU, MAP, LOADING, PLAYING, OVER }

    private val prefs = UserPreferencesRepository(appContext)
    private val study = StudyRepository.create(appContext)
    private val quran = QuranRepository.getInstance(appContext)

    var phase by mutableStateOf(Phase.MENU)
        private set
    var game by mutableStateOf<LanternsGame?>(null)
        private set

    /** The words of the round by id, to draw them on the lanterns. */
    var words: Map<Int, QuranWord> = emptyMap()
        private set

    /** The level being played or just played; null for the open round. */
    var level by mutableStateOf<LevelRules?>(null)
        private set

    /** The words to build in the level being played. */
    var levelWords by mutableIntStateOf(0)
        private set

    /** The level whose card is open, over the menu or the map; null for none. */
    var card by mutableStateOf<Int?>(null)
        private set

    /** Whether the open round just over beat the best score. */
    var newBest by mutableStateOf(false)
        private set

    /** The stars the level just played was won with (0 when it was lost). */
    var starsWon by mutableIntStateOf(0)
        private set

    /** True when no verse could be found to play (nothing memorised fits). */
    var nothingToPlay by mutableStateOf(false)
        private set

    val best: StateFlow<Int> = prefs.lanternsBest.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val stars: StateFlow<List<Int>> = prefs.lanternsStars.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val fromHifz: StateFlow<Boolean> = prefs.lanternsFromHifz.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val sound: StateFlow<Boolean> = prefs.lanternsSound.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hasHifz: StateFlow<Boolean> = study.coveredPages()
        .map { covered -> covered?.any { it } == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A test build may open any level, locked or not (a long press on it), to try the late ones. */
    val anyLevel: Boolean = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    init {
        // The look-alike words' index takes a moment to build: build it while the menu shows.
        viewModelScope.launch(Dispatchers.Default) { runCatching { NextWords.get(quran) } }
    }

    fun setFromHifz(on: Boolean) {
        viewModelScope.launch { prefs.setLanternsFromHifz(on) }
    }

    fun setSound(on: Boolean) {
        viewModelScope.launch { prefs.setLanternsSound(on) }
    }

    fun openMap() {
        card = null
        game = null
        nothingToPlay = false
        phase = Phase.MAP
    }

    fun openCard(level: Int) {
        nothingToPlay = false
        card = level
    }

    fun closeCard() {
        card = null
    }

    fun toMenu() {
        card = null
        game = null
        level = null
        phase = Phase.MENU
    }

    fun startLevel(number: Int) = start(LanternsLevels.rules(number))

    fun startFree() = start(null)

    /** The same round again: the same level, or another open round. */
    fun again() = start(level)

    fun nextLevel() {
        val played = level ?: return
        start(LanternsLevels.rules(played.number + 1))
    }

    /** Reads a round (from the hifz, or Juz 'Amma) and starts it: the level [rules], or the open round. */
    private fun start(rules: LevelRules?) {
        if (phase == Phase.LOADING) return
        val from = if (phase == Phase.MENU) Phase.MENU else Phase.MAP
        card = null
        phase = Phase.LOADING
        newBest = false
        starsWon = 0
        nothingToPlay = false
        level = rules
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                val covered = study.coveredPages().first()
                val hifz = covered?.indices?.filter { covered[it] }?.map { it + 1 }.orEmpty()
                val amma = quran.pagesOfJuz(AMMA)
                val pages = if (prefs.lanternsFromHifz.first() && hifz.isNotEmpty()) hifz else amma
                val slipped = prefs.lanternsSlips.first().keys.toList()
                fun read(on: List<Int>): LanternsSource =
                    if (rules == null) LanternsSource.load(quran, on, slipped = slipped)
                    else LanternsSource.forLevel(quran, on, rules, slipped, if (rules.lookAlike > 0f) NextWords.get(quran) else null)
                read(pages).takeUnless { it.isEmpty } ?: read(amma)
            }
            if (source.isEmpty) {
                nothingToPlay = true
                phase = from
                return@launch
            }
            words = source.words
            levelWords = source.toBuild
            game = LanternsGame(source, fixed = rules?.dials).also { it.start() }
            phase = Phase.PLAYING
        }
    }

    /** The round is over: a level won keeps its stars, the open round its score if the best, and the slips are kept. */
    fun finished() {
        val round = game ?: return
        if (phase != Phase.PLAYING) return
        phase = Phase.OVER
        val rules = level
        if (rules != null && round.cleared) starsWon = LanternsLevels.stars(round.slips)
        viewModelScope.launch {
            prefs.recordLanternsSlips(round.played)
            when {
                rules == null -> newBest = prefs.recordLanternsRound(round.score)
                round.cleared -> prefs.recordLanternsLevel(rules.number, starsWon)
            }
        }
    }

    private companion object {
        const val AMMA = 30
    }
}

class LanternsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LanternsViewModel(context.applicationContext) as T
}
