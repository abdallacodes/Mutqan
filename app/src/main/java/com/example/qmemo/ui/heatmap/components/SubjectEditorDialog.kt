package com.example.qmemo.ui.heatmap.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.qmemo.R
import com.example.qmemo.data.local.entity.VerseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectEditorDialog(
    ayahs: List<VerseEntity>,
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
                    label = { Text("Subject Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedVerse?.let { "Ayah ${it.ayahNumber} (Surah ${it.surahId})" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Start Ayah") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ayahs.forEach { verse ->
                            DropdownMenuItem(
                                text = { Text("Ayah ${verse.ayahNumber} (Surah ${verse.surahId})") },
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
