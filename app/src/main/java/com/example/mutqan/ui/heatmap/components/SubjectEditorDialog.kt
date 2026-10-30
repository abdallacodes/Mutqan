package com.example.mutqan.ui.heatmap.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.quran.QuranAyah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectEditorDialog(
    ayahs: List<QuranAyah>,
    initialText: String = "",
    initialAyahId: Int? = null,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedVerse by remember { 
        mutableStateOf(ayahs.find { it.id == initialAyahId } ?: ayahs.firstOrNull()) 
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (initialAyahId == null) stringResource(R.string.add_subject) 
                else stringResource(R.string.edit_subjects)
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.subject_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedVerse?.let { stringResource(R.string.subject_ayah_option, it.ayah, it.surah) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.subject_start_ayah_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ayahs.forEach { verse ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subject_ayah_option, verse.ayah, verse.surah)) },
                                onClick = {
                                    selectedVerse = verse
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    selectedVerse?.let { onSave(text, it.id) }
                },
                enabled = text.isNotBlank() && selectedVerse != null
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
