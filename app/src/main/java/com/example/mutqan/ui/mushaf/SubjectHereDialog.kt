package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.data.local.entity.UserSubjectEntity
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.theme.FolioTheme

/**
 * The row in the verse sheet that makes this verse the start of a subject. With no subject starting here it invites one
 * ("Start a subject here"); with one, it shows the title and a pencil, and a tap edits it.
 */
@Composable
fun SubjectRow(title: String?, onClick: () -> Unit) {
    val shape = FolioTheme.shapes.control
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(
            text = if (title == null) stringResource(R.string.mushaf_subject_start_here) else stringResource(R.string.mushaf_subject_here, title),
            style = MaterialTheme.typography.bodyLarge,
            color = if (title == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (title == null) Icons.Filled.Add else Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Names the subject that starts at the verse pressed, or renames or deletes the one that already does. The start verse is
 * not asked for: it is the verse the sheet is about ([reference]). Deleting asks once more before it does it.
 */
@Composable
fun SubjectHereDialog(
    reference: String,
    existing: UserSubjectEntity?,
    onSave: (String) -> Unit,
    onDelete: (UserSubjectEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(existing?.subjectText.orEmpty()) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete && existing != null) {
        PaperDialog(
            onDismissRequest = { confirmingDelete = false },
            text = { Text(stringResource(R.string.delete_subject_confirmation)) },
            confirmButton = {
                TextButton(onClick = { onDelete(existing) }) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    } else {
        PaperDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (existing == null) R.string.add_subject else R.string.edit_subjects)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.mushaf_subject_starts_at, reference),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.subject_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existing != null) {
                        TextButton(onClick = { confirmingDelete = true }) {
                            Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
