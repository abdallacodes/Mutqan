package com.example.mutqan.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.domain.game.LanternsGame
import com.example.mutqan.domain.game.LanternsLevels
import com.example.mutqan.domain.game.LevelNews
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlin.math.roundToInt
import kotlin.math.sin

private val NodeWidth = 62.dp
private val NodeHeight = 80.dp
private val RowHeight = 112.dp

/** Locked levels shown above the next one, dark, so the climb goes on out of sight. */
private const val LEVELS_AHEAD = 4

/** Where a level's lantern hangs across the map, as a fraction of the width: a path winding up the sky. */
private fun nodeX(level: Int): Float = 0.5f + 0.27f * sin((level - 1) * 0.9f)

/**
 * The map of levels: lanterns up the night sky along a winding path, level 1 at the bottom. A level won is lit with its
 * stars under it, the next one glows and bobs, those after it are dark. Tapping a lit or glowing lantern opens its card;
 * a test build also opens a dark one with a long press.
 */
@Composable
internal fun LevelMap(
    stars: List<Int>,
    clock: () -> Float,
    anyLevel: Boolean,
    failed: Boolean,
    onLevel: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val next = stars.size + 1
    val count = next + LEVELS_AHEAD
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (next - 3).coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.game_back),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(10.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.game_levels),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = GameColors.Gold)
            )
            Spacer(Modifier.weight(1f))
            StarCount(stars.sum(), Modifier.padding(horizontal = 10.dp))
        }
        if (failed) {
            Text(
                stringResource(R.string.game_nothing),
                style = MaterialTheme.typography.bodyMedium,
                color = GameColors.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
        }
        LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp)
        ) {
            items(count, key = { it }) { i ->
                val level = i + 1
                val kind = when {
                    level <= stars.size -> NodeKind.WON
                    level == next -> NodeKind.NEXT
                    else -> NodeKind.LOCKED
                }
                LevelNode(
                    level = level,
                    stars = stars.getOrNull(level - 1) ?: 0,
                    kind = kind,
                    pathUp = level < count,
                    pathLit = level < next,
                    clock = clock,
                    onTap = { if (kind != NodeKind.LOCKED) onLevel(level) },
                    onLongTap = { if (kind == NodeKind.LOCKED && anyLevel) onLevel(level) }
                )
            }
        }
    }
}

private enum class NodeKind { WON, NEXT, LOCKED }

@Composable
private fun LevelNode(
    level: Int,
    stars: Int,
    kind: NodeKind,
    pathUp: Boolean,
    pathLit: Boolean,
    clock: () -> Float,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val density = LocalDensity.current
    val label = when (kind) {
        NodeKind.LOCKED -> stringResource(R.string.game_level_locked_cd, level)
        NodeKind.NEXT -> stringResource(R.string.game_level_title, level)
        NodeKind.WON -> stringResource(R.string.game_level_cd, level, pluralStringResource(R.plurals.game_stars, stars, stars))
    }
    // Laid out from the left whatever the language: the path is drawn in the same coordinates.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(RowHeight)) {
            val width = constraints.maxWidth.toFloat()
            val rowPx = with(density) { RowHeight.toPx() }
            val x = nodeX(level) * width
            if (pathUp) {
                Canvas(Modifier.fillMaxSize()) {
                    // To the next level's lantern, in the row above (drawn past this row's top).
                    val from = Offset(x, rowPx / 2f)
                    val to = Offset(nodeX(level + 1) * width, -rowPx / 2f)
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        cubicTo(from.x, from.y - rowPx * 0.55f, to.x, to.y + rowPx * 0.55f, to.x, to.y)
                    }
                    drawPath(
                        path,
                        color = if (pathLit) GameColors.Gold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
            val nodeW = with(density) { NodeWidth.toPx() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .absoluteOffset { IntOffset((x - nodeW / 2f).roundToInt(), 0) }
                    .width(NodeWidth)
                    .graphicsLayer {
                        if (kind == NodeKind.NEXT) translationY = sin(clock() * 2.2f) * 4.dp.toPx()
                    }
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .combinedClickable(onClick = onTap, onLongClick = onLongTap)
            ) {
                Box(Modifier.size(NodeWidth, NodeHeight), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val t = clock()
                        when (kind) {
                            NodeKind.WON -> drawLantern(GameColors.hue(level), lit = 1f, glow = 0.9f)
                            NodeKind.NEXT -> {
                                drawCircle(GameColors.Gold.copy(alpha = 0.16f + 0.1f * sin(t * 3f)), radius = size.width * 0.9f, center = Offset(size.width / 2f, size.height * 0.52f))
                                drawLantern(GameColors.Golden, lit = 1f, glow = 1.3f + 0.35f * sin(t * 3f))
                            }
                            NodeKind.LOCKED -> drawLantern(GameColors.hue(level), lit = 0.22f, glow = 0.3f, area = Size(size.width, size.height))
                        }
                    }
                    Text(
                        text = level.toString(),
                        style = TextStyle(
                            fontFamily = AmiriFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (level >= 100) 16.sp else 20.sp,
                            color = if (kind == NodeKind.LOCKED) Color.White.copy(alpha = 0.35f) else GameColors.Ink
                        ),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                when (kind) {
                    NodeKind.WON -> StarsRow(stars, 15.dp)
                    NodeKind.NEXT -> Text(
                        text = stringResource(R.string.game_play),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = GameColors.Ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GameColors.Gold)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    NodeKind.LOCKED -> Spacer(Modifier.height(15.dp))
                }
            }
        }
    }
}

/** Three stars, [filled] of them gold. */
@Composable
internal fun StarsRow(filled: Int, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(3) { i ->
            Icon(
                imageVector = if (i < filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i < filled) GameColors.Gold else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(size)
            )
        }
    }
}

/** A star and the stars won in all. */
@Composable
internal fun StarCount(total: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.game_stars, total, total)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = GameColors.Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(total.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = GameColors.Gold)
    }
}

/** What a level brings, for its card. */
@Composable
private fun newsText(level: Int): String = when (LanternsLevels.news(level)) {
    LevelNews.START -> stringResource(R.string.game_news_start)
    LevelNews.ONE_GIVEN -> stringResource(R.string.game_news_one_given)
    LevelNews.FOURTH_LANTERN -> stringResource(R.string.game_news_fourth)
    LevelNews.LOOK_ALIKE -> stringResource(R.string.game_news_look_alike)
    LevelNews.GOLDEN -> stringResource(R.string.game_news_golden)
    LevelNews.FIFTH_LANTERN -> stringResource(R.string.game_news_fifth)
    LevelNews.WIND -> stringResource(R.string.game_news_wind)
    LevelNews.NONE_GIVEN -> stringResource(R.string.game_news_none_given)
    LevelNews.SIXTH_LANTERN -> stringResource(R.string.game_news_sixth)
    LevelNews.FASTER_LONGER -> stringResource(R.string.game_news_faster_longer)
    LevelNews.FASTER -> stringResource(R.string.game_news_faster)
}

/** A level's card: its number, its stars so far, what it brings, the words to build, and the way in. */
@Composable
internal fun LevelCard(level: Int, stars: Int, onStart: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val rules = remember(level) { LanternsLevels.rules(level) }
    val news = remember(level) { LanternsLevels.news(level) }
    val isNew = news != LevelNews.FASTER && news != LevelNews.FASTER_LONGER
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3050816))
            .clickable(interactionSource = null, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(GameColors.Panel)
                .border(1.dp, GameColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                // Taps on the card stay on it.
                .clickable(interactionSource = null, indication = null) {}
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.game_level_title, level),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = GameColors.Gold,
                    shadow = Shadow(GameColors.Gold.copy(alpha = 0.5f), blurRadius = 24f)
                )
            )
            if (stars > 0) {
                StarsRow(stars, 26.dp)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                if (isNew) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(GameColors.Gold)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = GameColors.Ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(stringResource(R.string.game_new_label), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = GameColors.Ink)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(newsText(level), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = pluralStringResource(R.plurals.game_level_words, rules.words, rules.words),
                style = MaterialTheme.typography.titleSmall,
                color = GameColors.Gold.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(18.dp))
            GameButton(stringResource(R.string.game_start), onStart, Modifier.widthIn(min = 220.dp))
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.game_back), onClose, Modifier.widthIn(min = 220.dp), primary = false)
        }
    }
}

/** Stars landing one after another, each popping in; [onLand] is called as each gold one lands. */
@Composable
private fun StarsReveal(won: Int, onLand: (Int) -> Unit) {
    val scales = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(won) {
        kotlinx.coroutines.delay(350)
        for (i in 0 until 3) {
            if (i < won) onLand(i)
            scales[i].animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 320f))
            kotlinx.coroutines.delay(120)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 3) {
            val big = i == 1
            Icon(
                imageVector = if (i < won) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i < won) GameColors.Gold else Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(if (big) 64.dp else 50.dp)
                    .graphicsLayer {
                        scaleX = scales[i].value
                        scaleY = scales[i].value
                        rotationZ = (1f - scales[i].value) * if (i == 0) -40f else 40f
                    }
            )
        }
    }
}

/** The end of a level: won with its stars, or lost; the score, the round's numbers and verses, and where next. */
@Composable
internal fun LevelOverOverlay(
    game: LanternsGame,
    level: Int,
    starsWon: Int,
    onStarLand: (Int) -> Unit,
    onNext: () -> Unit,
    onAgain: () -> Unit,
    onMap: () -> Unit,
    onOpenVerse: (page: Int, ayahId: Int) -> Unit
) {
    BackHandler(onBack = onMap)
    val cleared = game.cleared
    val shownScore by animateIntAsState(game.score, tween(900), label = "level score")
    var counted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { counted = true }
    Scrim(color = Color(0xE0050816)) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(if (cleared) R.string.game_level_cleared else R.string.game_level_failed, level),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = GameColors.Gold,
                    shadow = Shadow(GameColors.Gold.copy(alpha = if (cleared) 0.7f else 0.25f), blurRadius = 28f),
                    textAlign = TextAlign.Center
                )
            )
            Spacer(Modifier.height(8.dp))
            if (cleared) StarsReveal(starsWon, onStarLand)
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !cleared -> stringResource(R.string.game_failed_hint)
                    starsWon == 3 -> stringResource(R.string.game_perfect)
                    else -> stringResource(R.string.game_three_stars_hint)
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (cleared && starsWon == 3) GameColors.Gold else Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Text(
                text = (if (counted) shownScore else 0).toString(),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 56.sp, color = Color.White)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stringResource(R.string.game_verses_done), game.versesDone.toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.game_slips), game.slips.toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.game_best_combo), game.bestCombo.toString(), Modifier.weight(1f))
            }
            PlayedVerses(game, onOpenVerse)
            Spacer(Modifier.height(22.dp))
            if (cleared) {
                GameButton(stringResource(R.string.game_level_title, level + 1), onNext, Modifier.widthIn(min = 240.dp))
                Spacer(Modifier.height(10.dp))
                GameButton(stringResource(R.string.game_replay_level), onAgain, Modifier.widthIn(min = 240.dp), primary = false)
            } else {
                GameButton(stringResource(R.string.game_try_again), onAgain, Modifier.widthIn(min = 240.dp))
            }
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.game_levels), onMap, Modifier.widthIn(min = 240.dp), primary = false)
        }
    }
}
