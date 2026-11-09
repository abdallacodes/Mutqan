package com.example.mutqan.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mutqan.R
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PlanItem
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.ui.components.DialogSystemBars
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperProgress
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.revision.OutcomePreview
import com.example.mutqan.ui.revision.caption
import com.example.mutqan.ui.revision.rememberOutcomeEffects
import androidx.compose.foundation.layout.PaddingValues
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.theme.FolioTheme
import com.example.mutqan.ui.components.rememberHaptics

/**
 * Today's due portions one after another: each shows what it is, and a tap on how it went logs it and brings up
 * the next. Nothing is stored here beyond what was skipped; what is left is whatever the plan still has due, so
 * leaving (to read a page in the Mushaf, say) and coming back picks up where it stopped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewSession(
    due: List<PlanItem>,
    doneCount: Int,
    onLog: (PlanItem, RecallOutcome, Set<MistakeTag>) -> Unit,
    onOpenMushaf: (Int) -> Unit,
    onClose: () -> Unit
) {
    var skipped by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    val order = due.filter { it.startPage !in skipped } + skipped.mapNotNull { s -> due.firstOrNull { it.startPage == s } }
    val current = order.firstOrNull()
    var tags by remember(current?.startPage) { mutableStateOf(emptySet<MistakeTag>()) }
    var showTags by remember(current?.startPage) { mutableStateOf(false) }
    val effects = rememberOutcomeEffects(current?.let { OutcomePreview(it.startPage..it.endPage) })
    val total = doneCount + due.size

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        DialogSystemBars()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = PaperSpace.Gutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.session_close))
                }
                if (current != null) {
                    Text(
                        text = stringResource(R.string.session_progress, doneCount + 1, total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PaperProgress(
                fraction = if (total == 0) 1f else doneCount.toFloat() / total,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
            )

            if (current == null) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(PaperSpace.Gutter),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_all_done),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(24.dp))
                    PaperButton(stringResource(R.string.session_finish), onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PaperSpace.Gutter, vertical = PaperSpace.Section)
                ) {
                    HeroCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = current.label(),
                            style = MaterialTheme.typography.displayMedium
                        )
                        if (current.unit != PlanUnit.PAGE) {
                            Text(
                                text = stringResource(R.string.pages_range, current.startPage, current.endPage),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        current.tier?.let {
                            Text(it.hint(), style = MaterialTheme.typography.labelLarge, color = FolioTheme.colors.heroAccent)
                        }
                    }
                    PaperOutlinedButton(
                        text = stringResource(R.string.home_btn_open_mushaf),
                        onClick = { onOpenMushaf(current.startPage) },
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    )

                    Text(
                        text = stringResource(R.string.session_how_did_it_go),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
                    )
                    if (showTags) {
                        FlowRow(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MistakeTag.entries.forEach { tag ->
                                PaperChip(
                                    selected = tag in tags,
                                    onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                                    label = { Text(tag.localizedLabel()) }
                                )
                            }
                        }
                    } else {
                        PaperLink(
                            text = stringResource(R.string.session_add_mistakes),
                            onClick = { showTags = true },
                            soft = true,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    RecallOutcome.entries.forEach { option ->
                        OutcomeButton(
                            label = option.localizedLabel(),
                            caption = effects?.get(option)?.caption(),
                            onClick = { onLog(current, option, if (option.isRecallAttempt) tags else emptySet()) }
                        )
                    }

                    if (due.size > 1) {
                        PaperLink(
                            text = stringResource(R.string.session_skip),
                            onClick = { skipped = skipped.filter { it != current.startPage } + current.startPage },
                            soft = true,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** One way it could have gone: a card, a tap logs it. Under the name, what it would do to the strength of the pages. */
@Composable
private fun OutcomeButton(label: String, caption: Pair<String, Color>?, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Folio(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        onClick = { haptics.confirm(); onClick() },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (caption != null) {
            Text(text = caption.first, style = MaterialTheme.typography.labelMedium, color = caption.second)
        }
    }
}
