package com.example.mutqan.ui.recall

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.quran.QuranAyah
import com.example.mutqan.quran.toCleanQuranicText
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.revision.OutcomePicker
import com.example.mutqan.ui.theme.AmiriFontFamily

/** Recite a passage from memory, reveal it, then record how it went (FR-013). */
@Composable
fun RecallExerciseScreen(startPage: Int, endPage: Int, onBack: () -> Unit, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val viewModel: RecallExerciseViewModel = viewModel(
        key = "recall-$startPage-$endPage",
        factory = RecallExerciseViewModelFactory(context, startPage, endPage)
    )
    val state by viewModel.state.collectAsState()
    val range = if (endPage > startPage) stringResource(R.string.pages_range, startPage, endPage)
    else stringResource(R.string.dialog_page_title, startPage)
    val savedToast = stringResource(R.string.recall_saved_toast)

    Scaffold(
        topBar = { PaperHeader(title = stringResource(R.string.recall_title), subtitle = range, onBack = onBack) },
        bottomBar = {
            if (state.revealed) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    Hairline()
                    PaperButton(
                        text = stringResource(R.string.recall_btn_save),
                        onClick = {
                            viewModel.save {
                                Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!state.revealed) {
            Hidden(
                range = range,
                ayahs = state.ayahs,
                showCue = state.showCue,
                onToggleCue = viewModel::toggleCue,
                onReveal = viewModel::reveal,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.ayahs, key = { it.id }) { ayah ->
                    Text(
                        text = "${ayah.text.toCleanQuranicText()} ﴿${ayah.ayah}﴾",
                        style = ayahStyle(),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    OrnamentDivider()
                    Spacer(Modifier.height(12.dp))
                    OutcomePicker(
                        outcome = state.outcome,
                        tags = state.tags,
                        onOutcomeChange = viewModel::setOutcome,
                        onTagsChange = viewModel::setTags
                    )
                    PaperLink(stringResource(R.string.home_btn_open_mushaf), onClick = { onOpenMushaf(startPage) })
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ayahStyle() = MaterialTheme.typography.bodyLarge.copy(
    textDirection = TextDirection.Rtl,
    fontFamily = AmiriFontFamily,
    fontSize = 26.sp,
    lineHeight = 52.sp
)

/** The text is hidden. An optional cue shows only the first word of each ayah. */
@Composable
private fun Hidden(
    range: String,
    ayahs: List<QuranAyah>,
    showCue: Boolean,
    onToggleCue: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        OrnamentDivider()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.recall_prompt, range),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        PaperButton(text = stringResource(R.string.recall_btn_reveal), onClick = onReveal, modifier = Modifier.fillMaxWidth())
        PaperLink(
            text = stringResource(if (showCue) R.string.recall_btn_hide_cue else R.string.recall_btn_cue),
            onClick = onToggleCue
        )
        if (showCue) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ayahs, key = { it.id }) { ayah ->
                    val first = ayah.text.toCleanQuranicText().substringBefore(' ')
                    Text(
                        text = "$first … ﴿${ayah.ayah}﴾",
                        style = ayahStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
