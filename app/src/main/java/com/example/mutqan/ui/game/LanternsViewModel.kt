package com.example.mutqan.ui.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.StudyRepository
import com.example.mutqan.domain.game.LanternsGame
import com.example.mutqan.domain.game.LanternsSource
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

/** A round of «فوانيس» and what is around it: the menu, the loading, the round itself and its end. */
class LanternsViewModel(appContext: Context) : ViewModel() {

    enum class Phase { MENU, LOADING, PLAYING, OVER }

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

    /** Whether the round just over beat the best score. */
    var newBest by mutableStateOf(false)
        private set

    /** True when no verse could be found to play (nothing memorised fits). */
    var nothingToPlay by mutableStateOf(false)
        private set

    val best: StateFlow<Int> = prefs.lanternsBest.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val fromHifz: StateFlow<Boolean> = prefs.lanternsFromHifz.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hasHifz: StateFlow<Boolean> = study.coveredPages()
        .map { covered -> covered?.any { it } == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setFromHifz(on: Boolean) {
        viewModelScope.launch { prefs.setLanternsFromHifz(on) }
    }

    /** Reads a round (from the hifz, or Juz 'Amma) and starts it. */
    fun start() {
        if (phase == Phase.LOADING) return
        phase = Phase.LOADING
        newBest = false
        nothingToPlay = false
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                val covered = study.coveredPages().first()
                val hifz = covered?.indices?.filter { covered[it] }?.map { it + 1 }.orEmpty()
                val pages = if (prefs.lanternsFromHifz.first() && hifz.isNotEmpty()) hifz else quran.pagesOfJuz(AMMA)
                LanternsSource.load(quran, pages).takeUnless { it.isEmpty } ?: LanternsSource.load(quran, quran.pagesOfJuz(AMMA))
            }
            if (source.isEmpty) {
                nothingToPlay = true
                phase = Phase.MENU
                return@launch
            }
            words = source.words
            game = LanternsGame(source).also { it.start() }
            phase = Phase.PLAYING
        }
    }

    /** The round is over: its score is counted (and kept if it is the best). */
    fun finished() {
        val round = game ?: return
        if (phase != Phase.PLAYING) return
        phase = Phase.OVER
        viewModelScope.launch { newBest = prefs.recordLanternsRound(round.score) }
    }

    fun toMenu() {
        phase = Phase.MENU
        game = null
    }

    private companion object {
        const val AMMA = 30
    }
}

class LanternsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LanternsViewModel(context.applicationContext) as T
}
