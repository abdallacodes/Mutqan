package com.example.mutqan.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.TafsirText
import com.example.mutqan.domain.game.GameVerse
import com.example.mutqan.domain.game.LanternsGame
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranWord
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.practice.ScopePreset
import com.example.mutqan.ui.theme.AmiriFontFamily

/**
 * The game's front: its title and one line on how it plays, the three modes as cards (the calm one, the levels, the
 * hardest), where the verses come from, and the sound. Nothing else to set.
 */
@Composable
internal fun ModesMenu(
    sound: Boolean,
    onSound: () -> Unit,
    level: Int,
    starsTotal: Int,
    masteryBest: Int,
    source: LanternsViewModel.Source?,
    failed: Boolean,
    onCalm: () -> Unit,
    onLevel: () -> Unit,
    onMap: () -> Unit,
    onMastery: () -> Unit,
    onSource: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.game_back),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(10.dp)
            )
            Spacer(Modifier.weight(1f))
            SoundButton(sound, onSound)
        }
        Text(
            text = stringResource(R.string.game_title),
            style = TextStyle(
                fontFamily = AmiriFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                color = GameColors.Gold,
                shadow = Shadow(GameColors.Gold.copy(alpha = 0.65f), blurRadius = 40f),
                textAlign = TextAlign.Center
            )
        )
        Text(
            text = stringResource(R.string.game_how),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(18.dp))
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(
                title = stringResource(R.string.game_mode_challenge),
                line = stringResource(R.string.game_mode_challenge_line),
                note = stringResource(R.string.game_level_title, level),
                stars = starsTotal,
                action = stringResource(R.string.game_play_level, level),
                onAction = onLevel,
                second = stringResource(R.string.game_levels),
                onSecond = onMap,
                primary = true
            )
            ModeCard(
                title = stringResource(R.string.game_mode_calm),
                line = stringResource(R.string.game_mode_calm_line),
                action = stringResource(R.string.game_start),
                onAction = onCalm
            )
            ModeCard(
                title = stringResource(R.string.game_mode_mastery),
                line = stringResource(R.string.game_mode_mastery_line),
                note = if (masteryBest > 0) pluralStringResource(R.plurals.game_mastery_best, masteryBest, masteryBest) else stringResource(R.string.game_mode_hardest),
                action = stringResource(R.string.game_start),
                onAction = onMastery
            )
        }
        Spacer(Modifier.height(14.dp))
        SourceLine(source, onSource)
        if (failed) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.game_nothing), style = MaterialTheme.typography.bodyMedium, color = GameColors.Red, textAlign = TextAlign.Center)
        }
    }
}

/** A mode: its name and what sets it apart, a note on the side (the level, a record), and the way in. */
@Composable
private fun ModeCard(
    title: String,
    line: String,
    action: String,
    onAction: () -> Unit,
    note: String? = null,
    stars: Int = 0,
    second: String? = null,
    onSecond: () -> Unit = {},
    primary: Boolean = false
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GameColors.Panel)
            .border(1.dp, GameColors.Gold.copy(alpha = if (primary) 0.7f else 0.3f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = GameColors.Gold),
                modifier = Modifier.weight(1f)
            )
            if (stars > 0) {
                StarCount(stars)
                Spacer(Modifier.width(8.dp))
            }
            if (note != null) Text(note, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
        }
        Text(line, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameButton(action, onAction, Modifier.weight(1f), primary = primary)
            if (second != null) {
                Spacer(Modifier.width(10.dp))
                GameButton(second, onSecond, primary = false)
            }
        }
    }
}

/** The name of the choice [scope] is, or «custom» when it is none of the one-tap ones. */
@Composable
private fun scopeName(scope: PracticeScope): String =
    stringResource(ScopePreset.entries.firstOrNull { it.scope == scope }?.label ?: R.string.practice_chip_custom)

/** «Verses from: all my hifz · 104 pages» (or Juz 'Amma, when nothing is memorised); a tap changes it. */
@Composable
private fun SourceLine(source: LanternsViewModel.Source?, onClick: () -> Unit) {
    val text = when {
        source == null -> " "
        source.amma -> stringResource(R.string.game_source_amma)
        else -> {
            val pages = pluralStringResource(R.plurals.scope_span_pages, source.pages.size, source.pages.size)
            val name = if (source.widened) stringResource(R.string.practice_chip_all) else scopeName(source.scope)
            stringResource(R.string.game_source_from, "$name · $pages")
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            // A panel under it: the lit town shows through the bottom of the menu.
            .background(GameColors.Panel)
            .border(1.dp, GameColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = GameColors.Gold)
        Spacer(Modifier.width(6.dp))
        Text("‹", style = MaterialTheme.typography.titleMedium, color = GameColors.Gold.copy(alpha = 0.8f))
    }
}

/** Where the verses come from: the same one-tap choices as the Practice screen, kept for both. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourcePicker(source: LanternsViewModel.Source?, onPick: (PracticeScope) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Scrim {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GameColors.Panel)
                .border(1.dp, GameColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.game_source_title),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = GameColors.Gold)
            )
            val current = source?.scope
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopePreset.entries.forEach { preset ->
                    GameChip(stringResource(preset.label), selected = preset.scope == current, enabled = source?.amma != true) { onPick(preset.scope) }
                }
                if (current != null && ScopePreset.entries.none { it.scope == current }) {
                    GameChip(stringResource(R.string.practice_chip_custom), selected = true, enabled = false) {}
                }
            }
            Text(
                stringResource(if (source?.amma == true) R.string.game_source_amma_hint else R.string.game_source_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            GameButton(stringResource(R.string.game_done), onClose, Modifier.widthIn(min = 200.dp))
        }
    }
}

/**
 * After a verse of a calm sitting: the verse whole, then its meaning (المختصر في التفسير, the verse's own words in it
 * in gold), until the reader goes on.
 */
@Composable
internal fun MeaningCard(verse: GameVerse, words: Map<Int, QuranWord>, meaning: String?, last: Boolean, onNext: () -> Unit) {
    Scrim(color = Color(0xCC050816)) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(18.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GameColors.Panel)
                .border(1.dp, GameColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shown = remember(verse) { verse.words.mapNotNull { words[it.id] } }
            MushafText(span = remember(shown) { MushafSpan.of(shown) }, size = 24.sp, color = GameColors.Gold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.game_meaning_title),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            val body = remember(meaning) {
                buildAnnotatedString {
                    TafsirText.parse(meaning.orEmpty()).forEachIndexed { i, block ->
                        if (i > 0) append("\n")
                        for (run in block.runs) {
                            if (run.quote) withStyle(SpanStyle(color = GameColors.Gold)) { append(run.text) }
                            else if (block.heading) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(run.text) }
                            else append(run.text)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = if (body.isEmpty()) buildAnnotatedString { append(stringResource(R.string.game_meaning_none)) } else body,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp),
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
            Text(
                stringResource(R.string.game_meaning_source),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            Spacer(Modifier.height(14.dp))
            GameButton(stringResource(if (last) R.string.game_meaning_finish else R.string.game_meaning_next), onNext, Modifier.widthIn(min = 220.dp))
        }
    }
}

/** A calm sitting done: the verses built (each opens in the Mushaf), and another sitting or the end. */
@Composable
internal fun CalmOverOverlay(
    game: LanternsGame,
    similarCount: Int,
    onSimilar: () -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
    onOpenVerse: (page: Int, ayahId: Int) -> Unit
) {
    BackHandler(onBack = onDone)
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
                text = stringResource(R.string.game_calm_done),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = GameColors.Gold,
                    shadow = Shadow(GameColors.Gold.copy(alpha = 0.5f), blurRadius = 30f)
                )
            )
            Text(
                text = pluralStringResource(R.plurals.game_calm_built, game.versesDone, game.versesDone),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            if (game.played.any { it.mistakes > 0 }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.game_calm_slipped),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            PlayedVerses(game, onOpenVerse)
            Spacer(Modifier.height(22.dp))
            GameButton(stringResource(R.string.game_calm_more), onMore, Modifier.widthIn(min = 220.dp))
            SimilarButton(similarCount, onSimilar)
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.game_calm_finish), onDone, Modifier.widthIn(min = 220.dp), primary = false)
        }
    }
}

/** A mastery run over: the verses built in it, the record, how it went, and another run. */
@Composable
internal fun MasteryOverOverlay(
    game: LanternsGame,
    best: Int,
    newRecord: Boolean,
    similarCount: Int,
    onSimilar: () -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    onOpenVerse: (page: Int, ayahId: Int) -> Unit
) {
    BackHandler(onBack = onBack)
    val shown by animateIntAsState(game.versesDone, tween(700), label = "verses")
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
                text = stringResource(if (newRecord) R.string.game_mastery_new else R.string.game_mastery_over),
                style = TextStyle(
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = GameColors.Gold,
                    shadow = Shadow(GameColors.Gold.copy(alpha = if (newRecord) 0.8f else 0.3f), blurRadius = 30f),
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = (if (counted) shown else 0).toString(),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 64.sp, color = Color.White)
            )
            Text(
                text = pluralStringResource(R.plurals.game_mastery_verses, game.versesDone, game.versesDone),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            val record = maxOf(best, game.versesDone)
            if (record > 0) {
                Text(pluralStringResource(R.plurals.game_mastery_best, record, record), style = MaterialTheme.typography.titleSmall, color = GameColors.Gold)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stringResource(R.string.game_score_label), game.score.toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.game_best_combo), game.bestCombo.toString(), Modifier.weight(1f))
            }
            PlayedVerses(game, onOpenVerse)
            Spacer(Modifier.height(22.dp))
            GameButton(stringResource(R.string.game_again), onAgain, Modifier.widthIn(min = 220.dp))
            SimilarButton(similarCount, onSimilar)
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.game_back), onBack, Modifier.widthIn(min = 220.dp), primary = false)
        }
    }
}

/** The way from an end screen to the mutashabihat of the round's verses, when some have them. */
@Composable
internal fun SimilarButton(count: Int, onClick: () -> Unit) {
    if (count <= 0) return
    Spacer(Modifier.height(10.dp))
    GameButton(pluralStringResource(R.plurals.game_similar_button, count, count), onClick, Modifier.widthIn(min = 240.dp), primary = false)
}

/**
 * The mutashabihat of the round's verses: each verse that has some, with the closest of them (the words they share in
 * gold), each chosen or not, and a button that puts the verse and the chosen ones in a mutashabihat group.
 */
@Composable
internal fun RoundSimilarOverlay(
    items: List<LanternsViewModel.RoundSimilar>,
    onAdd: (List<Int>) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val context = androidx.compose.ui.platform.LocalContext.current
    val index = remember(context) { com.example.mutqan.quran.QuranRepository.getInstance(context).ayahIndex }
    fun ref(id: Int) = if (id in index) "${com.example.mutqan.data.SurahData.nameOf(index.surahOf(id))} · ${index.ayahOf(id)}" else ""
    // Opaque: the end screen behind it would show its buttons through.
    Scrim(color = Color(0xFF060A1A)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.game_similar_title),
                style = TextStyle(fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = GameColors.Gold),
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.game_similar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items.size, key = { items[it].verse.ayahId }) { i ->
                    val item = items[i]
                    // The ones chosen to go in a group with the verse: all of them to begin with.
                    var chosen by remember(item) { mutableStateOf(item.similar.map { it.ayahId }.toSet()) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GameColors.Panel)
                            .border(1.dp, GameColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(14.dp)
                    ) {
                        Text(ref(item.verse.ayahId), style = MaterialTheme.typography.labelLarge, color = GameColors.Gold)
                        MushafText(span = remember(item) { MushafSpan.of(item.verse.words) }, size = 22.sp, color = GameColors.Gold)
                        Spacer(Modifier.height(6.dp))
                        for (other in item.similar) {
                            val on = other.ayahId in chosen
                            // The words it shares in gold; the word taken for the verse's own in red.
                            val shared = remember(other) {
                                other.matched.flatMap { r -> r.mapNotNull { pos -> other.words.getOrNull(pos - 1)?.id } }
                                    .associateWith { GameColors.Gold } + listOfNotNull(other.taken).associateWith { GameColors.Red }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(role = Role.Checkbox) { chosen = if (on) chosen - other.ayahId else chosen + other.ayahId }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (on) Icons.Filled.CheckCircle
                                    else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = if (on) GameColors.Gold else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(top = 6.dp).height(22.dp).width(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ref(other.ayahId), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
                                    if (other.taken != null) {
                                        Text(
                                            stringResource(R.string.game_similar_taken),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GameColors.Red
                                        )
                                    }
                                    MushafText(
                                        span = remember(other) { MushafSpan.of(other.words) },
                                        size = 20.sp,
                                        color = Color.White.copy(alpha = if (on) 0.92f else 0.5f),
                                        wordColors = if (on) shared else emptyMap()
                                    )
                                }
                            }
                        }
                        if (item.more > 0) {
                            Text(
                                pluralStringResource(R.plurals.game_similar_more, item.more, item.more),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        GameButton(
                            pluralStringResource(R.plurals.game_similar_add, chosen.size + 1, chosen.size + 1),
                            { onAdd(listOf(item.verse.ayahId) + item.similar.map { it.ayahId }.filter { it in chosen }) },
                            Modifier.fillMaxWidth(),
                            primary = false
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GameButton(stringResource(R.string.game_back), onClose, Modifier.widthIn(min = 220.dp))
        }
    }
}
