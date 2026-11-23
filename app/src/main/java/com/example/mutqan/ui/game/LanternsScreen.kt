package com.example.mutqan.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.game.GameEvent
import com.example.mutqan.domain.game.GameMode
import com.example.mutqan.domain.game.GameVerse
import com.example.mutqan.domain.game.Lantern
import com.example.mutqan.domain.game.LanternKind
import com.example.mutqan.domain.game.LanternState
import com.example.mutqan.domain.game.LanternsGame
import com.example.mutqan.domain.game.LanternsLevels
import com.example.mutqan.domain.time.TimeKind
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.mushaf.AddVersesToGroupSheet
import com.example.mutqan.ui.components.rememberHaptics
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.time.TrackTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlin.math.sin

private val LanternWidth = 112.dp
private val LanternHeight = 146.dp

/** What the top of the screen shows of the round, copied from the game when it changes (not every frame). */
@Stable
private class Hud {
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(LanternsGame.START_LIVES)
    var startLives by mutableIntStateOf(LanternsGame.START_LIVES)
    var versesDone by mutableIntStateOf(0)
    var waiting by mutableStateOf(false)
    var combo by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var placed by mutableIntStateOf(0)
    var built by mutableIntStateOf(0)
    var verse by mutableStateOf<GameVerse?>(null)
    var slow by mutableStateOf(false)

    fun sync(game: LanternsGame) {
        if (score != game.score) score = game.score
        if (lives != game.lives) lives = game.lives
        if (startLives != game.startLives) startLives = game.startLives
        if (versesDone != game.versesDone) versesDone = game.versesDone
        if (waiting != game.waiting) waiting = game.waiting
        if (combo != game.combo) combo = game.combo
        if (level != game.level) level = game.level
        if (placed != game.placed) placed = game.placed
        if (built != game.built) built = game.built
        if (verse != game.verse) verse = game.verse
        val slowNow = game.slowLeft > 0f
        if (slow != slowNow) slow = slowNow
    }
}

/** A word in big letters over the sky for a moment: a verse done, a level, a power. */
private data class Banner(val kind: Kind, val start: Float, val bonus: Int = 0, val perfect: Boolean = false, val level: Int = 0) {
    enum class Kind { VERSE, SLOW, LIFE }
}

/**
 * «فوانيس», the Practice game: lanterns carry the words of a verse up the night sky, and the reader taps the next word
 * to build it. See [LanternsGame] for the rules; this draws the sky, the town, the lanterns and their sparks, and counts
 * the time as practice.
 */
@Composable
fun LanternsScreen(onBack: () -> Unit, onOpenVerse: (page: Int, ayahId: Int) -> Unit, breakPages: Set<Int>? = null) {
    TrackTime(TimeKind.PRACTICE)
    val context = LocalContext.current
    val vm: LanternsViewModel = viewModel(factory = LanternsViewModelFactory(context, breakPages))
    val masteryBest by vm.masteryBest.collectAsState()
    val stars by vm.stars.collectAsState()
    val source by vm.source.collectAsState()
    val soundOn by vm.sound.collectAsState()
    var choosingSource by remember { mutableStateOf(false) }
    var showingSimilar by remember { mutableStateOf(false) }
    var addingToGroup by remember { mutableStateOf<List<Int>?>(null) }
    val similarScope = rememberCoroutineScope()
    val sounds = rememberGameSounds(soundOn)
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    NightBars()

    var clock by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    val effects = remember { Effects() }
    // Confetti over everything, for a new best or a level won.
    val cheers = remember { Effects() }
    val hud = remember { Hud() }
    var shown by remember { mutableStateOf<List<Lantern>>(emptyList()) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var panelBottom by remember { mutableFloatStateOf(0f) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    var hintWord by remember { mutableStateOf<Int?>(null) }
    var hintUntil by remember { mutableFloatStateOf(0f) }
    var pendingLevel by remember { mutableIntStateOf(0) }
    // A new round counts down before the first lantern rises; a tick for each number, a chime to go.
    var countdownUntil by remember { mutableFloatStateOf(0f) }
    var countedTo by remember { mutableIntStateOf(0) }
    val game = vm.game
    val level = vm.level
    val next = LanternsLevels.furthestOpen(stars)
    LaunchedEffect(game) {
        if (game == null) return@LaunchedEffect
        // A new round starts clean: no sparks, banner or hint left from the one before.
        effects.sparks.clear()
        effects.floaters.clear()
        banner = null
        hintWord = null
        pendingLevel = 0
        paused = false
        // A calm sitting starts at once; the others count down.
        val calm = vm.mode == GameMode.CALM
        countdownUntil = clock + if (calm) 0f else COUNTDOWN
        countedTo = if (calm) 0 else 4
    }
    val inRound = game != null && (vm.phase == LanternsViewModel.Phase.PLAYING || vm.phase == LanternsViewModel.Phase.OVER)
    // The night turns toward dawn as the levels go up (and as the open round goes on).
    val dawn by animateFloatAsState(
        targetValue = when {
            inRound && vm.mode == GameMode.CALM -> CALM_DAWN
            inRound && vm.mode == GameMode.MASTERY -> 0f
            inRound && level != null -> LanternsLevels.dawn(level.number)
            inRound -> LanternsLevels.dawn(hud.level)
            else -> LanternsLevels.dawn(next)
        },
        animationSpec = tween(1400),
        label = "dawn"
    )

    // Leaving the app pauses a round; coming back shows it paused.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && vm.phase == LanternsViewModel.Phase.PLAYING) paused = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = vm.phase == LanternsViewModel.Phase.PLAYING && !paused) { paused = true }

    // The game's sizes follow the screen: columns as many as fit (kept clear of the edges by the wind), and the line
    // where lanterns pass under the panel.
    LaunchedEffect(game, area, panelBottom) {
        val round = game ?: return@LaunchedEffect
        if (area.width == 0 || area.height == 0) return@LaunchedEffect
        val lanternW = with(density) { LanternWidth.toPx() }
        val lanternH = with(density) { LanternHeight.toPx() }
        val widthDp = with(density) { area.width.toDp().value }
        val count = (widthDp / 104f).toInt().coerceIn(3, 6)
        val margin = (lanternW / 2f + with(density) { 4.dp.toPx() }) / area.width + round.dials.wind * 1.4f
        round.laneXs = List(count) { margin + (1f - 2f * margin) * it / (count - 1) }
        round.lanternHeight = lanternH / area.height
        round.lanternWidth = lanternW / area.width
        round.escapeY = ((panelBottom - lanternH * 0.15f) / area.height).coerceIn(0.1f, 0.5f)
    }

    // The frame loop: the game moves on, its events become sparks, words and taps of the phone.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    if (paused) return@withFrameNanos
                    clock += dt
                    val round = vm.game
                    val w = area.width.toFloat()
                    val h = area.height.toFloat()
                    val hintTime = vm.level?.hintTime ?: HINT_TIME
                    if (round != null && vm.phase == LanternsViewModel.Phase.PLAYING && countedTo > 0) {
                        val number = if (clock >= countdownUntil) 0 else countdownNumber(countdownUntil - clock)
                        if (number < countedTo) {
                            sounds.play(if (number == 0) GameSound.GO else GameSound.TICK)
                            countedTo = number
                        }
                    }
                    // After the round the game still runs, only for the lanterns' last animations.
                    if (round != null && vm.phase == LanternsViewModel.Phase.OVER) round.update(dt)
                    if (round != null && vm.phase == LanternsViewModel.Phase.PLAYING && clock >= countdownUntil) {
                        round.update(dt)
                        while (round.events.isNotEmpty()) {
                            when (val e = round.events.removeFirst()) {
                                is GameEvent.Right -> {
                                    val x = e.lantern.x * w
                                    val y = e.lantern.y * h
                                    effects.burst(x, y, GameColors.hue(e.lantern.hue), 20, w * 0.9f)
                                    effects.float("+${e.points}", GameColors.Gold, x, y - h * 0.05f)
                                    sounds.play(GameSound.RIGHT, pitch = GameSounds.natural())
                                    if (e.lantern.golden) {
                                        effects.burst(x, y, GameColors.Gold, 28, w * 1.1f)
                                        effects.float("×${LanternsGame.GOLDEN_TIMES}", GameColors.Gold, x, y - h * 0.1f, big = true)
                                        sounds.play(GameSound.GOLDEN)
                                    } else if (e.combo >= 4 && e.combo % 4 == 0) {
                                        effects.float("×${LanternsGame.multiplierFor(e.combo)}", Color.White, x, y - h * 0.1f, big = true)
                                        sounds.play(GameSound.COMBO)
                                    }
                                    hintWord = null
                                    haptics.tick()
                                }
                                is GameEvent.Wrong -> {
                                    effects.redFlash = 1f
                                    effects.shake = 1f
                                    effects.burst(e.lantern.x * w, e.lantern.y * h, Color(0xFF8B8794), 14, w * 0.6f)
                                    hintWord = round.needed?.id
                                    hintUntil = clock + hintTime
                                    sounds.play(GameSound.WRONG, pitch = GameSounds.natural())
                                    haptics.reject()
                                }
                                is GameEvent.Missed -> {
                                    effects.redFlash = 1f
                                    hintWord = e.lantern.word?.id
                                    hintUntil = clock + hintTime
                                    sounds.play(GameSound.MISSED)
                                    haptics.reject()
                                }
                                is GameEvent.VerseDone -> {
                                    effects.goldFlash = 1f
                                    GameColors.hues.forEach { effects.burst(w / 2f, panelBottom, it, 10, w * 1.1f) }
                                    // A calm sitting shows the verse's meaning instead of the points.
                                    if (vm.mode != GameMode.CALM) banner = Banner(Banner.Kind.VERSE, clock, bonus = e.bonus, perfect = e.perfect, level = pendingLevel)
                                    hintWord = null
                                    sounds.play(GameSound.VERSE)
                                    if (e.perfect) sounds.play(GameSound.PERFECT)
                                    haptics.confirm()
                                }
                                is GameEvent.LevelUp -> {
                                    pendingLevel = e.level
                                    banner = banner?.copy(level = e.level)
                                }
                                is GameEvent.PowerUp -> {
                                    val slow = e.lantern.kind == LanternKind.SLOW
                                    effects.burst(e.lantern.x * w, e.lantern.y * h, if (slow) GameColors.Calm else GameColors.Red, 22, w)
                                    banner = Banner(if (slow) Banner.Kind.SLOW else Banner.Kind.LIFE, clock)
                                    sounds.play(if (slow) GameSound.SLOW else GameSound.LIFE)
                                    haptics.confirm()
                                }
                                GameEvent.Over -> {
                                    // A level won, a calm sitting done, or a new record cheers; anything else ends softly.
                                    val cheer = round.cleared || (vm.mode == GameMode.MASTERY && round.versesDone > vm.masteryBest.value)
                                    vm.finished()
                                    sounds.play(if (cheer) GameSound.CLEAR else GameSound.FAIL)
                                    if (cheer) haptics.confirm() else haptics.reject()
                                }
                            }
                        }
                    }
                    effects.update(dt, gravity = h * 0.5f)
                    cheers.update(dt, gravity = h * 0.35f)
                    if (round != null) {
                        hud.sync(round)
                        val now = round.lanterns
                        if (now.size != shown.size || now.indices.any { now[it] !== shown[it] }) shown = now.toList()
                    }
                    if (hintWord != null && clock > hintUntil) hintWord = null
                }
            }
        }
    }

    CompositionLocalProvider(LocalGameSounds provides sounds) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GameColors.sky(dawn)[0])
    ) {
        // The sky, the town and the lanterns shake together after a mistake; the top stays still.
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { area = it }
                .graphicsLayer { translationX = sin(clock * 70f) * effects.shake * 14.dp.toPx() }
        ) {
            // In a round the moon goes low on the right, out from behind the hearts and the verse.
            NightSky(time = { clock }, moon = if (inRound) Offset(0.82f, 0.44f) else Offset(0.16f, 0.15f), dawn = dawn)
            if (vm.phase == LanternsViewModel.Phase.MENU) MenuLanterns(time = { clock })
            Skyline(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.2f))
            if (game != null && inRound) {
                // The sky is measured from the left whatever the language, like the sparks drawn over it; each word on
                // its lantern still reads right to left.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        for (lantern in shown) {
                            key(lantern.id) {
                                LanternView(
                                    lantern = lantern,
                                    word = lantern.word?.let { vm.words[it.id] },
                                    clock = { clock },
                                    area = area,
                                    panelY = if (area.height > 0) panelBottom / area.height - 0.04f else 0.2f,
                                    escapeY = { game.escapeY },
                                    wind = game.dials.wind,
                                    onTap = { id -> if (!paused && vm.phase == LanternsViewModel.Phase.PLAYING) vm.game?.tap(id) }
                                )
                            }
                        }
                    }
                }
            }
            SparksLayer(effects = effects, clock = { clock }, slow = hud.slow)
        }

        // The top of a round, while it is played (an end screen shows its own numbers over the sky).
        if (game != null && vm.phase == LanternsViewModel.Phase.PLAYING) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                HudBar(
                    hud = hud,
                    mode = vm.mode,
                    levelNumber = level?.number,
                    progress = if (vm.mode != GameMode.MASTERY && vm.levelWords > 0) ({ hud.built / vm.levelWords.toFloat() }) else null,
                    onPause = { paused = true }
                )
                Spacer(Modifier.height(6.dp))
                VersePanel(
                    verse = hud.verse,
                    placed = hud.placed,
                    words = vm.words,
                    hint = hintWord?.let { vm.words[it] },
                    glow = { effects.goldFlash },
                    modifier = Modifier.onGloballyPositioned { panelBottom = it.boundsInRoot().bottom }
                )
            }
            banner?.let { b -> BannerView(b, clock = { clock }, onDone = { if (banner === b) banner = null }) }
            if (vm.phase == LanternsViewModel.Phase.PLAYING && clock < countdownUntil) {
                Countdown(
                    left = countdownUntil - clock,
                    title = when {
                        vm.mode == GameMode.MASTERY -> stringResource(R.string.game_mode_mastery)
                        level != null -> stringResource(R.string.game_level_title, level.number)
                        else -> null
                    }
                )
            }
        }

        // A new record, or a level won: bursts of every colour from the top, one after another (more for more stars).
        val cheering = vm.newRecord || (vm.phase == LanternsViewModel.Phase.OVER && vm.starsWon > 0)
        LaunchedEffect(cheering, vm.starsWon) {
            if (!cheering) return@LaunchedEffect
            repeat(if (vm.newRecord) 3 else vm.starsWon) { wave ->
                val w = area.width.toFloat()
                val h = area.height.toFloat()
                GameColors.hues.forEach { cheers.burst(w * (0.3f + 0.2f * wave), h * 0.2f, it, 9, w * 1.2f) }
                cheers.burst(w * (0.3f + 0.2f * wave), h * 0.2f, GameColors.Gold, 14, w * 1.3f)
                haptics.confirm()
                kotlinx.coroutines.delay(350)
            }
        }

        when (vm.phase) {
            LanternsViewModel.Phase.MENU -> ModesMenu(
                sound = soundOn,
                onSound = { vm.setSound(!soundOn) },
                level = next,
                starsTotal = stars.sum(),
                masteryBest = masteryBest,
                source = source,
                failed = vm.nothingToPlay,
                onCalm = vm::startCalm,
                onLevel = { vm.openCard(next) },
                onMap = vm::openMap,
                onMastery = vm::startMastery,
                onSource = { choosingSource = true },
                onBack = onBack
            )
            LanternsViewModel.Phase.MAP -> LevelMap(
                stars = stars,
                clock = { clock },
                anyLevel = vm.anyLevel,
                failed = vm.nothingToPlay,
                onLevel = vm::openCard,
                onBack = vm::toMenu
            )
            LanternsViewModel.Phase.LOADING -> Loading()
            LanternsViewModel.Phase.PLAYING -> if (paused) PauseOverlay(
                sound = soundOn,
                onSound = { vm.setSound(!soundOn) },
                restart = stringResource(if (level != null) R.string.game_replay_level else R.string.game_restart),
                onResume = { paused = false },
                onRestart = { paused = false; vm.again() },
                onQuit = {
                    paused = false
                    when {
                        vm.isBreak -> onBack()
                        level != null -> vm.openMap()
                        else -> vm.toMenu()
                    }
                }
            )
            LanternsViewModel.Phase.OVER -> game?.let { round ->
                when (vm.mode) {
                    GameMode.CHALLENGE -> LevelOverOverlay(
                        game = round,
                        level = level?.number ?: 1,
                        starsWon = vm.starsWon,
                        nextLevel = vm.nextAfter(level?.number ?: 1),
                        similarCount = vm.roundSimilar.size,
                        onSimilar = { showingSimilar = true },
                        onStarLand = { _ ->
                            haptics.tick()
                            sounds.play(GameSound.STAR, pitch = GameSounds.natural())
                        },
                        onNext = vm::nextLevel,
                        onAgain = vm::again,
                        onMap = vm::openMap,
                        onOpenVerse = onOpenVerse
                    )
                    GameMode.CALM -> CalmOverOverlay(
                        game = round,
                        similarCount = vm.roundSimilar.size,
                        onSimilar = { showingSimilar = true },
                        onMore = vm::again,
                        onDone = { if (vm.isBreak) onBack() else vm.toMenu() },
                        onOpenVerse = onOpenVerse
                    )
                    GameMode.MASTERY -> MasteryOverOverlay(
                        game = round,
                        best = masteryBest,
                        newRecord = vm.newRecord,
                        similarCount = vm.roundSimilar.size,
                        onSimilar = { showingSimilar = true },
                        onAgain = vm::again,
                        onBack = vm::toMenu,
                        onOpenVerse = onOpenVerse
                    )
                }
            }
        }
        // A calm sitting: after each verse, its meaning, until the reader goes on.
        val waitingVerse = hud.verse
        if (vm.phase == LanternsViewModel.Phase.PLAYING && vm.mode == GameMode.CALM && hud.waiting && !paused && waitingVerse != null) {
            MeaningCard(
                verse = waitingVerse,
                words = vm.words,
                meaning = vm.meanings[waitingVerse.ayahId],
                last = hud.versesDone >= vm.roundVerses,
                onNext = vm::proceed
            )
        }
        if (showingSimilar && vm.phase == LanternsViewModel.Phase.OVER) {
            RoundSimilarOverlay(items = vm.roundSimilar, onAdd = { addingToGroup = it }, onClose = { showingSimilar = false })
        }
        addingToGroup?.let { ayahIds ->
            val groups by vm.groups.collectAsState()
            AddVersesToGroupSheet(
                title = stringResource(R.string.game_similar_sheet_title),
                subtitle = pluralStringResource(R.plurals.count_ayahs, ayahIds.size, ayahIds.size),
                createLabel = stringResource(R.string.game_similar_create),
                emptyText = stringResource(R.string.game_similar_no_groups),
                groups = groups,
                memberOf = emptySet(),
                onPick = { entry ->
                    similarScope.launch {
                        val added = vm.addToGroup(entry.group.id, ayahIds)
                        addingToGroup = null
                        val text = context.resources.getQuantityString(R.plurals.game_similar_added, added, added, entry.group.description)
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                },
                onCreate = { name ->
                    similarScope.launch {
                        vm.createGroup(name, ayahIds)
                        addingToGroup = null
                        val text = context.resources.getQuantityString(R.plurals.game_similar_added, ayahIds.size, ayahIds.size, name)
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { addingToGroup = null }
            )
        }
        if (choosingSource && vm.phase == LanternsViewModel.Phase.MENU) {
            SourcePicker(source = source, onPick = vm::setScope, onClose = { choosingSource = false })
        }
        vm.card?.let { number ->
            LevelCard(
                level = number,
                stars = stars.getOrNull(number - 1) ?: 0,
                onStart = { vm.startLevel(number) },
                onClose = vm::closeCard
            )
        }
        SparksLayer(effects = cheers, clock = { clock }, slow = false)
    }
    }
}

/** While a round is read: a word over the sky. */
@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.game_loading),
            style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = GameColors.Gold)
        )
    }
}

private const val HINT_TIME = 2f

/** Light status-bar icons over the night sky, and the screen kept on while the game is open. */
@Composable
private fun NightBars() {
    val view = LocalView.current
    val context = LocalContext.current
    val appLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            controller?.isAppearanceLightStatusBars = appLight
            controller?.isAppearanceLightNavigationBars = appLight
            view.keepScreenOn = false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Lays [content] out as wide as it wants to be and, when that is wider than the width it is given, shrinks all of it
 * (keeping its shape, centred) to fit: nothing is cut short, however wide the word.
 */
@Composable
private fun FitWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val inner = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else inner.width
        val fit = if (inner.width > width) width.toFloat() / inner.width else 1f
        layout(width, inner.height) {
            inner.placeRelativeWithLayer((width - inner.width) / 2, 0) {
                scaleX = fit
                scaleY = fit
            }
        }
    }
}

/** The size of a word on a lantern, smaller for a longer word so it fits the window (and [FitWidth] does the rest). */
private fun wordSize(word: QuranWord): TextUnit {
    val letters = word.hafs.count { it.isLetter() }
    return when {
        letters <= 3 -> 30.sp
        letters <= 5 -> 27.sp
        letters <= 7 -> 23.sp
        letters <= 9 -> 20.sp
        else -> 17.sp
    }
}

@Composable
private fun LanternView(
    lantern: Lantern,
    word: QuranWord?,
    clock: () -> Float,
    area: IntSize,
    panelY: Float,
    escapeY: () -> Float,
    wind: Float,
    onTap: (Long) -> Unit
) {
    val density = LocalDensity.current
    val widthPx = with(density) { LanternWidth.toPx() }
    val heightPx = with(density) { LanternHeight.toPx() }
    val body = when (lantern.kind) {
        LanternKind.WORD -> if (lantern.golden) GameColors.Golden else GameColors.hue(lantern.hue)
        LanternKind.SLOW -> GameColors.hues[2]
        LanternKind.LIFE -> GameColors.hues[1]
    }
    Box(
        modifier = Modifier
            .offset {
                clock()
                IntOffset((lantern.x * area.width - widthPx / 2f).roundToInt(), (lantern.y * area.height - heightPx / 2f).roundToInt())
            }
            .size(LanternWidth, LanternHeight)
            .graphicsLayer {
                clock()
                // Fading by drawing each part fainter, not through a layer of its own size, which would cut the
                // glow (wider than the lantern) into a box.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val t = lantern.stateTime
                when (lantern.state) {
                    LanternState.RISING -> {
                        // A gentle lean with the sway, a fade-in as it comes up out of the town, and a fade-out as it
                        // goes under the verse panel.
                        // In the wind it leans the way it is pushed.
                        rotationZ = sin(clock() * 1.35f + lantern.id) * 3f + if (wind > 0f) sin(clock() * 0.45f) * wind * 140f else 0f
                        val fadeIn = ((LanternsGame.SPAWN_Y + 0.05f - lantern.y) / 0.18f).coerceIn(0f, 1f)
                        val fadeOut = ((lantern.y - escapeY()) / 0.07f).coerceIn(0f, 1f)
                        alpha = minOf(fadeIn, fadeOut)
                    }
                    LanternState.CAUGHT -> {
                        val p = (t / LanternsGame.CAUGHT_TIME).coerceIn(0f, 1f)
                        val e = p * p * (3f - 2f * p)
                        translationX = (0.5f - lantern.x) * area.width * e
                        translationY = (panelY - lantern.y) * area.height * e
                        scaleX = 1f - 0.7f * e
                        scaleY = 1f - 0.7f * e
                        alpha = 1f - p * p
                    }
                    LanternState.BROKEN -> {
                        val p = (t / LanternsGame.BROKEN_TIME).coerceIn(0f, 1f)
                        rotationZ = sin(t * 40f) * 12f * (1f - p)
                        translationY = p * p * area.height * 0.3f
                        alpha = 1f - p
                    }
                    LanternState.RELEASED -> {
                        val p = (t / LanternsGame.RELEASE_TIME).coerceIn(0f, 1f)
                        translationY = -p * area.height * 0.18f
                        scaleX = 1f + 0.12f * p
                        scaleY = 1f + 0.12f * p
                        alpha = 1f - p
                    }
                }
            }
            .drawWithContent {
                clock()
                val broken = lantern.state == LanternState.BROKEN
                val shine = if (lantern.golden && !broken) 1.35f + 0.25f * sin(clock() * 5f) else 1f
                drawLantern(body, lit = if (broken) 0.25f else 1f, glow = if (lantern.state == LanternState.CAUGHT) 1.6f else shine)
                if (lantern.golden && !broken) drawTwinkles(clock())
                drawContent()
                if (broken) drawRect(Color(0x66200814))
            }
            .pointerInput(lantern.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTap(lantern.id)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (lantern.kind) {
            LanternKind.WORD -> if (word != null) {
                // However wide the word is in its page font, it is shrunk to fit the window, never cut to an ellipsis.
                FitWidth(Modifier.width(LanternWidth * 0.72f).offset(y = 4.dp)) {
                    MushafText(
                        span = remember(word) { MushafSpan.of(listOf(word)) },
                        modifier = Modifier,
                        size = wordSize(word),
                        color = GameColors.Ink,
                        textAlign = TextAlign.Center
                    )
                }
            }
            LanternKind.SLOW -> Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = GameColors.Ink, modifier = Modifier.size(36.dp).offset(y = 3.dp))
            LanternKind.LIFE -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = GameColors.hues[1], modifier = Modifier.size(36.dp).offset(y = 3.dp))
        }
    }
}

/** The sparks, the floating points, and the flashes (red for a mistake, gold for a verse, blue while time is slow). */
@Composable
private fun SparksLayer(effects: Effects, clock: () -> Float, slow: Boolean) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        clock()
        if (slow) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, GameColors.Calm.copy(alpha = 0.28f)),
                    center = center,
                    radius = size.maxDimension * 0.75f
                )
            )
        }
        if (effects.goldFlash > 0f) {
            drawRect(GameColors.Gold.copy(alpha = 0.12f * effects.goldFlash))
        }
        if (effects.redFlash > 0f) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, GameColors.Red.copy(alpha = 0.5f * effects.redFlash)),
                    center = center,
                    radius = size.maxDimension * 0.7f
                )
            )
        }
        for (s in effects.sparks) {
            val a = (1f - s.age / s.life).coerceIn(0f, 1f)
            drawCircle(s.color.copy(alpha = 0.3f * a), radius = s.radius * 2.6f, center = Offset(s.x, s.y))
            drawCircle(s.color.copy(alpha = a), radius = s.radius, center = Offset(s.x, s.y))
        }
        for (f in effects.floaters) {
            val a = (1f - f.age / Effects.FLOAT_LIFE).coerceIn(0f, 1f)
            val style = TextStyle(
                color = f.color.copy(alpha = a),
                fontSize = if (f.big) 40.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = 0.6f * a), Offset(0f, 2f), 8f),
                // "+200" and "×3" read left to right in any language.
                textDirection = TextDirection.Ltr
            )
            val layout = measurer.measure(f.text, style)
            drawText(layout, topLeft = Offset(f.x - layout.size.width / 2f, f.y - layout.size.height / 2f))
        }
    }
}

/** A few lanterns drifting up behind the menu. */
@Composable
private fun MenuLanterns(time: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val lw = 50.dp.toPx()
        val lh = lw * 1.3f
        // Along the two sides only, so they never pass behind the words and buttons in the middle.
        val xs = listOf(0.08f, 0.92f, 0.1f, 0.9f)
        xs.forEachIndexed { i, x ->
            val p = (t * 0.03f + i * 0.26f) % 1f
            val y = size.height * (1.12f - p * 1.35f)
            val sx = size.width * x + sin(t * 1.1f + i) * 6.dp.toPx()
            // Faint while passing the words in the middle of the screen, bright over the town and the high sky.
            val band = y / size.height
            val clear = ((kotlin.math.abs(band - 0.5f) - 0.24f) / 0.1f).coerceIn(0f, 1f)
            drawLantern(
                GameColors.hue(i + 1), lit = 0.85f, glow = 0.8f,
                topLeft = Offset(sx - lw / 2f, y - lh / 2f), area = Size(lw, lh), alpha = 0.12f + 0.88f * clear
            )
        }
    }
}

@Composable
private fun HudBar(hud: Hud, mode: GameMode, levelNumber: Int?, progress: (() -> Float)?, onPause: () -> Unit) {
    val score by animateIntAsState(hud.score, tween(450), label = "score")
    // The score jumps a little with each right word, more as the run grows.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(hud.combo) {
        if (hud.combo > 0) {
            pop.snapTo(1f + 0.06f * LanternsGame.multiplierFor(hud.combo))
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPause) {
            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.game_pause_cd), tint = GameColors.Gold)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val scoreLabel = stringResource(R.string.game_score_cd, hud.score)
            // A calm sitting keeps no score: its name, and how far it has come.
            if (mode == GameMode.CALM) Text(
                text = stringResource(R.string.game_mode_calm),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = GameColors.Gold)
            ) else Text(
                text = score.toString(),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = GameColors.Gold,
                    shadow = Shadow(GameColors.Gold.copy(alpha = 0.5f), blurRadius = 18f)
                ),
                modifier = Modifier
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .semantics { contentDescription = scoreLabel }
            )
            val mult = LanternsGame.multiplierFor(hud.combo)
            Text(
                text = when {
                    mode == GameMode.CALM -> pluralStringResource(R.plurals.game_calm_built, hud.versesDone, hud.versesDone)
                    hud.combo >= 2 -> stringResource(R.string.game_combo, hud.combo, mult)
                    levelNumber != null -> stringResource(R.string.game_level_title, levelNumber)
                    mode == GameMode.MASTERY -> pluralStringResource(R.plurals.game_mastery_verses, hud.versesDone, hud.versesDone)
                    else -> stringResource(R.string.game_level, hud.level)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (mult > 1) Color.White else Color.White.copy(alpha = 0.7f)
            )
        }
        // Nothing to lose in a calm sitting: no hearts (a space keeps the score in the middle).
        if (mode == GameMode.CALM) Spacer(Modifier.width(48.dp)) else Lights(hud.lives, hud.startLives)
        Spacer(Modifier.width(8.dp))
    }
    // A level: how much of it is built.
    if (progress != null) {
        val label = stringResource(R.string.game_progress_cd, hud.built)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .semantics { contentDescription = label }
                .drawWithContent {
                    val p = progress().coerceIn(0f, 1f)
                    // From the start of the line in the reading direction.
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val w = size.width * p
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(GameColors.GoldDeep, GameColors.Gold)),
                        topLeft = Offset(if (rtl) size.width - w else 0f, 0f),
                        size = Size(w, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                }
        )
    }
}

/** The lights left: a heart for each, and empty ones for those put out (as many as the round began with). */
@Composable
private fun Lights(lives: Int, start: Int) {
    val label = stringResource(R.string.game_lives_cd, lives)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(maxOf(start, lives)) { i ->
            Icon(
                imageVector = if (i < lives) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (i < lives) GameColors.hues[1] else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The verse being built: where it is, the words in place, how many are left, and after a slip the word wanted. */
@Composable
private fun VersePanel(
    verse: GameVerse?,
    placed: Int,
    words: Map<Int, QuranWord>,
    hint: QuranWord?,
    glow: () -> Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GameColors.Panel)
            .drawWithContent {
                drawContent()
                val g = glow()
                drawRoundRect(
                    GameColors.Gold.copy(alpha = 0.35f + 0.65f * g),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke((1.2f + 2.5f * g).dp.toPx())
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(min = 84.dp)
    ) {
        if (verse == null) return@Column
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val id = verse.ayahId
            Text(
                text = if (id in index) "${SurahData.nameOf(index.surahOf(id))} · ${index.ayahOf(id)}" else "",
                style = MaterialTheme.typography.labelLarge,
                color = GameColors.Gold,
                modifier = Modifier.weight(1f)
            )
            val left = (verse.words.size - placed).coerceAtLeast(0)
            if (left > 0) {
                Text(
                    text = pluralStringResource(R.plurals.game_words_left, left, left),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        val inPlace = verse.words.take(placed).mapNotNull { words[it.id] }
        // A long verse shows its last words, after an ellipsis: nine, or fewer when two lines cannot hold them (a narrow
        // screen, wide words), so the word just built is never the one cut off.
        var tailWords by remember(verse) { mutableIntStateOf(TAIL_WORDS) }
        val tailShown = tailWords
        val tail = inPlace.takeLast(tailShown)
        // Started from nothing: the end of the verse before it, faint, to go on from (until the verse is under way).
        val cue = if (verse.given == 0 && placed < CUE_UNTIL) verse.cue.mapNotNull { words[it] } else emptyList()
        val span = remember(verse, placed, tailShown) {
            (if (cue.isNotEmpty()) MushafSpan.More + MushafSpan.of(cue) else MushafSpan.Empty) +
                (if (inPlace.size > tail.size) MushafSpan.More else MushafSpan.Empty) + MushafSpan.of(tail) +
                if (placed < verse.words.size) MushafSpan.More else MushafSpan.Empty
        }
        val faint = remember(verse, placed) { cue.associate { it.id to Color.White.copy(alpha = 0.42f) } }
        MushafText(
            span = span,
            size = 24.sp,
            color = if (placed >= verse.words.size) GameColors.Gold else GameColors.Window,
            maxLines = 2,
            wordColors = faint,
            onOverflow = { if (tailWords == tailShown && tailShown > 1) tailWords = tailShown - 1 }
        )
        if (hint != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.game_next_word), style = MaterialTheme.typography.labelMedium, color = GameColors.Red)
                Spacer(Modifier.width(8.dp))
                MushafText(span = remember(hint) { MushafSpan.of(listOf(hint)) }, modifier = Modifier, size = 22.sp, color = GameColors.Gold, maxLines = 1)
            }
        }
    }
}

private const val TAIL_WORDS = 9

/** How far toward dawn the sky of a calm sitting is: soft, before the light. */
private const val CALM_DAWN = 0.5f

/** The end of the verse before shows until this many words of the new one are in place. */
private const val CUE_UNTIL = 3

@Composable
private fun BannerView(banner: Banner, clock: () -> Float, onDone: () -> Unit) {
    val age = clock() - banner.start
    if (age > BANNER_TIME) {
        LaunchedEffect(banner) { onDone() }
        return
    }
    val title: String
    val line: String?
    val color: Color
    when (banner.kind) {
        Banner.Kind.VERSE -> {
            title = stringResource(R.string.game_verse_done)
            line = buildList {
                // Isolated left to right, so "+800" keeps its sign in front in an Arabic line.
                add("$LTR_START+${banner.bonus}$LTR_END")
                if (banner.perfect) add(stringResource(R.string.game_perfect))
                if (banner.level > 0) add(stringResource(R.string.game_level, banner.level))
            }.joinToString(" · ")
            color = GameColors.Gold
        }
        Banner.Kind.SLOW -> {
            title = stringResource(R.string.game_slow)
            line = null
            color = GameColors.Calm
        }
        Banner.Kind.LIFE -> {
            title = stringResource(R.string.game_life)
            line = null
            color = GameColors.hues[1]
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val a = clock() - banner.start
                val inP = (a / 0.28f).coerceIn(0f, 1f)
                val pop = 0.6f + 0.4f * inP + 0.08f * sin(inP * 3.1416f)
                scaleX = pop
                scaleY = pop
                alpha = if (a > BANNER_TIME - 0.45f) ((BANNER_TIME - a) / 0.45f).coerceIn(0f, 1f) else inP
                translationY = -a * 18.dp.toPx()
            }
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.sp,
                    color = color,
                    shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = 28f),
                    textAlign = TextAlign.Center
                )
            )
            if (line != null) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }
    }
}

private const val BANNER_TIME = 1.6f

/** Unicode isolates (built from their code points, not written in the source): what is between them reads left to right in an Arabic line. */
private val LTR_START = Char(0x2066)
private val LTR_END = Char(0x2069)
private const val COUNTDOWN = 2.4f

/** The game's sounds on or off. */
@Composable
internal fun SoundButton(on: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (on) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = stringResource(if (on) R.string.game_sound_on_cd else R.string.game_sound_off_cd),
            tint = if (on) GameColors.Gold else Color.White.copy(alpha = 0.5f)
        )
    }
}

/** The number the countdown shows with [left] seconds to go: 3, 2, 1. */
private fun countdownNumber(left: Float): Int = kotlin.math.ceil(left / (COUNTDOWN / 3f)).toInt().coerceIn(1, 3)

/** "3, 2, 1" before a round, each number popping in; a level's name over it. */
@Composable
private fun Countdown(left: Float, title: String?) {
    val number = countdownNumber(left)
    val within = 1f - ((left % (COUNTDOWN / 3f)) / (COUNTDOWN / 3f))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (title != null) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = Color.White,
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 16f)
                ),
                modifier = Modifier.offset(y = (-110).dp)
            )
        }
        Text(
            text = number.toString(),
            style = TextStyle(
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 120.sp,
                color = GameColors.Gold,
                shadow = Shadow(GameColors.Gold.copy(alpha = 0.7f), blurRadius = 40f)
            ),
            modifier = Modifier.graphicsLayer {
                val grow = 1.4f - 0.4f * kotlin.math.min(1f, within * 3f)
                scaleX = grow
                scaleY = grow
                alpha = 1f - within * within
            }
        )
    }
}

/** A big gold button in the game's look. */
@Composable
internal fun GameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val sounds = LocalGameSounds.current
    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(
                if (primary) Brush.verticalGradient(listOf(GameColors.Gold, Color(0xFFE0A63A)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.08f)))
            )
            .border(1.dp, if (primary) Color(0x66FFFFFF) else GameColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
            .clickable { sounds?.play(GameSound.TAP); onClick() }
            .heightIn(min = 54.dp)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (primary) GameColors.Ink else GameColors.Gold
        )
    }
}

@Composable
internal fun GameChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val sounds = LocalGameSounds.current
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) GameColors.Gold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) GameColors.Gold else Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { sounds?.play(GameSound.TAP); onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.3f)
                selected -> GameColors.Gold
                else -> Color.White.copy(alpha = 0.8f)
            }
        )
    }
}

@Composable
internal fun Scrim(color: Color = Color(0xB3050816), content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun PauseOverlay(sound: Boolean, onSound: () -> Unit, restart: String, onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    BackHandler(onBack = onResume)
    Scrim {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.game_paused),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = GameColors.Gold)
            )
            Spacer(Modifier.height(6.dp))
            GameButton(stringResource(R.string.game_resume), onResume, Modifier.widthIn(min = 220.dp))
            GameButton(restart, onRestart, Modifier.widthIn(min = 220.dp), primary = false)
            GameButton(stringResource(R.string.game_quit), onQuit, Modifier.widthIn(min = 220.dp), primary = false)
            SoundButton(sound, onSound)
        }
    }
}

/** The verses of a round, the last first, each with how it went: a tap opens it in the Mushaf. */
@Composable
internal fun PlayedVerses(game: LanternsGame, onOpenVerse: (page: Int, ayahId: Int) -> Unit) {
    val context = LocalContext.current
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    if (game.played.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GameColors.Panel)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.game_review),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            for (verse in game.played.takeLast(8).reversed()) {
                val id = verse.ayahId
                if (id !in index) continue
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVerse(index.firstPageOf(id), id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${SurahData.nameOf(index.surahOf(id))} · ${index.ayahOf(id)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when {
                            !verse.done -> stringResource(R.string.game_unfinished)
                            verse.mistakes == 0 -> stringResource(R.string.game_perfect)
                            else -> pluralStringResource(R.plurals.game_mistakes, verse.mistakes, verse.mistakes)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (verse.done && verse.mistakes == 0) GameColors.Gold else GameColors.Red
                    )
                }
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GameColors.Panel)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = GameColors.Gold))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

/**
 * The game's card on the Practice screen: a little night sky with lanterns bobbing over a town, the name, a line on
 * what it is, the best score, and a play button.
 */
@Composable
fun LanternsCard(masteryBest: Int, stars: List<Int>, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) clock += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(shape)
            .border(1.dp, GameColors.Gold.copy(alpha = 0.45f), shape)
            .clickable(onClick = onPlay)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock
            drawRect(Brush.verticalGradient(listOf(GameColors.SkyTop, GameColors.SkyMid, GameColors.SkyLow)))
            // A few stars.
            for (i in 0 until 18) {
                val x = ((i * 0.37f) % 1f) * size.width
                val y = ((i * 0.53f) % 0.7f) * size.height
                val a = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (1f + i % 3) + i))
                drawCircle(Color.White.copy(alpha = a * 0.8f), radius = 1.4.dp.toPx(), center = Offset(x, y))
            }
            val lw = 46.dp.toPx()
            val lh = lw * 1.3f
            // In a right-to-left layout the words are on the right, so the lanterns float on the left (and the other way round).
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            listOf(0.12f, 0.26f, 0.4f).forEachIndexed { i, x ->
                val cx = (if (rtl) x else 1f - x) * size.width
                val cy = size.height * (0.42f + 0.07f * sin(t * 1.3f + i * 1.7f))
                drawLantern(GameColors.hue(i * 2), lit = 1f, glow = 1f, topLeft = Offset(cx - lw / 2f, cy - lh / 2f), area = Size(lw, lh))
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 20.dp)
                .fillMaxWidth(0.62f)
        ) {
            // «لعبة», and the level reached once one is won.
            val tag = stringResource(R.string.game_tag)
            Text(
                text = if (stars.isEmpty()) tag else "$tag · ${stringResource(R.string.game_level_title, LanternsLevels.furthestOpen(stars))}",
                style = MaterialTheme.typography.labelMedium,
                color = GameColors.Gold.copy(alpha = 0.85f)
            )
            Text(
                text = stringResource(R.string.game_title),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = GameColors.Gold,
                    shadow = Shadow(GameColors.Gold.copy(alpha = 0.6f), blurRadius = 22f)
                )
            )
            Text(
                text = stringResource(R.string.game_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(GameColors.Gold)
                        .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = GameColors.Ink, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.game_play), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = GameColors.Ink)
                }
                when {
                    stars.isNotEmpty() -> {
                        Spacer(Modifier.width(10.dp))
                        StarCount(stars.sum())
                    }
                    masteryBest > 0 -> {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            pluralStringResource(R.plurals.game_mastery_best, masteryBest, masteryBest),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}
