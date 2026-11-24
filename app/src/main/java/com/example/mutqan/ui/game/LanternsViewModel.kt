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
import com.example.mutqan.domain.PassageStates
import com.example.mutqan.data.local.dao.GroupWithCount
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.TafsirLibrary
import com.example.mutqan.domain.TafsirSource
import com.example.mutqan.domain.game.AlikeVerses
import com.example.mutqan.domain.game.GameMode
import com.example.mutqan.domain.game.GameModes
import com.example.mutqan.domain.game.LanternsGame
import com.example.mutqan.domain.game.LanternsLevels
import com.example.mutqan.domain.game.LanternsSource
import com.example.mutqan.domain.game.LevelNews
import com.example.mutqan.domain.game.LevelRules
import com.example.mutqan.domain.game.NextWords
import com.example.mutqan.domain.game.PlayedVerse
import com.example.mutqan.domain.game.WordTraps
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «فوانيس» and what is around a round: the menu of its three modes, the map of levels and a level's card, the loading,
 * the round itself and its end. [breakPages], when given, are pages just read in the Mushaf: a calm sitting on them
 * starts at once (a break from reading that stays with what was read).
 */
class LanternsViewModel(appContext: Context, private val breakPages: Set<Int>? = null) : ViewModel() {

    enum class Phase { MENU, MAP, LOADING, PLAYING, OVER }

    /**
     * A verse to show: its id, its words (to draw in the Mushaf's fonts), the words (1-based) it shares with another, and
     * the word of it taken in the round for the other's ([taken]: a word id), if one was.
     */
    data class ShownVerse(val ayahId: Int, val words: List<QuranWord>, val matched: List<IntRange> = emptyList(), val taken: Int? = null)

    /** A verse of the round that has mutashabihat: the verse, the closest of them, and how many more there are. */
    data class RoundSimilar(val verse: ShownVerse, val similar: List<ShownVerse>, val more: Int)

    /**
     * Where the verses come from: the pages of [scope] (the Practice screen's choice); all the hifz when that choice has
     * none ([widened]); Juz 'Amma when nothing is memorised ([amma]).
     */
    data class Source(val scope: PracticeScope, val pages: Set<Int>, val widened: Boolean, val amma: Boolean)

    private val context = appContext.applicationContext
    private val prefs = UserPreferencesRepository(appContext)
    private val study = StudyRepository.create(appContext)
    private val quran = QuranRepository.getInstance(appContext)

    var phase by mutableStateOf(Phase.MENU)
        private set
    var game by mutableStateOf<LanternsGame?>(null)
        private set

    /** The mode of the round being played or just played. */
    var mode by mutableStateOf(GameMode.CHALLENGE)
        private set

    /** The words of the round by id, to draw them on the lanterns. */
    var words: Map<Int, QuranWord> = emptyMap()
        private set

    /** Where the round being played or just played takes its verses and wrong words from. */
    private var roundSource: LanternsSource? = null

    /** The meaning of each verse of a calm sitting (المختصر في التفسير, as kept), by ayah id. */
    var meanings: Map<Int, String> = emptyMap()
        private set

    /** The level being played or just played (a challenge); null otherwise. */
    var level by mutableStateOf<LevelRules?>(null)
        private set

    /** The verses the round holds (a calm sitting's few; an endless run's first ones). */
    var roundVerses by mutableIntStateOf(0)
        private set

    /** The words to build in the round (a level, or a calm sitting). */
    var levelWords by mutableIntStateOf(0)
        private set

    /** The level whose card is open, over the menu or the map; null for none. */
    var card by mutableStateOf<Int?>(null)
        private set

    /** The verses of the round just over that have mutashabihat, with them (read when the round ends). */
    var roundSimilar by mutableStateOf<List<RoundSimilar>>(emptyList())
        private set

    /** The user's mutashabihat groups, to add verses to. */
    val groups: StateFlow<List<GroupWithCount>> = study.allGroupsWithMemberCount().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Whether the mastery run just over is a new record. */
    var newRecord by mutableStateOf(false)
        private set

    /** The stars the level just played was won with (0 when it was lost). */
    var starsWon by mutableIntStateOf(0)
        private set

    /** True when no verse could be found to play. */
    var nothingToPlay by mutableStateOf(false)
        private set

    /** Whether this screen was opened as a break from reading (its calm sittings are on the pages read). */
    val isBreak: Boolean get() = breakPages != null

    val stars: StateFlow<List<Int>> = prefs.lanternsStars.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val masteryBest: StateFlow<Int> = prefs.lanternsMasteryBest.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val sound: StateFlow<Boolean> = prefs.lanternsSound.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Whether rounds draw verses that have look-alikes, each followed by its look-alike. */
    val alikeFocus: StateFlow<Boolean> = prefs.lanternsAlikeFocus.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val source: StateFlow<Source?> = combine(
        prefs.practiceScope, study.coveredPages(), study.revisionLogs(), study.selfRatedStrength()
    ) { scope, covered, logs, rated ->
        if (covered == null || covered.none { it }) {
            Source(scope, quran.pagesOfJuz(AMMA).toSet(), widened = false, amma = true)
        } else {
            val states = PassageStates.compute(logs)
            val chosen = scope.pages(covered, states, rated, logs)
            if (chosen.isNotEmpty()) Source(scope, chosen, widened = false, amma = false)
            else Source(scope, PracticeScope().pages(covered, states, rated, logs), widened = true, amma = false)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A test build may open any level, locked or not (a long press on it), to try the late ones. */
    val anyLevel: Boolean = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Seconds the wanted word shows after a slip in the round being played. */
    val hintTime: Float
        get() = when (mode) {
            GameMode.CALM -> GameModes.CALM_HINT
            GameMode.CHALLENGE -> level?.hintTime ?: GameModes.CALM_HINT
            GameMode.MASTERY -> MASTERY_HINT
        }

    init {
        // The look-alike words' index takes a moment to build: build it while the menu shows.
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { NextWords.get(quran); WordTraps.get(quran) }
            if (prefs.lanternsAlikeFocus.first()) warmFocus()
        }
        if (breakPages != null) startCalm()
    }

    fun setSound(on: Boolean) {
        viewModelScope.launch { prefs.setLanternsSound(on) }
    }

    /** Chooses where the verses come from: the same choice as the Practice screen's, kept for both. */
    fun setScope(scope: PracticeScope) {
        viewModelScope.launch { prefs.setPracticeScope(scope) }
    }

    fun setAlikeFocus(on: Boolean) {
        viewModelScope.launch {
            prefs.setLanternsAlikeFocus(on)
            if (on) warmFocus()
        }
    }

    /** Finds the look-alikes of the verses rounds draw from, so a round on mutashabihat starts without a wait. */
    private fun warmFocus() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val pages = source.filterNotNull().first().pages
                LanternsSource.warmLookAlikes(quran, pages, NextWords.get(quran))
            }
        }
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

    fun startCalm() = start(GameMode.CALM, null)

    fun startLevel(number: Int) = start(GameMode.CHALLENGE, LanternsLevels.rules(number))

    fun startMastery() = start(GameMode.MASTERY, null)

    /** The same again: the same level, another calm sitting, another run. */
    fun again() = start(mode, level)

    /**
     * On to the level after the one just won ([nextAfter]): straight in, or through its card on the map when it brings
     * something new.
     */
    fun nextLevel() {
        val played = level ?: return
        val next = nextAfter(played.number)
        val news = LanternsLevels.news(next)
        if (news == LevelNews.FASTER || news == LevelNews.FASTER_LONGER) {
            start(GameMode.CHALLENGE, LanternsLevels.rules(next))
        } else {
            openMap()
            card = next
        }
    }

    /**
     * The level to go on to after [played] was won with [starsWon]: when it was the furthest level, the furthest one
     * open now (two on after three stars); else simply the next.
     */
    fun nextAfter(played: Int): Int {
        val kept = LanternsLevels.withStars(stars.value, played, starsWon)
        return if (played >= kept.size) LanternsLevels.furthestOpen(kept) else played + 1
    }

    /** Goes on after a verse of a calm sitting, once its meaning is read. */
    fun proceed() {
        game?.proceed()
    }

    /** Reads a round of [mode] (for a level, its [rules]) from where the verses come from, and starts it. */
    private fun start(mode: GameMode, rules: LevelRules?) {
        if (phase == Phase.LOADING) return
        val from = if (phase == Phase.MAP) Phase.MAP else Phase.MENU
        card = null
        phase = Phase.LOADING
        newRecord = false
        starsWon = 0
        nothingToPlay = false
        roundSimilar = emptyList()
        this.mode = mode
        level = rules
        viewModelScope.launch {
            val round = withContext(Dispatchers.IO) {
                val pages = (if (mode == GameMode.CALM) breakPages else null) ?: source.filterNotNull().first().pages
                val amma = quran.pagesOfJuz(AMMA)
                val slipped = prefs.lanternsSlips.first().keys.toList()
                val recent = prefs.lanternsSeen.first()
                val near = WordTraps.get(quran)
                val next = NextWords.get(quran)
                // The verses each one is known to look like: the reader's own groups first, then the ready lists.
                val similar = SimilarLibrary.load(context)
                val alike = AlikeVerses(study.groupMates()) { id -> similar.similarTo(id).sortedByDescending { it.score }.map { it.ayahId } }
                // The pages the reader knows: words from them are the ones they reach for.
                val known = study.coveredPages().first()?.let { c -> c.indices.filter { c[it] }.mapTo(HashSet()) { it + 1 } }.orEmpty()
                // A round on mutashabihat: verses with look-alikes, each followed by its look-alike.
                val focus = prefs.lanternsAlikeFocus.first()
                fun read(on: Collection<Int>): LanternsSource = when {
                    mode == GameMode.CALM -> LanternsSource.calm(quran, on, slipped, recent, near, next, alike, known = known, focus = focus)
                    mode == GameMode.MASTERY -> LanternsSource.load(
                        quran, on, slipped = slipped, recent = recent,
                        given = { 0 }, next = next, nearForms = near, chance = GameModes.MASTERY_TRAPS,
                        alike = alike, favour = GameModes.MASTERY_FAVOUR, known = known, focus = focus
                    )
                    rules != null -> LanternsSource.forLevel(quran, on, rules, slipped, recent, next, near, alike, known = known, focus = focus)
                    else -> error("a level without its rules")
                }
                val source = read(pages).takeUnless { it.isEmpty } ?: read(amma)
                // The verses of this round are noted at once, so the next round (or a replay) draws others.
                prefs.recordLanternsSeen(source.drawn)
                meanings = if (mode != GameMode.CALM) emptyMap() else {
                    val tafsir = TafsirLibrary.get(context)
                    source.drawn.associateWith { id -> tafsir.entryFor(TafsirSource.MUKHTASAR, id)?.body.orEmpty() }
                        .filterValues { it.isNotBlank() }
                }
                source
            }
            if (round.isEmpty) {
                nothingToPlay = true
                phase = from
                return@launch
            }
            words = round.words
            roundSource = round
            levelWords = round.toBuild
            roundVerses = round.drawn.size
            game = when (mode) {
                GameMode.CALM -> LanternsGame(round, fixed = GameModes.calmDials, rules = GameModes.calmRules)
                GameMode.MASTERY -> LanternsGame(round, fixed = GameModes.masteryDials, rules = GameModes.masteryRules)
                GameMode.CHALLENGE -> LanternsGame(round, fixed = rules?.dials)
            }.also { it.start() }
            phase = Phase.PLAYING
        }
    }

    /** The round is over: a level won keeps its stars, a mastery run its record if the best, and the slips are kept. */
    fun finished() {
        val round = game ?: return
        if (phase != Phase.PLAYING) return
        phase = Phase.OVER
        val rules = level
        val mode = mode
        if (mode == GameMode.CHALLENGE && rules != null && round.cleared) starsWon = LanternsLevels.stars(round.slips)
        viewModelScope.launch { roundSimilar = withContext(Dispatchers.IO) { similarOf(round.played.toList()) } }
        viewModelScope.launch {
            // The verses the round reached, the last first: an endless round noted only its first few when it began.
            prefs.recordLanternsSeen(round.played.map { it.ayahId }.reversed())
            prefs.recordLanternsSlips(round.played)
            when (mode) {
                GameMode.CHALLENGE -> if (rules != null && round.cleared) prefs.recordLanternsLevel(rules.number, starsWon)
                GameMode.MASTERY -> newRecord = prefs.recordLanternsMastery(round.versesDone)
                GameMode.CALM -> prefs.countLanternsRound()
            }
        }
    }

    /**
     * Of the verses [played], those with mutashabihat, each with the closest of them: first the verses whose words were
     * taken for its own in the round (a word that comes after the same words there), then the ready list's, best match
     * first.
     */
    private suspend fun similarOf(played: List<PlayedVerse>): List<RoundSimilar> {
        val data = SimilarLibrary.load(context)
        val next = NextWords.get(quran)
        val from = roundSource
        val index = quran.ayahIndex
        fun shown(id: Int, matched: List<IntRange> = emptyList(), taken: Int? = null): ShownVerse? =
            if (id !in index) null
            else ShownVerse(id, quran.wordsOfAyah(index.surahOf(id), index.ayahOf(id)).filter { !it.isEnd && it.hafs.isNotBlank() }, matched, taken)
        val takenIn = LinkedHashMap<Int, MutableList<Int>>()
        for (verse in played) takenIn.getOrPut(verse.ayahId) { ArrayList() } += verse.taken
        return takenIn.mapNotNull { (id, takenWords) ->
            // The verse each wrong word taken came from, when it comes after the same words there, and the word taken.
            val takenFrom = LinkedHashMap<Int, Int>()
            for (word in takenWords) from?.lookAlikeOf(id, word)?.let { takenFrom.putIfAbsent(it, word) }
            val matches = data.similarTo(id).sortedByDescending { it.score }.filter { it.ayahId !in takenFrom }
            if (takenFrom.isEmpty() && matches.isEmpty()) return@mapNotNull null
            val verse = shown(id) ?: return@mapNotNull null
            val first = takenFrom.entries.mapNotNull { (other, word) -> shown(other, next.sharedWords(id, other), word) }
            val rest = matches.take((MOST_SIMILAR - first.size).coerceAtLeast(0)).mapNotNull { shown(it.ayahId, it.matchedWords) }
            RoundSimilar(verse, first + rest, (matches.size - rest.size).coerceAtLeast(0))
        }
    }

    /** Puts [ayahIds] in the group [groupId]; returns how many were not in it already. */
    suspend fun addToGroup(groupId: Int, ayahIds: List<Int>): Int = ayahIds.count { study.addAyahToGroup(groupId, it) }

    /** Starts a new group called [name] with [ayahIds]. */
    suspend fun createGroup(name: String, ayahIds: List<Int>) {
        study.createGroup(name, ayahIds)
    }

    private companion object {
        const val AMMA = 30
        const val MASTERY_HINT = 1f

        /** The mutashabihat shown for each verse; the others are counted. */
        const val MOST_SIMILAR = 6
    }
}

class LanternsViewModelFactory(private val context: Context, private val breakPages: Set<Int>? = null) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LanternsViewModel(context.applicationContext, breakPages) as T
}
