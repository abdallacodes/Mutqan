package com.example.mutqan.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.theme.FolioTheme

private const val JUZ_COUNT = 30
private const val PER_ROW = 5

/**
 * Choose a juz to open its map (the quarters, their subjects and the joint test). The map is also reached from the Hifz
 * board; this is the way to it from Practice, where there is no juz to start from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuzPickerSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = PaperSpace.Gutter)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.juz_pick_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..JUZ_COUNT).chunked(PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { juz -> JuzCell(juz, onClick = { onPick(juz) }, modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JuzCell(juz: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = FolioTheme.shapes.control
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(FolioTheme.colors.folio, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.plan_item_juz, juz),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
