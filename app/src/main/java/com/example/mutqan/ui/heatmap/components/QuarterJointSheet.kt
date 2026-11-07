package com.example.mutqan.ui.heatmap.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.quran.QuranRub
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.heatmap.JointTest

/**
 * Tests the joint between two hizb quarters: the end of the first is shown, the user recalls how the next begins, then
 * reveals it and says how it went. The result is kept as an exercise result, apart from page revisions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarterJointSheet(
    test: JointTest,
    onFinish: (RecallOutcome) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var revealed by rememberSaveable(test.from.number) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.joint_title),
                style = MaterialTheme.typography.titleMedium
            )
            SectionLabel(stringResource(R.string.joint_ends_with, quarterLabel(test.from)))
            AyahText(ayahId = test.from.endAyahId, size = 24.sp)
            Hairline()
            Text(
                text = stringResource(R.string.joint_how_begins, quarterLabel(test.to)),
                style = MaterialTheme.typography.titleSmall
            )
            if (!revealed) {
                PaperButton(
                    text = stringResource(R.string.recall_btn_reveal),
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AyahText(ayahId = test.to.startAyahId, size = 24.sp)
                SectionLabel(stringResource(R.string.ex_how_did_it_go))
                PaperButton(
                    text = stringResource(R.string.ex_knew),
                    onClick = { onFinish(RecallOutcome.INDEPENDENT) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PaperOutlinedButton(
                        text = stringResource(R.string.ex_hint),
                        onClick = { onFinish(RecallOutcome.HINTED) },
                        modifier = Modifier.weight(1f)
                    )
                    PaperOutlinedButton(
                        text = stringResource(R.string.ex_missed),
                        onClick = { onFinish(RecallOutcome.UNABLE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** "Juz 3 · Quarter 8": a quarter named by where it is, so a joint across two juz reads right. */
@Composable
private fun quarterLabel(rub: QuranRub): String = stringResource(R.string.quarter_full, rub.juz, rub.quarterInJuz)
