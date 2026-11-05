package com.example.mutqan.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.mutqan.ui.components.PaperChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.SavedKind
import com.example.mutqan.domain.SavedPassage
import com.example.mutqan.domain.WeakTag
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.components.priorityLabel

/**
 * Saves a passage as a bookmark or a weak passage, or edits one already saved ([initial]).
 * [reference] is the human-readable location shown as the title.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavePassageSheet(
    reference: String,
    initial: SavedPassage?,
    onSave: (kind: SavedKind, tags: Set<WeakTag>, priority: Int, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var kind by remember(initial) { mutableStateOf(initial?.kind ?: SavedKind.WEAK) }
    var tags by remember(initial) { mutableStateOf(initial?.tags ?: emptySet()) }
    var priority by remember(initial) { mutableIntStateOf(initial?.priority ?: SavedPassage.PRIORITY_NORMAL) }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.saved_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = reference,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Label(stringResource(R.string.saved_label_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SavedKind.entries.forEach { option ->
                    PaperChip(
                        selected = option == kind,
                        onClick = { kind = option },
                        label = { Text(option.localizedLabel()) }
                    )
                }
            }

            if (kind == SavedKind.WEAK) {
                Label(stringResource(R.string.saved_label_tags))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeakTag.entries.forEach { tag ->
                        PaperChip(
                            selected = tag in tags,
                            onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                            label = { Text(tag.localizedLabel()) }
                        )
                    }
                }
            }

            Label(stringResource(R.string.saved_label_priority))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SavedPassage.PRIORITY_LOW, SavedPassage.PRIORITY_NORMAL, SavedPassage.PRIORITY_HIGH).forEach { option ->
                    PaperChip(
                        selected = option == priority,
                        onClick = { priority = option },
                        label = { Text(priorityLabel(option)) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.saved_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onSave(kind, tags, priority, note) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Black)
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.saved_btn_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
