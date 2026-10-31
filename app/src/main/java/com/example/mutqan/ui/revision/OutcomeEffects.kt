package com.example.mutqan.ui.revision

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.mutqan.domain.LogEffect
import com.example.mutqan.domain.LogEffects
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.domain.RevisionLog
import com.example.mutqan.domain.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * What an [OutcomePicker] should preview: the pages being logged ([pages] is null until the user has said which),
 * and the date the revision is for.
 */
class OutcomePreview(val pages: IntRange?, val dateMillis: Long = 0L)

private class History(val logs: List<RevisionLog>, val covered: BooleanArray?, val rated: Map<Int, PlanTier>)

/**
 * For each outcome, what logging it for [preview]'s pages would change; null while there is nothing to preview. It
 * follows the history, so a log made meanwhile is taken into account.
 */
@Composable
fun rememberOutcomeEffects(preview: OutcomePreview?): Map<RecallOutcome, LogEffect>? {
    val context = LocalContext.current.applicationContext
    val history by remember(context) {
        val study = StudyRepository.create(context)
        combine(study.revisionLogs(), study.coveredPages(), study.selfRatedStrength(), ::History)
    }.collectAsState(initial = null)
    val pages = preview?.pages
    val date = preview?.dateMillis ?: 0L
    val effects by produceState<Map<RecallOutcome, LogEffect>?>(null, pages, date, history) {
        val h = history
        value = if (h == null || pages == null) null else withContext(Dispatchers.Default) {
            LogEffects.previewAll(h.logs, pages.first, pages.last, date, System.currentTimeMillis(), h.covered, h.rated)
        }
    }
    return effects
}
